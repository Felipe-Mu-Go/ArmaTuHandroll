function doPost(e) {
  try {
    // Webpay vuelve mediante un POST de formulario, no mediante el JSON de la app.
    if (e && e.parameter && (e.parameter.token_ws || e.parameter.TBK_TOKEN)) {
      return handleWebpayReturn_(e.parameter);
    }
    var data = JSON.parse(e.postData.contents);

    if (data.action === "createWebpayTransaction") {
      return createWebpayTransaction_(data);
    }

    if (data.action === "updateOrderStatus") {
      return updateOrderStatus_(data);
    }

    if (data.action === "registerPayment") {
      return registerPayment_(data);
    }

    if (data.action === "rejectOrder") {
      return rejectOrder_(data);
    }
    if (data.action === "reportTransfer") {
      return reportTransfer_(data);
    }
    if (data.action === "confirmTransfer") {
      return confirmTransfer_(data);
    }

    return createOrder_(data);

  } catch (error) {
    return createJsonResponse({
      success: false,
      message: "No fue posible procesar la solicitud"
    });
  }
}

function createOrder_(data) {
  var orderNumber = String(data.pedido_numero || "").trim();
  if (!orderNumber) return createJsonResponse({ success: false, message: "Debe indicar el número del pedido" });
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Hoja 1");
    if (!sheet) return createJsonResponse({ success: false, message: "No se encontró la hoja de pedidos" });
    if (sheet.getLastRow() >= 2) {
      var existing = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1).getValues();
      for (var index = 0; index < existing.length; index++) {
        if (String(existing[index][0]).trim() === orderNumber) {
          return createJsonResponse({ success: true, message: "Pedido ya registrado", orderNumber: orderNumber });
        }
      }
    }
    sheet.appendRow([
      orderNumber, data.fecha_hora || "", data.productos || "", data.cantidad_total || "",
      data.total_pagado || "", data.tiempo_estimado || "", data.nombre_usuario || "",
      "pending_review", data.fcm_token || ""
    ]);
    return createJsonResponse({ success: true, message: "Pedido guardado correctamente", orderNumber: orderNumber });
  } catch (error) {
    return createJsonResponse({ success: false, message: error.toString() });
  } finally {
    if (lock.hasLock()) lock.releaseLock();
  }
}


function doGet(e) {
  try {
    if (e.parameter.action === "openWebpay") {
      return openWebpayForm_(e.parameter.token);
    }
    if (e.parameter.action === "webpayStatus") {
      return getWebpayStatus_(e.parameter.orderNumber);
    }
    if (e.parameter.action === "validateAdminDevice") {
      var installationId = e.parameter.installationId;

      return createJsonResponse({
        success: true,
        authorized: isAdminDeviceAuthorized_(installationId)
      });
    }

    if (e.parameter.action === "listOrders") {
      var ordersSheet = SpreadsheetApp
        .getActiveSpreadsheet()
        .getSheetByName("Hoja 1");

      if (!ordersSheet) {
        return createJsonResponse({
          success: false,
          message: "No se encontró la hoja de pedidos"
        });
      }

      var latestPayments = getLatestPaymentsMap_();
      var ordersLastRow = ordersSheet.getLastRow();
      var orders = [];

      if (ordersLastRow >= 2) {
        var firstOrderRow = Math.max(2, ordersLastRow - 49);
        var orderRows = ordersSheet
          .getRange(firstOrderRow, 1, ordersLastRow - firstOrderRow + 1, 11)
          .getValues();
        var timeZone = SpreadsheetApp.getActiveSpreadsheet().getSpreadsheetTimeZone();

        for (var orderIndex = orderRows.length - 1; orderIndex >= 0; orderIndex--) {
          var orderRow = orderRows[orderIndex];
          var orderNumber = String(orderRow[0] || "").trim();
          if (orderNumber === "") {
            continue;
          }
          var dateTime = orderRow[1] instanceof Date
            ? Utilities.formatDate(orderRow[1], timeZone, "yyyy-MM-dd HH:mm:ss")
            : String(orderRow[1]);


          var payment = latestPayments[orderNumber];

          orders.push({
            orderNumber: orderNumber,
            dateTime: dateTime,
            products: String(orderRow[2]),
            totalQuantity: Number(orderRow[3]) || 0,
            totalPaid: Number(orderRow[4]) || 0,
            estimatedTime: String(orderRow[5]),
            customerName: String(orderRow[6]),
            status: String(orderRow[7]).trim() || "pending_review",
            paymentStatus: payment ? payment.paymentStatus : "pending",
            paymentMethod: payment ? payment.paymentMethod : "",
            paidAmount: payment ? payment.amount : 0,
            rejectionReason: String(orderRow[9] || "").trim(),
            rejectionDetail: String(orderRow[10] || "").trim()
          });
        }
      }

      return createJsonResponse({
        success: true,
        orders: orders
      });
    }

    if (e.parameter.action === "listPayments") {
      return listPayments_();
    }

    var orderNumber = e.parameter.orderNumber;

    if (!orderNumber || orderNumber.trim() === "") {
      return createJsonResponse({
        success: false,
        message: "Debe indicar el número del pedido"
      });
    }

    var sheet = SpreadsheetApp
      .getActiveSpreadsheet()
      .getSheetByName("Hoja 1");

    if (!sheet) {
      return createJsonResponse({
        success: false,
        message: "No se encontró la hoja de pedidos"
      });
    }

    var lastRow = sheet.getLastRow();

    if (lastRow < 2) {
      return createJsonResponse({
        success: false,
        message: "No se encontró el pedido solicitado"
      });
    }

    var rows = sheet
      .getRange(2, 1, lastRow - 1, 11)
      .getValues();

    for (var index = rows.length - 1; index >= 0; index--) {
      var storedOrderNumber = String(rows[index][0]).trim();

      if (storedOrderNumber === orderNumber.trim()) {
        var storedStatus = String(rows[index][7]).trim();

        if (storedStatus === "") {
          storedStatus = "pending_review";
        }

        var response = {
          success: true,
          orderNumber: storedOrderNumber,
          status: storedStatus
        };
        var payment = getLatestPaymentsMap_()[storedOrderNumber];
        response.paymentStatus = payment ? payment.paymentStatus : "pending";
        response.paymentMethod = payment ? payment.paymentMethod : "";
        if (storedStatus === "rejected") {
          response.rejectionReason = String(rows[index][9] || "").trim();
          response.rejectionDetail = String(rows[index][10] || "").trim();
        }
        return createJsonResponse(response);
      }
    }

    return createJsonResponse({
      success: false,
      message: "No se encontró el pedido solicitado"
    });

  } catch (error) {
    return createJsonResponse({
      success: false,
      message: error.toString()
    });
  }
}


