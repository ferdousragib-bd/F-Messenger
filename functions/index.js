const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const crypto = require("node:crypto");
const admin = require("firebase-admin");

admin.initializeApp();

const turnSecret = defineSecret("TURN_SHARED_SECRET");
const turnUrls = defineSecret("TURN_URLS");

exports.getTurnCredentials = onCall(
  { secrets: [turnSecret, turnUrls], enforceAppCheck: true },
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

    // Coturn TURN REST API: time-limited credentials (default lifetime: 1 hour).
    const expiresAt = Math.floor(Date.now() / 1000) + 3600;
    const username = `${expiresAt}:${request.auth.uid}`;
    const credential = crypto.createHmac("sha1", secret).update(username).digest("base64");

    return { urls, username, credential, expiresAt };
  }
);
