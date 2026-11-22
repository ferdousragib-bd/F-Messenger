# F Messenger — Android প্রজেক্ট

প্যাকেজ: `com.fmessenger.chat`  
Firebase project: `f-messenger-9f14a`

## এই প্রজেক্টে যা আছে
- Email/password দিয়ে নিবন্ধন ও লগইন
- নাম, Username এবং ফোন নম্বর দিয়ে ব্যবহারকারী খোঁজা
- Firestore-এ ব্যক্তিগত 1-to-1 টেক্সট চ্যাট
- রিয়েল-টাইম মেসেজ আপডেট
- বাংলা UI, নীল-সাদা থিম

## Android Studio-তে চালু করা
1. ZIP ফাইল Extract করুন।
2. Android Studio থেকে `FMessenger` ফোল্ডার Open করুন।
3. Gradle sync সম্পন্ন হতে দিন।
4. Firebase Console-এ `Authentication > Sign-in method` থেকে Email/Password Enable করুন।
5. Firestore Database তৈরি করুন এবং `firestore.rules`-এর নিয়ম Publish করুন।
6. Storage প্রয়োজন হলে Enable করে `storage.rules` Publish করুন।
7. Android Studio-তে Run চাপুন। APK বানাতে `Build > Build APK(s)` ব্যবহার করুন।

## Firebase-এ প্রয়োজনীয় সেটিংস
- Authentication: Email/Password চালু করুন।
- Firestore: Database তৈরি করুন।
- Rules: এই প্রজেক্টের `firestore.rules` Publish করুন।
- Storage: ব্যবহার করলে `storage.rules` Publish করুন।

## গুরুত্বপূর্ণ সীমাবদ্ধতা ও পরবর্তী কাজ
এটি একটি প্রাথমিক কার্যকর Android source project; এখানে তৈরি করা APK নয়।
- ফোন নম্বর এখন প্রোফাইলে সংরক্ষিত ও সার্চযোগ্য; SMS OTP login এখনো যোগ করা হয়নি।
- ছবি/ফাইল আপলোড UI, push notification, online status, read receipts, block/report এবং admin/moderation এখনো যোগ করা হয়নি।
- Username uniqueness-এর জন্য production-এ transaction/Cloud Function দিয়ে reservation যোগ করা উচিত।
- বর্তমান Firestore user-read rule signed-in ব্যবহারকারীদের প্রোফাইল পড়তে দেয়; প্রকাশের আগে প্রয়োজন অনুযায়ী phone number visibility ও privacy policy শক্ত করুন।
- Storage rule-এ chat membership যাচাইয়ের জন্য metadata-based design যোগ করে production-এ প্রকাশ করুন।
- Firestore rules publish করার আগে Firebase Emulator-এ পরীক্ষা করুন।

## নিরাপত্তা
`google-services.json`-এর API key Firebase client configuration; এটিকে server password ভাববেন না। Firebase Security Rules, Authentication এবং API restrictions সঠিকভাবে সেট করুন। কোনো service-account private key অ্যাপে রাখবেন না।
