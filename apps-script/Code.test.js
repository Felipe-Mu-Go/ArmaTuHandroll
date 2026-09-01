const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

const sandbox = { console };
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(`${__dirname}/Code.gs`, "utf8"), sandbox);

const originalFunctions = {};
[
  "findWebpayByToken_", "findLatestWebpayByOrder_", "findOrderForWebpay_", "getWebpayConfig_",
  "webpayFetch_", "updateWebpayResult_", "hasIncompatiblePaymentForWebpay_", "isAdminDeviceAuthorized_",
  "getLatestPaymentsMap_", "getPendingWebpayOrderNumbers_", "reconcileWebpayTransaction_"
].forEach(name => { originalFunctions[name] = sandbox[name]; });

const tests = [];
function test(name, body) { tests.push({ name, body }); }

function jsonServices() {
  sandbox.ContentService = {
    MimeType: { JSON: "json" },
    createTextOutput(value) {
      return { value, setMimeType() { return this; } };
    }
  };
  sandbox.HtmlService = {
    createHtmlOutput(value) { return { value, setTitle() { return this; } }; }
  };
  sandbox.LockService = {
    getScriptLock: () => ({ waitLock() {}, hasLock: () => true, releaseLock() {} })
  };
}

function responseJson(response) { return JSON.parse(response.value); }

class Sheet {
  constructor(rows) { this.rows = rows.map(row => row.slice()); this.writes = 0; }
  getLastRow() { return this.rows.length; }
  getRange(row, column, rowCount = 1, columnCount = 1) {
    const sheet = this;
    return {
      getValues() {
        return Array.from({ length: rowCount }, (_, r) =>
          Array.from({ length: columnCount }, (_, c) => sheet.rows[row - 1 + r]?.[column - 1 + c] ?? "")
        );
      },
      getValue() { return sheet.rows[row - 1]?.[column - 1] ?? ""; },
      setValue(value) {
        while (sheet.rows.length < row) sheet.rows.push([]);
        sheet.rows[row - 1][column - 1] = value;
        sheet.writes += 1;
        return this;
      }
    };
  }
  appendRow(row) { this.rows.push(row.slice()); }
}

function installSpreadsheet(sheets) {
  const spreadsheet = {
    getSheetByName(name) { return sheets[name] || null; },
    getSpreadsheetTimeZone() { return "UTC"; }
  };
  sandbox.SpreadsheetApp = { getActiveSpreadsheet: () => spreadsheet };
  sandbox.LockService = {
    getScriptLock: () => ({ waitLock() {}, hasLock: () => true, releaseLock() {} })
  };
  sandbox.Utilities = {
    getUuid: () => "12345678-1234-1234-1234-123456789abc",
    formatDate: () => "2026-08-31 12:00:00"
  };
  return spreadsheet;
}

function webpayTransaction(status = "pending") {
  return {
    status, orderNumber: "PED-1", paymentId: "PAY-1", token: "token",
    amount: 12500, buyOrder: "PED-1-abc", sessionId: "WP-session",
    formUrl: "https://webpay.test/form"
  };
}

function authorizedResponse(overrides = {}) {
  return {
    code: 200,
    body: Object.assign({
      status: "AUTHORIZED", response_code: 0, amount: 12500,
      buy_order: "PED-1-abc", session_id: "WP-session",
      authorization_code: "AUTH", payment_type_code: "VD"
    }, overrides)
  };
}

jsonServices();

test("webpayFetch sends the configured headers without logging them", () => {
  let captured;
  sandbox.UrlFetchApp = {
    fetch(url, options) {
      captured = { url, options };
      return { getResponseCode: () => 200, getContentText: () => '{"token":"token","url":"https://example.test"}' };
    }
  };
  const config = { commerceCode: "commerce", apiKey: "secret" };
  const result = sandbox.webpayFetch_("https://api.test/transactions", "post", { amount: 12500 }, config);
  assert.equal(result.code, 200);
  assert.equal(captured.options.headers["Tbk-Api-Key-Id"], "commerce");
  assert.equal(captured.options.headers["Tbk-Api-Key-Secret"], "secret");
});

