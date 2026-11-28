package com.ovion.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ================= Compose post =================
@Composable
fun ComposeScreen(app: AppState, onPosted: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) image = it }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("منشور جديد")
        Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(app.me?.name ?: "", app.me?.avatar, 44.dp)
                Spacer(Modifier.width(10.dp))
                Text(app.me?.name ?: "", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                text, { text = it }, placeholder = { Text("بماذا تفكر؟") },
                minLines = 5, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            )
            if (image != null) {
                Spacer(Modifier.height(10.dp))
                Box {
                    AsyncImage(image, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(14.dp)))
                    IconButton(onClick = { image = null }, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(Icons.Default.Close, "إزالة الصورة", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pick.launch("image/*") }, enabled = !busy) {
                    Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("إضافة صورة")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = !busy && (text.isNotBlank() || image != null),
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val url = image?.let { Api.uploadImage(readImage(ctx, it)) }
                                Api.createPost(text.trim(), url)
                                text = ""; image = null
                                app.refreshTick++
                                toast(ctx, "تم نشر المنشور")
                                onPosted()
                            } catch (e: ApiException) { toast(ctx, e.message ?: "تعذر النشر") }
                            busy = false
                        }
                    }
                ) { Text(if (busy) "جارٍ النشر..." else "نشر") }
            }
        }
    }
}

// ================= Notifications =================
@Composable
fun NotificationsScreen(app: AppState) {
    var state by remember { mutableStateOf<Load<List<Notif>>>(Load.Loading) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        state = safe { Api.notifications() }
        if (state is Load.Ok) {
            safe { Api.markNotificationsRead() }
            app.unreadNotifs = 0
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("الإشعارات")
        when (val s = state) {
            Load.Loading -> CenterLoading()
            is Load.Err -> StateMessage("تعذر التحميل", s.msg, "إعادة المحاولة") { state = Load.Loading; tick++ }
            is Load.Ok -> if (s.v.isEmpty()) StateMessage("لا توجد إشعارات", "ستظهر هنا الإعجابات والتعليقات والمتابعات")
            else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.v, key = { it.id }) { n ->
                    val msg = when (n.type) {
                        "like" -> "أعجب بمنشورك"
                        "comment" -> "علّق على منشورك"
                        else -> "بدأ بمتابعتك"
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (n.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { app.push(Route.User(n.actorId)) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(n.actorName, n.actorAvatar, 42.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${n.actorName} $msg", fontSize = 14.sp)
                            Text(TimeFmt.ago(n.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ================= Profile =================
@Composable
fun ProfileScreen(userId: String, app: AppState, isTab: Boolean, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(userId) { mutableStateOf<Load<Profile>>(Load.Loading) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(userId, tick, app.refreshTick) {
        state = safe { Api.profile(userId) }
        if (userId == Api.uid) (state as? Load.Ok)?.let { app.me = it.v }
    }
    val mine = userId == Api.uid

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (mine) "ملفي الشخصي" else "الملف الشخصي", onBack = if (isTab) null else ({ app.pop() }), actions = {
            if (mine && isTab) IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "تسجيل الخروج") }
        })
        when (val s = state) {
            Load.Loading -> CenterLoading()
            is Load.Err -> StateMessage("تعذر تحميل الحساب", s.msg, "إعادة المحاولة") { state = Load.Loading; tick++ }
            is Load.Ok -> {
                val p = s.v
                val isF = app.following[p.id] == true
                FeedList(key = p.id, app = app, emptyTitle = "لا توجد منشورات بعد", header = {
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))) {
                            if (p.cover != null) AsyncImage(p.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.name, p.avatar, 76.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                if (p.username.isNotBlank()) Text("@${p.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (p.bio.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(p.bio) }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text("${p.followers} متابِع", fontWeight = FontWeight.SemiBold)
                            Text("${p.following} يتابع", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        if (mine) {
                            OutlinedButton(onClick = { app.push(Route.EditProfile) }, modifier = Modifier.fillMaxWidth()) { Text("تعديل الملف الشخصي") }
                        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isF) OutlinedButton(onClick = { toggleFollow(app, p.id, scope, ctx); tick++ }, modifier = Modifier.weight(1f)) { Text("إلغاء المتابعة") }
                            else Button(onClick = { toggleFollow(app, p.id, scope, ctx); tick++ }, modifier = Modifier.weight(1f)) { Text("متابعة") }
                            OutlinedButton(onClick = { app.push(Route.Chat(p)) }, modifier = Modifier.weight(1f)) { Text("مراسلة") }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }) { Api.posts(Api.ORDER_NEW, authorId = p.id) }
            }
        }
    }
}

// ================= Edit profile =================
@Composable
fun EditProfileScreen(app: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = app.me
    var name by remember { mutableStateOf(me?.name ?: "") }
    var username by remember { mutableStateOf(me?.username ?: "") }
    var bio by remember { mutableStateOf(me?.bio ?: "") }
    var avatar by remember { mutableStateOf<Uri?>(null) }
    var cover by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) avatar = it }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) cover = it }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("تعديل الملف الشخصي", onBack = { app.pop() })
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (avatar != null) AsyncImage(avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape))
                else Avatar(name, me?.avatar, 64.dp)
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { pickAvatar.launch("image/*") }) { Text("تغيير الصورة الشخصية") }
            }
            OutlinedButton(onClick = { pickCover.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (cover != null) "تم اختيار صورة الغلاف ✓" else "تغيير صورة الغلاف")
            }
            OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                username, { username = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(24) },
                label = { Text("اسم المستخدم (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(bio, { bio = it.take(200) }, label = { Text("نبذة") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val a = avatar?.let { Api.uploadImage(readImage(ctx, it, 512)) }
                            val c = cover?.let { Api.uploadImage(readImage(ctx, it, 1280)) }
                            Api.updateProfile(name.trim(), username.trim(), bio.trim(), a, c)
                            app.refreshTick++
                            toast(ctx, "تم حفظ التعديلات")
                            app.pop()
                        } catch (e: ApiException) { toast(ctx, e.message ?: "تعذر الحفظ") }
                        busy = false
                    }
                }
            ) { Text(if (busy) "جارٍ الحفظ..." else "حفظ") }
        }
    }
}