function isAdminDeviceAuthorized_(installationId) {
  var adminDevicesSheet = SpreadsheetApp
    .getActiveSpreadsheet()
    .getSheetByName("ADMIN_DEVICES");

  if (!installationId || !adminDevicesSheet || adminDevicesSheet.getLastRow() < 2) {
    return false;
  }

  var adminDeviceRows = adminDevicesSheet
    .getRange(2, 1, adminDevicesSheet.getLastRow() - 1, 3)
    .getValues();

  for (var deviceIndex = 0; deviceIndex < adminDeviceRows.length; deviceIndex++) {
    var storedInstallationId = String(adminDeviceRows[deviceIndex][0]);
    var activeValue = adminDeviceRows[deviceIndex][2];
    var isActive = activeValue === true ||
      String(activeValue).trim().toLowerCase() === "true";

    if (storedInstallationId === installationId && isActive) {
      return true;
    }
  }

  return false;
}


function isAllowedTransition_(currentStatus, newStatus) {
  var normalizedCurrentStatus = currentStatus === "ready"
    ? "ready_for_pickup"
    : currentStatus;
  var allowedTransitions = {
    pending_review: ["accepted"],
    accepted: ["preparing"],
    preparing: ["ready_for_pickup"],
    ready_for_pickup: ["delivered"]
  };

  return allowedTransitions[normalizedCurrentStatus] !== undefined &&
    allowedTransitions[normalizedCurrentStatus].indexOf(newStatus) !== -1;
}


