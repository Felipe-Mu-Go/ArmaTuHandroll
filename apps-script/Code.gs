function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);

    if (data.action === "updateOrderStatus") {
      return updateOrderStatus_(data);
    }

    var sheet = SpreadsheetApp
      .getActiveSpreadsheet()
      .getActiveSheet();

    sheet.appendRow([
      data.pedido_numero || "",
      data.fecha_hora || "",
      data.productos || "",
      data.cantidad_total || "",
      data.total_pagado || "",
      data.tiempo_estimado || "",
      data.nombre_usuario || "",
      data.estado || "pending_review",
      data.fcm_token || ""
    ]);

    return createJsonResponse({
      success: true,
      message: "Pedido guardado correctamente"
    });

  } catch (error) {
    return createJsonResponse({
      success: false,
      message: error.toString()
    });
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
        .getActiveSheet();
      var ordersLastRow = ordersSheet.getLastRow();
      var orders = [];

      if (ordersLastRow >= 2) {
        var firstOrderRow = Math.max(2, ordersLastRow - 49);
        var orderRows = ordersSheet
          .getRange(firstOrderRow, 1, ordersLastRow - firstOrderRow + 1, 8)
          .getValues();
        var timeZone = SpreadsheetApp.getActiveSpreadsheet().getSpreadsheetTimeZone();

        for (var orderIndex = orderRows.length - 1; orderIndex >= 0; orderIndex--) {
          var orderRow = orderRows[orderIndex];
          var dateTime = orderRow[1] instanceof Date
            ? Utilities.formatDate(orderRow[1], timeZone, "yyyy-MM-dd HH:mm:ss")
            : String(orderRow[1]);

          orders.push({
            orderNumber: String(orderRow[0]),
            dateTime: dateTime,
            products: String(orderRow[2]),
            totalQuantity: Number(orderRow[3]) || 0,
            totalPaid: Number(orderRow[4]) || 0,
            estimatedTime: String(orderRow[5]),
            customerName: String(orderRow[6]),
            status: String(orderRow[7]).trim() || "pending_review"
          });
        }
      }

      return createJsonResponse({
        success: true,
        orders: orders
      });
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
      .getActiveSheet();

    var lastRow = sheet.getLastRow();

    if (lastRow < 2) {
      return createJsonResponse({
        success: false,
        message: "No se encontró el pedido solicitado"
      });
    }

    var rows = sheet
      .getRange(2, 1, lastRow - 1, 8)
      .getValues();

    for (var index = rows.length - 1; index >= 0; index--) {
      var storedOrderNumber = String(rows[index][0]).trim();

      if (storedOrderNumber === orderNumber.trim()) {
        var storedStatus = String(rows[index][7]).trim();

        if (storedStatus === "") {
          storedStatus = "pending_review";
        }

        return createJsonResponse({
          success: true,
          orderNumber: storedOrderNumber,
          status: storedStatus
        });
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
  if (newStatus !== "accepted" && newStatus !== "cancelled") {
    return createJsonResponse({ success: false, message: "Transición de estado no permitida" });
  }

  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var ordersSheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
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
        if (currentStatus !== "pending_review") {
          return createJsonResponse({ success: false, message: "El pedido cambió de estado" });
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


function createJsonResponse(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}
