package com.ovion.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

object Api {
    private const val BASE = BuildConfig.SUPABASE_URL
    private const val KEY = BuildConfig.SUPABASE_KEY
    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile var session: Session? = null
    var onSessionChanged: ((Session?) -> Unit)? = null

    val uid: String get() = session?.userId ?: ""

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // ---------- low level ----------
    private suspend fun req(
        method: String,
        path: String,
        body: String? = null,
        bytes: ByteArray? = null,
        mime: String? = null,
        prefer: String? = null,
        authed: Boolean = true,
        canRetry: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(BASE + path).addHeader("apikey", KEY)
        val s = session
        if (authed && s != null) b.addHeader("Authorization", "Bearer ${s.accessToken}")
        if (prefer != null) b.addHeader("Prefer", prefer)
        val reqBody: RequestBody? = when {
            bytes != null -> bytes.toRequestBody((mime ?: "application/octet-stream").toMediaType())
            body != null -> body.toRequestBody(json)
            method == "POST" || method == "PATCH" || method == "PUT" -> "{}".toRequestBody(json)
            else -> null
        }
        b.method(method, reqBody)
        try {
            client.newCall(b.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                when {
                    r.isSuccessful -> text
                    r.code == 401 && authed && canRetry && s != null -> {
                        if (refresh()) req(method, path, body, bytes, mime, prefer, authed, false)
                        else {
                            session = null
                            onSessionChanged?.invoke(null)
                            throw ApiException("انتهت الجلسة، سجّل الدخول مرة أخرى")
                        }
                    }
                    else -> throw ApiException(Parse.error(text))
                }
            }
        } catch (e: IOException) {
            throw ApiException("تعذر الاتصال بالإنترنت")
        }
    }

