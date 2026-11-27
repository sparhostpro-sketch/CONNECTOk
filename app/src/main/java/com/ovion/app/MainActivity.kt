package com.ovion.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private val blue = Color(0xFF1877F2)
private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private const val BASE_URL =
    "https://ypkqwpqkhpdvkaqmwxuw.supabase.co"

private const val API_KEY =
    "sb_publishable_RrCdbXePOnQGRZsxi53C6A_00jjgW6B"

data class OvionPost(
    val id: String,
    val content: String,
    val author: String,
    val date: String
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("ovion", Context.MODE_PRIVATE)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = blue,
                    background = Color(0xFFF0F2F5)
                )
            ) {
                OvionApp(
                    prefs = prefs,
                    context = this
                )
            }
        }
    }
}

@Composable
fun OvionApp(
    prefs: android.content.SharedPreferences,
    context: Context
) {
    var token by remember {
        mutableStateOf(prefs.getString("token", "") ?: "")
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    var isRegister by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    var posts by remember { mutableStateOf(listOf<OvionPost>()) }

    fun showMessage(value: String) {
        message = value
    }

    fun loadFeed() {
        if (token.isBlank()) return

        loading = true

        OvionApi.getPosts(token) { result, error ->
            loading = false

            if (error != null) {
                showMessage(error)
            } else {
                posts = result ?: emptyList()
            }
        }
    }

    fun saveToken(value: String) {
        token = value
        prefs.edit().putString("token", value).apply()
        loadFeed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "OVION",
                color = blue,
                fontSize = 27.sp
            )

            Spacer(Modifier.weight(1f))

            Text(
                "أوفيون",
                fontSize = 20.sp
            )
        }

        if (token.isBlank()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    "مرحباً بك في OVION",
                    fontSize = 25.sp,
                    color = blue
                )

                Spacer(Modifier.height(24.dp))

                if (isRegister) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("الاسم") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("البريد الإلكتروني") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور") },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (
                            email.isBlank() ||
                            password.length < 8 ||
                            (isRegister && displayName.isBlank())
                        ) {
                            showMessage("تأكد من إدخال البيانات بشكل صحيح")
                            return@Button
                        }

                        loading = true
                        message = ""

                        if (isRegister) {
                            OvionApi.register(
                                email,
                                password,
                                displayName
                            ) { result, error ->
                                loading = false

                                if (result != null) {
                                    if (result.isNotBlank()) {
                                        saveToken(result)
                                        showMessage("تم إنشاء الحساب")
                                    } else {
                                        showMessage(
                                            "تم التسجيل. راجع بريدك الإلكتروني لتأكيد الحساب ثم سجّل الدخول."
                                        )
                                        isRegister = false
                                    }
                                } else {
                                    showMessage(error ?: "تعذر التسجيل")
                                }
                            }
                        } else {
                            OvionApi.login(
                                email,
                                password
                            ) { result, error ->
                                loading = false

                                if (result != null) {
                                    saveToken(result)
                                    showMessage("تم تسجيل الدخول")
                                } else {
                                    showMessage(error ?: "تعذر تسجيل الدخول")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Text(
                        if (loading) "يرجى الانتظار..."
                        else if (isRegister) "إنشاء حساب"
                        else "تسجيل الدخول"
                    )
                }

                TextButton(
                    onClick = {
                        isRegister = !isRegister
                        message = ""
                    }
                ) {
                    Text(
                        if (isRegister)
                            "لديك حساب؟ تسجيل الدخول"
                        else
                            "ليس لديك حساب؟ إنشاء حساب"
                    )
                }

                if (message.isNotBlank()) {
                    Text(
                        message,
                        color = blue,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

        } else {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "آخر المنشورات",
                    fontSize = 21.sp
                )

                TextButton(
                    onClick = { loadFeed() }
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "تحديث"
                    )
                    Text("تحديث")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = {
                            Text("بماذا تفكر؟")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Button(
                        onClick = {
                            if (content.isBlank()) {
                                showMessage("اكتب شيئاً أولاً")
                                return@Button
                            }

                            loading = true

                            OvionApi.createPost(
                                token,
                                content
                            ) { error ->
                                loading = false

                                if (error == null) {
                                    content = ""
                                    showMessage("تم نشر المنشور")
                                    loadFeed()
                                } else {
                                    showMessage(error)
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        enabled = !loading
                    ) {
                        Text("نشر")
                    }
                }
            }

            if (message.isNotBlank()) {
                Text(
                    message,
                    color = blue,
                    modifier = Modifier.padding(12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(posts) { post ->

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                post.author,
                                color = blue,
                                fontSize = 17.sp
                            )

                            Spacer(Modifier.height(10.dp))

                            Text(
                                post.content,
                                fontSize = 16.sp,
                                lineHeight = 25.sp
                            )

                            Spacer(Modifier.height(12.dp))

                            Text(
                                post.date,
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    prefs.edit().remove("token").apply()
                    token = ""
                    posts = emptyList()
                    message = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text("تسجيل الخروج")
            }
        }
    }
}

object OvionApi {

    private val jsonType =
        "application/json; charset=utf-8".toMediaType()

    private fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        token: String? = null,
        callback: (String?, String?) -> Unit
    ) {
        val builder = Request.Builder()
            .url("$BASE_URL$path")
            .addHeader("apikey", API_KEY)

        if (!token.isNullOrBlank()) {
            builder.addHeader(
                "Authorization",
                "Bearer $token"
            )
        }

        if (body != null) {
            builder.addHeader("Content-Type", "application/json")
        }

        when (method) {
            "POST" -> builder.post(
                (body?.toString() ?: "{}").toRequestBody(jsonType)
            )

            "GET" -> builder.get()

            else -> builder.get()
        }

        client.newCall(builder.build()).enqueue(
            object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    callback(
                        null,
                        "تعذر الاتصال بالإنترنت"
                    )
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use {
                        val text = it.body?.string().orEmpty()

                        if (it.isSuccessful) {
                            callback(text, null)
                        } else {
                            callback(
                                null,
                                parseError(text)
                            )
                        }
                    }
                }
            }
        )
    }

    private fun parseError(text: String): String {
        return try {
            val obj = JSONObject(text)
            obj.optString(
                "msg",
                obj.optString(
                    "message",
                    obj.optString(
                        "error_description",
                        "حدث خطأ، حاول مرة أخرى"
                    )
                )
            )
        } catch (_: Exception) {
            "حدث خطأ، حاول مرة أخرى"
        }
    }

    fun register(
        email: String,
        password: String,
        name: String,
        callback: (String?, String?) -> Unit
    ) {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put(
                "data",
                JSONObject().put("display_name", name)
            )

        request(
            "/auth/v1/signup",
            "POST",
            body
        ) { response, error ->

            if (response == null) {
                callback(null, error)
                return@request
            }

            try {
                val obj = JSONObject(response)
                callback(
                    obj.optString("access_token"),
                    null
                )
            } catch (_: Exception) {
                callback(null, "تعذر قراءة نتيجة التسجيل")
            }
        }
    }

    fun login(
        email: String,
        password: String,
        callback: (String?, String?) -> Unit
    ) {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)

        request(
            "/auth/v1/token?grant_type=password",
            "POST",
            body
        ) { response, error ->

            if (response == null) {
                callback(null, error)
                return@request
            }

            try {
                callback(
                    JSONObject(response).optString("access_token"),
                    null
                )
            } catch (_: Exception) {
                callback(null, "تعذر تسجيل الدخول")
            }
        }
    }

    fun getPosts(
        token: String,
        callback: (List<OvionPost>?, String?) -> Unit
    ) {
        request(
            "/rest/v1/posts?select=id,content,created_at,profiles(display_name,username)&order=created_at.desc",
            "GET",
            token = token
        ) { response, error ->

            if (response == null) {
                callback(null, error)
                return@request
            }

            try {
                val array = JSONArray(response)
                val result = mutableListOf<OvionPost>()

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val profile = item.optJSONObject("profiles")

                    result.add(
                        OvionPost(
                            id = item.optString("id"),
                            content = item.optString("content"),
                            author = profile?.optString("display_name")
                                ?.takeIf { !it.isNullOrBlank() }
                                ?: "مستخدم OVION",
                            date = item.optString("created_at")
                        )
                    )
                }

                callback(result, null)

            } catch (_: Exception) {
                callback(
                    null,
                    "تعذر تحميل المنشورات"
                )
            }
        }
    }

    fun createPost(
        token: String,
        content: String,
        callback: (String?) -> Unit
    ) {
        val body = JSONObject()
            .put("content", content)

        val request = Request.Builder()
            .url("$BASE_URL/rest/v1/posts")
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        client.newCall(request).enqueue(
            object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    callback("تعذر الاتصال بالإنترنت")
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use {
                        callback(
                            if (it.isSuccessful) null
                            else "تعذر نشر المنشور"
                        )
                    }
                }
            }
        )
    }
}
