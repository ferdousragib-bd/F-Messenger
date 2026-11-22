package com.fmessenger.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val Navy = Color(0xFF123B70)
private val Blue = Color(0xFF247BDB)
private val Pale = Color(0xFFF1F6FC)
private val db by lazy { FirebaseFirestore.getInstance() }
private val auth by lazy { FirebaseAuth.getInstance() }

data class Person(val uid: String = "", val name: String = "", val username: String = "", val phone: String = "")
data class ChatMessage(val id: String = "", val senderId: String = "", val text: String = "", val createdAt: Long = 0L)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Pale)) { MessengerApp() } }
    }
}

@Composable
fun MessengerApp() {
    var currentUser by remember { mutableStateOf(auth.currentUser?.uid) }
    var selected by remember { mutableStateOf<Person?>(null) }
    auth.addAuthStateListener { currentUser = it.currentUser?.uid }
    Surface(Modifier.fillMaxSize(), color = Pale) {
        if (currentUser == null) AuthScreen(onSignedIn = { currentUser = it })
        else if (selected != null) ChatScreen(selected!!, onBack = { selected = null })
        else HomeScreen(onSelect = { selected = it }, onSignOut = { auth.signOut(); currentUser = null })
    }
}

@Composable
fun AuthScreen(onSignedIn: (String) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(76.dp).background(Blue, CircleShape), contentAlignment = Alignment.Center) {
            Text("F", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text("F Messenger", style = MaterialTheme.typography.headlineMedium, color = Navy, fontWeight = FontWeight.Bold)
        Text(if (register) "নতুন অ্যাকাউন্ট তৈরি করুন" else "আপনার অ্যাকাউন্টে প্রবেশ করুন", color = Color.Gray)
        Spacer(Modifier.height(22.dp))
        if (register) {
            OutlinedTextField(name, { name = it }, label = { Text("আপনার নাম") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it.lowercase().replace(" ", "") }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("ফোন নম্বর") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(email, { email = it }, label = { Text("ইমেইল") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(14.dp))
        Button(onClick = {
            scope.launch {
                busy = true; error = ""
                try {
                    if (register) {
                        require(name.isNotBlank() && username.isNotBlank() && phone.isNotBlank()) { "নাম, Username ও ফোন নম্বর দিন" }
                        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                        val uid = result.user!!.uid
                        db.collection("users").document(uid).set(mapOf(
                            "uid" to uid, "name" to name.trim(), "username" to username.trim(),
                            "usernameLower" to username.trim().lowercase(), "phone" to phone.trim(),
                            "phoneDigits" to phone.filter { it.isDigit() }, "email" to email.trim(),
                            "createdAt" to System.currentTimeMillis()
                        )).await()
                        onSignedIn(uid)
                    } else {
                        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                        onSignedIn(result.user!!.uid)
                    }
                } catch (e: Exception) { error = e.localizedMessage ?: "আবার চেষ্টা করুন" }
                busy = false
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text(if (busy) "অপেক্ষা করুন..." else if (register) "অ্যাকাউন্ট তৈরি করুন" else "লগইন")
        }
        TextButton(onClick = { register = !register; error = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "আগে অ্যাকাউন্ট আছে? লগইন করুন" else "নতুন অ্যাকাউন্ট খুলুন")
        }
    }
}

@Composable
fun HomeScreen(onSelect: (Person) -> Unit, onSignOut: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Person>()) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Navy).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("F Messenger", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("আপনার মানুষের সঙ্গে যুক্ত থাকুন", color = Color(0xFFD8E8FF))
            }
            TextButton(onClick = onSignOut) { Text("লগআউট", color = Color.White) }
        }
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(14.dp),
            label = { Text("নাম, Username বা ফোন নম্বর দিয়ে খুঁজুন") },
            trailingIcon = { IconButton(onClick = {
                scope.launch {
                    error = ""
                    try {
                        val q = query.trim()
                        if (q.isBlank()) { results = emptyList(); return@launch }
                        val uid = auth.currentUser?.uid
                        val byName = db.collection("users").orderBy("name").startAt(q).endAt(q + "\uf8ff").limit(20).get().await()
                        val byUsername = db.collection("users").whereEqualTo("usernameLower", q.lowercase()).limit(20).get().await()
                        val digits = q.filter { it.isDigit() }
                        val byPhone = if (digits.isNotBlank()) db.collection("users").whereEqualTo("phoneDigits", digits).limit(20).get().await() else null
                        results = (byName.documents + byUsername.documents + (byPhone?.documents ?: emptyList()))
                            .mapNotNull { d -> d.toObject(Person::class.java) }.filter { it.uid != uid }.distinctBy { it.uid }
                    } catch (e: Exception) { error = e.localizedMessage ?: "খোঁজা যায়নি" }
                }
            }) { Icon(Icons.Default.Search, contentDescription = "Search") })
        )
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        Text("ব্যবহারকারী", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, color = Navy)
        LazyColumn {
            items(results) { person ->
                ListItem(
                    headlineContent = { Text(person.name.ifBlank { person.username }) },
                    supportingContent = { Text("@${person.username}  •  ${person.phone}") },
                    leadingContent = { Box(Modifier.size(44.dp).background(Blue, CircleShape), contentAlignment = Alignment.Center) {
                        Text(person.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    } },
                    modifier = Modifier.clickable { onSelect(person) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ChatScreen(person: Person, onBack: () -> Unit) {
    val myUid = auth.currentUser?.uid ?: return
    val chatId = listOf(myUid, person.uid).sorted().joinToString("_")
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    DisposableEffect(chatId) {
        val listener: ListenerRegistration = db.collection("chats").document(chatId)
            .collection("messages").orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) error = e.localizedMessage ?: "মেসেজ লোড হয়নি"
                else messages = snap?.documents?.mapNotNull { d ->
                    d.toObject(ChatMessage::class.java)?.copy(id = d.id)
                } ?: emptyList()
            }
        onDispose { listener.remove() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Navy).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Column {
                Text(person.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text("@${person.username}", color = Color(0xFFD8E8FF))
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), reverseLayout = false) {
            items(messages) { msg ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.senderId == myUid) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (msg.senderId == myUid) Blue else Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp) {
                        Text(msg.text, modifier = Modifier.padding(12.dp), color = if (msg.senderId == myUid) Color.White else Navy)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("মেসেজ লিখুন...") }, maxLines = 4)
            IconButton(onClick = {
                val text = draft.trim()
                if (text.isNotBlank()) scope.launch {
                    try {
                        val chatRef = db.collection("chats").document(chatId)
                        chatRef.set(mapOf(
                            "members" to listOf(myUid, person.uid), "lastMessage" to text,
                            "updatedAt" to System.currentTimeMillis()
                        )).await()
                        chatRef.collection("messages").add(mapOf(
                            "senderId" to myUid, "text" to text, "createdAt" to System.currentTimeMillis()
                        )).await()
                        draft = ""
                    } catch (e: Exception) { error = e.localizedMessage ?: "পাঠানো যায়নি" }
                }
            }) { Icon(Icons.Default.Send, contentDescription = "Send", tint = Blue) }
        }
    }
}
