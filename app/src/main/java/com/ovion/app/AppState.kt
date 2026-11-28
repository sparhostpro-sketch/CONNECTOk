package com.ovion.app

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList

sealed interface Route {
    object Inbox : Route
    object EditProfile : Route
    data class User(val id: String) : Route
    data class PostDetail(val post: Post) : Route
    data class Chat(val peer: Profile) : Route
}

class AppState {
    var me by mutableStateOf<Profile?>(null)
    val liked = mutableStateMapOf<String, Boolean>()
    val likeCounts = mutableStateMapOf<String, Int>()
    val following = mutableStateMapOf<String, Boolean>()
    var refreshTick by mutableIntStateOf(0)
    var unreadNotifs by mutableIntStateOf(0)
    var unreadMsgs by mutableIntStateOf(0)
    val stack: SnapshotStateList<Route> = mutableStateListOf()

    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
}
