package com.fmessenger.chat

// F Messenger Android client — current maintained build.

import android.net.Uri
import android.app.Activity
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnection.Observer
import org.webrtc.MediaStream
import org.webrtc.RtpReceiver
import org.webrtc.DataChannel
import android.os.Bundle
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FieldValue
import android.media.MediaRecorder
import android.media.MediaPlayer
import java.io.File
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val Navy = Color(0xFF24352F)
private val Blue = Color(0xFF438B78)
private val Pale = Color(0xFFF7F1E2)
private val Cream = Color(0xFFFFFDF7)
private val Green = Color(0xFF438B78)
private val db by lazy { FirebaseFirestore.getInstance() }
private val auth by lazy { FirebaseAuth.getInstance() }
private val storage by lazy { FirebaseStorage.getInstance() }

data class Person(val uid:String="", val name:String="", val username:String="", val phone:String="")
data class FeedPost(val id:String="", val authorId:String="", val authorName:String="", val authorUsername:String="", val caption:String="", val mediaUrl:String="", val mediaType:String="text", val createdAt:Long=0L)
data class ChatMessage(val id:String="", val senderId:String="", val text:String="", val mediaUrl:String="", val mediaType:String="text", val createdAt:Long=0L)
data class CommentItem(val id:String="", val authorId:String="", val authorName:String="", val text:String="", val createdAt:Long=0L)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Blue,background=Pale)) { MessengerApp() } } }
}

