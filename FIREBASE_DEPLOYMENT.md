# Firebase deployment for F Messenger

The repository includes Firebase configuration, Firestore rules/indexes, and Storage rules.

## Enable phone-number sign-in (required)

1. Open Firebase Console: https://console.firebase.google.com/
2. Select project `f-messenger-9f14a`.
3. Open **Authentication → Sign-in method** (or **Sign-in providers**).
4. Enable **Phone** and save.
5. In **Authentication → Settings → SMS region policy**, allow Bangladesh (+880). Keep other regions disabled unless the service is intended for them.
6. In **Project settings → General → Your apps → Android app** for package `com.fmessenger.chat`, add the app's SHA-1 and SHA-256 signing certificate fingerprints. The debug fingerprints can be obtained with `./gradlew signingReport`; add the release certificate fingerprints separately before publishing.
7. Confirm the Android app has the matching `google-services.json`.

Firebase Phone Authentication sends an SMS verification code. SMS delivery can incur charges and is subject to Firebase quotas and abuse protection. Use Firebase Authentication test phone numbers only for development; never share real OTP codes.

## One-time GitHub setup for Firebase rules deployment

In GitHub repository **Settings → Secrets and variables → Actions → New repository secret**, add:

1. `FIREBASE_PROJECT_ID` — the exact project ID from Firebase Console → Project settings → General.
2. `FIREBASE_SERVICE_ACCOUNT_JSON` — the complete JSON key for a Google Cloud service account in this Firebase project.

The service account needs permission to deploy Firebase Security Rules and manage Firestore indexes. Protect this key: never commit it to the repository or paste it into chat.

## Deploy rules and indexes

Open **Actions → Deploy F Messenger Firebase Rules → Run workflow**. Wait for the run to finish successfully. This deploys Firestore rules, Firestore indexes, and Storage rules only; it does not enable Authentication providers.

## Before public launch

- Authentication → Sign-in method → **Phone** is enabled.
- Bangladesh (+880) is allowed in the SMS region policy.
- Firestore Database exists in the intended project.
- Storage is enabled.
- Android package name is `com.fmessenger.chat`.
- The matching `google-services.json` is included.
- Test OTP login on a real Android device using a number you control.

Existing email/password accounts are not automatically converted to phone accounts. Users must sign in using the phone number that has been verified and linked to their Firebase account, or complete a separate account migration.

A successful Android APK build does not prove that Firebase SMS delivery works. End-to-end testing on a real phone is still required.