function updateOrderStatus_(data) {
  if (!isAdminDeviceAuthorized_(data.installationId)) {
    return createJsonResponse({
      success: false,
      message: "Dispositivo no autorizado"
    });
  }

  var orderNumber = String(data.orderNumber || "").trim();
  var newStatus = String(data.newStatus || "").trim();
  if (!orderNumber) {
    return createJsonResponse({ success: false, message: "Debe indicar el número del pedido" });
  }
  var administrativeStatuses = [
    "accepted",
    "preparing",
    "ready_for_pickup",
    "delivered"
  ];
  if (administrativeStatuses.indexOf(newStatus) === -1) {
    return createJsonResponse({ success: false, message: "Transición de estado no permitida" });
  }

  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var ordersSheet = SpreadsheetApp
      .getActiveSpreadsheet()
      .getSheetByName("Hoja 1");

    if (!ordersSheet) {
      return createJsonResponse({
        success: false,
        message: "No se encontró la hoja de pedidos"
      });
    }

    var lastRow = ordersSheet.getLastRow();
    if (lastRow < 2) {
      return createJsonResponse({ success: false, message: "No se encontró el pedido solicitado" });
    }

    var orderNumbers = ordersSheet.getRange(2, 1, lastRow - 1, 1).getValues();
    for (var index = orderNumbers.length - 1; index >= 0; index--) {
      if (String(orderNumbers[index][0]).trim() === orderNumber) {
        var sheetRow = index + 2;
        var currentStatus = String(ordersSheet.getRange(sheetRow, 8).getValue()).trim() ||
          "pending_review";
        if (!isAllowedTransition_(currentStatus, newStatus)) {
          return createJsonResponse({ success: false, message: "El pedido cambió de estado" });
        }

        if (newStatus === "accepted") {
          var acceptedPaymentsSheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("PAYMENTS");
          if (!acceptedPaymentsSheet || !hasConfirmedPayment_(acceptedPaymentsSheet, orderNumber)) {
            return createJsonResponse({
              success: false,
              message: "El pedido debe tener el pago confirmado antes de aceptarse"
            });
          }
        }

        if (newStatus === "delivered") {
          var paymentsSheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("PAYMENTS");
          if (!paymentsSheet || !hasConfirmedPayment_(paymentsSheet, orderNumber)) {
            return createJsonResponse({
              success: false,
              message: "El pedido debe estar pagado antes de marcarlo como entregado"
            });
          }
        }

        // Deliberadamente se escribe una única celda: H (estado). I (fcm_token) queda intacta.
        ordersSheet.getRange(sheetRow, 8).setValue(newStatus);
        return createJsonResponse({
          success: true,
          orderNumber: orderNumber,
          status: newStatus
        });
      }
    }

    return createJsonResponse({ success: false, message: "No se encontró el pedido solicitado" });
  } catch (error) {
    return createJsonResponse({ success: false, message: error.toString() });
  } finally {
    if (lock.hasLock()) {
      lock.releaseLock();
    }
  }
}

function rejectOrder_(data) {
  if (!isAdminDeviceAuthorized_(data.installationId)) {
    return createJsonResponse({ success: false, message: "Dispositivo no autorizado" });
  }
  var orderNumber = String(data.orderNumber || "").trim();
  var reason = String(data.reason || "").trim();
  var detail = String(data.detail || "").trim();
  var allowedReasons = [
    "out_of_stock", "store_closed", "high_demand",
    "technical_issue", "invalid_order", "other"
  ];
  if (!orderNumber) {
    return createJsonResponse({ success: false, message: "Debe indicar el número del pedido" });
  }
  if (allowedReasons.indexOf(reason) === -1) {
    return createJsonResponse({ success: false, message: "Motivo de rechazo no permitido" });
  }
  if (reason === "other" && (detail.length < 3 || detail.length > 120)) {
    return createJsonResponse({
      success: false,
      message: "El detalle debe tener entre 3 y 120 caracteres"
    });
  }
  if (reason !== "other") detail = "";

  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    var ordersSheet = spreadsheet.getSheetByName("Hoja 1");
    if (!ordersSheet) {
      return createJsonResponse({ success: false, message: "No se encontró la hoja de pedidos" });
    }
    var lastRow = ordersSheet.getLastRow();
    var orderNumbers = lastRow >= 2
      ? ordersSheet.getRange(2, 1, lastRow - 1, 1).getValues() : [];
    for (var index = orderNumbers.length - 1; index >= 0; index--) {
      if (String(orderNumbers[index][0]).trim() !== orderNumber) continue;
      var sheetRow = index + 2;
      var currentStatus = String(ordersSheet.getRange(sheetRow, 8).getValue()).trim() ||
        "pending_review";
      if (currentStatus !== "pending_review") {
        return createJsonResponse({ success: false, message: "El pedido cambió de estado" });
      }
      var paymentsSheet = spreadsheet.getSheetByName("PAYMENTS");
      if (!paymentsSheet) {
        return createJsonResponse({ success: false, message: "No se encontró la hoja de pagos" });
      }
      if (hasConfirmedPayment_(paymentsSheet, orderNumber)) {
        return createJsonResponse({
          success: false,
          message: "El pedido tiene un pago confirmado y no puede rechazarse sin gestionar la devolución"
        });
      }
      // Solo H, J y K. La columna I (fcm_token) y PAYMENTS permanecen intactos.
      ordersSheet.getRange(sheetRow, 8).setValue("rejected");
      ordersSheet.getRange(sheetRow, 10).setValue(reason);
      ordersSheet.getRange(sheetRow, 11).setValue(detail);
      return createJsonResponse({
        success: true,
        orderNumber: orderNumber,
        status: "rejected",
        rejectionReason: reason,
        rejectionDetail: detail
      });
    }
    return createJsonResponse({ success: false, message: "No se encontró el pedido solicitado" });
  } catch (error) {
    return createJsonResponse({ success: false, message: error.toString() });
  } finally {
    if (lock.hasLock()) lock.releaseLock();
  }
}