@Composable
fun MessengerApp() {
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    var selected by remember { mutableStateOf<Person?>(null) }
    DisposableEffect(Unit) { val l=FirebaseAuth.AuthStateListener { uid=it.currentUser?.uid }; auth.addAuthStateListener(l); onDispose { auth.removeAuthStateListener(l) } }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(uid) {
        val currentUid = uid
        if (currentUid != null) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (token.isNotBlank()) db.collection("users").document(currentUid).set(
                    mapOf("fcmTokens" to FieldValue.arrayUnion(token)),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color=Pale) {
        if(uid==null) AuthScreen { uid=it }
        else {
            CallCoordinator.listenForIncoming(uid!!)
            Box(Modifier.fillMaxSize()) {
                if(selected!=null) ChatScreen(selected!!) { selected=null }
                else MainTabs(onChat={selected=it}, onSignOut={auth.signOut();uid=null})
                IncomingCallHost()
            }
        }
    }
}

@Composable
fun AuthScreen(onSignedIn:(String)->Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(72.dp).background(Blue, CircleShape), contentAlignment = Alignment.Center) {
            Text("F", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Text("F Messenger", style = MaterialTheme.typography.headlineMedium, color = Navy, fontWeight = FontWeight.Bold)
        Text("বিনামূল্যে ইমেইল ও পাসওয়ার্ড দিয়ে লগইন করুন", color = Color.Gray)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("ইমেইল") },
            placeholder = { Text("name@example.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("পাসওয়ার্ড") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (info.isNotBlank()) Text(info, color = Blue)
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !busy && email.contains("@") && password.length >= 6,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                error = ""
                info = ""
                scope.launch {
                    try {
                        val result = if (isRegister) {
                            auth.createUserWithEmailAndPassword(email, password).await()
                        } else {
                            auth.signInWithEmailAndPassword(email, password).await()
                        }
                        val user = result.user ?: throw IllegalStateException("অ্যাকাউন্ট পাওয়া যায়নি")
                        val uid = user.uid
                        val profileRef = db.collection("publicProfiles").document(uid)
                        val profile = profileRef.get().await()
                        if (!profile.exists()) {
                            val base = email.substringBefore("@").lowercase().replace(Regex("[^a-z0-9_]"), "").take(18).ifBlank { "member" }
                            val username = "${base}_${uid.takeLast(6)}"
                            profileRef.set(mapOf(
                                "uid" to uid,
                                "name" to email.substringBefore("@").ifBlank { "নতুন ব্যবহারকারী" },
                                "username" to username,
                                "usernameLower" to username.lowercase(),
                                "bio" to "",
                                "profileVisibility" to "public",
                                "createdAt" to System.currentTimeMillis()
                            )).await()
                        }
                        db.collection("users").document(uid).set(
                            mapOf("uid" to uid, "email" to email, "lastLoginAt" to System.currentTimeMillis()),
                            com.google.firebase.firestore.SetOptions.merge()
                        ).await()
                        onSignedIn(uid)
                    } catch (e: Exception) {
                        error = when {
                            e.message?.contains("email address is already in use", true) == true -> "এই ইমেইলে অ্যাকাউন্ট আছে। লগইন করুন।"
                            e.message?.contains("password is invalid", true) == true -> "ইমেইল বা পাসওয়ার্ড সঠিক নয়।"
                            e.message?.contains("no user record", true) == true -> "এই ইমেইলে অ্যাকাউন্ট নেই। আগে নিবন্ধন করুন।"
                            else -> e.localizedMessage ?: "লগইন করা যায়নি"
                        }
                    } finally { busy = false }
                }
            }
        ) { Text(if (busy) "অপেক্ষা করুন..." else if (isRegister) "বিনামূল্যে অ্যাকাউন্ট খুলুন" else "লগইন") }
        TextButton(
            onClick = { isRegister = !isRegister; error = ""; info = "" },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enabled = !busy
        ) {
            Text(if (isRegister) "আগে অ্যাকাউন্ট আছে? লগইন করুন" else "নতুন অ্যাকাউন্ট খুলুন")
        }
        if (!isRegister) {
            TextButton(
                enabled = !busy && email.contains("@"),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = {
                    busy = true; error = ""; info = ""
                    scope.launch {
                        try {
                            auth.sendPasswordResetEmail(email.trim()).await()
                            info = "পাসওয়ার্ড রিসেটের লিংক ইমেইলে পাঠানো হয়েছে। ইনবক্স/স্প্যাম ফোল্ডার দেখুন।"
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "রিসেট ইমেইল পাঠানো যায়নি"
                        } finally { busy = false }
                    }
                }
            ) { Text("পাসওয়ার্ড ভুলে গেছেন? রিসেট করুন") }
        }
        Text("পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে। ফোনে SMS পাঠানো হয় না, তাই SMS Billing লাগবে না।", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun MainTabs(onChat:(Person)->Unit,onSignOut:()->Unit) {
    var tab by remember { mutableStateOf("হোম") }
    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { try { isAdmin = auth.currentUser?.getIdToken(true)?.await()?.claims?.get("admin") == true } catch (_: Exception) { isAdmin = false } }
    Column(Modifier.fillMaxSize().background(Pale)) {
        Row(
            Modifier.fillMaxWidth().background(Cream).padding(horizontal=20.dp, vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Text("F", color=Green, style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick={tab="খুঁজুন"}) { Text("⌕", color=Navy, style=MaterialTheme.typography.headlineMedium) }
            TextButton(onClick=onSignOut) { Text("⋮", color=Navy, style=MaterialTheme.typography.headlineMedium) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when(tab) {
                "হোম" -> HomeProfileScreen(onSearch={tab="খুঁজুন"}, onMessage={tab="চ্যাট";})
                "ফিড" -> FeedScreen()
                "খুঁজুন" -> DiscoverScreen(onChat)
                "চ্যাট" -> ChatsScreen(onChat)
                "রিলস" -> ReelsScreen()
                "অ্যাডমিন" -> if (isAdmin) AdminReportsScreen() else ProfileScreen()
                else -> ProfileScreen()
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Cream).padding(vertical=5.dp),
            horizontalArrangement=Arrangement.SpaceEvenly,
            verticalAlignment=Alignment.CenterVertically
        ) {
            listOf("হোম" to "⌂", "ফিড" to "▤", "খুঁজুন" to "⌕", "চ্যাট" to "✉", "প্রোফাইল" to "♙").forEach { (item, glyph) ->
                Column(
                    Modifier.weight(1f).clickable { tab=item }.padding(vertical=5.dp),
                    horizontalAlignment=Alignment.CenterHorizontally
                ) {
                    Text(glyph, color=if(tab==item) Green else Color.DarkGray, style=MaterialTheme.typography.headlineSmall)
                    Text(item, color=if(tab==item) Green else Color.Gray, style=MaterialTheme.typography.labelSmall, fontWeight=if(tab==item) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

private data class FacebookPageItem(val name:String, val url:String)

private val featuredFacebookPages = listOf(
    FacebookPageItem("প্রথম আলো", "https://www.facebook.com/DailyProthomAlo"),
    FacebookPageItem("BBC বাংলা", "https://www.facebook.com/BBCBengaliService"),
    FacebookPageItem("সময় নিউজ", "https://www.facebook.com/somoynews.tv"),
    FacebookPageItem("The Daily Star", "https://www.facebook.com/dailystarnews"),
    FacebookPageItem("ICT Division", "https://www.facebook.com/ictdivisionbd"),
    FacebookPageItem("BDJobs", "https://www.facebook.com/mybdjobs"),
    FacebookPageItem("বাংলাদেশ পুলিশ", "https://www.facebook.com/BangladeshPoliceOfficialPage"),
    FacebookPageItem("Daraz", "https://www.facebook.com/DarazBangladesh"),
    FacebookPageItem("আড়ং", "https://www.facebook.com/BRAC.Aarong"),
    FacebookPageItem("রকমারি", "https://www.facebook.com/rokomari"),
    FacebookPageItem("চালডাল", "https://www.facebook.com/chaldalcom"),
    FacebookPageItem("Foodpanda", "https://www.facebook.com/foodappi"),
    FacebookPageItem("Rafsan The ChotoBhai", "https://www.facebook.com/RafsanTheChotoBhai"),
    FacebookPageItem("সালাহউদ্দিন সুমন", "https://www.facebook.com/salahuddinsumon.official"),
    FacebookPageItem("বাংলাদেশ পর্যটন", "https://www.facebook.com/parjatan.gov.bd"),
    FacebookPageItem("BCB", "https://www.facebook.com/bcbtigercricket"),
    FacebookPageItem("Shakib Al Hasan", "https://www.facebook.com/ShakibAlHasan"),
    FacebookPageItem("N24 Sports", "https://www.facebook.com/n24sports/"),
    FacebookPageItem("Chorki", "https://www.facebook.com/chorkiofficial"),
    FacebookPageItem("Tahsan", "https://www.facebook.com/TahsanOfficial"),
    FacebookPageItem("বিনোদন", "https://www.facebook.com/BangladeshPratidinEntertainment/"),
    FacebookPageItem("10 Minute School", "https://www.facebook.com/10minuteschool"),
    FacebookPageItem("Ayman Sadiq", "https://www.facebook.com/aymansadiq10"),
    FacebookPageItem("TechTunes", "https://www.facebook.com/techtunes")
)

@Composable
fun FacebookPageSection() {
    var selectedPage by remember { mutableStateOf(featuredFacebookPages.first()) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=8.dp)
            .background(Cream, RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Text("Facebook Pages", color=Navy, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
        Text("আপনার দেওয়া ২৩টি Facebook Page এক জায়গায় দেখুন।", color=Color.Gray, style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            items(featuredFacebookPages) { page ->
                if (page.url == selectedPage.url) {
                    Button(
                        onClick={ selectedPage=page },
                        colors=ButtonDefaults.buttonColors(containerColor=Green),
                        contentPadding=PaddingValues(horizontal=13.dp, vertical=7.dp)
                    ) { Text(page.name, color=Color.White, maxLines=1) }
                } else {
                    OutlinedButton(
                        onClick={ selectedPage=page },
                        contentPadding=PaddingValues(horizontal=13.dp, vertical=7.dp)
                    ) { Text(page.name, color=Navy, maxLines=1) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(selectedPage.name, color=Navy, fontWeight=FontWeight.SemiBold)
        AndroidView(
            modifier=Modifier.fillMaxWidth().height(700.dp),
            factory={ ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled=true
                    settings.domStorageEnabled=true
                    settings.loadsImagesAutomatically=true
                    settings.javaScriptCanOpenWindowsAutomatically=false
                    setBackgroundColor(android.graphics.Color.WHITE)
                }
            },
            update={ webView ->
                val pluginUrl = "https://www.facebook.com/plugins/page.php?href=${Uri.encode(selectedPage.url)}&tabs=timeline%2Cevents%2Cmessages&width=500&height=700&small_header=false&adapt_container_width=true&hide_cover=false&show_facepile=true"
                if (webView.tag != pluginUrl) {
                    webView.tag=pluginUrl
                    val html = "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head><body style='margin:0;padding:0;overflow-x:hidden'><iframe src='$pluginUrl' width='100%' height='700' style='border:none;overflow:hidden' scrolling='no' frameborder='0' allow='autoplay; clipboard-write; encrypted-media; picture-in-picture; web-share'></iframe></body></html>"
                    webView.loadDataWithBaseURL("https://www.facebook.com/", html, "text/html", "UTF-8", null)
                }
            }
        )
        Text("নোট: কোনো Page-এর Plugin Facebook থেকে বন্ধ থাকলে বা Page-এর Embed অনুমতি না থাকলে তার পোস্ট নাও দেখা যেতে পারে।", color=Color.Gray, style=MaterialTheme.typography.bodySmall)
    }
}


private data class SocialProfileItem(val platform:String, val name:String, val url:String)

private val featuredSocialProfiles = listOf(
    SocialProfileItem("TikTok", "Shakib Al Hasan", "https://www.tiktok.com/@shakibalhasan"),
    SocialProfileItem("TikTok", "Tahsan Khan", "https://www.tiktok.com/@tahsankhanofficial"),
    SocialProfileItem("TikTok", "Ayman Sadiq", "https://www.tiktok.com/@aymansadiq10"),
    SocialProfileItem("TikTok", "Rafsan The ChotoBhai", "https://www.tiktok.com/@rafsanthechotobhai"),
    SocialProfileItem("TikTok", "Tawhid Afridi", "https://www.tiktok.com/@tawhidafridi"),
    SocialProfileItem("X", "BCB Tigers", "https://x.com/BCBtigers"),
    SocialProfileItem("X", "প্রথম আলো", "https://x.com/ProthomAlo"),
    SocialProfileItem("X", "The Daily Star", "https://x.com/dailystarnews"),
    SocialProfileItem("X", "Mushfiqur Rahim", "https://x.com/mushfiqur15"),
    SocialProfileItem("X", "Tamim Iqbal", "https://x.com/TamimOfficial28")
)

@Composable
fun SocialProfileSection() {
    var selected by remember { mutableStateOf(featuredSocialProfiles.first()) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=8.dp)
            .background(Cream, RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Text("TikTok ও X অ্যাকাউন্ট", color=Navy, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
        Text("আপনার দেওয়া ১০টি প্রোফাইল থেকে বেছে দেখুন।", color=Color.Gray, style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            items(featuredSocialProfiles) { profile ->
                if (profile.url == selected.url) {
                    Button(onClick={ selected=profile }, colors=ButtonDefaults.buttonColors(containerColor=Green),
                        contentPadding=PaddingValues(horizontal=13.dp, vertical=7.dp)) {
                        Text("${profile.platform}: ${profile.name}", color=Color.White, maxLines=1)
                    }
                } else {
                    OutlinedButton(onClick={ selected=profile },
                        contentPadding=PaddingValues(horizontal=13.dp, vertical=7.dp)) {
                        Text("${profile.platform}: ${profile.name}", color=Navy, maxLines=1)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("${selected.platform} • ${selected.name}", color=Navy, fontWeight=FontWeight.SemiBold)
        AndroidView(
            modifier=Modifier.fillMaxWidth().height(650.dp),
            factory={ ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled=true
                    settings.domStorageEnabled=true
                    settings.loadsImagesAutomatically=true
                    settings.javaScriptCanOpenWindowsAutomatically=true
                    settings.setSupportMultipleWindows(true)
                    setBackgroundColor(android.graphics.Color.WHITE)
                }
            },
            update={ webView ->
                if (webView.tag != selected.url) {
                    webView.tag=selected.url
                    webView.loadUrl(selected.url)
                }
            }
        )
        Text("নোট: TikTok বা X-এর লগইন, অঞ্চল বা WebView নীতির কারণে কিছু কনটেন্ট অ্যাপে নাও খুলতে পারে। প্রয়োজনে প্রোফাইলটি ব্রাউজারে খুলুন।",
            color=Color.Gray, style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun HomeProfileScreen(onSearch:()->Unit, onMessage:()->Unit) {
    val uid = auth.currentUser?.uid ?: return
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("আপনার পরিচয় ও আগ্রহ শেয়ার করুন") }
    var posts by remember { mutableStateOf(listOf<FeedPost>()) }
    var followers by remember { mutableIntStateOf(0) }
    var connections by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        val profileListener = db.collection("publicProfiles").document(uid).addSnapshotListener { doc, e ->
            if (e != null) error = e.localizedMessage ?: "প্রোফাইল লোড হয়নি"
            else if (doc != null) {
                name = doc.getString("name") ?: ""
                username = doc.getString("username") ?: ""
                bio = doc.getString("bio") ?: "আপনার পরিচয় ও আগ্রহ শেয়ার করুন"
            }
        }
        val postListener = db.collection("posts").whereEqualTo("authorId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snap, e ->
                if (e != null) error = e.localizedMessage ?: "পোস্ট লোড হয়নি"
                else posts = snap?.documents?.mapNotNull { d -> d.toObject(FeedPost::class.java)?.copy(id=d.id) } ?: emptyList()
            }
        db.collection("follows").document(uid).collection("followers").get()
            .addOnSuccessListener { followers = it.size() }
        db.collection("users").document(uid).collection("following").get()
            .addOnSuccessListener { connections = it.size() }
        onDispose { profileListener.remove(); postListener.remove() }
    }

    Column(Modifier.fillMaxSize().background(Pale)) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier=Modifier.fillMaxSize(),
            contentPadding=PaddingValues(bottom=18.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(Pale).padding(horizontal=16.dp, vertical=18.dp),
                    horizontalAlignment=Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(112.dp).background(Color(0xFFE6DCC8), RoundedCornerShape(18.dp)), contentAlignment=Alignment.Center) {
                        Text(name.take(1).uppercase().ifBlank{"F"}, color=Green, style=MaterialTheme.typography.displayMedium, fontWeight=FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(name.ifBlank{"আপনার নাম"}, style=MaterialTheme.typography.headlineSmall, color=Navy, fontWeight=FontWeight.Bold)
                        Spacer(Modifier.width(5.dp))
                        Text("✓", color=Color(0xFF269AC7), fontWeight=FontWeight.Bold)
                    }
                    Text("@$username", color=Color.Gray, style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(5.dp))
                    Text(bio, color=Navy, style=MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("$connections", style=MaterialTheme.typography.titleLarge, color=Navy, fontWeight=FontWeight.Bold)
                            Text("Connections", color=Color.Gray, style=MaterialTheme.typography.labelMedium)
                        }
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("$followers", style=MaterialTheme.typography.titleLarge, color=Navy, fontWeight=FontWeight.Bold)
                            Text("Followers", color=Color.Gray, style=MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick=onMessage) { Text("Message", color=Navy, fontWeight=FontWeight.SemiBold) }
                        Button(onClick=onSearch, colors=ButtonDefaults.buttonColors(containerColor=Green), shape=RoundedCornerShape(28.dp)) { Text("Connect", color=Color.White) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp, vertical=10.dp), horizontalArrangement=Arrangement.SpaceEvenly) {
                    listOf("▤" to "Projects", "♧" to "Journeys", "▧" to "Thoughts", "↗" to "Growth").forEach { (glyph, label) ->
                        Column(Modifier.weight(1f).clickable { onSearch() }, horizontalAlignment=Alignment.CenterHorizontally) {
                            Box(Modifier.size(66.dp).background(Cream, CircleShape), contentAlignment=Alignment.Center) {
                                Box(Modifier.fillMaxSize().padding(3.dp).background(Color.Transparent, CircleShape)) {
                                    Text(glyph, modifier=Modifier.align(Alignment.Center), color=Green, style=MaterialTheme.typography.headlineMedium)
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(label, color=Navy, style=MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            item { FacebookPageSection() }\n            item { SocialProfileSection() }
            if (error.isNotBlank()) item { Text(error, color=MaterialTheme.colorScheme.error, modifier=Modifier.padding(12.dp)) }
            if (posts.isEmpty()) {
                item {
                    Text("আপনার পোস্টগুলো এখানে দেখা যাবে। প্রথম পোস্টটি শেয়ার করুন!", color=Color.Gray, modifier=Modifier.padding(18.dp))
                }
            } else {
                item {
                    Text("আপনার পোস্ট", color=Navy, fontWeight=FontWeight.Bold, style=MaterialTheme.typography.titleMedium, modifier=Modifier.padding(horizontal=14.dp, vertical=8.dp))
                }
                items(posts.chunked(2)) { rowPosts ->
                    Row(Modifier.fillMaxWidth()) {
                        rowPosts.forEach { post ->
                            Column(
                                Modifier.weight(1f).padding(horizontal=5.dp, vertical=5.dp)
                                    .background(Cream, RoundedCornerShape(12.dp))
                            ) {
                                if (post.mediaUrl.isNotBlank() && post.mediaType != "video") {
                                    AsyncImage(model=post.mediaUrl, contentDescription="পোস্টের ছবি", modifier=Modifier.fillMaxWidth().height(190.dp))
                                } else {
                                    Box(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFFE7E0D2), RoundedCornerShape(topStart=12.dp, topEnd=12.dp)), contentAlignment=Alignment.Center) {
                                        Text(if(post.mediaType=="video") "▶ ভিডিও" else post.caption.take(65).ifBlank{"নতুন পোস্ট"}, color=Navy)
                                    }
                                }
                                Text(post.caption.ifBlank{"নতুন পোস্ট"}, color=Navy, maxLines=2, modifier=Modifier.padding(10.dp))
                            }
                        }
                        if (rowPosts.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun FeedScreen() {
    val uid=auth.currentUser?.uid ?: return
    var posts by remember { mutableStateOf(listOf<FeedPost>()) }; var caption by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val scope= rememberCoroutineScope(); val context= LocalContext.current
    var picked by remember { mutableStateOf<Uri?>(null) }; var pickedType by remember { mutableStateOf("image") }
    val picker= rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){ uri-> picked=uri }
    DisposableEffect(Unit){val l:ListenerRegistration=db.collection("posts").whereEqualTo("visibility","public").orderBy("createdAt",Query.Direction.DESCENDING).limit(60).addSnapshotListener{snap,e->if(e==null)posts=snap?.documents?.mapNotNull{d->d.toObject(FeedPost::class.java)?.copy(id=d.id)}?: emptyList() else error=e.localizedMessage?: "ফিড লোড হয়নি"};onDispose{l.remove()}}
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(12.dp).background(Color.White,RoundedCornerShape(16.dp)).padding(12.dp)){
            Text("নতুন পোস্ট লিখুন",fontWeight=FontWeight.Bold,color=Navy)
            OutlinedTextField(caption,{caption=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("আপনার মনের কথা শেয়ার করুন...")},minLines=2,maxLines=5)
            if(picked!=null) Text("মিডিয়া নির্বাচন করা হয়েছে",color=Blue)
            Row(verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={pickedType="image";picker.launch("image/*")}){Text("ছবি")}
                TextButton(onClick={pickedType="video";picker.launch("video/*")}){Text("ভিডিও")}
                Spacer(Modifier.weight(1f))
                Button(enabled=!busy&&(caption.isNotBlank()||picked!=null),onClick={scope.launch {
                    busy=true;error=""
                    try {
                        // Avoid a profile document read before posting; it fails immediately when Firestore is offline.
                        val authorName=db.collection("publicProfiles").document(uid).get().await().getString("name")?.takeIf{it.isNotBlank()} ?: "ব্যবহারকারী"
                        var url="";var type="text"
                        if(picked!=null){type=pickedType;val fileName="${System.currentTimeMillis()}_${if(type=="video")"video.mp4" else "image.jpg"}";val ref=storage.reference.child("post-media/$uid/$fileName");url=ref.putFile(picked!!).await().storage.downloadUrl.await().toString()}
                        db.collection("posts").add(mapOf("authorId" to uid,"authorName" to authorName,"authorUsername" to "","caption" to caption.trim(),"mediaUrl" to url,"mediaType" to type,"visibility" to "public","createdAt" to System.currentTimeMillis())).await()
                        caption="";picked=null
                    }catch(e:Exception){error=e.localizedMessage?: "পোস্ট করা যায়নি"};busy=false
                }}){Text(if(busy)"আপলোড..." else "পোস্ট করুন")}
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        }
        LazyColumn(contentPadding=PaddingValues(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            items(posts,key={it.id}){post->PostCard(post)}
        }
    }
}

@Composable
fun PostCard(post:FeedPost) {
    val uid=auth.currentUser?.uid?:return
    val context= LocalContext.current
    val scope= rememberCoroutineScope()
    var liked by remember(post.id){mutableStateOf(false)}
    var likes by remember(post.id){mutableIntStateOf(0)}
    var showComments by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf(false)}
    var editText by remember(post.id){mutableStateOf(post.caption)}
    var status by remember{mutableStateOf("")}
    var showDelete by remember{mutableStateOf(false)}
    LaunchedEffect(post.id){try{liked=db.collection("posts").document(post.id).collection("likes").document(uid).get().await().exists();likes=db.collection("posts").document(post.id).collection("likes").get().await().size()}catch(_:Exception){}}
    Column(Modifier.fillMaxWidth().padding(horizontal=12.dp).background(Color.White,RoundedCornerShape(16.dp)).padding(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(38.dp).background(Blue,CircleShape),contentAlignment=Alignment.Center){Text(post.authorName.take(1).uppercase(),color=Color.White,fontWeight=FontWeight.Bold)}
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)){Text(post.authorName,fontWeight=FontWeight.Bold,color=Navy);Text("@${post.authorUsername}",style=MaterialTheme.typography.labelSmall,color=Color.Gray)}
            if(post.authorId==uid){
                TextButton(onClick={editing=true;editText=post.caption}){Text("সম্পাদনা")}
                TextButton(onClick={showDelete=true}){Text("মুছুন",color=MaterialTheme.colorScheme.error)}
            }
        }
        if(post.caption.isNotBlank()){Spacer(Modifier.height(10.dp));Text(post.caption,color=Color(0xFF263445))}
        if(post.mediaUrl.isNotBlank()){Spacer(Modifier.height(8.dp));if(post.mediaType=="video") VideoPlayer(post.mediaUrl) else AsyncImage(model=post.mediaUrl,contentDescription="পোস্টের ছবি",modifier=Modifier.fillMaxWidth().heightIn(max=360.dp))}
        if(status.isNotBlank()) Text(status,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={val ref=db.collection("posts").document(post.id).collection("likes").document(uid);if(liked){ref.delete();liked=false;likes=(likes-1).coerceAtLeast(0)}else{ref.set(mapOf("userId" to uid,"createdAt" to System.currentTimeMillis()));liked=true;likes++}}){Text(if(liked)"♥ পছন্দ হয়েছে" else "♡ লাইক  •  $likes",color=if(liked)Blue else Navy)}
            TextButton(onClick={showComments=true}){Text("মন্তব্য")}
            TextButton(onClick={
                val send=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"${post.caption}\nF Messenger পোস্ট")}
                context.startActivity(Intent.createChooser(send,"পোস্ট শেয়ার করুন"))
            }){Text("শেয়ার")}
        }
    }
    if(editing) AlertDialog(
        onDismissRequest={editing=false},
        title={Text("পোস্ট সম্পাদনা")},
        text={OutlinedTextField(editText,{editText=it},modifier=Modifier.fillMaxWidth(),minLines=3)},
        confirmButton={TextButton(onClick={scope.launch{try{db.collection("posts").document(post.id).update("caption",editText.trim(),"updatedAt",System.currentTimeMillis()).await();editing=false}catch(e:Exception){status=e.localizedMessage?: "আপডেট হয়নি"}}}){Text("সংরক্ষণ")}},
        dismissButton={TextButton(onClick={editing=false}){Text("বাতিল")}}
    )
    if(showDelete) AlertDialog(
        onDismissRequest={showDelete=false},
        title={Text("পোস্ট মুছে ফেলবেন?")},
        text={Text("এই কাজটি ফিরিয়ে আনা যাবে না।")},
        confirmButton={TextButton(onClick={scope.launch{try{db.collection("posts").document(post.id).delete().await();showDelete=false}catch(e:Exception){status=e.localizedMessage?: "মুছতে পারেনি"}}}){Text("মুছে ফেলুন",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={showDelete=false}){Text("বাতিল")}}
    )
    if(showComments) CommentsDialog(post.id){showComments=false}
}

@Composable
fun VideoPlayer(url:String) {
    AndroidView(factory={ctx->VideoView(ctx).apply{setVideoURI(Uri.parse(url));setOnPreparedListener{it.isLooping=true;start()}}},modifier=Modifier.fillMaxWidth().height(330.dp))
}

@Composable
fun CommentsDialog(postId:String,onClose:()->Unit) {
    val uid=auth.currentUser?.uid?:return;var list by remember{mutableStateOf(listOf<CommentItem>())};var draft by remember{mutableStateOf("")};val scope= rememberCoroutineScope()
    DisposableEffect(postId){val l=db.collection("posts").document(postId).collection("comments").orderBy("createdAt").addSnapshotListener{snap,_->list=snap?.documents?.mapNotNull{d->d.toObject(CommentItem::class.java)?.copy(id=d.id)}?: emptyList()};onDispose{l.remove()}}
    AlertDialog(onDismissRequest=onClose,title={Text("মন্তব্য")},text={Column(Modifier.heightIn(max=360.dp)){
        LazyColumn(Modifier.weight(1f,false)){items(list){Text("${it.authorName}: ${it.text}",modifier=Modifier.padding(vertical=5.dp))}}
        OutlinedTextField(draft,{draft=it},label={Text("মন্তব্য লিখুন")},modifier=Modifier.fillMaxWidth())
    }},confirmButton={TextButton(onClick={val text=draft.trim();if(text.isNotBlank())scope.launch{try{val me=db.collection("publicProfiles").document(uid).get().await().toObject(Person::class.java);db.collection("posts").document(postId).collection("comments").add(mapOf("authorId" to uid,"authorName" to (me?.name?: "ব্যবহারকারী"),"text" to text,"createdAt" to System.currentTimeMillis())).await();draft=""}catch(_:Exception){}}}){Text("পাঠান")}},dismissButton={TextButton(onClick=onClose){Text("বন্ধ")}})
}

@Composable
fun ReelsScreen() {
    var videos by remember{mutableStateOf(listOf<FeedPost>())};var error by remember{mutableStateOf("")}
    DisposableEffect(Unit){val l=db.collection("posts").whereEqualTo("visibility","public").whereEqualTo("mediaType","video").orderBy("createdAt",Query.Direction.DESCENDING).limit(40).addSnapshotListener{snap,e->if(e==null)videos=snap?.documents?.mapNotNull{d->d.toObject(FeedPost::class.java)?.copy(id=d.id)}?:emptyList() else error=e.localizedMessage?: "ভিডিও লোড হয়নি"};onDispose{l.remove()}}
    if(error.isNotBlank())Text(error)
    else if(videos.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("এখনো কোনো রিলস নেই। ফিড থেকে ভিডিও পোস্ট করুন।",color=Color.Gray)}
    else LazyColumn{items(videos,key={it.id}){p->Column(Modifier.fillMaxWidth().padding(10.dp).background(Color.Black,RoundedCornerShape(16.dp)).padding(10.dp)){VideoPlayer(p.mediaUrl);Text(p.authorName,color=Color.White,fontWeight=FontWeight.Bold);Text(p.caption,color=Color.White)}}}
}

@Composable
fun DiscoverScreen(onChat:(Person)->Unit) {
    var query by remember{mutableStateOf("")};var results by remember{mutableStateOf(listOf<Person>())};var error by remember{mutableStateOf("")};val scope= rememberCoroutineScope();val uid=auth.currentUser?.uid?:return
    Column(Modifier.fillMaxSize().padding(12.dp)){
        Text("মানুষ খুঁজুন",style=MaterialTheme.typography.titleLarge,color=Navy,fontWeight=FontWeight.Bold)
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),label={Text("নাম অথবা Username")},trailingIcon={TextButton(onClick={scope.launch{error="";try{val q=query.trim();if(q.isBlank()){results=emptyList();return@launch};val a=db.collection("publicProfiles").whereEqualTo("profileVisibility","public").orderBy("name").startAt(q).endAt(q+"").limit(20).get().await();val b=db.collection("publicProfiles").whereEqualTo("profileVisibility","public").whereEqualTo("usernameLower",q.lowercase()).limit(20).get().await();results=(a.documents+b.documents).mapNotNull{it.toObject(Person::class.java)}.filter{it.uid!=uid}.distinctBy{it.uid}}catch(e:Exception){error=e.localizedMessage?: "খোঁজা যায়নি"}}}){Text("খুঁজুন")}})
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        LazyColumn{items(results,key={it.uid}){person->PersonRow(person,onChat)}}
    }
}

@Composable
fun PersonRow(person:Person,onChat:(Person)->Unit) {
    val uid=auth.currentUser?.uid?:return;var following by remember(person.uid){mutableStateOf(false)};val scope= rememberCoroutineScope()
    LaunchedEffect(person.uid){following=db.collection("users").document(uid).collection("following").document(person.uid).get().await().exists()}
    Row(Modifier.fillMaxWidth().clickable{onChat(person)}.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(42.dp).background(Blue,CircleShape),contentAlignment=Alignment.Center){Text(person.name.take(1).uppercase(),color=Color.White)}
        Column(Modifier.weight(1f).padding(start=10.dp)){Text(person.name,color=Navy,fontWeight=FontWeight.Bold);Text("@${person.username}",color=Color.Gray)}
        TextButton(onClick={scope.launch{val a=db.collection("follows").document(person.uid).collection("followers").document(uid);val b=db.collection("users").document(uid).collection("following").document(person.uid);if(following){a.delete().await();b.delete().await();following=false}else{a.set(mapOf("uid" to uid,"createdAt" to System.currentTimeMillis())).await();b.set(mapOf("uid" to person.uid,"createdAt" to System.currentTimeMillis())).await();following=true}}}){Text(if(following)"Following" else "Follow")}
        TextButton(onClick={onChat(person)}){Text("চ্যাট")}
    };HorizontalDivider()
}

@Composable
fun ChatsScreen(onChat:(Person)->Unit) {
    val uid=auth.currentUser?.uid?:return;var people by remember{mutableStateOf(listOf<Person>())}
    LaunchedEffect(uid){try{val chats=db.collection("chats").whereArrayContains("members",uid).get().await();val ids=chats.documents.flatMap{(it.get("members") as? List<*>)?.filterIsInstance<String>()?:emptyList()}.filter{it!=uid}.distinct();people=ids.mapNotNull{id->db.collection("publicProfiles").document(id).get().await().toObject(Person::class.java)}}catch(_:Exception){}}
    Column(Modifier.fillMaxSize().padding(12.dp)){Text("আপনার চ্যাট",style=MaterialTheme.typography.titleLarge,color=Navy,fontWeight=FontWeight.Bold);if(people.isEmpty())Text("কাউকে খুঁজে নিয়ে চ্যাট শুরু করুন।",color=Color.Gray);LazyColumn{items(people){PersonRow(it,onChat)}}}
}

@Composable
fun ProfileScreen() {
    val uid=auth.currentUser?.uid?:return
    var name by remember{mutableStateOf("")}
    var username by remember{mutableStateOf("")}
    var phone by remember{mutableStateOf("")}
    var bio by remember{mutableStateOf("")}
    var avatarUrl by remember{mutableStateOf("")}
    var coverUrl by remember{mutableStateOf("")}
    var profileVisibility by remember{mutableStateOf("public")}
    var count by remember{mutableIntStateOf(0)}
    var busy by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    val scope= rememberCoroutineScope()
    val pickerContext= LocalContext.current
    val avatarPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null) scope.launch{try{busy=true;val ref=storage.reference.child("profile-media/$uid/avatar_${System.currentTimeMillis()}.jpg");avatarUrl=ref.putFile(uri).await().storage.downloadUrl.await().toString();status="প্রোফাইল ছবি আপলোড হয়েছে"}catch(e:Exception){status=e.localizedMessage?: "ছবি আপলোড হয়নি"}finally{busy=false}}
    }
    val coverPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null) scope.launch{try{busy=true;val ref=storage.reference.child("profile-media/$uid/cover_${System.currentTimeMillis()}.jpg");coverUrl=ref.putFile(uri).await().storage.downloadUrl.await().toString();status="কভার ফটো আপলোড হয়েছে"}catch(e:Exception){status=e.localizedMessage?: "কভার আপলোড হয়নি"}finally{busy=false}}
    }
    LaunchedEffect(uid){try{
        val doc=db.collection("publicProfiles").document(uid).get().await()
        name=doc.getString("name")?: ""
        username=doc.getString("username")?: ""
        bio=doc.getString("bio")?: ""
        avatarUrl=doc.getString("avatarUrl")?: ""
        coverUrl=doc.getString("coverUrl")?: ""
        profileVisibility=doc.getString("profileVisibility")?: "public"
        phone=db.collection("users").document(uid).get().await().getString("phone")?: ""
        count=db.collection("posts").whereEqualTo("authorId",uid).get().await().size()
    }catch(e:Exception){status=e.localizedMessage?: "প্রোফাইল লোড হয়নি"}}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                if(coverUrl.isNotBlank()) AsyncImage(model=coverUrl,contentDescription="কভার ফটো",modifier=Modifier.fillMaxWidth().height(130.dp))
                else Box(Modifier.fillMaxWidth().height(90.dp).background(Cream,RoundedCornerShape(14.dp)))
                Spacer(Modifier.height(8.dp))
                if(avatarUrl.isNotBlank()) AsyncImage(model=avatarUrl,contentDescription="প্রোফাইল ছবি",modifier=Modifier.size(88.dp).background(Blue,CircleShape))
                else Box(Modifier.size(88.dp).background(Blue,CircleShape),contentAlignment=Alignment.Center){Text(name.take(1).uppercase().ifBlank{"F"},color=Color.White,style=MaterialTheme.typography.headlineLarge)}
                Text(name.ifBlank{"আপনার নাম"},style=MaterialTheme.typography.headlineSmall,color=Navy,fontWeight=FontWeight.Bold)
                Text("@$username",color=Color.Gray)
                if(bio.isNotBlank()) Text(bio,color=Navy)
                Text("পোস্ট: $count",fontWeight=FontWeight.Bold)
            }
        }
        item {
            Text("প্রোফাইল সম্পাদনা",style=MaterialTheme.typography.titleMedium,color=Navy,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=14.dp))
            OutlinedTextField(name,{name=it},label={Text("নাম")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(username,{username=it.lowercase().replace(" ","")},label={Text("Username")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(bio,{bio=it},label={Text("ছোট বায়ো")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4)
            OutlinedTextField(phone,{phone=it},label={Text("ফোন নম্বর (ঐচ্ছিক)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={avatarPicker.launch("image/*")},modifier=Modifier.weight(1f),enabled=!busy){Text("প্রোফাইল ছবি")}
                OutlinedButton(onClick={coverPicker.launch("image/*")},modifier=Modifier.weight(1f),enabled=!busy){Text("কভার ফটো")}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("প্রাইভেট প্রোফাইল",fontWeight=FontWeight.SemiBold,color=Navy)
                    Text("চালু করলে শুধু আপনি ও অনুমোদিত ফলোয়াররা প্রোফাইল দেখতে পারবে",style=MaterialTheme.typography.bodySmall,color=Color.Gray)
                }
                Switch(checked=profileVisibility=="private",onCheckedChange={profileVisibility=if(it)"private" else "public"})
            }
            if(status.isNotBlank()) Text(status,color=if(status.contains("হয়েছে"))Blue else MaterialTheme.colorScheme.error)
            Button(onClick={scope.launch{
                busy=true;status=""
                try{
                    require(name.trim().isNotEmpty()){"নাম লিখুন"}
                    require(username.trim().length>=3){"Username কমপক্ষে ৩ অক্ষরের হতে হবে"}
                    db.collection("publicProfiles").document(uid).set(
                        mapOf("uid" to uid,"name" to name.trim(),"username" to username.trim(),"usernameLower" to username.trim().lowercase(),
                            "bio" to bio.trim(),"avatarUrl" to avatarUrl,"coverUrl" to coverUrl,"profileVisibility" to profileVisibility),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                    db.collection("users").document(uid).set(
                        mapOf("phone" to phone.trim(),"phoneDigits" to phone.filter{it.isDigit()}),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                    status="প্রোফাইল সংরক্ষিত হয়েছে"
                }catch(e:Exception){status=e.localizedMessage?: "সংরক্ষণ করা যায়নি"}
                busy=false
            }},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(busy)"সংরক্ষণ হচ্ছে..." else "প্রোফাইল সংরক্ষণ করুন")}
        }
    }
}

/**
 * WebRTC voice-call coordinator. Firestore is used only for SDP/ICE signaling;
 * media travels peer-to-peer. Configure a TURN server for reliable mobile/NAT calls.
 */
private object CallCoordinator {
    var activeCallId by mutableStateOf<String?>(null)
    var callStatus by mutableStateOf("")
    var remoteName by mutableStateOf("")
    var incomingCallId by mutableStateOf<String?>(null)
    var incomingCallerName by mutableStateOf("")
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var callListener: ListenerRegistration? = null
    private var signalListener: ListenerRegistration? = null
    private var candidateListener: ListenerRegistration? = null
    private var localUid: String = ""
    private var remoteUid: String = ""
    private var localCandidatePath: String = ""
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    private fun addOrQueueRemoteCandidate(candidate: IceCandidate) {
        val currentPeer = peer ?: return
        if (currentPeer.remoteDescription != null) currentPeer.addIceCandidate(candidate)
        else synchronized(pendingRemoteCandidates) { pendingRemoteCandidates.add(candidate) }
    }

    private fun flushRemoteCandidates() {
        val currentPeer = peer ?: return
        val queued = synchronized(pendingRemoteCandidates) {
            val copy = pendingRemoteCandidates.toList()
            pendingRemoteCandidates.clear()
            copy
        }
        queued.forEach { currentPeer.addIceCandidate(it) }
    }

    private fun initFactory(context: Context) {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    fun listenForIncoming(uid: String) {
        if (localUid == uid && callListener != null) return
        callListener?.remove()
        localUid = uid
        callListener = db.collection("calls").whereEqualTo("calleeId", uid)
            .whereEqualTo("status", "ringing").addSnapshotListener { snap, _ ->
                // Do not show the incoming-call dialog until the caller's SDP offer
            // has been committed. This prevents accepting a call before its offer exists.
            val doc = snap?.documents?.firstOrNull { d ->
                val offer = d.get("offer") as? Map<*, *>
                (offer?.get("sdp") as? String)?.isNotBlank() == true
            }
            if (doc != null && activeCallId == null) {
                incomingCallId = doc.id
                incomingCallerName = doc.getString("callerName") ?: "অজানা ব্যবহারকারী"
            }
            }
    }

    fun placeVoiceCall(context: Context, person: Person) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callStatus = "মাইক্রোফোনের অনুমতি প্রয়োজন"; return
        }
        try {
            initFactory(context)
            localUid = auth.currentUser?.uid ?: return
            remoteUid = person.uid
            remoteName = person.name
            callStatus = "কল করা হচ্ছে..."
            val ref = db.collection("calls").document()
            activeCallId = ref.id
            localCandidatePath = "callerCandidates"
            ref.set(mapOf(
                "callerId" to localUid, "calleeId" to remoteUid,
                "callerName" to (auth.currentUser?.displayName ?: "ব্যবহারকারী"),
                "status" to "ringing", "type" to "audio", "createdAt" to System.currentTimeMillis()
            )).addOnSuccessListener {
                // Create the offer only after the call document exists.
                createPeer(context, ref.id, true) { readyPeer ->
                    if (readyPeer == null) {
                        callStatus = "কলের নেটওয়ার্ক প্রস্তুত করা যায়নি"
                        endLocal()
                        return@createPeer
                    }
                    readyPeer.createOffer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        peer?.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                ref.update("offer", mapOf("type" to desc.type.canonicalForm(), "sdp" to desc.description))
                                    .addOnSuccessListener { callStatus = "রিং হচ্ছে..." }
                                    .addOnFailureListener { callStatus = "কলের অফার পাঠানো যায়নি: ${it.localizedMessage}" }
                            }
                            override fun onCreateSuccess(value: SessionDescription) {}
                            override fun onCreateFailure(error: String) { callStatus = "SDP ব্যর্থ: $error" }
                            override fun onSetFailure(error: String) { callStatus = "SDP ব্যর্থ: $error" }
                        }, desc)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String) { callStatus = "Offer ব্যর্থ: $error"; endLocal() }
                    override fun onSetFailure(error: String) { callStatus = "SDP ব্যর্থ: $error"; endLocal() }
                    }, MediaConstraints())
                    observeCall(ref.id, true)
                }
            }.addOnFailureListener {
                callStatus = "কল শুরু হয়নি: ${it.localizedMessage}"
                endLocal()
            }
        } catch (e: Exception) { callStatus = e.localizedMessage ?: "কল শুরু করা যায়নি" }
    }

    fun acceptVoiceCall(context: Context, callId: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callStatus = "মাইক্রোফোনের অনুমতি প্রয়োজন"; return
        }
        try {
            initFactory(context)
            val ref = db.collection("calls").document(callId)
            ref.get().addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener
                localUid = auth.currentUser?.uid ?: return@addOnSuccessListener
                remoteUid = doc.getString("callerId") ?: return@addOnSuccessListener
                remoteName = doc.getString("callerName") ?: "ব্যবহারকারী"
                activeCallId = callId
                incomingCallId = null
                localCandidatePath = "calleeCandidates"
                val offerMap = doc.get("offer") as? Map<*, *> ?: return@addOnSuccessListener
                val offer = SessionDescription(SessionDescription.Type.OFFER, offerMap["sdp"] as? String ?: return@addOnSuccessListener)
                createPeer(context, callId, false) { readyPeer ->
                    if (readyPeer == null) {
                        callStatus = "কলের নেটওয়ার্ক প্রস্তুত করা যায়নি"
                        endLocal()
                        return@createPeer
                    }
                    readyPeer.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        flushRemoteCandidates()
                        peer?.createAnswer(object : SdpObserver {
                            override fun onCreateSuccess(desc: SessionDescription) {
                                peer?.setLocalDescription(SimpleSdpObserver(), desc)
                                ref.update("answer", mapOf("type" to desc.type.canonicalForm(), "sdp" to desc.description), "status", "connected")
                                callStatus = "কল সংযুক্ত"
                            }
                            override fun onSetSuccess() {}
                            override fun onCreateFailure(error: String) { callStatus = error }
                            override fun onSetFailure(error: String) { callStatus = error }
                        }, MediaConstraints())
                    }
                    override fun onCreateSuccess(desc: SessionDescription) {}
                    override fun onCreateFailure(error: String) { callStatus = error }
                    override fun onSetFailure(error: String) { callStatus = error }
                    }, offer)
                    observeCall(callId, false)
                }
            }.addOnFailureListener { callStatus = it.localizedMessage ?: "কল গ্রহণ করা যায়নি" }
        } catch (e: Exception) { callStatus = e.localizedMessage ?: "কল গ্রহণ করা যায়নি" }
    }

    private fun createPeer(context: Context, callId: String, caller: Boolean, onReady: (PeerConnection?) -> Unit) {
        // Metered TURN credentials. These are embedded as explicitly requested.
        // WARNING: APKs and public source repositories can expose these credentials.
        val meteredUsername = "8a999f7f2a241910a335f635"
        val meteredCredential = "SxG/L3xCwVwCOgO9"
        val iceServers = listOf(
            IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
            IceServer.builder("turn:global.relay.metered.ca:80")
                .setUsername(meteredUsername).setPassword(meteredCredential).createIceServer(),
            IceServer.builder("turn:global.relay.metered.ca:80?transport=tcp")
                .setUsername(meteredUsername).setPassword(meteredCredential).createIceServer(),
            IceServer.builder("turn:global.relay.metered.ca:443")
                .setUsername(meteredUsername).setPassword(meteredCredential).createIceServer(),
            IceServer.builder("turns:global.relay.metered.ca:443?transport=tcp")
                .setUsername(meteredUsername).setPassword(meteredCredential).createIceServer()
        )
        callStatus = "Metered TURN দিয়ে সংযোগের চেষ্টা করা হচ্ছে"
        createPeerWithIceServers(context, callId, caller, iceServers, onReady)
    }

    private fun createPeerWithIceServers(
        context: Context,
        callId: String,
        caller: Boolean,
        iceServers: List<IceServer>,
        onReady: (PeerConnection?) -> Unit
    ) {
        val rtcConfig = RTCConfiguration(iceServers).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }
        peer = factory?.createPeerConnection(rtcConfig, object : Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> callStatus = "কল সংযুক্ত"
                    PeerConnection.IceConnectionState.DISCONNECTED -> callStatus = "সংযোগ বিচ্ছিন্ন"
                    PeerConnection.IceConnectionState.FAILED -> callStatus = "নেটওয়ার্ক সংযোগ ব্যর্থ"
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                val path = if (caller) "callerCandidates" else "calleeCandidates"
                db.collection("calls").document(callId).collection(path).add(mapOf(
                    "sdpMid" to candidate.sdpMid, "sdpMLineIndex" to candidate.sdpMLineIndex, "candidate" to candidate.sdp
                ))
            }
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<MediaStream>) {}
        })
        audioSource = factory?.createAudioSource(MediaConstraints())
        audioTrack = factory?.createAudioTrack("f-messenger-audio", audioSource)
        audioTrack?.setEnabled(true)
        peer?.addTrack(audioTrack, listOf("f-messenger-stream"))
        synchronized(pendingRemoteCandidates) { pendingRemoteCandidates.clear() }
        val remotePath = if (caller) "calleeCandidates" else "callerCandidates"
        candidateListener?.remove()
        candidateListener = db.collection("calls").document(callId).collection(remotePath).addSnapshotListener { snap, _ ->
            snap?.documentChanges?.forEach { change ->
                val d = change.document
                val mid = d.getString("sdpMid")
                val index = (d.getLong("sdpMLineIndex") ?: 0L).toInt()
                val candidate = d.getString("candidate")
                if (mid != null && candidate != null) addOrQueueRemoteCandidate(IceCandidate(mid, index, candidate))
            }
        }
        onReady(peer)
    }

    private fun observeCall(callId: String, caller: Boolean) {
        signalListener?.remove()
        signalListener = db.collection("calls").document(callId).addSnapshotListener { doc, _ ->
            if (doc == null || !doc.exists()) { endLocal(); return@addSnapshotListener }
            val status = doc.getString("status") ?: ""
            if (status == "ended" || status == "rejected" || status == "missed") {
                callStatus = if (status == "rejected") "কল প্রত্যাখ্যান করা হয়েছে" else "কল শেষ"
                endLocal(); return@addSnapshotListener
            }
            if (caller) {
                val answer = doc.get("answer") as? Map<*, *>
                if (answer != null && peer?.remoteDescription == null) {
                    val sdp = answer["sdp"] as? String
                    if (sdp != null) peer?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() { flushRemoteCandidates() }
                        override fun onCreateSuccess(value: SessionDescription) {}
                        override fun onCreateFailure(error: String) { callStatus = error }
                        override fun onSetFailure(error: String) { callStatus = error }
                    }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                    callStatus = "কল সংযুক্ত হচ্ছে..."
                }
            }
        }
    }

    fun rejectIncoming(callId: String) {
        db.collection("calls").document(callId).update("status", "rejected")
        incomingCallId = null
    }

    fun endCall() {
        val id = activeCallId
        if (id != null) db.collection("calls").document(id).update("status", "ended")
        endLocal()
    }

    private fun endLocal() {
        signalListener?.remove(); signalListener = null
        candidateListener?.remove(); candidateListener = null
        peer?.close(); peer = null
        synchronized(pendingRemoteCandidates) { pendingRemoteCandidates.clear() }
        audioTrack?.dispose(); audioTrack = null
        audioSource?.dispose(); audioSource = null
        activeCallId = null
        incomingCallId = null
    }

    private class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String) {}
        override fun onSetFailure(error: String) {}
    }
}

@Composable
private fun IncomingCallHost() {
    val context = LocalContext.current
    val callId = CallCoordinator.incomingCallId
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = CallCoordinator.incomingCallId
        if (granted && id != null) CallCoordinator.acceptVoiceCall(context, id)
    }
    if (callId != null) AlertDialog(
        onDismissRequest = { CallCoordinator.rejectIncoming(callId) },
        title = { Text("ইনকামিং ভয়েস কল") },
        text = { Text("${CallCoordinator.incomingCallerName} আপনাকে কল করছেন।") },
        confirmButton = { TextButton(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                CallCoordinator.acceptVoiceCall(context, callId)
            else permissions.launch(Manifest.permission.RECORD_AUDIO)
        }) { Text("রিসিভ") } },
        dismissButton = { TextButton(onClick = { CallCoordinator.rejectIncoming(callId) }) { Text("বাতিল") } }
    )
    if (CallCoordinator.activeCallId != null) AlertDialog(
        onDismissRequest = { CallCoordinator.endCall() },
        title = { Text("Pasha অ্যাপ কল") },
        text = { Column { Text(CallCoordinator.remoteName); Text(CallCoordinator.callStatus) } },
        confirmButton = { TextButton(onClick = { CallCoordinator.endCall() }) { Text("কল কেটে দিন") } }
    )
}


