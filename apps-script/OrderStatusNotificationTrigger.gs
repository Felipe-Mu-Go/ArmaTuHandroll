function handleOrderStatusEdit(e) {
  if (!e || !e.range) {
    return;
  }

  var range = e.range;
  if (range.getNumRows() !== 1 || range.getNumColumns() !== 1) {
    return;
  }

  var row = range.getRow();
  var column = range.getColumn();
  if (row < 2 || column !== 8) {
    return;
  }

  var sheet = range.getSheet();
  var rowValues = sheet.getRange(row, 1, 1, 9).getValues()[0];
  var orderNumber = String(rowValues[0] || "").trim();
  var newStatus = String(rowValues[7] || "").trim();
  var fcmToken = String(rowValues[8] || "").trim();

  if (!orderNumber || !newStatus || !fcmToken) {
    return;
  }

  var oldStatus = String(e.oldValue || "").trim();
  if (oldStatus === newStatus || newStatus === "pending_review") {
    return;
  }

  var normalizedStatus = normalizeOrderStatusForNotification_(newStatus);
  if (!normalizedStatus) {
    return;
  }

  var notificationBody = getOrderStatusNotificationBody_(normalizedStatus, orderNumber);
  if (!notificationBody) {
    return;
  }

  var payload = {
    message: {
      token: fcmToken,
      notification: {
        title: "Arma Tu Handroll",
        body: notificationBody
      },
      data: {
        orderNumber: String(orderNumber),
        status: String(normalizedStatus),
        source: "apps_script_status_change"
      },
      android: {
        priority: "high"
      }
    }
  };

  var response = UrlFetchApp.fetch(getFirebaseMessagingEndpoint_(), {
    method: "post",
    contentType: "application/json",
    headers: {
      Authorization: "Bearer " + getFirebaseMessagingAccessToken()
    },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  });

  var responseCode = response.getResponseCode();
  var responseBody = response.getContentText();
  if (responseCode < 200 || responseCode > 299) {
    throw new Error(
      "Error al enviar notificación de estado por FCM. Código HTTP: " +
        responseCode +
        ". Respuesta Firebase: " +
        responseBody
    );
  }

  console.log("Notificación de estado enviada para el pedido " + orderNumber);
}

function normalizeOrderStatusForNotification_(status) {
  var normalizedInput = String(status || "").trim().toLowerCase();
  var statuses = {
    accepted: "accepted",
    "aceptado": "accepted",
    preparing: "preparing",
    "en preparación": "preparing",
    "en preparacion": "preparing",
    ready: "ready",
    "listo para retirar": "ready",
    delivered: "delivered",
    "entregado": "delivered",
    cancelled: "cancelled",
    "cancelado": "cancelled",
    "eliminado": "cancelled"
  };

  return statuses[normalizedInput] || "";
}

function getOrderStatusNotificationBody_(normalizedStatus, orderNumber) {
  var messages = {
    accepted: "Tu pedido " + orderNumber + " fue aceptado.",
    preparing: "Estamos preparando tu pedido " + orderNumber + ".",
    ready: "Tu pedido " + orderNumber + " está listo para retirar.",
    delivered: "Tu pedido " + orderNumber + " fue entregado. ¡Gracias por tu compra!",
    cancelled: "Tu pedido " + orderNumber + " fue cancelado."
  };

  return messages[normalizedStatus] || "";
}

function installOrderStatusTrigger() {
  var triggers = ScriptApp.getProjectTriggers();
  for (var i = 0; i < triggers.length; i++) {
    if (triggers[i].getHandlerFunction() === "handleOrderStatusEdit") {
      console.log("El trigger ya existe.");
      return;
    }
  }

  ScriptApp.newTrigger("handleOrderStatusEdit")
    .forSpreadsheet(SpreadsheetApp.getActive())
    .onEdit()
    .create();
  console.log("Trigger instalado correctamente.");
}
