// Run once from a trusted machine after Firebase Admin SDK credentials are configured:
// node scripts/set-first-admin.js FIREBASE_AUTH_UID
const admin = require("firebase-admin");
const uid = process.argv[2];
if (!uid) throw new Error("Usage: node scripts/set-first-admin.js FIREBASE_AUTH_UID");
admin.initializeApp();
(async () => {
  const user = await admin.auth().getUser(uid);
  await admin.auth().setCustomUserClaims(uid, { ...(user.customClaims || {}), admin: true });
  console.log("Admin claim enabled for", uid, "- sign out and sign in again on the app.");
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
