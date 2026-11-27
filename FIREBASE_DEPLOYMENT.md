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

## Deploy the updated profile privacy rules and indexes

The latest app adds profile bio, avatar/cover image URLs, and public/private profile visibility. To enforce private-profile reads and support public-profile discovery:

1. Deploy the updated `firestore.rules` and `firestore.indexes.json` from GitHub Actions → **Deploy F Messenger Firebase Rules** → **Run workflow**.
2. This workflow requires the repository secrets listed above. If those secrets are not configured, open Firebase Console → Firestore Database → Rules and publish the contents of `firestore.rules`; create the composite indexes listed in `firestore.indexes.json` under Firestore → Indexes.
3. Do not treat private profiles as protected until the updated Firestore rules have finished deploying.

## Before public launch

- Authentication → Sign-in method → **Email/Password** is enabled.
- Firestore Database exists in the intended project.
- Storage is enabled and usage stays within the free-plan quota.
- Android package name is `com.fmessenger.chat`.
- The matching `google-services.json` is included.
- Test new account registration and existing-account login on a real Android device.

A successful Android APK build does not prove that Firebase services are configured correctly. End-to-end testing against the intended Firebase project is still required.


## Free no-VPS mode (current recommended setup)

The Android client can make direct WebRTC connection attempts using Google's public STUN servers without a self-hosted TURN VPS. This keeps the app on the Firebase Spark plan for Auth, Firestore, and Storage within their free quotas. It does **not** guarantee calls on every mobile carrier or NAT: restrictive networks may require a TURN relay.

In this no-VPS mode:
- Do not configure `TURN_SHARED_SECRET` or `TURN_URLS` and do not run the TURN deployment workflow.
- Do not expect the `getTurnCredentials` callable or server-triggered Cloud Functions notifications to work unless Functions are separately deployed on an eligible billing plan.
- Firestore and Storage rules still need to be published to the Firebase project. This can be done manually in Firebase Console by copying the repository's `firestore.rules` into Firestore → Rules and `storage.rules` into Storage → Rules, then publishing. Create the composite indexes from `firestore.indexes.json` when Firestore reports they are required.
- Enable Email/Password in Firebase Authentication → Sign-in method.

For reliable calls across restrictive networks, add a paid TURN VPS later and deploy the callable credential function. Never put TURN shared secrets in the Android app.
