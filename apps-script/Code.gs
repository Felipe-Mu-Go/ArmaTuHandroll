function doPost(e) {
  try {
    var sheet = SpreadsheetApp
      .getActiveSpreadsheet()
      .getActiveSheet();

    var data = JSON.parse(e.postData.contents);

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


function createJsonResponse(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}