function registerPayment_(data) {
  if (!isAdminDeviceAuthorized_(data.installationId)) {
    return createJsonResponse({ success: false, message: "Dispositivo no autorizado" });
  }
  var orderNumber = String(data.orderNumber || "").trim();
  var paymentMethod = String(data.paymentMethod || "").trim();
  if (!orderNumber) {
    return createJsonResponse({ success: false, message: "Debe indicar el número del pedido" });
  }
  if (["cash", "transfer"].indexOf(paymentMethod) === -1) {
    return createJsonResponse({ success: false, message: "Método de pago no permitido" });
  }

  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    var ordersSheet = spreadsheet.getSheetByName("Hoja 1");
    if (!ordersSheet) {
      return createJsonResponse({ success: false, message: "No se encontró la hoja de pedidos" });
    }
    var lastRow = ordersSheet.getLastRow();
    var orderRows = lastRow >= 2 ? ordersSheet.getRange(2, 1, lastRow - 1, 8).getValues() : [];
    var foundOrder = null;
    for (var index = orderRows.length - 1; index >= 0; index--) {
      if (String(orderRows[index][0]).trim() === orderNumber) {
        foundOrder = orderRows[index];
        break;
      }
    }
    if (!foundOrder) {
      return createJsonResponse({ success: false, message: "No se encontró el pedido solicitado" });
    }
    var paymentOrderStatus = String(foundOrder[7]).trim();
    if (paymentOrderStatus === "cancelled" || paymentOrderStatus === "rejected") {
      return createJsonResponse({ success: false, message: "No es posible registrar pago para este pedido" });
    }
    var amount = Number(foundOrder[4]) || 0;
    var paymentsSheet = spreadsheet.getSheetByName("PAYMENTS");
    if (!paymentsSheet) {
      return createJsonResponse({ success: false, message: "No se encontró la hoja de pagos" });
    }
    if (hasConfirmedPayment_(paymentsSheet, orderNumber)) {
      return createJsonResponse({ success: false, message: "El pedido ya tiene un pago confirmado" });
    }
    var paymentId = "PAY-" + Utilities.getUuid();
    var dateTime = Utilities.formatDate(new Date(), spreadsheet.getSpreadsheetTimeZone(), "yyyy-MM-dd HH:mm:ss");
    paymentsSheet.appendRow([
      paymentId, orderNumber, dateTime, paymentMethod, amount, "confirmed", data.installationId
    ]);
    return createJsonResponse({
      success: true,
      payment: {
        paymentId: paymentId,
        orderNumber: orderNumber,
        dateTime: dateTime,
        paymentMethod: paymentMethod,
        amount: amount,
        paymentStatus: "confirmed"
      }
    });
  } catch (error) {
    return createJsonResponse({ success: false, message: error.toString() });
  } finally {
    if (lock.hasLock()) lock.releaseLock();
  }
}

function reportTransfer_(data) {
  var orderNumber = String(data.orderNumber || "").trim();
  if (!orderNumber) {
    return createJsonResponse({ success: false, message: "Debe indicar el número del pedido" });
  }
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    var ordersSheet = spreadsheet.getSheetByName("Hoja 1");
    if (!ordersSheet) return createJsonResponse({ success: false, message: "No se encontró la hoja de pedidos" });
    var lastRow = ordersSheet.getLastRow();
    var rows = lastRow >= 2 ? ordersSheet.getRange(2, 1, lastRow - 1, 8).getValues() : [];
    var order = null;
    for (var index = rows.length - 1; index >= 0; index--) {
      if (String(rows[index][0]).trim() === orderNumber) { order = rows[index]; break; }
    }
    if (!order) return createJsonResponse({ success: false, message: "No se encontró el pedido solicitado" });
    if (["rejected", "cancelled"].indexOf(String(order[7]).trim()) !== -1) {
      return createJsonResponse({ success: false, message: "No es posible informar pago para este pedido" });
    }
    var paymentsSheet = spreadsheet.getSheetByName("PAYMENTS");
    if (!paymentsSheet) return createJsonResponse({ success: false, message: "No se encontró la hoja de pagos" });
    var paymentRows = paymentsSheet.getLastRow() >= 2
      ? paymentsSheet.getRange(2, 1, paymentsSheet.getLastRow() - 1, 6).getValues() : [];
    for (var paymentIndex = 0; paymentIndex < paymentRows.length; paymentIndex++) {
      var sameOrder = String(paymentRows[paymentIndex][1]).trim() === orderNumber;
      var status = String(paymentRows[paymentIndex][5]).trim();
      if (sameOrder && ["reported", "confirmed"].indexOf(status) !== -1) {
        return createJsonResponse({ success: false, message: "El pedido ya tiene un pago informado o confirmado" });
      }
    }
    var paymentId = "PAY-" + Utilities.getUuid();
    var dateTime = Utilities.formatDate(new Date(), spreadsheet.getSpreadsheetTimeZone(), "yyyy-MM-dd HH:mm:ss");
    paymentsSheet.appendRow([paymentId, orderNumber, dateTime, "transfer", Number(order[4]) || 0, "reported", ""]);
    return createJsonResponse({ success: true, paymentStatus: "reported", orderNumber: orderNumber });
  } catch (error) {
    return createJsonResponse({ success: false, message: error.toString() });
  } finally {
    if (lock.hasLock()) lock.releaseLock();
  }
}

