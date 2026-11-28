package com.ovion.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Session(val accessToken: String, val refreshToken: String, val userId: String)

data class Profile(
    val id: String,
    val name: String,
    val username: String,
    val bio: String,
    val avatar: String?,
    val cover: String?,
    val followers: Int,
    val following: Int
)

data class Post(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String?,
    val content: String,
    val imageUrl: String?,
    val createdAt: String,
    val likes: Int,
    val comments: Int,
    val shares: Int
)

data class Comment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String?,
    val parentId: String?,
    val content: String,
    val createdAt: String
)

data class Notif(
    val id: String,
    val type: String,
    val actorId: String,
    val actorName: String,
    val actorAvatar: String?,
    val postId: String?,
    val read: Boolean,
    val createdAt: String
)

data class Msg(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val createdAt: String,
    val read: Boolean
)

data class Convo(val peer: Profile, val last: Msg, val unread: Int)

object Parse {
    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    fun profile(o: JSONObject) = Profile(
        id = o.optString("id"),
        name = o.str("display_name") ?: "مستخدم OVION",
        username = o.str("username") ?: "",
        bio = o.str("bio") ?: "",
        avatar = o.str("avatar_url"),
        cover = o.str("cover_url"),
        followers = o.optInt("followers_count", 0),
        following = o.optInt("following_count", 0)
    )

    fun profiles(text: String): List<Profile> {
        val a = JSONArray(text)
        return (0 until a.length()).map { profile(a.getJSONObject(it)) }
    }

    fun posts(text: String): List<Post> {
        val a = JSONArray(text)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val p = o.optJSONObject("profiles")
            Post(
                id = o.optString("id"),
                authorId = o.optString("author_id"),
                authorName = p?.str("display_name") ?: "مستخدم OVION",
                authorAvatar = p?.str("avatar_url"),
                content = o.optString("content"),
                imageUrl = o.str("image_url"),
                createdAt = o.optString("created_at"),
                likes = o.optInt("likes_count", 0),
                comments = o.optInt("comments_count", 0),
                shares = o.optInt("shares_count", 0)
            )
        }
    }

    fun comments(text: String): List<Comment> {
        val a = JSONArray(text)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val p = o.optJSONObject("profiles")
            Comment(
                id = o.optString("id"),
                postId = o.optString("post_id"),
                authorId = o.optString("author_id"),
                authorName = p?.str("display_name") ?: "مستخدم OVION",
                authorAvatar = p?.str("avatar_url"),
                parentId = o.str("parent_id"),
                content = o.optString("content"),
                createdAt = o.optString("created_at")
            )
        }
    }

    fun notifs(text: String): List<Notif> {
        val a = JSONArray(text)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val p = o.optJSONObject("actor")
            Notif(
                id = o.optString("id"),
                type = o.optString("type"),
                actorId = o.optString("actor_id"),
                actorName = p?.str("display_name") ?: "مستخدم OVION",
                actorAvatar = p?.str("avatar_url"),
                postId = o.str("post_id"),
                read = !o.isNull("read_at"),
                createdAt = o.optString("created_at")
            )
        }
    }

    fun messages(text: String): List<Msg> {
        val a = JSONArray(text)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Msg(
                id = o.optString("id"),
                senderId = o.optString("sender_id"),
                receiverId = o.optString("receiver_id"),
                content = o.optString("content"),
                createdAt = o.optString("created_at"),
                read = !o.isNull("read_at")
            )
        }
    }

    /** يحوّل رسالة خطأ Supabase إلى نص عربي مفهوم. */
    fun error(text: String): String {
        val raw = try {
            val o = JSONObject(text)
            listOf("msg", "message", "error_description", "error")
                .map { o.optString(it) }
                .firstOrNull { it.isNotBlank() } ?: ""
        } catch (_: Exception) {
            ""
        }
        val l = raw.lowercase(Locale.ROOT)
        return when {
            l.contains("invalid login") -> "البريد الإلكتروني أو كلمة المرور غير صحيحة"
            l.contains("already registered") -> "هذا البريد مسجّل بالفعل"
            l.contains("email not confirmed") -> "أكّد بريدك الإلكتروني أولاً ثم سجّل الدخول"
            l.contains("duplicate") || l.contains("unique") -> "هذه القيمة مستخدمة بالفعل"
            l.contains("row-level security") -> "ليس لديك صلاحية لتنفيذ هذا الإجراء"
            l.contains("rate limit") -> "محاولات كثيرة، انتظر قليلاً ثم أعد المحاولة"
            raw.isNotBlank() -> raw
            else -> "حدث خطأ، حاول مرة أخرى"
        }
    }
}

object TimeFmt {
    private fun parse(iso: String): Date? = try {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        f.parse(iso.take(19))
    } catch (_: Exception) {
        null
    }

    fun nowIso(): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date())
    }

    fun ago(iso: String, now: Long = System.currentTimeMillis()): String {
        val t = parse(iso)?.time ?: return ""
        val s = ((now - t) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "الآن"
            s < 3600 -> "منذ ${s / 60} د"
            s < 86400 -> "منذ ${s / 3600} س"
            s < 86400 * 7 -> "منذ ${s / 86400} يوم"
            else -> iso.take(10)
        }
    }
}