test("confirmed plus late TBK_TOKEN remains confirmed", () => {
  const transaction = webpayTransaction("confirmed");
  let updates = 0;
  sandbox.findWebpayByToken_ = () => transaction;
  sandbox.updateWebpayResult_ = () => { updates += 1; };
  const page = sandbox.handleWebpayReturn_({ TBK_TOKEN: "token" });
  assert.match(page.value, /Pago realizado correctamente/);
  assert.equal(transaction.status, "confirmed");
  assert.equal(updates, 0);
});

test("pending plus valid cancellation becomes cancelled", () => {
  const transaction = webpayTransaction();
  sandbox.findWebpayByToken_ = () => transaction;
  sandbox.updateWebpayResult_ = (_transaction, status) => { transaction.status = status; };
  sandbox.handleWebpayReturn_({ TBK_TOKEN: "token" });
  assert.equal(transaction.status, "cancelled");
});

test("repeated cancelled callback is idempotent", () => {
  const transactionSheet = new Sheet([["header"],
    ["WPT-1", "PED-1", "PAY-1", "PED-1-abc", "WP-session", "token", "pending", "", "", "url"]]);
  const payments = new Sheet([["header"], ["PAY-1", "PED-1", "", "webpay", 12500, "pending"]]);
  installSpreadsheet({ WEBPAY_TRANSACTIONS: transactionSheet, PAYMENTS: payments });
  sandbox.handleWebpayReturn_({ TBK_TOKEN: "token" });
  const writesAfterFirstCallback = transactionSheet.writes + payments.writes;
  sandbox.handleWebpayReturn_({ TBK_TOKEN: "token" });
  assert.equal(transactionSheet.rows[1][6], "cancelled");
  assert.equal(payments.rows[1][5], "cancelled");
  assert.equal(transactionSheet.writes + payments.writes, writesAfterFirstCallback);
});

test("transport exception during commit remains pending", () => {
  const transaction = webpayTransaction();
  let persistedStatus;
  sandbox.findWebpayByToken_ = () => transaction;
  sandbox.getWebpayConfig_ = () => ({});
  sandbox.webpayFetch_ = () => { throw new Error("timeout"); };
  sandbox.updateWebpayResult_ = (_transaction, status) => { persistedStatus = status; };
  assert.equal(sandbox.commitWebpayTransaction_("token"), false);
  assert.equal(persistedStatus, undefined);
  assert.equal(transaction.status, "pending");
});

test("definitive rejected commit becomes failed", () => {
  const transaction = webpayTransaction();
  let persistedStatus;
  sandbox.findWebpayByToken_ = () => transaction;
  sandbox.getWebpayConfig_ = () => ({});
  sandbox.webpayFetch_ = () => authorizedResponse({ status: "FAILED", response_code: -1 });
  sandbox.updateWebpayResult_ = (_transaction, status) => { persistedStatus = status; };
  assert.equal(sandbox.commitWebpayTransaction_("token"), false);
  assert.equal(persistedStatus, "failed");
});

test("ambiguous authorized data mismatch remains pending", () => {
  const transaction = webpayTransaction();
  let persistedStatus;
  sandbox.findWebpayByToken_ = () => transaction;
  sandbox.getWebpayConfig_ = () => ({});
  sandbox.webpayFetch_ = () => authorizedResponse({ amount: 1 });
  sandbox.updateWebpayResult_ = (_transaction, status) => { persistedStatus = status; };
  assert.equal(sandbox.commitWebpayTransaction_("token"), false);
  assert.equal(persistedStatus, undefined);
});

test("create Webpay reuses a complete pending transaction", () => {
  const orders = new Sheet([["header"]]);
  const payments = new Sheet([["header"]]);
  const transactions = new Sheet([["header"]]);
  installSpreadsheet({ "Hoja 1": orders, PAYMENTS: payments, WEBPAY_TRANSACTIONS: transactions });
  sandbox.findOrderForWebpay_ = () => ({ amount: 12500, status: "pending_review" });
  sandbox.findLatestWebpayByOrder_ = () => webpayTransaction();
  sandbox.getWebpayConfig_ = () => ({ returnUrl: "https://return.test/exec" });
  sandbox.webpayFetch_ = () => { throw new Error("must not create"); };
  const result = responseJson(sandbox.createWebpayTransaction_({ orderNumber: "PED-1" }));
  assert.equal(result.success, true);
  assert.equal(result.token, "token");
  assert.equal(payments.rows.length, 1);
  assert.equal(transactions.rows.length, 1);
});