function confirmTransfer_(data) {
  if (!isAdminDeviceAuthorized_(data.installationId)) {
    return createJsonResponse({ success: false, message: "Dispositivo no autorizado" });
  }
  var orderNumber = String(data.orderNumber || "").trim();
  if (!orderNumber) return createJsonResponse({ success: false, message: "Debe indicar el número del pedido" });
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = spreadsheet.getSheetByName("PAYMENTS");
    if (!sheet) return createJsonResponse({ success: false, message: "No se encontró la hoja de pagos" });
    var rows = sheet.getLastRow() >= 2 ? sheet.getRange(2, 1, sheet.getLastRow() - 1, 6).getValues() : [];
    for (var index = rows.length - 1; index >= 0; index--) {
      if (String(rows[index][1]).trim() === orderNumber &&
          String(rows[index][3]).trim() === "transfer" &&
          String(rows[index][5]).trim() === "reported") {
        sheet.getRange(index + 2, 6).setValue("confirmed");
        return createJsonResponse({ success: true, payment: {
          paymentId: String(rows[index][0]), orderNumber: orderNumber,
          dateTime: String(rows[index][2]), paymentMethod: "transfer",
          amount: Number(rows[index][4]) || 0, paymentStatus: "confirmed"
        }});
      }
    }
    return createJsonResponse({ success: false, message: "No existe una transferencia informada para este pedido" });
  } catch (error) {
    return createJsonResponse({ success: false, message: error.toString() });
  } finally {
    if (lock.hasLock()) lock.releaseLock();
  }
}

function hasConfirmedPayment_(paymentsSheet, orderNumber) {
  if (paymentsSheet.getLastRow() < 2) return false;
  var rows = paymentsSheet.getRange(2, 2, paymentsSheet.getLastRow() - 1, 5).getValues();
  for (var index = 0; index < rows.length; index++) {
    if (String(rows[index][0]).trim() === orderNumber && String(rows[index][4]).trim() === "confirmed") {
      return true;
    }
  }
  return false;
}

function getLatestPaymentsMap_() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("PAYMENTS");
  var result = {};
  if (!sheet || sheet.getLastRow() < 2) return result;
  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 6).getValues();
  for (var index = rows.length - 1; index >= 0; index--) {
    var orderNumber = String(rows[index][1]).trim();
    var paymentStatus = String(rows[index][5]).trim();
    if (!result[orderNumber] && ["pending", "reported", "confirmed", "failed", "cancelled"].indexOf(paymentStatus) !== -1) {
      result[orderNumber] = {
        paymentMethod: String(rows[index][3]),
        amount: Number(rows[index][4]) || 0,
        paymentStatus: paymentStatus
      };
    }
  }
  return result;
}

function listPayments_() {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = spreadsheet.getSheetByName("PAYMENTS");
  if (!sheet) {
    return createJsonResponse({ success: false, message: "No se encontró la hoja de pagos" });
  }
  var payments = [];
  if (sheet.getLastRow() >= 2) {
    var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 6).getValues();
    var timeZone = spreadsheet.getSpreadsheetTimeZone();
    var today = Utilities.formatDate(new Date(), timeZone, "yyyy-MM-dd");
    for (var index = rows.length - 1; index >= 0 && payments.length < 100; index--) {
      var paymentStatus = String(rows[index][5]).trim();
      if (["reported", "confirmed"].indexOf(paymentStatus) === -1) continue;
      var formattedDateTime = rows[index][2] instanceof Date
        ? Utilities.formatDate(rows[index][2], timeZone, "yyyy-MM-dd HH:mm:ss") : String(rows[index][2]);
      payments.push({
        paymentId: String(rows[index][0]),
        orderNumber: String(rows[index][1]),
        dateTime: formattedDateTime,
        paymentMethod: String(rows[index][3]),
        amount: Number(rows[index][4]) || 0,
        paymentStatus: paymentStatus,
        isToday: formattedDateTime.indexOf(today) === 0
      });
    }
  }
  return createJsonResponse({ success: true, payments: payments });
}


