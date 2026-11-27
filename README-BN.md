# F Messenger — Android প্রজেক্ট

**Package:** `com.fmessenger.chat`  
**Firebase project ID (repository documentation):** `f-messenger-9f14a`

## বর্তমান Android সোর্সে অন্তর্ভুক্ত
- Email/password নিবন্ধন, লগইন ও password reset
- নাম, username ও ফোন নম্বর দিয়ে ব্যবহারকারী খোঁজা
- Public text/image/video post এবং Firebase Storage upload
- Public feed, video reels/player
- Like ও comment
- Follow/unfollow
- 1-to-1 text chat এবং real-time message updates
- Profile-এর name/username/phone edit

এগুলো সোর্স কোডে উপস্থিত। Firebase প্রকল্পে সঠিক কনফিগারেশন, Security Rules প্রকাশ এবং বাস্তব ডিভাইসে end-to-end পরীক্ষা না হওয়া পর্যন্ত এগুলোকে production-ready বা সম্পূর্ণ কার্যকর বলা যাবে না।

## Firebase deployment files
- `firebase.json`
- `firestore.rules`
- `firestore.indexes.json`
- `storage.rules`
- `.github/workflows/deploy-firebase.yml`

### Firebase deployment-এর বাধা
GitHub Actions workflow-টি চালাতে repository secrets প্রয়োজন:
- `FIREBASE_PROJECT_ID`: Firebase Console → Project settings → General থেকে সঠিক Project ID
- `FIREBASE_SERVICE_ACCOUNT_JSON`: ঐ Firebase project-এর service account JSON key

এই secrets ছাড়া GitHub Actions থেকে Firebase Rules/Indexes/Storage Rules প্রকাশ করা সম্ভব নয়। Service-account key কখনো public repository বা chat-এ দেওয়া যাবে না। এই workflow কেবল rules/indexes deploy করে; Firebase Auth provider enable, Firestore/Storage resource তৈরি, Android app registration বা app runtime test করে না।

## জরুরি নিরাপত্তা সতর্কতা — public launch-এর আগে ঠিক করতে হবে
বর্তমান `firestore.rules`-এ `/users/{userId}`-এর read rule যেকোনো authenticated user-কে সম্পূর্ণ user document পড়তে দেয়। Android registration code-এ ঐ document-এ ফোন নম্বর ও email রাখা হয়। ফলে ব্যবহারকারীর ব্যক্তিগত contact data অন্য logged-in ব্যবহারকারী পড়তে পারে।

সঠিক সমাধান হলো public profile data (নাম, username, profile photo ইত্যাদি) আলাদা `publicProfiles/{uid}` collection-এ রাখা এবং private contact data `users/{uid}`-এ রাখা; তারপর Android discover/profile/chat code-কে publicProfiles-এ migrate করে users collection-এর read permission কেবল owner/admin-এ সীমাবদ্ধ করা। এই migration সম্পন্ন না হওয়া পর্যন্ত production launch করা উচিত নয়। শুধু rules-এ read বন্ধ করলে বর্তমান Discover ও user lookup ভেঙে যাবে।

অন্যান্য release blocker:
- Username uniqueness server-side transaction/Cloud Function দিয়ে enforce করা
- Block/report enforcement ও admin moderation UI/backend
- Push notifications
- Voice messages এবং audio/video calling (signaling + STUN/TURN infrastructure)
- Account deletion, privacy policy, abuse reporting এবং rate limits
- Storage upload/download access, MIME type ও size checks বাস্তব Firebase Emulator/production project-এ পরীক্ষা
- Firestore Rules/Storage Rules unit tests এবং দুইটি পৃথক test account দিয়ে end-to-end পরীক্ষা

## Android build
GitHub Actions-এর **Build F Messenger APK** workflow debug APK build করে artifact হিসেবে আপলোড করে। Build success কেবল compilation সফল হওয়া বোঝায়; Firebase backend runtime-এ কাজ করছে তার প্রমাণ নয়।

APK workflow: https://github.com/ferdousragib-bd/F-Messenger/actions

## নিরাপত্তা
- `google-services.json` client configuration; এটি service-account private key নয়।
- Service-account JSON কখনো Android app, GitHub repository file, issue, screenshot বা chat-এ প্রকাশ করবেন না।
- Admin custom claims কেবল trusted server environment থেকে দিতে হবে; client app থেকে নয়।