test("pending Webpay is not reopened over a reported transfer", () => {
  const orders = new Sheet([["header"]]);
  const payments = new Sheet([["id", "order", "date", "method", "amount", "status"],
    ["PAY-T", "PED-1", "", "transfer", 12500, "reported"]]);
  const transactions = new Sheet([["header"]]);
  installSpreadsheet({ "Hoja 1": orders, PAYMENTS: payments, WEBPAY_TRANSACTIONS: transactions });
  sandbox.findOrderForWebpay_ = () => ({ amount: 12500, status: "pending_review" });
  sandbox.findLatestWebpayByOrder_ = () => webpayTransaction();
  sandbox.webpayFetch_ = () => { throw new Error("must not reopen"); };
  const result = responseJson(sandbox.createWebpayTransaction_({ orderNumber: "PED-1" }));
  assert.equal(result.success, false);
  assert.match(result.message, /otro pago activo o confirmado/);
});

test("pending Webpay is not reopened over another confirmed payment", () => {
  const orders = new Sheet([["header"]]);
  const payments = new Sheet([["id", "order", "date", "method", "amount", "status"],
    ["PAY-C", "PED-1", "", "cash", 12500, "confirmed"]]);
  const transactions = new Sheet([["header"]]);
  installSpreadsheet({ "Hoja 1": orders, PAYMENTS: payments, WEBPAY_TRANSACTIONS: transactions });
  sandbox.findOrderForWebpay_ = () => ({ amount: 12500, status: "pending_review" });
  sandbox.findLatestWebpayByOrder_ = () => webpayTransaction();
  sandbox.webpayFetch_ = () => { throw new Error("must not reopen"); };
  const result = responseJson(sandbox.createWebpayTransaction_({ orderNumber: "PED-1" }));
  assert.equal(result.success, false);
  assert.match(result.message, /otro pago activo o confirmado/);
});

function emptyCreationSheets() {
  return {
    orders: new Sheet([["header"]]),
    payments: new Sheet([["id", "order", "date", "method", "amount", "status"]]),
    transactions: new Sheet([["id", "order", "payment", "buy", "session", "token", "status", "created", "updated", "url"]])
  };
}

function configureNewWebpayCreation(sheets) {
  installSpreadsheet({ "Hoja 1": sheets.orders, PAYMENTS: sheets.payments, WEBPAY_TRANSACTIONS: sheets.transactions });
  sandbox.findOrderForWebpay_ = () => ({ amount: 12500, status: "pending_review" });
  sandbox.findLatestWebpayByOrder_ = () => null;
  sandbox.getWebpayConfig_ = () => ({ returnUrl: "https://return.test/exec" });
}

test("definitive creation failure releases the incomplete reservation", () => {
  const sheets = emptyCreationSheets();
  configureNewWebpayCreation(sheets);
  sandbox.webpayFetch_ = () => ({ code: 422, body: { error: "invalid request" } });
  const result = responseJson(sandbox.createWebpayTransaction_({ orderNumber: "PED-1" }));
  assert.equal(result.success, false);
  assert.equal(sheets.transactions.rows[1][6], "failed");
  assert.equal(sheets.payments.rows[1][5], "failed");
  assert.equal(sandbox.hasActiveWebpay_(sheets.transactions, "PED-1"), false);
});

test("malformed successful creation response releases the incomplete reservation", () => {
  const sheets = emptyCreationSheets();
  configureNewWebpayCreation(sheets);
  sandbox.webpayFetch_ = () => ({ code: 200, body: {} });
  sandbox.createWebpayTransaction_({ orderNumber: "PED-1" });
  assert.equal(sheets.transactions.rows[1][6], "failed");
  assert.equal(sheets.payments.rows[1][5], "failed");
});

test("ambiguous creation transport error keeps the reservation active", () => {
  const sheets = emptyCreationSheets();
  configureNewWebpayCreation(sheets);
  sandbox.webpayFetch_ = () => { throw new Error("timeout"); };
  const result = responseJson(sandbox.createWebpayTransaction_({ orderNumber: "PED-1" }));
  assert.equal(result.success, false);
  assert.equal(sheets.transactions.rows[1][6], "pending");
  assert.equal(sheets.payments.rows[1][5], "pending");
  assert.equal(sandbox.hasActiveWebpay_(sheets.transactions, "PED-1"), true);
});