function createJsonResponse(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

// Webpay Plus REST, exclusivamente ambiente de integración.
var WEBPAY_API_BASE_ = "https://webpay3gint.transbank.cl/rswebpaytransaction/api/webpay/v1.2/transactions";
var WEBPAY_SHEET_ = "WEBPAY_TRANSACTIONS";

function createWebpayTransaction_(data) {
  var orderNumber = String(data.orderNumber || "").trim();
  var debugStage = "start";
  console.log("WEBPAY DEBUG - start - orderNumber: " + sanitizeWebpayDebugText_(orderNumber));
  if (!orderNumber || orderNumber.length > 64) {
    return createJsonResponse({ success: false, message: "Debe indicar un pedido válido" });
  }
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    var ordersSheet = spreadsheet.getSheetByName("Hoja 1");
    var paymentsSheet = spreadsheet.getSheetByName("PAYMENTS");
    var transactionsSheet = spreadsheet.getSheetByName(WEBPAY_SHEET_);
    if (!ordersSheet || !paymentsSheet || !transactionsSheet) {
      return createJsonResponse({ success: false, message: "La configuración de pagos está incompleta" });
    }
    var order = findOrderForWebpay_(ordersSheet, orderNumber);
    if (!order) return createJsonResponse({ success: false, message: "No se encontró el pedido solicitado" });
    debugStage = "order located";
    console.log("WEBPAY DEBUG - order located - orderNumber: " + sanitizeWebpayDebugText_(orderNumber));
    if (order.status !== "pending_review") {
      return createJsonResponse({ success: false, message: "El pedido no está disponible para pago Webpay" });
    }
    if (!(order.amount > 0)) return createJsonResponse({ success: false, message: "El pedido no tiene un monto válido" });
    debugStage = "amount valid";
    console.log("WEBPAY DEBUG - amount valid - amount: " + order.amount);
    if (hasConfirmedPayment_(paymentsSheet, orderNumber)) {
      return createJsonResponse({ success: false, message: "El pedido ya tiene un pago confirmado" });
    }
    if (hasActiveWebpay_(transactionsSheet, orderNumber)) {
      return createJsonResponse({ success: false, message: "El pedido ya tiene un pago Webpay en curso" });
    }

    debugStage = "loading configuration";
    logWebpayPropertyPresence_();
    var config = getWebpayConfig_();
    debugStage = "configuration loaded";
    console.log("WEBPAY DEBUG - configuration loaded");
    var suffix = Utilities.getUuid().replace(/-/g, "").substring(0, 8);
    var cleanOrder = orderNumber.replace(/[^A-Za-z0-9_-]/g, "").substring(0, 16);
    var buyOrder = (cleanOrder + "-" + suffix).substring(0, 26);
    var sessionId = ("WP-" + Utilities.getUuid().replace(/-/g, "")).substring(0, 61);
    var request = {
      buy_order: buyOrder,
      session_id: sessionId,
      amount: order.amount,
      return_url: config.returnUrl
    };
    debugStage = "calling Transbank";
    console.log("WEBPAY DEBUG - calling Transbank");
    var response = webpayFetch_(WEBPAY_API_BASE_, "post", request, config);
    debugStage = "Transbank response";
    console.log("WEBPAY DEBUG - HTTP status: " + response.code);
    console.log("WEBPAY DEBUG - response: " + response.debugBody);
    if (response.code !== 200 || !response.body.token || !response.body.url) {
      return createJsonResponse({ success: false, message: "No fue posible iniciar el pago" });
    }

    var paymentId = "PAY-" + Utilities.getUuid();
    var now = webpayTimestamp_(spreadsheet);
    paymentsSheet.appendRow([paymentId, orderNumber, now, "webpay", order.amount, "pending", ""]);
    transactionsSheet.appendRow([
      "WPT-" + Utilities.getUuid(), orderNumber, paymentId, buyOrder, sessionId,
      response.body.token, "pending", now, now, response.body.url
    ]);
    var separator = config.returnUrl.indexOf("?") === -1 ? "?" : "&";
    return createJsonResponse({
      success: true,
      token: response.body.token,
      url: response.body.url,
      redirectUrl: config.returnUrl + separator + "action=openWebpay&token=" + encodeURIComponent(response.body.token)
    });
  } catch (error) {
    console.log("WEBPAY DEBUG - failed stage: " + debugStage);
    console.log("WEBPAY DEBUG - exception: " + sanitizeWebpayDebugText_(
      error && error.message ? error.message : String(error || "Error desconocido")
    ));
    return createJsonResponse({ success: false, message: "No fue posible iniciar el pago" });
  } finally {
    if (lock.hasLock()) lock.releaseLock();
  }
}

function findOrderForWebpay_(sheet, orderNumber) {
  var lastRow = sheet.getLastRow();
  if (lastRow < 2) return null;
  var rows = sheet.getRange(2, 1, lastRow - 1, 8).getValues();
  for (var index = rows.length - 1; index >= 0; index--) {
    if (String(rows[index][0]).trim() === orderNumber) {
      return { amount: Number(rows[index][4]) || 0, status: String(rows[index][7]).trim() || "pending_review" };
    }
  }
  return null;
}

function hasActiveWebpay_(sheet, orderNumber) {
  if (sheet.getLastRow() < 2) return false;
  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 7).getValues();
  for (var index = rows.length - 1; index >= 0; index--) {
    if (String(rows[index][1]).trim() === orderNumber && String(rows[index][6]).trim() === "pending") return true;
  }
  return false;
}