@Composable
fun AdminReportsScreen() {
    var reports by remember { mutableStateOf(listOf<com.google.firebase.firestore.DocumentSnapshot>()) }
    var status by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        val listener = db.collection("reports").orderBy("createdAt", Query.Direction.DESCENDING).limit(100).addSnapshotListener { snap, error ->
            if (error != null) status = error.localizedMessage ?: "রিপোর্ট লোড হয়নি" else reports = snap?.documents ?: emptyList()
        }
        onDispose { listener.remove() }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("অ্যাডমিন প্যানেল • রিপোর্ট", style=MaterialTheme.typography.titleLarge, color=Navy, fontWeight=FontWeight.Bold)
        if (status.isNotBlank()) Text(status, color=MaterialTheme.colorScheme.error)
        if (reports.isEmpty()) Text("কোনো রিপোর্ট নেই", color=Color.Gray)
        LazyColumn { items(reports, key={ it.id }) { report ->
            val target = report.getString("targetId") ?: "unknown"
            Column(Modifier.fillMaxWidth().padding(vertical=8.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text("টার্গেট: " + target, color=Navy, fontWeight=FontWeight.Bold)
                Text("কারণ: " + (report.getString("reason") ?: "উল্লেখ নেই"))
                Text("অবস্থা: " + (report.getString("status") ?: "open"), color=Color.Gray)
                TextButton(onClick={ report.reference.update(mapOf("status" to "reviewed", "reviewedAt" to System.currentTimeMillis())) }) { Text("রিভিউ সম্পন্ন") }
            }
        } }
    }
}