    private fun sessionFrom(o: JSONObject): Session? {
        val access = o.optString("access_token")
        if (access.isBlank()) return null
        val uid = o.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() }
            ?: userIdFromToken(access) ?: return null
        return Session(access, o.optString("refresh_token"), uid)
    }

    fun userIdFromToken(token: String): String? = try {
        val part = token.split(".")[1]
        val d = Base64.decode(part, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        JSONObject(String(d, Charsets.UTF_8)).optString("sub").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private suspend fun refresh(): Boolean {
        val s = session ?: return false
        if (s.refreshToken.isBlank()) return false
        return try {
            val text = req(
                "POST", "/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", s.refreshToken).toString(),
                authed = false, canRetry = false
            )
            val n = sessionFrom(JSONObject(text)) ?: return false
            session = n
            onSessionChanged?.invoke(n)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ---------- auth ----------
    suspend fun login(email: String, password: String): Session {
        val text = req(
            "POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password).toString(), authed = false
        )
        val s = sessionFrom(JSONObject(text)) ?: throw ApiException("تعذر تسجيل الدخول")
        session = s
        return s
    }

    /** يرجع null لو الحساب محتاج تأكيد بالبريد. */
    suspend fun register(email: String, password: String, name: String): Session? {
        val text = req(
            "POST", "/auth/v1/signup",
            JSONObject().put("email", email).put("password", password)
                .put("data", JSONObject().put("display_name", name)).toString(), authed = false
        )
        val s = sessionFrom(JSONObject(text))
        if (s != null) session = s
        return s
    }

    // ---------- posts ----------
    private const val POST_SEL =
        "id,author_id,content,image_url,created_at,likes_count,comments_count,shares_count,profiles!author_id(display_name,username,avatar_url)"
    const val ORDER_NEW = "created_at.desc"
    const val ORDER_TOP = "likes_count.desc,comments_count.desc,created_at.desc"

    suspend fun posts(order: String, authorId: String? = null, query: String? = null): List<Post> {
        var p = "/rest/v1/posts?select=$POST_SEL&order=$order&limit=50"
        if (authorId != null) p += "&author_id=eq.$authorId"
        if (!query.isNullOrBlank()) p += "&content=ilike.*${enc(query.trim())}*"
        return Parse.posts(req("GET", p))
    }

    suspend fun likedPostIds(): Set<String> {
        val a = org.json.JSONArray(req("GET", "/rest/v1/likes?select=post_id&user_id=eq.$uid&limit=1000"))
        return (0 until a.length()).map { a.getJSONObject(it).optString("post_id") }.toSet()
    }

    suspend fun like(postId: String) {
        req("POST", "/rest/v1/likes", JSONObject().put("post_id", postId).put("user_id", uid).toString(), prefer = "return=minimal")
    }

    suspend fun unlike(postId: String) {
        req("DELETE", "/rest/v1/likes?post_id=eq.$postId&user_id=eq.$uid")
    }

    suspend fun createPost(content: String, imageUrl: String?) {
        val o = JSONObject().put("author_id", uid).put("content", content)
        if (imageUrl != null) o.put("image_url", imageUrl)
        req("POST", "/rest/v1/posts", o.toString(), prefer = "return=minimal")
    }

    suspend fun updatePost(id: String, content: String) {
        req("PATCH", "/rest/v1/posts?id=eq.$id&author_id=eq.$uid", JSONObject().put("content", content).toString(), prefer = "return=minimal")
    }

    suspend fun deletePost(id: String) {
        req("DELETE", "/rest/v1/posts?id=eq.$id&author_id=eq.$uid")
    }

    suspend fun sharePost(id: String) {
        req("POST", "/rest/v1/shares", JSONObject().put("post_id", id).put("user_id", uid).toString(), prefer = "return=minimal")
    }

    // ---------- comments ----------
    suspend fun comments(postId: String): List<Comment> = Parse.comments(
        req("GET", "/rest/v1/comments?select=id,post_id,author_id,parent_id,content,created_at,profiles!author_id(display_name,avatar_url)&post_id=eq.$postId&order=created_at.asc&limit=300")
    )

    suspend fun addComment(postId: String, content: String, parentId: String?) {
        val o = JSONObject().put("post_id", postId).put("author_id", uid).put("content", content)
        if (parentId != null) o.put("parent_id", parentId)
        req("POST", "/rest/v1/comments", o.toString(), prefer = "return=minimal")
    }

    suspend fun deleteComment(id: String) {
        req("DELETE", "/rest/v1/comments?id=eq.$id&author_id=eq.$uid")
    }

    // ---------- profiles / follow ----------
    suspend fun profile(id: String): Profile {
        val l = Parse.profiles(req("GET", "/rest/v1/profiles?select=*&id=eq.$id"))
        return l.firstOrNull() ?: throw ApiException("الحساب غير موجود")
    }

    suspend fun profilesByIds(ids: Collection<String>): List<Profile> {
        if (ids.isEmpty()) return emptyList()
        return Parse.profiles(req("GET", "/rest/v1/profiles?select=*&id=in.(${ids.joinToString(",")})"))
    }

    suspend fun updateProfile(name: String, username: String, bio: String, avatar: String?, cover: String?) {
        val o = JSONObject().put("display_name", name).put("bio", bio)
        o.put("username", if (username.isBlank()) JSONObject.NULL else username)
        if (avatar != null) o.put("avatar_url", avatar)
        if (cover != null) o.put("cover_url", cover)
        req("PATCH", "/rest/v1/profiles?id=eq.$uid", o.toString(), prefer = "return=minimal")
    }

    suspend fun searchUsers(q: String): List<Profile> {
        val e = enc(q.trim())
        return Parse.profiles(req("GET", "/rest/v1/profiles?select=*&or=(display_name.ilike.*$e*,username.ilike.*$e*)&limit=30"))
    }

    suspend fun suggestions(): List<Profile> =
        Parse.profiles(req("GET", "/rest/v1/profiles?select=*&id=neq.$uid&order=followers_count.desc,created_at.desc&limit=30"))

    suspend fun followingIds(): Set<String> {
        val a = org.json.JSONArray(req("GET", "/rest/v1/follows?select=following_id&follower_id=eq.$uid&limit=2000"))
        return (0 until a.length()).map { a.getJSONObject(it).optString("following_id") }.toSet()
    }

    suspend fun follow(id: String) {
        req("POST", "/rest/v1/follows", JSONObject().put("follower_id", uid).put("following_id", id).toString(), prefer = "return=minimal")
    }

    suspend fun unfollow(id: String) {
        req("DELETE", "/rest/v1/follows?follower_id=eq.$uid&following_id=eq.$id")
    }

    // ---------- messages ----------
    suspend fun conversations(): List<Convo> {
        val all = Parse.messages(
            req("GET", "/rest/v1/messages?select=*&or=(sender_id.eq.$uid,receiver_id.eq.$uid)&order=created_at.desc&limit=300")
        )
        val byPeer = LinkedHashMap<String, MutableList<Msg>>()
        for (m in all) {
            val peer = if (m.senderId == uid) m.receiverId else m.senderId
            byPeer.getOrPut(peer) { mutableListOf() }.add(m)
        }
        val profiles = profilesByIds(byPeer.keys).associateBy { it.id }
        return byPeer.mapNotNull { (peer, list) ->
            val p = profiles[peer] ?: return@mapNotNull null
            Convo(p, list.first(), list.count { it.receiverId == uid && !it.read })
        }
    }

    suspend fun chat(peer: String): List<Msg> = Parse.messages(
        req("GET", "/rest/v1/messages?select=*&or=(and(sender_id.eq.$uid,receiver_id.eq.$peer),and(sender_id.eq.$peer,receiver_id.eq.$uid))&order=created_at.asc&limit=300")
    )

    suspend fun sendMessage(peer: String, content: String) {
        req("POST", "/rest/v1/messages", JSONObject().put("sender_id", uid).put("receiver_id", peer).put("content", content).toString(), prefer = "return=minimal")
    }

    suspend fun markChatRead(peer: String) {
        req("PATCH", "/rest/v1/messages?sender_id=eq.$peer&receiver_id=eq.$uid&read_at=is.null", JSONObject().put("read_at", TimeFmt.nowIso()).toString(), prefer = "return=minimal")
    }

    suspend fun unreadMessages(): Int =
        org.json.JSONArray(req("GET", "/rest/v1/messages?select=id&receiver_id=eq.$uid&read_at=is.null&limit=99")).length()

    // ---------- notifications ----------
    suspend fun notifications(): List<Notif> = Parse.notifs(
        req("GET", "/rest/v1/notifications?select=id,type,post_id,read_at,created_at,actor_id,actor:profiles!actor_id(display_name,avatar_url)&user_id=eq.$uid&order=created_at.desc&limit=60")
    )

    suspend fun unreadNotifications(): Int =
        org.json.JSONArray(req("GET", "/rest/v1/notifications?select=id&user_id=eq.$uid&read_at=is.null&limit=99")).length()

    suspend fun markNotificationsRead() {
        req("PATCH", "/rest/v1/notifications?user_id=eq.$uid&read_at=is.null", JSONObject().put("read_at", TimeFmt.nowIso()).toString(), prefer = "return=minimal")
    }

    // ---------- storage ----------
    suspend fun uploadImage(bytes: ByteArray): String {
        val path = "$uid/${UUID.randomUUID()}.jpg"
        req("POST", "/storage/v1/object/media/$path", bytes = bytes, mime = "image/jpeg")
        return "$BASE/storage/v1/object/public/media/$path"
    }
}
