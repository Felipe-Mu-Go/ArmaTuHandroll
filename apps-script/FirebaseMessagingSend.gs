/**
 * Sends a test FCM notification for the latest order with a registered token.
 */
function sendFirebaseTestNotificationToLatestOrder() {
  var sheet = SpreadsheetApp
    .getActiveSpreadsheet()
    .getActiveSheet();
  var lastRow = sheet.getLastRow();

  if (lastRow < 2) {
    throw new Error("No existen pedidos para enviar una notificación FCM.");
  }

  var rows = sheet
    .getRange(2, 1, lastRow - 1, 9)
    .getValues();
  var orderNumber = "";
  var status = "";
  var fcmToken = "";

  for (var index = rows.length - 1; index >= 0; index--) {
    var currentToken = String(rows[index][8] || "").trim();

    if (currentToken) {
      orderNumber = String(rows[index][0] || "").trim();
      status = String(rows[index][7] || "").trim();
      fcmToken = currentToken;
      break;
    }
  }

  if (!fcmToken) {
    throw new Error("No se encontró ningún pedido con token FCM.");
  }

  var payload = {
    message: {
      token: fcmToken,
      notification: {
        title: "Arma Tu Handroll",
        body: "Notificación de prueba para el pedido " + orderNumber + "."
      },
      data: {
        orderNumber: orderNumber,
        status: status || "pending_review",
        source: "apps_script_test"
      },
      android: {
        priority: "high"
      }
    }
  };
  var accessToken = getFirebaseMessagingAccessToken();
  var endpoint = getFirebaseMessagingEndpoint_();
  var response = UrlFetchApp.fetch(endpoint, {
    method: "post",
    contentType: "application/json",
    headers: {
      Authorization: "Bearer " + accessToken
    },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  });
  var statusCode = response.getResponseCode();
  var responseBody = response.getContentText();

  if (statusCode < 200 || statusCode >= 300) {
    throw new Error(
      "FCM rechazó la notificación (HTTP " +
      statusCode +
      "): " +
      responseBody
    );
  }

  console.log(
    "Notificación FCM de prueba enviada para el pedido " +
    orderNumber
  );
}