@Composable
fun ChatScreen(person:Person,onBack:()->Unit) {
    val uid=auth.currentUser?.uid?:return;val chatId=listOf(uid,person.uid).sorted().joinToString("_");var messages by remember{mutableStateOf(listOf<ChatMessage>())};var draft by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};val scope= rememberCoroutineScope();val context= LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var voiceBusy by remember { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) CallCoordinator.placeVoiceCall(context, person) else error = "কল করতে মাইক্রোফোনের অনুমতি দিন"
    }
    DisposableEffect(chatId){val l:ListenerRegistration=db.collection("chats").document(chatId).collection("messages").orderBy("createdAt").addSnapshotListener{snap,e->if(e!=null)error=e.localizedMessage?: "মেসেজ লোড হয়নি" else messages=snap?.documents?.mapNotNull{d->d.toObject(ChatMessage::class.java)?.copy(id=d.id)}?:emptyList()};onDispose{l.remove()}}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal=8.dp, vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=onBack){Text("‹",color=Color.White,style=MaterialTheme.typography.headlineMedium)}
            Column(Modifier.weight(1f)){Text(person.name,color=Color.White,fontWeight=FontWeight.Bold);Text("@${person.username}",color=Color(0xFFD8E8FF))}
            TextButton(onClick={ scope.launch { try {
                db.collection("reports").add(mapOf("reporterId" to uid, "targetId" to person.uid, "reason" to "ব্যবহারকারী রিপোর্ট", "status" to "open", "createdAt" to System.currentTimeMillis())).await()
                error = "রিপোর্ট জমা হয়েছে"
            } catch(e:Exception) { error = e.localizedMessage ?: "রিপোর্ট পাঠানো যায়নি" } } }) { Text("রিপোর্ট", color=Color.White) }
            TextButton(onClick={ scope.launch { try {
                db.collection("blocks").document(uid).collection("blocked").document(person.uid).set(mapOf("blockedUid" to person.uid, "createdAt" to System.currentTimeMillis())).await()
                error = "ব্যবহারকারী ব্লক করা হয়েছে"
            } catch(e:Exception) { error = e.localizedMessage ?: "ব্লক করা যায়নি" } } }) { Text("ব্লক", color=Color.White) }
            TextButton(onClick={
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    CallCoordinator.placeVoiceCall(context, person)
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text("📞 কল", color=Color.White, fontWeight=FontWeight.Bold) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp)){items(messages){m->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=if(m.senderId==uid)Arrangement.End else Arrangement.Start){
                Surface(color=if(m.senderId==uid)Blue else Color.White,shape=RoundedCornerShape(15.dp)){
                    if(m.mediaType=="audio" && m.mediaUrl.isNotBlank()) {
                        TextButton(onClick={
                            try { MediaPlayer().apply { setDataSource(m.mediaUrl); setOnPreparedListener { it.start() }; prepareAsync() } }
                            catch(e:Exception){ error=e.localizedMessage ?: "ভয়েস মেসেজ চালানো যায়নি" }
                        }) { Text("▶ ভয়েস মেসেজ", color=if(m.senderId==uid)Color.White else Navy) }
                    } else Text(m.text,Modifier.padding(12.dp),color=if(m.senderId==uid)Color.White else Navy)
                }
            };Spacer(Modifier.height(7.dp))
        }}
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp),verticalAlignment=Alignment.CenterVertically){
            OutlinedTextField(draft,{draft=it},modifier=Modifier.weight(1f),placeholder={Text("মেসেজ লিখুন...")})
            TextButton(enabled=!voiceBusy,onClick={
                if(!recording){
                    try {
                        val file=File(context.cacheDir,"voice_${System.currentTimeMillis()}.m4a")
                        val r=MediaRecorder()
                        r.setAudioSource(MediaRecorder.AudioSource.MIC)
                        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        r.setAudioEncodingBitRate(64000)
                        r.setAudioSamplingRate(44100)
                        r.setOutputFile(file.absolutePath)
                        r.prepare();r.start();recorder=r;voiceFile=file;recording=true;error="রেকর্ড হচ্ছে..."
                    } catch(e:Exception){recorder?.release();recorder=null;recording=false;error=e.localizedMessage ?: "রেকর্ড শুরু হয়নি"}
                } else {
                    try { recorder?.stop();recorder?.release();recorder=null;recording=false;voiceBusy=true;error="ভয়েস মেসেজ পাঠানো হচ্ছে..." }
                    catch(e:Exception){recorder?.release();recorder=null;recording=false;voiceBusy=false;error="রেকর্ডিং সংরক্ষণ করা যায়নি"}
                    val file=voiceFile
                    if(file!=null) scope.launch {
                        try {
                            val ref=db.collection("chats").document(chatId)
                            val remote=storage.reference.child("chat-media/$chatId/${file.name}")
                            remote.putFile(Uri.fromFile(file)).await()
                            val url=remote.downloadUrl.await().toString()
                            ref.set(mapOf("members" to listOf(uid,person.uid),"lastMessage" to "🎤 ভয়েস মেসেজ","updatedAt" to System.currentTimeMillis())).await()
                            ref.collection("messages").add(mapOf("senderId" to uid,"text" to "","mediaUrl" to url,"mediaType" to "audio","createdAt" to System.currentTimeMillis())).await()
                            file.delete();voiceFile=null;error=""
                        } catch(e:Exception){error=e.localizedMessage ?: "ভয়েস মেসেজ পাঠানো যায়নি"}
                        voiceBusy=false
                    } else voiceBusy=false
                }
            }){Text(if(voiceBusy)"..." else if(recording)"■" else "🎤")}
            TextButton(onClick={val msg=draft.trim();if(msg.isNotBlank())scope.launch{try{val ref=db.collection("chats").document(chatId);ref.set(mapOf("members" to listOf(uid,person.uid),"lastMessage" to msg,"updatedAt" to System.currentTimeMillis())).await();ref.collection("messages").add(mapOf("senderId" to uid,"text" to msg,"mediaType" to "text","createdAt" to System.currentTimeMillis())).await();draft=""}catch(e:Exception){error=e.localizedMessage?: "পাঠানো যায়নি"}}}){Text("পাঠান")}
        }
    }
}
