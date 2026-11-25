# F Messenger — Android প্রজেক্ট

প্যাকেজ: `com.fmessenger.chat`  
Firebase project: `f-messenger-9f14a`

## বর্তমান Android অ্যাপে যা কাজ করে
- Email/password দিয়ে নিবন্ধন ও লগইন
- নাম, Username এবং ফোন নম্বর দিয়ে ব্যবহারকারী খোঁজা
- Firestore-এ ব্যক্তিগত 1-to-1 টেক্সট চ্যাট
- রিয়েল-টাইম মেসেজ আপডেট
- বাংলা UI, নীল-সাদা থিম

## সোশ্যাল প্ল্যাটফর্মের Firebase ভিত্তি
এই রিপোজিটরিতে সোশ্যাল ফিচারের জন্য Firestore ও Storage security rules-এর প্রাথমিক কাঠামো যোগ করা হয়েছে:
- `posts/{postId}`: public/followers/private visibility, text/image/video media type
- `posts/{postId}/likes/{userId}` এবং `comments/{commentId}`
- `follows/{targetUid}/followers/{followerUid}` ও `users/{uid}/following/{targetUid}`
- `notifications/{notificationId}`, `reports/{reportId}`, `blocks/{uid}/blocked/{blockedUid}`
- Storage: `post-media`, `profile-media`, `chat-media`

**গুরুত্বপূর্ণ:** Rules যোগ করা মানে Android UI বা ফিচারগুলো ইতিমধ্যে সম্পূর্ণ তৈরি হয়েছে—এমন নয়। বর্তমান APK-তে এখনো মূলত login, user search ও text chat আছে। Feed UI, media picker/upload, reels/player, likes/comments UI, follow UI, push notification, audio/video calling এবং admin dashboard-এর Android implementation ও পরীক্ষা বাকি।

## Firebase সেটআপ
1. Firebase Console-এ Authentication থেকে Email/Password enable করুন।
2. Firestore Database তৈরি করুন।
3. এই রিপোজিটরির `firestore.rules` Firebase Console → Firestore Database → Rules-এ publish করুন।
4. Firebase Storage enable করে `storage.rules` Storage → Rules-এ publish করুন।
5. Rules publish করার আগে Firebase Emulator Suite-এ authenticated/unauthenticated access পরীক্ষা করুন।
6. Admin moderation চালু করতে trusted server থেকে Firebase custom claim `admin: true` দিতে হবে। কখনো ক্লায়েন্ট অ্যাপ থেকে admin claim দেবেন না।

## Android build
1. Project ZIP extract করে Android Studio-তে `FMessenger` ফোল্ডার open করুন।
2. Gradle sync সম্পন্ন করুন।
3. Firebase project-এর সঠিক `google-services.json` app module-এ রাখুন।
4. Build → Build APK(s) চালান।

## নিরাপত্তা ও প্রকাশের আগে করণীয়
- বর্তমান user document-এ ফোন/ইমেইল ফিল্ড আছে; production-এ public profile ও private contact data আলাদা collection-এ রাখতে হবে।
- Username uniqueness server-side transaction/Cloud Function দিয়ে enforce করতে হবে।
- Public launch-এর আগে block/report enforcement, moderation queue, rate limits, spam controls, privacy policy, account deletion এবং abuse-response process বাস্তবায়ন করতে হবে।
- Storage media type ও size validation ছাড়াও server-side content moderation এবং upload cleanup প্রয়োজন।
- Voice/video calling-এর জন্য signaling backend ও STUN/TURN infrastructure প্রয়োজন; শুধু Firestore rules যথেষ্ট নয়।
- External news/video feed কেবল অনুমোদিত public RSS/API/embed ব্যবহার করে দেখাতে হবে; অন্য সাইটের কনটেন্ট অনুমতি ছাড়া কপি/রিপাবলিশ করা যাবে না।

`google-services.json`-এর API key Firebase client configuration; এটিকে server password ভাববেন না। কোনো service-account private key কখনো Android app বা public repository-তে রাখবেন না।