function getWebpayConfig_() {
  var properties = PropertiesService.getScriptProperties();
  var environment = String(properties.getProperty("TRANSBANK_ENVIRONMENT") || "").toLowerCase();
  var commerceCode = properties.getProperty("TRANSBANK_COMMERCE_CODE");
  var apiKey = properties.getProperty("TRANSBANK_API_KEY");
  var returnUrl = properties.getProperty("WEBPAY_RETURN_URL");
  if (environment !== "integration" || !commerceCode || !apiKey || !returnUrl || !/^https:\/\//.test(returnUrl)) {
    throw new Error("Webpay integration is not configured");
  }
  return { commerceCode: commerceCode, apiKey: apiKey, returnUrl: returnUrl };
}

function webpayFetch_(url, method, payload, config) {
  var options = {
    method: method,
    muteHttpExceptions: true,
    headers: { "Tbk-Api-Key-Id": config.commerceCode, "Tbk-Api-Key-Secret": config.apiKey },
    contentType: "application/json"
  };
  if (payload !== null) options.payload = JSON.stringify(payload);
  var response = UrlFetchApp.fetch(url, options);
  var responseText = response.getContentText();
  var body = {};
  try { body = JSON.parse(responseText); } catch (ignored) {}
  return {
    code: response.getResponseCode(),
    body: body,
    debugBody: sanitizeWebpayResponse_(body, responseText)
  };
}

function logWebpayPropertyPresence_() {
  var properties = PropertiesService.getScriptProperties();
  console.log("WEBPAY DEBUG - TRANSBANK_ENVIRONMENT present: " + Boolean(properties.getProperty("TRANSBANK_ENVIRONMENT")));
  console.log("WEBPAY DEBUG - TRANSBANK_COMMERCE_CODE present: " + Boolean(properties.getProperty("TRANSBANK_COMMERCE_CODE")));
  console.log("WEBPAY DEBUG - TRANSBANK_API_KEY present: " + Boolean(properties.getProperty("TRANSBANK_API_KEY")));
  console.log("WEBPAY DEBUG - WEBPAY_RETURN_URL present: " + Boolean(properties.getProperty("WEBPAY_RETURN_URL")));
}

function sanitizeWebpayResponse_(body, responseText) {
  var safe = {};
  var allowedFields = ["error_message", "message", "error", "code", "status", "type"];
  for (var index = 0; index < allowedFields.length; index++) {
    var field = allowedFields[index];
    if (body && body[field] !== undefined && body[field] !== null) {
      safe[field] = sanitizeWebpayDebugText_(body[field]);
    }
  }
  if (body && body.token) safe.tokenPresent = true;
  if (body && body.url) safe.urlPresent = true;
  if (Object.keys(safe).length === 0) {
    safe.parseableJson = Boolean(body && Object.keys(body).length > 0);
    safe.responsePresent = Boolean(responseText);
  }
  return JSON.stringify(safe);
}

