# Firebase deployment for F Messenger

The repository includes `firebase.json`, `firestore.rules`, `firestore.indexes.json`, and `storage.rules`.

## One-time GitHub setup

In the GitHub repository, open **Settings → Secrets and variables → Actions → New repository secret** and add:

1. `FIREBASE_PROJECT_ID` — the exact Firebase project ID shown in Firebase Console → Project settings → General.
2. `FIREBASE_SERVICE_ACCOUNT_JSON` — the complete JSON key for a Google Cloud service account in that Firebase project.

The service account needs permission to deploy Firebase Security Rules and manage Firestore indexes (for example, Firebase Rules Admin and Cloud Datastore Index Admin roles). Protect this key: never commit it to the repository or paste it into chat.

## Deploy

Open **Actions → Deploy F Messenger Firebase Rules → Run workflow**. Wait for the run to finish successfully. This deploys Firestore rules, Firestore indexes, and Storage rules only; it does not deploy Android app code or enable Firebase Authentication providers.

## Before users sign up

In Firebase Console, verify that:
- Authentication → Sign-in method → Email/Password is enabled.
- Firestore Database exists in the intended project.
- Storage is enabled and the Android app is registered with package `com.fmessenger.chat`.
- The Android project contains the matching `google-services.json`.

A successful workflow confirms the rules/index deployment, not end-to-end Android app testing.
