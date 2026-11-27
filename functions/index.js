const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const crypto = require("node:crypto");
const admin = require("firebase-admin");

admin.initializeApp();

const turnSecret = defineSecret("TURN_SHARED_SECRET");
const turnUrls = defineSecret("TURN_URLS");

exports.getTurnCredentials = onCall(
  { secrets: [turnSecret, turnUrls], enforceAppCheck: false },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in before requesting call credentials.");
    }

    const secret = turnSecret.value();
    const urls = turnUrls.value().split(",").map((v) => v.trim()).filter(Boolean);
    if (!secret || secret.length < 32 || urls.length === 0 ||
        urls.some((url) => !/^turns?:/i.test(url))) {
      throw new HttpsError("failed-precondition", "TURN server is not configured.");
    }

    // Basic per-user abuse guard: at most one credential request per 8 seconds.
    const rateRef = admin.firestore().collection("_turnRateLimits").doc(request.auth.uid);
    await admin.firestore().runTransaction(async (tx) => {
      const snap = await tx.get(rateRef);
      const last = snap.exists ? (snap.get("lastRequestAt") || 0) : 0;
      if (Date.now() - last < 8000) {
        throw new HttpsError("resource-exhausted", "Please wait before requesting TURN credentials again.");
      }
      tx.set(rateRef, { lastRequestAt: Date.now() });
    });

    // Coturn TURN REST API: time-limited credentials (default lifetime: 1 hour).
    const expiresAt = Math.floor(Date.now() / 1000) + 3600;
    const username = `${expiresAt}:${request.auth.uid}`;
    const credential = crypto.createHmac("sha1", secret).update(username).digest("base64");

    return { urls, username, credential, expiresAt };
  }
);


// Push notifications for new direct messages and incoming calls.
// The Android client stores its current FCM registration token in users/{uid}.fcmTokens.
async function notifyUser(uid, title, body, data = {}) {
  const user = await admin.firestore().collection("users").doc(uid).get();
  const tokens = user.get("fcmTokens") || [];
  if (!Array.isArray(tokens) || tokens.length === 0) return;
  const response = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
    data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
    android: { priority: "high", notification: { channelId: "pasha_messages", sound: "default" } }
  });
  const invalid = [];
  response.responses.forEach((r, i) => {
    if (!r.success && ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(r.error?.code)) invalid.push(tokens[i]);
  });
  if (invalid.length) await admin.firestore().collection("users").doc(uid).update({
    fcmTokens: admin.firestore.FieldValue.arrayRemove(...invalid)
  });
}

exports.notifyNewMessage = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
  const msg = event.data?.data();
  if (!msg?.senderId) return;
  const chat = await admin.firestore().collection("chats").doc(event.params.chatId).get();
  const members = chat.get("members") || [];
  const recipient = members.find((id) => id !== msg.senderId);
  if (!recipient) return;
  await notifyUser(recipient, "নতুন মেসেজ", msg.encrypted ? "আপনার একটি নতুন নিরাপদ বার্তা এসেছে" : (msg.text || "আপনার একটি নতুন বার্তা এসেছে").slice(0, 120), { type: "message", chatId: event.params.chatId });
});

exports.notifyIncomingCall = onDocumentCreated("calls/{callId}", async (event) => {
  const call = event.data?.data();
  if (!call?.calleeId || !call?.callerId) return;
  await notifyUser(call.calleeId, call.type === "video" ? "ইনকামিং ভিডিও কল" : "ইনকামিং অডিও কল",
    (call.callerName || "একজন ব্যবহারকারী") + " আপনাকে কল করছেন",
    { type: "call", callId: event.params.callId, callType: call.type || "audio" });
});

// Admin claims are only assignable by an existing administrator.
// Bootstrap the first administrator with the documented one-time Admin SDK script.
exports.setUserAdmin = onCall(async (request) => {
  if (!request.auth || request.auth.token.admin !== true) {
    throw new HttpsError("permission-denied", "শুধু প্রশাসক এই কাজ করতে পারবেন।");
  }
  const uid = request.data?.uid;
  const enabled = request.data?.enabled === true;
  if (typeof uid !== "string" || !uid || uid === request.auth.uid) {
    throw new HttpsError("invalid-argument", "সঠিক ব্যবহারকারী UID দিন।");
  }
  const user = await admin.auth().getUser(uid);
  await admin.auth().setCustomUserClaims(uid, { ...(user.customClaims || {}), admin: enabled });
  return { success: true, uid, admin: enabled };
});