test("INITIALIZED remains pending during commit", () => {
  const transaction = webpayTransaction();
  let persistedStatus;
  sandbox.findWebpayByToken_ = () => transaction;
  sandbox.getWebpayConfig_ = () => ({});
  sandbox.webpayFetch_ = () => ({ code: 200, body: { status: "INITIALIZED" } });
  sandbox.updateWebpayResult_ = (_transaction, status) => { persistedStatus = status; };
  assert.equal(sandbox.commitWebpayTransaction_("token"), false);
  assert.equal(persistedStatus, undefined);
  assert.equal(transaction.status, "pending");
});

test("valid AUTHORIZED commit confirms normally", () => {
  const transaction = webpayTransaction();
  let persistedStatus;
  sandbox.findWebpayByToken_ = () => transaction;
  sandbox.getWebpayConfig_ = () => ({});
  sandbox.webpayFetch_ = () => authorizedResponse();
  sandbox.hasIncompatiblePaymentForWebpay_ = () => false;
  sandbox.updateWebpayResult_ = (_transaction, status) => { persistedStatus = status; };
  assert.equal(sandbox.commitWebpayTransaction_("token"), true);
  assert.equal(persistedStatus, "confirmed");
});

function paymentSheets() {
  return {
    orders: new Sheet([["number", "date", "products", "qty", "amount", "eta", "name", "status"],
      ["PED-1", "", "", 1, 12500, "", "Client", "pending_review"]]),
    payments: new Sheet([["id", "order", "date", "method", "amount", "status"]]),
    transactions: new Sheet([["id", "order", "payment", "buy", "session", "token", "status", "created", "updated", "url"],
      ["WPT-1", "PED-1", "PAY-1", "buy", "session", "token", "pending", "", "", "https://form.test"]])
  };
}

test("Webpay pending blocks reportTransfer", () => {
  const sheets = paymentSheets();
  installSpreadsheet({ "Hoja 1": sheets.orders, PAYMENTS: sheets.payments, WEBPAY_TRANSACTIONS: sheets.transactions });
  const result = responseJson(sandbox.reportTransfer_({ orderNumber: "PED-1" }));
  assert.equal(result.success, false);
  assert.match(result.message, /otro método de pago activo/);
});

test("Webpay pending blocks confirmTransfer", () => {
  const sheets = paymentSheets();
  sheets.payments.appendRow(["PAY-T", "PED-1", "", "transfer", 12500, "reported"]);
  installSpreadsheet({ "Hoja 1": sheets.orders, PAYMENTS: sheets.payments, WEBPAY_TRANSACTIONS: sheets.transactions });
  sandbox.isAdminDeviceAuthorized_ = () => true;
  const result = responseJson(sandbox.confirmTransfer_({ orderNumber: "PED-1", installationId: "admin" }));
  assert.equal(result.success, false);
  assert.match(result.message, /otro método de pago activo/);
});

test("active transfer blocks create Webpay", () => {
  const sheets = paymentSheets();
  sheets.transactions = new Sheet([["header"]]);
  sheets.payments.appendRow(["PAY-T", "PED-1", "", "transfer", 12500, "reported"]);
  installSpreadsheet({ "Hoja 1": sheets.orders, PAYMENTS: sheets.payments, WEBPAY_TRANSACTIONS: sheets.transactions });
  sandbox.findOrderForWebpay_ = () => ({ amount: 12500, status: "pending_review" });
  sandbox.findLatestWebpayByOrder_ = () => null;
  const result = responseJson(sandbox.createWebpayTransaction_({ orderNumber: "PED-1" }));
  assert.equal(result.success, false);
  assert.match(result.message, /otro pago activo o confirmado/);
});