function sanitizeWebpayDebugText_(value) {
  var text = String(value === undefined || value === null ? "" : value);
  var secrets = [];
  try {
    var properties = PropertiesService.getScriptProperties();
    secrets = [
      properties.getProperty("TRANSBANK_API_KEY"),
      properties.getProperty("TRANSBANK_COMMERCE_CODE")
    ];
  } catch (ignored) {}
  for (var index = 0; index < secrets.length; index++) {
    if (secrets[index]) text = text.split(secrets[index]).join("[REDACTED]");
  }
  return text.replace(/(Tbk-Api-Key-(?:Id|Secret)\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .replace(/[\r\n\t]+/g, " ").substring(0, 300);
}

function openWebpayForm_(token) {
  var transaction = findWebpayByToken_(String(token || ""));
  if (!transaction || transaction.status !== "pending") return webpayResultPage_("No fue posible iniciar el pago");
  var config = getWebpayConfig_();
  var status = webpayFetch_(WEBPAY_API_BASE_ + "/" + encodeURIComponent(token), "get", null, config);
  if (status.code !== 200 || status.body.status !== "INITIALIZED") {
    return webpayResultPage_("No fue posible iniciar el pago");
  }
  var createUrl = transaction.formUrl;
  return HtmlService.createHtmlOutput(
    "<!doctype html><html><body><p>Redirigiendo a Webpay…</p><form id='webpay' method='post' action='" +
    escapeHtml_(createUrl) + "'><input type='hidden' name='token_ws' value='" + escapeHtml_(token) +
    "'></form><script>document.getElementById('webpay').submit();</script></body></html>"
  ).setTitle("Webpay");
}

function handleWebpayReturn_(parameters) {
  var token = String(parameters.token_ws || parameters.TBK_TOKEN || "").trim();
  var transaction = findWebpayByToken_(token);
  if (!transaction) return webpayResultPage_("No fue posible confirmar el pago");
  if (!parameters.token_ws) {
    updateWebpayResult_(transaction, "cancelled");
    return webpayResultPage_("Pago cancelado");
  }
  if (transaction.status === "confirmed") return webpayResultPage_("Pago realizado correctamente");
  var result = commitWebpayTransaction_(token);
  return webpayResultPage_(result ? "Pago realizado correctamente" : "No fue posible confirmar el pago");
}

function commitWebpayTransaction_(token) {
  var transaction = findWebpayByToken_(token);
  if (!transaction || transaction.status !== "pending") return false;
  try {
    var config = getWebpayConfig_();
    var response = webpayFetch_(WEBPAY_API_BASE_ + "/" + encodeURIComponent(token), "put", {}, config);
    var body = response.body;
    var valid = response.code === 200 && body.status === "AUTHORIZED" && Number(body.response_code) === 0 &&
      Number(body.amount) === transaction.amount && String(body.buy_order) === transaction.buyOrder &&
      String(body.session_id) === transaction.sessionId && String(body.authorization_code || "") !== "" &&
      String(body.payment_type_code || "") !== "";
    updateWebpayResult_(transaction, valid ? "confirmed" : "failed");
    return valid;
  } catch (error) {
    updateWebpayResult_(transaction, "failed");
    return false;
  }
}

function getWebpayStatus_(orderNumber) {
  var transaction = findLatestWebpayByOrder_(String(orderNumber || "").trim());
  if (!transaction) return createJsonResponse({ success: false, message: "No se encontró el pago Webpay" });
  if (transaction.status === "pending") {
    try {
      var response = webpayFetch_(WEBPAY_API_BASE_ + "/" + encodeURIComponent(transaction.token), "get", null, getWebpayConfig_());
      if (response.code === 200 && response.body.status === "AUTHORIZED") commitWebpayTransaction_(transaction.token);
      transaction = findWebpayByToken_(transaction.token);
    } catch (ignored) {}
  }
  return createJsonResponse({ success: true, orderNumber: transaction.orderNumber, paymentStatus: transaction.status });
}

function findWebpayByToken_(token) {
  if (!token) return null;
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(WEBPAY_SHEET_);
  if (!sheet || sheet.getLastRow() < 2) return null;
  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 10).getValues();
  for (var index = rows.length - 1; index >= 0; index--) {
    if (String(rows[index][5]) === token) return webpayRow_(sheet, index + 2, rows[index]);
  }
  return null;
}

function findLatestWebpayByOrder_(orderNumber) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(WEBPAY_SHEET_);
  if (!sheet || sheet.getLastRow() < 2) return null;
  var rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, 10).getValues();
  for (var index = rows.length - 1; index >= 0; index--) {
    if (String(rows[index][1]).trim() === orderNumber) return webpayRow_(sheet, index + 2, rows[index]);
  }
  return null;
}

function webpayRow_(sheet, rowNumber, row) {
  var payments = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("PAYMENTS");
  var amount = 0;
  if (payments && payments.getLastRow() >= 2) {
    var paymentRows = payments.getRange(2, 1, payments.getLastRow() - 1, 6).getValues();
    for (var index = paymentRows.length - 1; index >= 0; index--) {
      if (String(paymentRows[index][0]) === String(row[2])) { amount = Number(paymentRows[index][4]) || 0; break; }
    }
  }
  return { sheet: sheet, row: rowNumber, orderNumber: String(row[1]), paymentId: String(row[2]),
    buyOrder: String(row[3]), sessionId: String(row[4]), token: String(row[5]), status: String(row[6]),
    formUrl: String(row[9]), amount: amount };
}

function updateWebpayResult_(transaction, status) {
  transaction.sheet.getRange(transaction.row, 7).setValue(status);
  transaction.sheet.getRange(transaction.row, 9).setValue(webpayTimestamp_(SpreadsheetApp.getActiveSpreadsheet()));
  var payments = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("PAYMENTS");
  if (!payments || payments.getLastRow() < 2) return;
  var ids = payments.getRange(2, 1, payments.getLastRow() - 1, 1).getValues();
  for (var index = ids.length - 1; index >= 0; index--) {
    if (String(ids[index][0]) === transaction.paymentId) { payments.getRange(index + 2, 6).setValue(status); return; }
  }
}

function webpayTimestamp_(spreadsheet) {
  return Utilities.formatDate(new Date(), spreadsheet.getSpreadsheetTimeZone(), "yyyy-MM-dd HH:mm:ss");
}

function webpayResultPage_(message) {
  return HtmlService.createHtmlOutput("<!doctype html><html><body><h2>" + escapeHtml_(message) +
    "</h2><p>Puedes volver a ArmaTuHandroll y consultar tu pedido.</p></body></html>").setTitle("Resultado Webpay");
}

function escapeHtml_(value) {
  return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;").replace(/'/g, "&#39;");
}
