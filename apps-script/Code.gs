function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);

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
      message: error.toString()
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
    if (!result[orderNumber] && ["reported", "confirmed"].indexOf(paymentStatus) !== -1) {
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
