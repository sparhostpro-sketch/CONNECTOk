package com.ovion.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("ovion", Context.MODE_PRIVATE)
        setContent { OvionTheme { OvionRoot(prefs) } }
    }
}

private fun loadSession(p: SharedPreferences): Session? {
    val token = p.getString("token", "").orEmpty()
    if (token.isBlank()) return null
    val uid = p.getString("uid", null) ?: Api.userIdFromToken(token) ?: return null
    return Session(token, p.getString("refresh", "").orEmpty(), uid)
}

private fun saveSession(p: SharedPreferences, s: Session?) {
    val e = p.edit()
    if (s == null) e.remove("token").remove("refresh").remove("uid")
    else e.putString("token", s.accessToken).putString("refresh", s.refreshToken).putString("uid", s.userId)
    e.apply()
}

@Composable
fun OvionRoot(prefs: SharedPreferences) {
    var session by remember { mutableStateOf(loadSession(prefs).also { Api.session = it }) }

    DisposableEffect(Unit) {
        Api.onSessionChanged = { s -> saveSession(prefs, s); session = s }
        onDispose { Api.onSessionChanged = null }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        val s = session
        if (s == null) {
            AuthScreen { n -> Api.session = n; saveSession(prefs, n); session = n }
        } else {
            key(s.userId) { MainApp(onLogout = { Api.session = null; saveSession(prefs, null); session = null }) }
        }
      }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun MainApp(onLogout: () -> Unit) {
    val app = remember { AppState() }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Tab("الرئيسية", Icons.Default.Home),
        Tab("استكشاف", Icons.Default.Search),
        Tab("نشر", Icons.Default.AddCircle),
        Tab("الإشعارات", Icons.Default.Notifications),
        Tab("حسابي", Icons.Default.Person)
    )

    // بيانات المستخدم الأساسية
    LaunchedEffect(Unit) {
        (safe { Api.profile(Api.uid) } as? Load.Ok)?.let { app.me = it.v }
        (safe { Api.likedPostIds() } as? Load.Ok)?.let { r -> r.v.forEach { app.liked[it] = true } }
        (safe { Api.followingIds() } as? Load.Ok)?.let { r -> r.v.forEach { app.following[it] = true } }
    }
    // عدادات غير المقروء (تحديث دوري كل 15 ثانية)
    LaunchedEffect(Unit) {
        while (true) {
            (safe { Api.unreadNotifications() } as? Load.Ok)?.let { if (tab != 3) app.unreadNotifs = it.v }
            (safe { Api.unreadMessages() } as? Load.Ok)?.let { app.unreadMsgs = it.v }
            delay(15000)
        }
    }

    BackHandler(enabled = app.stack.isNotEmpty() || tab != 0) {
        if (app.stack.isNotEmpty()) app.pop() else tab = 0
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeScreen(app)
                    1 -> SearchScreen(app)
                    2 -> ComposeScreen(app) { tab = 0 }
                    3 -> NotificationsScreen(app)
                    else -> ProfileScreen(Api.uid, app, isTab = true, onLogout = onLogout)
                }
            }
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i; if (i == 3) app.unreadNotifs = 0 },
                        label = { Text(t.label) },
                        icon = {
                            BadgedBox(badge = { if (i == 3 && app.unreadNotifs > 0) Badge { Text("${app.unreadNotifs}") } }) {
                                Icon(t.icon, t.label)
                            }
                        }
                    )
                }
            }
        }

        // صفحات فوق التبويبات (ملف مستخدم، منشور، رسائل...)
        AnimatedContent(
            targetState = app.stack.lastOrNull(),
            transitionSpec = {
                if (targetState != null) (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith fadeOut()
                else fadeIn() togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            },
            label = "route"
        ) { r ->
            if (r != null) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
                when (r) {
                    Route.Inbox -> InboxScreen(app)
                    Route.EditProfile -> EditProfileScreen(app)
                    is Route.User -> ProfileScreen(r.id, app, isTab = false, onLogout = onLogout)
                    is Route.PostDetail -> PostDetailScreen(r.post, app)
                    is Route.Chat -> ChatScreen(r.peer, app)
                }
            }
        }
    }
}
