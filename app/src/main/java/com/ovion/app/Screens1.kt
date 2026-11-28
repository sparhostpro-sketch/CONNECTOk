package com.ovion.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ================= Auth =================
@Composable
fun AuthScreen(onSession: (Session) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("OVION", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text("أوفيون", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        if (register) {
            OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
            email, { email = it }, label = { Text("البريد الإلكتروني") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            password, { password = it }, label = { Text("كلمة المرور") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Button(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            onClick = {
                if (email.isBlank() || password.length < 8 || (register && name.isBlank())) {
                    message = "تأكد من إدخال البيانات (كلمة المرور 8 أحرف على الأقل)"
                    return@Button
                }
                loading = true
                message = ""
                scope.launch {
                    try {
                        if (register) {
                            val s = Api.register(email.trim(), password, name.trim())
                            if (s != null) onSession(s) else {
                                message = "تم التسجيل. راجع بريدك لتأكيد الحساب ثم سجّل الدخول."
                                register = false
                            }
                        } else onSession(Api.login(email.trim(), password))
                    } catch (e: ApiException) {
                        message = e.message ?: "حدث خطأ"
                    }
                    loading = false
                }
            }
        ) { Text(if (loading) "يرجى الانتظار..." else if (register) "إنشاء حساب" else "تسجيل الدخول") }
        TextButton(onClick = { register = !register; message = "" }) {
            Text(if (register) "لديك حساب؟ تسجيل الدخول" else "ليس لديك حساب؟ إنشاء حساب")
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(8.dp))
    }
}

// ================= Post card =================
@Composable
fun PostCard(post: Post, app: AppState, onChanged: () -> Unit, openDetail: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val liked = app.liked[post.id] == true
    val likes = app.likeCounts[post.id] ?: post.likes
    val mine = post.authorId == Api.uid
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var shares by remember(post.id) { mutableIntStateOf(post.shares) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.authorName, post.authorAvatar, 42.dp) { app.push(Route.User(post.authorId)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(TimeFmt.ago(post.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (mine) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "المزيد") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("تعديل") }, onClick = { menu = false; editing = true })
                        DropdownMenuItem(text = { Text("حذف") }, onClick = { menu = false; deleting = true })
                    }
                }
            }
            if (post.content.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(post.content, fontSize = 16.sp, lineHeight = 25.sp,
                    modifier = if (openDetail) Modifier.clickable { app.push(Route.PostDetail(post)) } else Modifier)
            }
            if (post.imageUrl != null) {
                Spacer(Modifier.height(10.dp))
                AsyncImage(
                    model = post.imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).clip(RoundedCornerShape(14.dp))
                        .let { if (openDetail) it.clickable { app.push(Route.PostDetail(post)) } else it }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val now = !liked
                    app.liked[post.id] = now
                    app.likeCounts[post.id] = (likes + if (now) 1 else -1).coerceAtLeast(0)
                    scope.launch {
                        try { if (now) Api.like(post.id) else Api.unlike(post.id) }
                        catch (e: ApiException) {
                            app.liked[post.id] = !now
                            app.likeCounts[post.id] = likes
                            toast(ctx, e.message ?: "تعذر تنفيذ الإجراء")
                        }
                    }
                }) {
                    Icon(
                        if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "إعجاب",
                        tint = if (liked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("$likes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { app.push(Route.PostDetail(post)) }) {
                    Icon(Icons.Default.ChatBubbleOutline, "تعليقات", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("${post.comments}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${post.authorName}: ${post.content}\n\nOVION")
                    }
                    ctx.startActivity(Intent.createChooser(i, "مشاركة المنشور"))
                    shares += 1
                    scope.launch { try { Api.sharePost(post.id) } catch (_: ApiException) { shares -= 1 } }
                }) {
                    Icon(Icons.Default.Share, "مشاركة", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("$shares", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (editing) {
        var text by remember { mutableStateOf(post.content) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("تعديل المنشور") },
            text = { OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { Api.updatePost(post.id, text.trim()); editing = false; onChanged() }
                        catch (e: ApiException) { toast(ctx, e.message ?: "تعذر التعديل") }
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("إلغاء") } }
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("حذف المنشور؟") },
            text = { Text("لا يمكن التراجع عن الحذف.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { Api.deletePost(post.id); deleting = false; onChanged() }
                        catch (e: ApiException) { toast(ctx, e.message ?: "تعذر الحذف") }
                    }
                }) { Text("حذف", color = MaterialTheme.colorScheme.secondary) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("إلغاء") } }
        )
    }
}

// ================= Feed list =================
@Composable
fun FeedList(
    key: Any, app: AppState, poll: Boolean = false, emptyTitle: String = "لا توجد منشورات بعد",
    header: (@Composable () -> Unit)? = null, loader: suspend () -> List<Post>
) {
    var state by remember(key) { mutableStateOf<Load<List<Post>>>(Load.Loading) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(key, app.refreshTick, tick) {
        val r = safe(loader)
        if (r is Load.Ok) r.v.forEach { app.likeCounts[it.id] = it.likes }
        state = r
        while (poll) {
            delay(20000)
            val n = safe(loader)
            if (n is Load.Ok) { n.v.forEach { app.likeCounts[it.id] = it.likes }; state = n }
        }
    }

    when (val s = state) {
        Load.Loading -> CenterLoading()
        is Load.Err -> StateMessage("تعذر التحميل", s.msg, "إعادة المحاولة") { state = Load.Loading; tick++ }
        is Load.Ok -> LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (header != null) item { header() }
            if (s.v.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text(emptyTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(s.v, key = { it.id }) { p -> PostCard(p, app, onChanged = { tick++ }) }
        }
    }
}

// ================= Home =================
@Composable
fun HomeScreen(app: AppState) {
    var top by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("OVION", actions = {
            IconButton(onClick = { app.push(Route.Inbox) }) {
                BadgedBox(badge = { if (app.unreadMsgs > 0) Badge { Text("${app.unreadMsgs}") } }) {
                    Icon(Icons.Default.MailOutline, "الرسائل")
                }
            }
        })
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            listOf("الأحدث" to false, "الأكثر تفاعلاً" to true).forEach { (label, v) ->
                Box(Modifier.weight(1f).clickable { top = v }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label, fontWeight = if (top == v) FontWeight.Bold else FontWeight.Normal,
                        color = if (top == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        FeedList(key = top, app = app, poll = true) { Api.posts(if (top) Api.ORDER_TOP else Api.ORDER_NEW) }
    }
}

// ================= Search / Explore =================
@Composable
fun UserRow(p: Profile, app: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isF = app.following[p.id] == true
    Row(
        Modifier.fillMaxWidth().clickable { app.push(Route.User(p.id)) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(p.name, p.avatar, 46.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.Bold)
            Text(
                if (p.username.isNotBlank()) "@${p.username} · ${p.followers} متابع" else "${p.followers} متابع",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (p.id != Api.uid) {
            if (isF) OutlinedButton(onClick = { toggleFollow(app, p.id, scope, ctx) }) { Text("إلغاء المتابعة") }
            else Button(onClick = { toggleFollow(app, p.id, scope, ctx) }) { Text("متابعة") }
        }
    }
}

fun toggleFollow(app: AppState, id: String, scope: kotlinx.coroutines.CoroutineScope, ctx: android.content.Context) {
    val now = app.following[id] != true
    app.following[id] = now
    scope.launch {
        try { if (now) Api.follow(id) else Api.unfollow(id) }
        catch (e: ApiException) { app.following[id] = !now; toast(ctx, e.message ?: "تعذر تنفيذ الإجراء") }
    }
}

@Composable
fun SearchScreen(app: AppState) {
    var q by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(q) { delay(400); query = q.trim() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("استكشاف")
        OutlinedTextField(
            q, { q = it }, singleLine = true, placeholder = { Text("ابحث عن أشخاص أو منشورات") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        if (query.isBlank()) {
            var sug by remember { mutableStateOf<Load<List<Profile>>>(Load.Loading) }
            LaunchedEffect(app.refreshTick) { sug = safe { Api.suggestions() } }
            FeedList(key = "explore", app = app, header = {
                Column {
                    Text("حسابات مقترحة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    when (val s = sug) {
                        Load.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        is Load.Err -> Text(s.msg, color = MaterialTheme.colorScheme.secondary)
                        is Load.Ok -> s.v.take(6).forEach { UserRow(it, app) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("الأكثر تفاعلاً", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }) { Api.posts(Api.ORDER_TOP) }
        } else {
            var users by remember(query) { mutableStateOf<Load<List<Profile>>>(Load.Loading) }
            LaunchedEffect(query) { users = safe { Api.searchUsers(query) } }
            FeedList(key = query, app = app, emptyTitle = "لا توجد منشورات مطابقة", header = {
                Column {
                    Text("الأشخاص", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    when (val u = users) {
                        Load.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        is Load.Err -> Text(u.msg, color = MaterialTheme.colorScheme.secondary)
                        is Load.Ok -> if (u.v.isEmpty()) Text("لا يوجد أشخاص مطابقون", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else u.v.forEach { UserRow(it, app) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("المنشورات", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }) { Api.posts(Api.ORDER_NEW, query = query) }
        }
    }
}
