package com.fmessenger.chat

import android.net.Uri
import android.Manifest
import android.content.Context
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
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val Navy = Color(0xFF123B70)
private val Blue = Color(0xFF247BDB)
private val Pale = Color(0xFFF1F6FC)
private val db by lazy { FirebaseFirestore.getInstance() }
private val auth by lazy { FirebaseAuth.getInstance() }
private val storage by lazy { FirebaseStorage.getInstance() }

data class Person(val uid:String="", val name:String="", val username:String="", val phone:String="")
data class FeedPost(val id:String="", val authorId:String="", val authorName:String="", val authorUsername:String="", val caption:String="", val mediaUrl:String="", val mediaType:String="text", val createdAt:Long=0L)
data class ChatMessage(val id:String="", val senderId:String="", val text:String="", val createdAt:Long=0L)
data class CommentItem(val id:String="", val authorId:String="", val authorName:String="", val text:String="", val createdAt:Long=0L)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Blue,background=Pale)) { MessengerApp() } } }
}

@Composable
fun MessengerApp() {
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    var selected by remember { mutableStateOf<Person?>(null) }
    DisposableEffect(Unit) { val l=FirebaseAuth.AuthStateListener { uid=it.currentUser?.uid }; auth.addAuthStateListener(l); onDispose { auth.removeAuthStateListener(l) } }
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
    var register by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    val scope= rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement=Arrangement.Center) {
        Box(Modifier.size(72.dp).background(Blue,CircleShape),contentAlignment=Alignment.Center){Text("F",color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)}
        Spacer(Modifier.height(14.dp)); Text("F Messenger",style=MaterialTheme.typography.headlineMedium,color=Navy,fontWeight=FontWeight.Bold)
        Text(if(register) "সবার জন্য সামাজিক যোগাযোগ" else "আপনার মানুষের সঙ্গে যুক্ত থাকুন",color=Color.Gray)
        Spacer(Modifier.height(18.dp))
        if(register){ OutlinedTextField(name,{name=it},label={Text("আপনার নাম")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(username,{username=it.lowercase().replace(" ","")},label={Text("Username")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(phone,{phone=it},label={Text("ফোন নম্বর")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),modifier=Modifier.fillMaxWidth()) }
        OutlinedTextField(email,{email=it},label={Text("ইমেইল")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick={scope.launch { busy=true;error="";try {
            require(email.contains("@") && password.length>=6){"সঠিক ইমেইল ও কমপক্ষে ৬ অক্ষরের পাসওয়ার্ড দিন"}
            if(register){ require(name.isNotBlank()&&username.length>=3&&phone.isNotBlank()){"নাম, ৩ অক্ষরের Username ও ফোন নম্বর দিন"}
                val u=auth.createUserWithEmailAndPassword(email.trim(),password).await().user!!
                db.collection("users").document(u.uid).set(mapOf("uid" to u.uid,"phone" to phone.trim(),"phoneDigits" to phone.filter{it.isDigit()},"email" to email.trim(),"createdAt" to System.currentTimeMillis())).await(); db.collection("publicProfiles").document(u.uid).set(mapOf("uid" to u.uid,"name" to name.trim(),"username" to username.trim(),"usernameLower" to username.trim().lowercase(),"createdAt" to System.currentTimeMillis())).await(); onSignedIn(u.uid)
            } else {
                val signed=auth.signInWithEmailAndPassword(email.trim(),password).await().user!!
                val old=db.collection("users").document(signed.uid).get().await()
                val publicRef=db.collection("publicProfiles").document(signed.uid)
                if(!publicRef.get().await().exists() && old.exists()) {
                    publicRef.set(mapOf("uid" to signed.uid,"name" to (old.getString("name") ?: "ব্যবহারকারী"),"username" to (old.getString("username") ?: ""),"usernameLower" to (old.getString("usernameLower") ?: ""))).await()
                }
                onSignedIn(signed.uid)
            }
        }catch(e:Exception){error=e.localizedMessage?: "আবার চেষ্টা করুন"};busy=false }},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(busy)"অপেক্ষা করুন..." else if(register)"অ্যাকাউন্ট তৈরি করুন" else "লগইন")}
        if(!register) TextButton(onClick={scope.launch { error=""; if(!email.contains("@")){error="পাসওয়ার্ড রিসেট করতে ইমেইল লিখুন"}else try{auth.sendPasswordResetEmail(email.trim()).await();error="পাসওয়ার্ড রিসেট লিংক ইমেইলে পাঠানো হয়েছে"}catch(e:Exception){error=e.localizedMessage?: "রিসেট ইমেইল পাঠানো যায়নি"} }},modifier=Modifier.align(Alignment.CenterHorizontally)){Text("পাসওয়ার্ড ভুলে গেছেন?")}
        TextButton(onClick={register=!register;error=""},modifier=Modifier.align(Alignment.CenterHorizontally)){Text(if(register)"আগে অ্যাকাউন্ট আছে? লগইন" else "নতুন অ্যাকাউন্ট খুলুন")}
    }
}

@Composable
fun MainTabs(onChat:(Person)->Unit,onSignOut:()->Unit) {
    var tab by remember { mutableStateOf("ফিড") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal=16.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("F Messenger",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("Connect • Share • Discover",color=Color(0xFFD8E8FF),style=MaterialTheme.typography.labelSmall)}
            TextButton(onClick=onSignOut){Text("লগআউট",color=Color.White)}
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when(tab){
                "ফিড"-> FeedScreen()
                "খুঁজুন"-> DiscoverScreen(onChat)
                "রিলস"-> ReelsScreen()
                "চ্যাট"-> ChatsScreen(onChat)
                else-> ProfileScreen()
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White),horizontalArrangement=Arrangement.SpaceEvenly){
            listOf("ফিড","খুঁজুন","রিলস","চ্যাট","প্রোফাইল").forEach { item ->
                TextButton(onClick={tab=item}){Text(item,color=if(tab==item)Blue else Color.Gray,fontWeight=if(tab==item)FontWeight.Bold else FontWeight.Normal)}
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
                        val authorName=auth.currentUser?.displayName?.takeIf{it.isNotBlank()} ?: "ব্যবহারকারী"
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
    val uid=auth.currentUser?.uid?:return; var liked by remember(post.id){mutableStateOf(false)};var likes by remember(post.id){mutableIntStateOf(0)};var showComments by remember{mutableStateOf(false)}
    LaunchedEffect(post.id){try{liked=db.collection("posts").document(post.id).collection("likes").document(uid).get().await().exists();likes=db.collection("posts").document(post.id).collection("likes").get().await().size()}catch(_:Exception){}}
    Column(Modifier.fillMaxWidth().padding(horizontal=12.dp).background(Color.White,RoundedCornerShape(16.dp)).padding(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).background(Blue,CircleShape),contentAlignment=Alignment.Center){Text(post.authorName.take(1).uppercase(),color=Color.White,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(9.dp));Column{Text(post.authorName,fontWeight=FontWeight.Bold,color=Navy);Text("@${post.authorUsername}",style=MaterialTheme.typography.labelSmall,color=Color.Gray)}}
        if(post.caption.isNotBlank()){Spacer(Modifier.height(10.dp));Text(post.caption,color=Color(0xFF263445))}
        if(post.mediaUrl.isNotBlank()){Spacer(Modifier.height(8.dp));if(post.mediaType=="video") VideoPlayer(post.mediaUrl) else AsyncImage(model=post.mediaUrl,contentDescription="পোস্টের ছবি",modifier=Modifier.fillMaxWidth().heightIn(max=360.dp))}
        Row(verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={val ref=db.collection("posts").document(post.id).collection("likes").document(uid);if(liked){ref.delete();liked=false;likes=(likes-1).coerceAtLeast(0)}else{ref.set(mapOf("userId" to uid,"createdAt" to System.currentTimeMillis()));liked=true;likes++}}){Text(if(liked)"♥ পছন্দ হয়েছে" else "♡ লাইক  •  $likes",color=if(liked)Blue else Navy)}
            TextButton(onClick={showComments=true}){Text("মন্তব্য")}
        }
    }
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
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),label={Text("নাম অথবা Username")},trailingIcon={TextButton(onClick={scope.launch{error="";try{val q=query.trim();if(q.isBlank()){results=emptyList();return@launch};val a=db.collection("publicProfiles").orderBy("name").startAt(q).endAt(q+"").limit(20).get().await();val b=db.collection("publicProfiles").whereEqualTo("usernameLower",q.lowercase()).limit(20).get().await();results=(a.documents+b.documents).mapNotNull{it.toObject(Person::class.java)}.filter{it.uid!=uid}.distinctBy{it.uid}}catch(e:Exception){error=e.localizedMessage?: "খোঁজা যায়নি"}}}){Text("খুঁজুন")}})
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
    var count by remember{mutableIntStateOf(0)}
    var busy by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    val scope= rememberCoroutineScope()
    LaunchedEffect(uid){try{
        val doc=db.collection("publicProfiles").document(uid).get().await()
        name=doc.getString("name")?: ""
        username=doc.getString("username")?: ""
        phone=db.collection("users").document(uid).get().await().getString("phone")?: ""
        count=db.collection("posts").whereEqualTo("authorId",uid).get().await().size()
    }catch(e:Exception){status=e.localizedMessage?: "প্রোফাইল লোড হয়নি"}}
    Column(Modifier.fillMaxSize().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).background(Blue,CircleShape),contentAlignment=Alignment.Center){Text(name.take(1).uppercase().ifBlank{"F"},color=Color.White,style=MaterialTheme.typography.headlineLarge)}
        Spacer(Modifier.height(12.dp))
        Text(name.ifBlank{"আপনার নাম"},style=MaterialTheme.typography.headlineSmall,color=Navy,fontWeight=FontWeight.Bold)
        Text("@$username",color=Color.Gray)
        Spacer(Modifier.height(12.dp))
        Text("পোস্ট: $count",fontWeight=FontWeight.Bold)
        HorizontalDivider(Modifier.padding(vertical=14.dp))
        Text("প্রোফাইল সম্পাদনা",style=MaterialTheme.typography.titleMedium,color=Navy,fontWeight=FontWeight.Bold)
        OutlinedTextField(name,{name=it},label={Text("নাম")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(username,{username=it.lowercase().replace(" ","")},label={Text("Username")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(phone,{phone=it},label={Text("ফোন নম্বর")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        if(status.isNotBlank()) Text(status,color=if(status.startsWith("সংরক্ষিত"))Blue else MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(10.dp))
        Button(onClick={scope.launch{
            busy=true;status=""
            try{
                require(name.trim().isNotEmpty()){"নাম লিখুন"}
                require(username.trim().length>=3){"Username কমপক্ষে ৩ অক্ষরের হতে হবে"}
                require(phone.trim().isNotEmpty()){"ফোন নম্বর লিখুন"}
                db.collection("publicProfiles").document(uid).set(
                    mapOf("uid" to uid,"name" to name.trim(),"username" to username.trim(),"usernameLower" to username.trim().lowercase()),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
                db.collection("users").document(uid).set(
                    mapOf("phone" to phone.trim(),"phoneDigits" to phone.filter{it.isDigit()}),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
                status="সংরক্ষিত হয়েছে"
            }catch(e:Exception){status=e.localizedMessage?: "সংরক্ষণ করা যায়নি"}
            busy=false
        }},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(busy)"সংরক্ষণ হচ্ছে..." else "প্রোফাইল সংরক্ষণ করুন")}
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
                createPeer(context, ref.id, true)
                peer?.createOffer(object : SdpObserver {
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
                createPeer(context, callId, false)
                val offerMap = doc.get("offer") as? Map<*, *> ?: return@addOnSuccessListener
                val offer = SessionDescription(SessionDescription.Type.OFFER, offerMap["sdp"] as? String ?: return@addOnSuccessListener)
                peer?.setRemoteDescription(object : SdpObserver {
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
            }.addOnFailureListener { callStatus = it.localizedMessage ?: "কল গ্রহণ করা যায়নি" }
        } catch (e: Exception) { callStatus = e.localizedMessage ?: "কল গ্রহণ করা যায়নি" }
    }

    private fun createPeer(context: Context, callId: String, caller: Boolean) {
        val rtcConfig = RTCConfiguration(listOf(IceServer.builder("stun:stun.l.google.com:19302").createIceServer()))
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
        title = { Text("F Messenger কল") },
        text = { Column { Text(CallCoordinator.remoteName); Text(CallCoordinator.callStatus) } },
        confirmButton = { TextButton(onClick = { CallCoordinator.endCall() }) { Text("কল কেটে দিন") } }
    )
}

@Composable
fun ChatScreen(person:Person,onBack:()->Unit) {
    val uid=auth.currentUser?.uid?:return;val chatId=listOf(uid,person.uid).sorted().joinToString("_");var messages by remember{mutableStateOf(listOf<ChatMessage>())};var draft by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};val scope= rememberCoroutineScope();val context= LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) CallCoordinator.placeVoiceCall(context, person) else error = "কল করতে মাইক্রোফোনের অনুমতি দিন"
    }
    DisposableEffect(chatId){val l:ListenerRegistration=db.collection("chats").document(chatId).collection("messages").orderBy("createdAt").addSnapshotListener{snap,e->if(e!=null)error=e.localizedMessage?: "মেসেজ লোড হয়নি" else messages=snap?.documents?.mapNotNull{d->d.toObject(ChatMessage::class.java)?.copy(id=d.id)}?:emptyList()};onDispose{l.remove()}}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal=8.dp, vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=onBack){Text("‹",color=Color.White,style=MaterialTheme.typography.headlineMedium)}
            Column(Modifier.weight(1f)){Text(person.name,color=Color.White,fontWeight=FontWeight.Bold);Text("@${person.username}",color=Color(0xFFD8E8FF))}
            TextButton(onClick={
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    CallCoordinator.placeVoiceCall(context, person)
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text("📞 কল", color=Color.White, fontWeight=FontWeight.Bold) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp)){items(messages){m->Row(Modifier.fillMaxWidth(),horizontalArrangement=if(m.senderId==uid)Arrangement.End else Arrangement.Start){Surface(color=if(m.senderId==uid)Blue else Color.White,shape=RoundedCornerShape(15.dp)){Text(m.text,Modifier.padding(12.dp),color=if(m.senderId==uid)Color.White else Navy)}};Spacer(Modifier.height(7.dp))}}
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(draft,{draft=it},modifier=Modifier.weight(1f),placeholder={Text("মেসেজ লিখুন...")});TextButton(onClick={val msg=draft.trim();if(msg.isNotBlank())scope.launch{try{val ref=db.collection("chats").document(chatId);ref.set(mapOf("members" to listOf(uid,person.uid),"lastMessage" to msg,"updatedAt" to System.currentTimeMillis())).await();ref.collection("messages").add(mapOf("senderId" to uid,"text" to msg,"createdAt" to System.currentTimeMillis())).await();draft=""}catch(e:Exception){error=e.localizedMessage?: "পাঠানো যায়নি"}}}){Text("পাঠান")}}
    }
}
