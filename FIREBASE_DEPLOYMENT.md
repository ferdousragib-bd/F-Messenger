# Firebase deployment for F Messenger

The repository includes Firebase configuration, Firestore rules/indexes, and Storage rules.

## Enable free email/password sign-in (required)

1. Open Firebase Console: https://console.firebase.google.com/
2. Select project `f-messenger-9f14a`.
3. Open **Authentication → Sign-in method** (or **Sign-in providers**).
4. Enable **Email/Password** and save. Email-link sign-in is optional and is not used by this Android screen.
5. Do not enable Phone Authentication if you want to avoid SMS charges. The Android app now uses email/password and does not request SMS OTP.
6. Confirm the Android app has the matching `google-services.json`.

Email/password authentication does not require a Google Cloud Billing account for SMS. Keep the Firebase project on the Spark plan if you want to avoid linking a billing account. Firestore and Storage usage remains subject to their free-plan quotas and limits.

## One-time GitHub setup for Firebase rules deployment

In GitHub repository **Settings → Secrets and variables → Actions → New repository secret**, add:

1. `FIREBASE_PROJECT_ID` — the exact project ID from Firebase Console → Project settings → General.
2. `FIREBASE_SERVICE_ACCOUNT_JSON` — the complete JSON key for a Google Cloud service account in this Firebase project.

The service account needs permission to deploy Firebase Security Rules and manage Firestore indexes. Protect this key: never commit it to the repository or paste it into chat.

## Deploy rules and indexes

Open **Actions → Deploy F Messenger Firebase Rules → Run workflow**. Wait for the run to finish successfully. This deploys Firestore rules, Firestore indexes, and Storage rules only; it does not enable Authentication providers.

## Before public launch

- Authentication → Sign-in method → **Email/Password** is enabled.
- Firestore Database exists in the intended project.
- Storage is enabled and usage stays within the free-plan quota.
- Android package name is `com.fmessenger.chat`.
- The matching `google-services.json` is included.
- Test new account registration and existing-account login on a real Android device.

A successful Android APK build does not prove that Firebase services are configured correctly. End-to-end testing against the intended Firebase project is still required.