test("confirmed transfer blocks create Webpay", () => {
  const sheets = paymentSheets();
  sheets.transactions = new Sheet([["header"]]);
  sheets.payments.appendRow(["PAY-T", "PED-1", "", "transfer", 12500, "confirmed"]);
  installSpreadsheet({ "Hoja 1": sheets.orders, PAYMENTS: sheets.payments, WEBPAY_TRANSACTIONS: sheets.transactions });
  sandbox.findOrderForWebpay_ = () => ({ amount: 12500, status: "pending_review" });
  sandbox.findLatestWebpayByOrder_ = () => null;
  const result = responseJson(sandbox.createWebpayTransaction_({ orderNumber: "PED-1" }));
  assert.equal(result.success, false);
  assert.match(result.message, /otro pago activo o confirmado/);
});

test("Webpay pending blocks administrative payment registration", () => {
  const sheets = paymentSheets();
  installSpreadsheet({ "Hoja 1": sheets.orders, PAYMENTS: sheets.payments, WEBPAY_TRANSACTIONS: sheets.transactions });
  sandbox.isAdminDeviceAuthorized_ = () => true;
  const result = responseJson(sandbox.registerPayment_({
    orderNumber: "PED-1", paymentMethod: "cash", installationId: "admin"
  }));
  assert.equal(result.success, false);
  assert.match(result.message, /otro método de pago activo/);
});

test("authorized Webpay cannot confirm over an incompatible active payment", () => {
  const transaction = webpayTransaction();
  let persistedStatus;
  sandbox.findWebpayByToken_ = () => transaction;
  sandbox.getWebpayConfig_ = () => ({});
  sandbox.webpayFetch_ = () => authorizedResponse();
  sandbox.hasIncompatiblePaymentForWebpay_ = () => true;
  sandbox.updateWebpayResult_ = (_transaction, status) => { persistedStatus = status; };
  assert.equal(sandbox.commitWebpayTransaction_("token"), false);
  assert.equal(persistedStatus, undefined);
});

test("Webpay pending blocks administrative rejection", () => {
  const sheets = paymentSheets();
  installSpreadsheet({ "Hoja 1": sheets.orders, PAYMENTS: sheets.payments, WEBPAY_TRANSACTIONS: sheets.transactions });
  sandbox.isAdminDeviceAuthorized_ = () => true;
  const result = responseJson(sandbox.rejectOrder_({ orderNumber: "PED-1", reason: "store_closed", installationId: "admin" }));
  assert.equal(result.success, false);
  assert.match(result.message, /Webpay en curso/);
  assert.equal(sheets.orders.rows[1][7], "pending_review");
});

test("authorized status reconciliation becomes confirmed without another commit", () => {
  const transaction = webpayTransaction();
  let status = "pending";
  sandbox.findLatestWebpayByOrder_ = () => Object.assign({}, transaction, { status });
  sandbox.findWebpayByToken_ = () => Object.assign({}, transaction, { status });
  sandbox.getWebpayConfig_ = () => ({});
  sandbox.webpayFetch_ = () => authorizedResponse();
  sandbox.hasIncompatiblePaymentForWebpay_ = () => false;
  sandbox.updateWebpayResult_ = (_transaction, nextStatus) => { status = nextStatus; };
  const result = responseJson(sandbox.getWebpayStatus_("PED-1"));
  assert.equal(result.paymentStatus, "confirmed");
});

test("INITIALIZED status reconciliation remains pending", () => {
  const transaction = webpayTransaction();
  sandbox.findLatestWebpayByOrder_ = () => transaction;
  sandbox.findWebpayByToken_ = () => transaction;
  sandbox.getWebpayConfig_ = () => ({});
  sandbox.webpayFetch_ = () => ({ code: 200, body: { status: "INITIALIZED" } });
  sandbox.hasIncompatiblePaymentForWebpay_ = () => false;
  let persistedStatus;
  sandbox.updateWebpayResult_ = (_transaction, nextStatus) => { persistedStatus = nextStatus; };
  const result = responseJson(sandbox.getWebpayStatus_("PED-1"));
  assert.equal(result.paymentStatus, "pending");
  assert.equal(persistedStatus, undefined);
});

