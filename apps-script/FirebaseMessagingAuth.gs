/**
 * OAuth infrastructure for Firebase Cloud Messaging HTTP v1.
 *
 * Store the Firebase service-account JSON in the Apps Script property
 * FIREBASE_SERVICE_ACCOUNT. Keeping the credential in Script Properties avoids
 * committing the private key to the repository.
 */
var FCM_V1_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
var GOOGLE_OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
var FIREBASE_SERVICE_ACCOUNT_PROPERTY = "FIREBASE_SERVICE_ACCOUNT";
var FCM_ACCESS_TOKEN_CACHE_KEY = "fcm_http_v1_access_token";

/**
 * Returns an OAuth access token authorized to call FCM HTTP v1.
 *
 * The token is cached slightly less than its real lifetime so callers never use
 * a token that is about to expire.
 *
 * @return {string} Google OAuth access token.
 */
function getFirebaseMessagingAccessToken() {
  var cache = CacheService.getScriptCache();
  var cachedToken = cache.get(FCM_ACCESS_TOKEN_CACHE_KEY);

  if (cachedToken) {
    return cachedToken;
  }

  var serviceAccount = getFirebaseServiceAccount_();
  var issuedAt = Math.floor(Date.now() / 1000);
  var jwtHeader = {
    alg: "RS256",
    typ: "JWT"
  };
  var jwtClaims = {
    iss: serviceAccount.client_email,
    scope: FCM_V1_SCOPE,
    aud: GOOGLE_OAUTH_TOKEN_URL,
    iat: issuedAt,
    exp: issuedAt + 3600
  };
  var unsignedJwt = base64UrlEncode_(JSON.stringify(jwtHeader)) +
    "." + base64UrlEncode_(JSON.stringify(jwtClaims));
  var signature = Utilities.computeRsaSha256Signature(
    unsignedJwt,
    serviceAccount.private_key
  );
  var assertion = unsignedJwt + "." +
    Utilities.base64EncodeWebSafe(signature).replace(/=+$/, "");

  var response = UrlFetchApp.fetch(GOOGLE_OAUTH_TOKEN_URL, {
    method: "post",
    contentType: "application/x-www-form-urlencoded",
    payload: {
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: assertion
    },
    muteHttpExceptions: true
  });
  var statusCode = response.getResponseCode();
  var responseBody = response.getContentText();

  if (statusCode < 200 || statusCode >= 300) {
    throw new Error(
      "No se pudo obtener el Access Token de Firebase (HTTP " +
      statusCode + "): " + responseBody
    );
  }

  var tokenData = JSON.parse(responseBody);

  if (!tokenData.access_token) {
    throw new Error("La respuesta OAuth no contiene access_token");
  }

  var tokenLifetime = Number(tokenData.expires_in) || 3600;
  var cacheLifetime = Math.max(1, Math.min(tokenLifetime - 60, 21600));
  cache.put(FCM_ACCESS_TOKEN_CACHE_KEY, tokenData.access_token, cacheLifetime);

  return tokenData.access_token;
}

/**
 * Reads and validates the minimum service-account fields needed for OAuth.
 *
 * @return {{client_email: string, private_key: string, project_id: string}}
 * @private
 */
function getFirebaseServiceAccount_() {
  var rawCredential = PropertiesService
    .getScriptProperties()
    .getProperty(FIREBASE_SERVICE_ACCOUNT_PROPERTY);

  if (!rawCredential) {
    throw new Error(
      "Falta la propiedad de script " + FIREBASE_SERVICE_ACCOUNT_PROPERTY
    );
  }

  var serviceAccount;

  try {
    serviceAccount = JSON.parse(rawCredential);
  } catch (error) {
    throw new Error(
      "La propiedad " + FIREBASE_SERVICE_ACCOUNT_PROPERTY +
      " no contiene un JSON válido"
    );
  }

  if (!serviceAccount.client_email ||
      !serviceAccount.private_key ||
      !serviceAccount.project_id) {
    throw new Error(
      "La cuenta de servicio debe incluir client_email, private_key y project_id"
    );
  }

  return serviceAccount;
}

/**
 * Encodes text as unpadded Base64 URL-safe data for a JWT segment.
 *
 * @param {string} value Text to encode.
 * @return {string} Encoded value.
 * @private
 */
function base64UrlEncode_(value) {
  return Utilities
    .base64EncodeWebSafe(value, Utilities.Charset.UTF_8)
    .replace(/=+$/, "");
}

/**
 * Returns the HTTP v1 endpoint associated with the configured Firebase project.
 * No request is sent by this helper.
 *
 * @return {string} FCM HTTP v1 messages endpoint.
 */
function getFirebaseMessagingEndpoint_() {
  var projectId = getFirebaseServiceAccount_().project_id;

  return "https://fcm.googleapis.com/v1/projects/" +
    encodeURIComponent(projectId) + "/messages:send";
}
