# Pasha Firebase deployment notes

## Push notifications
- Deploy Functions with `firebase deploy --only functions`.
- The Android client must request Android 13+ notification permission and save its FCM token in `users/{uid}.fcmTokens`.
- Functions send notifications for new chat messages and incoming calls.

## First administrator
The first admin must be bootstrapped by the project owner using a trusted Admin SDK environment:
`node scripts/set-first-admin.js FIREBASE_AUTH_UID`
After setting the claim, the administrator must sign out and sign in again. Never expose a service-account key in the Android app or public repository.

## Important
Deploy Firestore and Storage rules with `firebase deploy --only firestore:rules,storage`.
Do not call this production-ready until Firebase Functions and rules are deployed and two real devices have passed call, message, and notification tests.