// ================= Post detail + comments =================
@Composable
fun PostDetailScreen(post: Post, app: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Load<List<Comment>>>(Load.Loading) }
    var tick by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(tick) { state = safe { Api.comments(post.id) } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("المنشور", onBack = { app.pop() })
        Box(Modifier.weight(1f)) {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { PostCard(post, app, onChanged = { app.refreshTick++; app.pop() }, openDetail = false) }
                when (val s = state) {
                    Load.Loading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    is Load.Err -> item { Text(s.msg, color = MaterialTheme.colorScheme.secondary) }
                    is Load.Ok -> {
                        if (s.v.isEmpty()) item { Text("كن أول من يعلّق", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                        val tops = s.v.filter { it.parentId == null }
                        tops.forEach { c ->
                            item(key = c.id) { CommentRow(c, app, 0.dp, { replyTo = c }) { scope.launch { try { Api.deleteComment(c.id); tick++ } catch (e: ApiException) { toast(ctx, e.message ?: "") } } } }
                            s.v.filter { it.parentId == c.id }.forEach { r ->
                                item(key = r.id) { CommentRow(r, app, 28.dp, { replyTo = c }) { scope.launch { try { Api.deleteComment(r.id); tick++ } catch (e: ApiException) { toast(ctx, e.message ?: "") } } } }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
            if (replyTo != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("رد على ${replyTo!!.authorName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = { replyTo = null }) { Text("إلغاء") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, placeholder = { Text("اكتب تعليقاً...") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), maxLines = 4)
                IconButton(
                    enabled = !sending && text.isNotBlank(),
                    onClick = {
                        sending = true
                        scope.launch {
                            try { Api.addComment(post.id, text.trim(), replyTo?.id); text = ""; replyTo = null; tick++; app.refreshTick++ }
                            catch (e: ApiException) { toast(ctx, e.message ?: "تعذر إرسال التعليق") }
                            sending = false
                        }
                    }
                ) { Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
fun CommentRow(c: Comment, app: AppState, indent: androidx.compose.ui.unit.Dp, onReply: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = indent)) {
        Avatar(c.authorName, c.authorAvatar, 34.dp) { app.push(Route.User(c.authorId)) }
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp)
        ) {
            Text(c.authorName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(c.content, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(TimeFmt.ago(c.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("رد", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onReply() })
                if (c.authorId == Api.uid) Text("حذف", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.clickable { onDelete() })
            }
        }
    }
}

// ================= Messages =================
@Composable
fun InboxScreen(app: AppState) {
    var state by remember { mutableStateOf<Load<List<Convo>>>(Load.Loading) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        while (true) {
            val r = safe { Api.conversations() }
            if (r is Load.Ok || state is Load.Loading) state = r
            if (r is Load.Ok) app.unreadMsgs = r.v.sumOf { it.unread }
            delay(8000)
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("الرسائل", onBack = { app.pop() })
        when (val s = state) {
            Load.Loading -> CenterLoading()
            is Load.Err -> StateMessage("تعذر التحميل", s.msg, "إعادة المحاولة") { state = Load.Loading; tick++ }
            is Load.Ok -> if (s.v.isEmpty()) StateMessage("لا توجد محادثات", "ابدأ محادثة من الملف الشخصي لأي مستخدم")
            else LazyColumn(contentPadding = PaddingValues(12.dp)) {
                items(s.v, key = { it.peer.id }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { app.push(Route.Chat(c.peer)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.peer.name, c.peer.avatar, 48.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.peer.name, fontWeight = FontWeight.Bold)
                            Text(c.last.content, maxLines = 1, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (c.unread > 0) Badge { Text("${c.unread}") }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(peer: Profile, app: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msgs by remember { mutableStateOf<List<Msg>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    val list = rememberLazyListState()

    LaunchedEffect(peer.id) {
        while (true) {
            when (val r = safe { Api.chat(peer.id) }) {
                is Load.Ok -> {
                    msgs = r.v; error = null; loaded = true
                    if (r.v.any { it.receiverId == Api.uid && !it.read }) { safe { Api.markChatRead(peer.id) } }
                }
                is Load.Err -> if (!loaded) error = r.msg
                Load.Loading -> {}
            }
            delay(4000)
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.lastIndex) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(peer.name, onBack = { app.pop() })
        Box(Modifier.weight(1f)) {
            when {
                !loaded && error == null -> CenterLoading()
                !loaded -> StateMessage("تعذر تحميل المحادثة", error)
                msgs.isEmpty() -> StateMessage("ابدأ المحادثة", "أرسل أول رسالة إلى ${peer.name}")
                else -> LazyColumn(state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(msgs, key = { it.id }) { m ->
                        val mine = m.senderId == Api.uid
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
                            Text(
                                m.content,
                                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.background(MaterialTheme.colorScheme.surface).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, placeholder = { Text("اكتب رسالة...") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), maxLines = 4)
            IconButton(enabled = text.isNotBlank(), onClick = {
                val t = text.trim(); text = ""
                scope.launch {
                    try { Api.sendMessage(peer.id, t); msgs = Api.chat(peer.id) }
                    catch (e: ApiException) { text = t; toast(ctx, e.message ?: "تعذر الإرسال") }
                }
            }) { Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}