function configureAndroidStatusPath(initialStatus) {
  const orders = new Sheet([["number", "date", "products", "qty", "amount", "eta", "name", "status", "fcm", "reason", "detail"],
    ["PED-1", "", "", 1, 12500, "", "Client", "pending_review", "", "", ""]]);
  installSpreadsheet({ "Hoja 1": orders });
  const transaction = webpayTransaction();
  let status = initialStatus;
  let updates = 0;
  sandbox.findLatestWebpayByOrder_ = () => Object.assign({}, transaction, { status });
  sandbox.findWebpayByToken_ = () => Object.assign({}, transaction, { status });
  sandbox.getWebpayConfig_ = () => ({});
  sandbox.hasIncompatiblePaymentForWebpay_ = () => false;
  sandbox.updateWebpayResult_ = (_transaction, nextStatus) => { status = nextStatus; updates += 1; };
  sandbox.getLatestPaymentsMap_ = () => ({
    "PED-1": { paymentStatus: status, paymentMethod: "webpay", amount: 12500 }
  });
  return { getStatus: () => status, getUpdates: () => updates };
}

function configureAdminStatusPath(initialStatus) {
  const state = configureAndroidStatusPath(initialStatus);
  sandbox.getPendingWebpayOrderNumbers_ = () =>
    state.getStatus() === "pending" ? { "PED-1": true } : {};
  return state;
}

test("Android status path reconciles an authorized commit after a local timeout", () => {
  const state = configureAndroidStatusPath("pending");
  sandbox.webpayFetch_ = () => { throw new Error("commit response timeout"); };
  assert.equal(sandbox.commitWebpayTransaction_("token"), false);
  assert.equal(state.getStatus(), "pending");

  sandbox.webpayFetch_ = () => authorizedResponse();
  const result = responseJson(sandbox.doGet({ parameter: { orderNumber: "PED-1" } }));
  assert.equal(result.paymentStatus, "confirmed");
  assert.equal(state.getStatus(), "confirmed");
});

test("Android status path keeps pending when reconciliation fails", () => {
  const state = configureAndroidStatusPath("pending");
  sandbox.webpayFetch_ = () => { throw new Error("status transport error"); };
  const result = responseJson(sandbox.doGet({ parameter: { orderNumber: "PED-1" } }));
  assert.equal(result.paymentStatus, "pending");
  assert.equal(state.getStatus(), "pending");
});

test("Android status path is idempotent after confirmation", () => {
  configureAndroidStatusPath("confirmed");
  let remoteQueries = 0;
  sandbox.webpayFetch_ = () => { remoteQueries += 1; return authorizedResponse(); };
  const first = responseJson(sandbox.doGet({ parameter: { orderNumber: "PED-1" } }));
  const second = responseJson(sandbox.doGet({ parameter: { orderNumber: "PED-1" } }));
  assert.equal(first.paymentStatus, "confirmed");
  assert.equal(second.paymentStatus, "confirmed");
  assert.equal(remoteQueries, 0);
});

test("Android status path keeps INITIALIZED pending", () => {
  const state = configureAndroidStatusPath("pending");
  sandbox.webpayFetch_ = () => ({ code: 200, body: { status: "INITIALIZED" } });
  const result = responseJson(sandbox.doGet({ parameter: { orderNumber: "PED-1" } }));
  assert.equal(result.paymentStatus, "pending");
  assert.equal(state.getStatus(), "pending");
});

test("admin polling reconciles an authorized commit after a local timeout", () => {
  const state = configureAdminStatusPath("pending");
  sandbox.webpayFetch_ = () => { throw new Error("commit response timeout"); };
  assert.equal(sandbox.commitWebpayTransaction_("token"), false);
  assert.equal(state.getStatus(), "pending");

  sandbox.webpayFetch_ = () => authorizedResponse();
  const result = responseJson(sandbox.doGet({ parameter: { action: "listOrders" } }));
  assert.equal(result.orders[0].paymentStatus, "confirmed");
  assert.equal(state.getStatus(), "confirmed");
});

test("admin polling keeps pending when reconciliation fails", () => {
  const state = configureAdminStatusPath("pending");
  sandbox.webpayFetch_ = () => { throw new Error("status timeout"); };
  const result = responseJson(sandbox.doGet({ parameter: { action: "listOrders" } }));
  assert.equal(result.orders[0].paymentStatus, "pending");
  assert.equal(state.getStatus(), "pending");
});

test("admin polling keeps INITIALIZED pending", () => {
  const state = configureAdminStatusPath("pending");
  sandbox.webpayFetch_ = () => ({ code: 200, body: { status: "INITIALIZED" } });
  const result = responseJson(sandbox.doGet({ parameter: { action: "listOrders" } }));
  assert.equal(result.orders[0].paymentStatus, "pending");
  assert.equal(state.getStatus(), "pending");
});

