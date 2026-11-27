# F Messenger TURN server (Coturn)

এই ফোল্ডারটি একটি self-hosted Coturn relay server চালানোর template। এটি GitHub Actions বা Firebase নিজে থেকে কোনো সার্ভার চালু করে না। চালাতে একটি public IPv4-সহ Linux VPS/VM, firewall access এবং Docker লাগবে।

## 1) VPS প্রস্তুত করুন
Ubuntu 22.04/24.04 VPS-এ Docker Engine + Docker Compose plugin ইনস্টল করুন। DNS-এ `turn.example.com`-কে VPS-এর public IPv4-তে point করুন।

## 2) Secret তৈরি ও চালু
```bash
cd turn
cp .env.example .env
openssl rand -hex 32
```
উৎপন্ন random value-টি `.env`-এর `TURN_SHARED_SECRET`-এ বসান এবং `TURN_REALM`-এ নিজের TURN hostname দিন। তারপর:
```bash
docker compose up -d
docker compose logs -f coturn
```

## 3) Firewall
VPS provider firewall এবং Ubuntu firewall—দুই জায়গাতেই খুলুন:
- UDP 3478 এবং TCP 3478 (TURN)
- UDP 49160–49200 (media relay)
SSH ছাড়া অন্য অপ্রয়োজনীয় port বন্ধ রাখুন। এই template-এ TLS listener চালু করা হয়নি; production-এ DNS hostname ও trusted TLS certificate বসিয়ে TURNS/TCP 443 যোগ করা ভালো।

## 4) Firebase Functions secrets
Firebase project-এর একই shared secret Firebase Functions-এ দিন:
```bash
firebase functions:secrets:set TURN_SHARED_SECRET
```
Prompt-এ VPS-এর `.env`-এ রাখা একই secret দিন। এরপর:
```bash
firebase functions:secrets:set TURN_URLS
```
Value দিন: `turn:turn.example.com:3478?transport=udp,turn:turn.example.com:3478?transport=tcp`

এরপর Firebase Console → App Check-এ Android app-এর জন্য Play Integrity provider configure করুন এবং enforcement চালুর আগে debug/test app-এ App Check token generation ঠিক আছে নিশ্চিত করুন। `getTurnCredentials` callable-এ App Check enforced আছে।

## 5) Deploy callable function
Repository root থেকে:
```bash
firebase deploy --only functions:getTurnCredentials
```
প্রয়োজন হবে Firebase CLI login ও Cloud Functions/Blaze billing-enabled project। TURN relay bandwidth VPS-এর network traffic ব্যবহার করে; খরচ/limit provider-এর উপর নির্ভর করবে।

## 6) Verify
দুটি আলাদা নেটওয়ার্কে (যেমন Wi-Fi ও mobile data) দুই Android device দিয়ে কল পরীক্ষা করুন। WebRTC ICE gathering-এ `relay` candidate এবং সফল audio দুই দিকেই নিশ্চিত না হওয়া পর্যন্ত production-ready বলবেন না।

**সতর্কতা:** `.env`, shared secret, Firebase service-account JSON কখনো GitHub-এ commit করবেন না। TURN credentials অ্যাপে hard-code করা হয়নি; Firebase callable function প্রতি request-এ ১ ঘণ্টার temporary credentials তৈরি করে।
