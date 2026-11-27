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


## Free voice/video calling mode (no VPS)

The Android WebRTC client now tries Google's STUN servers and Metered Open Relay's public TURN relay as a fallback. Open Relay advertises a free allowance of 20 GB of relayed traffic per month and supports common UDP/TCP/TLS ports. See the provider's current terms and setup at https://www.metered.ca/tools/openrelay/ . The app uses the provider's published shared demo credentials, so this is a best-effort free fallback, not a private production credential or a guarantee of every network. The free relay quota may be exhausted or the public credentials/service may change.

The app now has separate voice-call and video-call buttons. Video calls request microphone and camera permission, send a WebRTC camera video track, and render the remote video inside the call screen. Both users need a recent APK containing this update and must grant the requested permissions.

- Firebase Spark can still be used for Authentication, Firestore and Storage within their free quotas; TURN media traffic is provided separately by Open Relay.
- No Firebase Cloud Functions deployment or TURN VPS is required for this free fallback.
- Do not put private TURN shared secrets in the Android APK. For a public launch or predictable service, use private expiring TURN credentials and a managed/owned TURN service with a suitable quota/SLA.
- Firestore and Storage rules still need to be published to the Firebase project. Copy `firestore.rules` into Firestore → Rules and `storage.rules` into Storage → Rules, then publish. Create the composite indexes from `firestore.indexes.json` when Firestore reports they are required.
- Enable Email/Password in Firebase Authentication → Sign-in method.

Important: TURN improves connectivity through restrictive NAT/firewalls but cannot promise 100% availability. Video relay consumes substantially more data than voice; monitor the provider's monthly quota.