test("admin polling does not query an already confirmed transaction", () => {
  const state = configureAdminStatusPath("confirmed");
  let remoteQueries = 0;
  sandbox.webpayFetch_ = () => { remoteQueries += 1; return authorizedResponse(); };
  const result = responseJson(sandbox.doGet({ parameter: { action: "listOrders" } }));
  assert.equal(result.orders[0].paymentStatus, "confirmed");
  assert.equal(state.getStatus(), "confirmed");
  assert.equal(remoteQueries, 0);
  assert.equal(state.getUpdates(), 0);
});

test("consecutive admin polls confirm once without duplicate effects", () => {
  const state = configureAdminStatusPath("pending");
  let remoteQueries = 0;
  sandbox.webpayFetch_ = () => { remoteQueries += 1; return authorizedResponse(); };
  const first = responseJson(sandbox.doGet({ parameter: { action: "listOrders" } }));
  const second = responseJson(sandbox.doGet({ parameter: { action: "listOrders" } }));
  assert.equal(first.orders[0].paymentStatus, "confirmed");
  assert.equal(second.orders[0].paymentStatus, "confirmed");
  assert.equal(remoteQueries, 1);
  assert.equal(state.getUpdates(), 1);
});

function configureBoundedAdminPolling(orderCount) {
  const rows = [["number", "date", "products", "qty", "amount", "eta", "name", "status", "fcm", "reason", "detail"]];
  const pending = {};
  for (let index = 1; index <= orderCount; index++) {
    const orderNumber = `PED-${index}`;
    rows.push([orderNumber, "", "", 1, 1000, "", "Client", "pending_review", "", "", ""]);
    pending[orderNumber] = true;
  }
  installSpreadsheet({ "Hoja 1": new Sheet(rows) });
  const reconciled = [];
  sandbox.getPendingWebpayOrderNumbers_ = () => Object.assign({}, pending);
  sandbox.reconcileWebpayTransaction_ = orderNumber => {
    reconciled.push(orderNumber);
    delete pending[orderNumber];
  };
  sandbox.getLatestPaymentsMap_ = () => {
    const result = {};
    for (let index = 1; index <= orderCount; index++) {
      const orderNumber = `PED-${index}`;
      result[orderNumber] = {
        paymentStatus: pending[orderNumber] ? "pending" : "confirmed",
        paymentMethod: "webpay",
        amount: 1000
      };
    }
    return result;
  };
  return { pending, reconciled };
}

test("admin polling bounds reconciliation and prioritizes recent orders", () => {
  const state = configureBoundedAdminPolling(10);
  sandbox.doGet({ parameter: { action: "listOrders" } });
  assert.equal(state.reconciled.length, sandbox.MAX_ADMIN_WEBPAY_RECONCILIATIONS_PER_REQUEST);
  assert.deepEqual(state.reconciled, ["PED-10", "PED-9", "PED-8", "PED-7"]);
});

test("a second admin poll continues with remaining pending orders", () => {
  const state = configureBoundedAdminPolling(10);
  sandbox.doGet({ parameter: { action: "listOrders" } });
  sandbox.doGet({ parameter: { action: "listOrders" } });
  assert.deepEqual(state.reconciled, ["PED-10", "PED-9", "PED-8", "PED-7", "PED-6", "PED-5", "PED-4", "PED-3"]);
});

test("one reconciliation failure does not stop the rest of the admin batch", () => {
  const state = configureBoundedAdminPolling(10);
  const successful = [];
  sandbox.reconcileWebpayTransaction_ = orderNumber => {
    if (orderNumber === "PED-10") throw new Error("isolated failure");
    successful.push(orderNumber);
  };
  const result = responseJson(sandbox.doGet({ parameter: { action: "listOrders" } }));
  assert.equal(result.success, true);
  assert.deepEqual(successful, ["PED-9", "PED-8", "PED-7"]);
});

test("confirmed orders do not consume admin reconciliation capacity", () => {
  const state = configureBoundedAdminPolling(6);
  delete state.pending["PED-6"];
  sandbox.doGet({ parameter: { action: "listOrders" } });
  assert.deepEqual(state.reconciled, ["PED-5", "PED-4", "PED-3", "PED-2"]);
});

test("duplicate order rows are reconciled once per admin request", () => {
  const state = configureBoundedAdminPolling(4);
  const ordersSheet = sandbox.SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Hoja 1");
  ordersSheet.appendRow(ordersSheet.rows[4]);
  sandbox.doGet({ parameter: { action: "listOrders" } });
  assert.equal(state.reconciled.filter(orderNumber => orderNumber === "PED-4").length, 1);
});

test("confirmed update cannot be degraded centrally", () => {
  const transactionSheet = new Sheet([["header"], ["WPT-1", "PED-1", "PAY-1", "buy", "session", "token", "confirmed", "", "", "url"]]);
  const payments = new Sheet([["header"], ["PAY-1", "PED-1", "", "webpay", 12500, "confirmed"]]);
  installSpreadsheet({ WEBPAY_TRANSACTIONS: transactionSheet, PAYMENTS: payments });
  const transaction = Object.assign(webpayTransaction("confirmed"), { sheet: transactionSheet, row: 2 });
  sandbox.findWebpayByToken_ = () => transaction;
  assert.equal(sandbox.updateWebpayResult_(transaction, "cancelled"), "confirmed");
  assert.equal(transactionSheet.rows[1][6], "confirmed");
  assert.equal(payments.rows[1][5], "confirmed");
});

function runPaymentRepair(transactionStatus, paymentStatus, requestedStatus = transactionStatus) {
  const transactionSheet = new Sheet([["header"],
    ["WPT-1", "PED-1", "PAY-1", "buy", "session", "token", transactionStatus, "", "", "url"]]);
  const payments = new Sheet([["header"], ["PAY-1", "PED-1", "", "webpay", 12500, paymentStatus]]);
  installSpreadsheet({ WEBPAY_TRANSACTIONS: transactionSheet, PAYMENTS: payments });
  const transaction = Object.assign(webpayTransaction(transactionStatus), { sheet: transactionSheet, row: 2 });
  sandbox.findWebpayByToken_ = () => transaction;
  const result = sandbox.updateWebpayResult_(transaction, requestedStatus);
  return { result, transactionSheet, payments };
}

test("idempotent confirmed update repairs pending PAYMENTS", () => {
  const state = runPaymentRepair("confirmed", "pending", "confirmed");
  assert.equal(state.transactionSheet.rows[1][6], "confirmed");
  assert.equal(state.payments.rows[1][5], "confirmed");
});

test("confirmed transaction repairs an empty payment status", () => {
  const state = runPaymentRepair("confirmed", "", "confirmed");
  assert.equal(state.payments.rows[1][5], "confirmed");
});

test("already synchronized confirmed rows avoid unnecessary writes", () => {
  const state = runPaymentRepair("confirmed", "confirmed", "confirmed");
  assert.equal(state.transactionSheet.writes, 0);
  assert.equal(state.payments.writes, 0);
});

test("idempotent failed update repairs pending PAYMENTS", () => {
  const state = runPaymentRepair("failed", "pending", "failed");
  assert.equal(state.payments.rows[1][5], "failed");
});

test("idempotent cancelled update repairs pending PAYMENTS", () => {
  const state = runPaymentRepair("cancelled", "pending", "cancelled");
  assert.equal(state.payments.rows[1][5], "cancelled");
});

test("confirmed transaction repairs a failed payment row", () => {
  const state = runPaymentRepair("confirmed", "failed", "confirmed");
  assert.equal(state.transactionSheet.rows[1][6], "confirmed");
  assert.equal(state.payments.rows[1][5], "confirmed");
});

test("confirmed PAYMENTS promotes the transaction instead of being degraded", () => {
  const state = runPaymentRepair("failed", "confirmed", "failed");
  assert.equal(state.transactionSheet.rows[1][6], "confirmed");
  assert.equal(state.payments.rows[1][5], "confirmed");
});

let passed = 0;
for (const { name, body } of tests) {
  try {
    Object.assign(sandbox, originalFunctions);
    body();
    passed += 1;
    console.log(`ok ${passed} - ${name}`);
  } catch (error) {
    console.error(`not ok - ${name}`);
    throw error;
  }
}
console.log(`${passed} Webpay backend tests passed`);
