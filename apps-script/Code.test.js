const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

class FakeSheet {
  constructor(rows) {
    this.rows = rows.map((row) => row.slice());
    this.writes = 0;
    this.reads = 0;
  }
  getLastRow() { return this.rows.length; }
  getRange(row, column, rowCount = 1, columnCount = 1) {
    return {
      getValues: () => {
        this.reads += 1;
        return this.rows.slice(row - 1, row - 1 + rowCount)
          .map((values) => values.slice(column - 1, column - 1 + columnCount));
      },
      getValue: () => this.rows[row - 1] && this.rows[row - 1][column - 1],
      setValue: (value) => {
        while (this.rows.length < row) this.rows.push([]);
        this.rows[row - 1][column - 1] = value;
        this.writes += 1;
      }
    };
  }
  appendRow(row) { this.rows.push(row.slice()); this.writes += 1; }
}

function harness({ transactionStatus = "pending", paymentStatus = "pending", missingPayment = false,
  transactionAmount = 12500, createdAt = "2026-09-07 10:30:00", orderAmount = 12500,
  onWaitLock = null, lockTimeouts = 0 } = {}) {
  const transactions = new FakeSheet([
    ["id", "order", "payment", "buy", "session", "token", "status", "created", "updated", "url"],
    ["WPT-1", "ORDER-1", "PAY-1", "BUY-1", "SESSION-1", "TOKEN-1", transactionStatus,
      createdAt, "now", "https://form.test", transactionAmount]
  ]);
  const payments = new FakeSheet([
    ["id", "order", "date", "method", "amount", "status", "proof"],
    ...(!missingPayment ? [["PAY-1", "ORDER-1", "now", "webpay", 12500, paymentStatus, ""]] : [])
  ]);
  const orders = new FakeSheet([
    ["order", "date", "products", "quantity", "amount", "eta", "name", "status"],
    ["ORDER-1", "now", "items", 1, orderAmount, "", "name", "pending_review"]
  ]);
  const sheets = { WEBPAY_TRANSACTIONS: transactions, PAYMENTS: payments, "Hoja 1": orders };
  const spreadsheet = {
    getSheetByName: (name) => sheets[name] || null,
    getSpreadsheetTimeZone: () => "UTC"
  };
  const lockStats = { waits: 0, releases: 0, held: false };
  const sandbox = {
    console,
    encodeURIComponent,
    SpreadsheetApp: { getActiveSpreadsheet: () => spreadsheet },
    Utilities: { formatDate: () => "2026-09-08 00:00:00" },
    LockService: {
      getScriptLock: () => ({
        waitLock: () => {
          lockStats.waits += 1;
          if (lockStats.waits <= lockTimeouts) throw new Error("Lock timeout");
          assert.equal(lockStats.held, false, "recursive ScriptLock acquisition");
          lockStats.held = true;
          if (onWaitLock) onWaitLock({ transactions, payments });
        },
        hasLock: () => lockStats.held,
        releaseLock: () => { lockStats.held = false; lockStats.releases += 1; }
      })
    },
    HtmlService: { createHtmlOutput: (html) => ({ html, setTitle() { return this; } }) },
    ContentService: {
      MimeType: { JSON: "json" },
      createTextOutput: (text) => ({ text, setMimeType() { return this; } })
    }
  };
  vm.createContext(sandbox);
  vm.runInContext(fs.readFileSync(`${__dirname}/Code.gs`, "utf8"), sandbox);
  sandbox.getWebpayConfig_ = () => ({ commerceCode: "commerce", apiKey: "secret" });
  return { sandbox, transactions, payments, lockStats };
}

const tests = [];
function test(name, operation) { tests.push({ name, operation }); }
function statuses(state) {
  return [state.transactions.rows[1][6], state.payments.rows[1] && state.payments.rows[1][5]];
}

test("webpayFetch sends credentials and JSON payload", () => {
  const { sandbox } = harness();
  let captured;
  sandbox.UrlFetchApp = { fetch(url, options) {
    captured = { url, options };
    return { getResponseCode: () => 200, getContentText: () => '{"token":"token"}' };
  } };
  const result = sandbox.webpayFetch_("https://api.test", "post", { amount: 12500 },
    { commerceCode: "commerce", apiKey: "secret" });
  assert.equal(result.code, 200);
  assert.equal(captured.options.headers["Tbk-Api-Key-Id"], "commerce");
  assert.deepEqual(JSON.parse(captured.options.payload), { amount: 12500 });
});

test("commit confirms a valid response", () => {
  const state = harness();
  state.sandbox.webpayFetch_ = () => ({ code: 200, body: {
    status: "AUTHORIZED", response_code: 0, amount: 12500, buy_order: "BUY-1",
    session_id: "SESSION-1", authorization_code: "AUTH", payment_type_code: "VD"
  } });
  assert.equal(state.sandbox.commitWebpayTransaction_("TOKEN-1"), true);
  assert.deepEqual(statuses(state), ["confirmed", "confirmed"]);
});

test("commit rejects mismatched transaction data", () => {
  const state = harness();
  state.sandbox.webpayFetch_ = () => ({ code: 200, body: {
    status: "AUTHORIZED", response_code: 0, amount: 1, buy_order: "BUY-1",
    session_id: "SESSION-1", authorization_code: "AUTH", payment_type_code: "VD"
  } });
  assert.equal(state.sandbox.commitWebpayTransaction_("TOKEN-1"), false);
  assert.deepEqual(statuses(state), ["failed", "failed"]);
});

test("ambiguous commit keeps pending for later reconciliation", () => {
  const state = harness();
  state.sandbox.webpayFetch_ = () => { throw new Error("network lost after PUT"); };
  assert.equal(state.sandbox.commitWebpayTransaction_("TOKEN-1"), false);
  assert.deepEqual(statuses(state), ["pending", "pending"]);
});

test("status reconciliation confirms without repeating commit PUT", () => {
  const state = harness();
  const methods = [];
  state.sandbox.webpayFetch_ = (_url, method) => {
    methods.push(method);
    return { code: 200, body: {
      status: "AUTHORIZED", response_code: 0, amount: 12500, buy_order: "BUY-1",
      session_id: "SESSION-1", authorization_code: "AUTH", payment_type_code: "VD"
    } };
  };
  assert.equal(state.sandbox.reconcileWebpayTransaction_("TOKEN-1").status, "confirmed");
  assert.deepEqual(methods, ["get"]);
});

test("reconciliation repairs confirmed transaction with pending payment", () => {
  const state = harness({ transactionStatus: "confirmed", paymentStatus: "pending" });
  let remoteCalls = 0;
  state.sandbox.webpayFetch_ = () => { remoteCalls += 1; throw new Error("unexpected"); };
  assert.equal(state.sandbox.reconcileWebpayTransaction_("TOKEN-1").status, "confirmed");
  assert.deepEqual(statuses(state), ["confirmed", "confirmed"]);
  assert.equal(remoteCalls, 0);
});

test("reconciliation rebuilds a missing confirmed payment", () => {
  const state = harness({ transactionStatus: "confirmed", missingPayment: true });
  state.sandbox.webpayFetch_ = () => { throw new Error("unexpected"); };
  state.sandbox.reconcileWebpayTransaction_("TOKEN-1");
  assert.deepEqual(statuses(state), ["confirmed", "confirmed"]);
  assert.equal(state.payments.rows[1][4], 12500);
  assert.equal(state.payments.rows[1][2], "2026-09-07 10:30:00");
});

test("historical repair preserves its day in payment listings", () => {
  const state = harness({ transactionStatus: "confirmed", missingPayment: true,
    createdAt: "2026-09-01 08:15:00" });
  state.sandbox.reconcileWebpayTransaction_("TOKEN-1");
  const listed = JSON.parse(state.sandbox.listPayments_().text).payments;
  assert.equal(listed[0].dateTime, "2026-09-01 08:15:00");
  assert.equal(listed[0].isToday, false);
});

test("normal Android status route repairs local Webpay state", () => {
  const state = harness({ transactionStatus: "confirmed", paymentStatus: "pending" });
  let remoteCalls = 0;
  state.sandbox.webpayFetch_ = () => { remoteCalls += 1; };
  const response = JSON.parse(state.sandbox.doGet({ parameter: { orderNumber: "ORDER-1" } }).text);
  assert.equal(response.paymentStatus, "confirmed");
  assert.deepEqual(statuses(state), ["confirmed", "confirmed"]);
  assert.equal(remoteCalls, 0);
});

test("historical reconstructed failure cannot override a newer confirmation", () => {
  const state = harness({ transactionStatus: "cancelled", missingPayment: true,
    createdAt: "2026-08-01 10:00:00" });
  state.payments.appendRow(["PAY-2", "ORDER-1", "2026-09-01 10:00:00", "webpay", 13000, "confirmed", ""]);
  state.sandbox.reconcileWebpayTransaction_("TOKEN-1");
  const latest = state.sandbox.getLatestPaymentsMap_()["ORDER-1"];
  assert.equal(latest.paymentStatus, "confirmed");
  assert.equal(latest.amount, 13000);
});

test("payment chronology is independent of physical row order", () => {
  const state = harness({ transactionStatus: "confirmed", paymentStatus: "confirmed" });
  state.payments.rows[1][2] = "2026-09-02 10:00:00";
  state.payments.appendRow(["PAY-OLD", "ORDER-1", "2026-08-01 10:00:00", "webpay", 100, "failed", ""]);
  const latest = state.sandbox.getLatestPaymentsMap_()["ORDER-1"];
  assert.equal(latest.paymentStatus, "confirmed");
  assert.equal(latest.amount, 12500);
});

for (const laterStatus of ["failed", "cancelled"]) {
  test(`earlier confirmed Webpay attempt wins over later ${laterStatus} attempt`, () => {
    const state = harness({ transactionStatus: "confirmed", missingPayment: true,
      createdAt: "2026-09-01 10:00:00" });
    state.transactions.appendRow(["WPT-2", "ORDER-1", "PAY-2", "BUY-2", "SESSION-2", "TOKEN-2",
      laterStatus, "2026-09-02 10:00:00", "now", "https://form.test", 13000]);
    state.payments.appendRow(["PAY-2", "ORDER-1", "2026-09-02 10:00:00", "webpay", 13000, laterStatus, ""]);
    let remoteCalls = 0;
    state.sandbox.webpayFetch_ = () => { remoteCalls += 1; };
    const response = JSON.parse(state.sandbox.getWebpayStatus_("ORDER-1").text);
    assert.equal(response.paymentStatus, "confirmed");
    assert.equal(state.payments.rows.find((row) => row[0] === "PAY-1")[5], "confirmed");
    assert.equal(remoteCalls, 0);
  });
}

test("multiple pending attempts reconcile only the newest attempt", () => {
  const state = harness({ createdAt: "2026-09-01 10:00:00" });
  state.transactions.appendRow(["WPT-2", "ORDER-1", "PAY-2", "BUY-2", "SESSION-2", "TOKEN-2",
    "pending", "2026-09-02 10:00:00", "now", "https://form.test", 13000]);
  state.payments.appendRow(["PAY-2", "ORDER-1", "2026-09-02 10:00:00", "webpay", 13000, "pending", ""]);
  const requested = [];
  state.sandbox.webpayFetch_ = (url) => {
    requested.push(url);
    return { code: 200, body: { status: "INITIALIZED" } };
  };
  assert.equal(state.sandbox.reconcileWebpayOrder_("ORDER-1", true), "pending");
  assert.equal(requested.length, 1);
  assert.match(requested[0], /TOKEN-2$/);
});

test("commit does not charge a pending attempt after another attempt confirmed", () => {
  const state = harness({ transactionStatus: "confirmed", paymentStatus: "confirmed",
    createdAt: "2026-09-01 10:00:00" });
  state.transactions.appendRow(["WPT-2", "ORDER-1", "PAY-2", "BUY-2", "SESSION-2", "TOKEN-2",
    "pending", "2026-09-02 10:00:00", "now", "https://form.test", 13000]);
  state.payments.appendRow(["PAY-2", "ORDER-1", "2026-09-02 10:00:00", "webpay", 13000, "pending", ""]);
  let remoteCalls = 0;
  state.sandbox.webpayFetch_ = () => { remoteCalls += 1; throw new Error("unexpected"); };
  assert.equal(state.sandbox.commitWebpayTransaction_("TOKEN-2"), true);
  assert.equal(remoteCalls, 0);
  assert.equal(state.transactions.rows[2][6], "pending");
  assert.equal(state.payments.rows[1][5], "confirmed");
});

test("listOrders performs the same local repair as client polling", () => {
  const state = harness({ transactionStatus: "confirmed", paymentStatus: "pending" });
  const response = JSON.parse(state.sandbox.doGet({ parameter: { action: "listOrders" } }).text);
  assert.equal(response.orders[0].paymentStatus, "confirmed");
  assert.deepEqual(statuses(state), ["confirmed", "confirmed"]);
});

test("administrative acceptance repairs Webpay before checking payment", () => {
  const state = harness({ transactionStatus: "confirmed", paymentStatus: "pending" });
  state.sandbox.isAdminDeviceAuthorized_ = () => true;
  const response = JSON.parse(state.sandbox.updateOrderStatus_({
    installationId: "admin", orderNumber: "ORDER-1", newStatus: "accepted"
  }).text);
  assert.equal(response.success, true);
  assert.equal(state.payments.rows[1][5], "confirmed");
  assert.equal(state.sandbox.SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Hoja 1").rows[1][7], "accepted");
});

test("creation cannot charge again when only Webpay records confirmation", () => {
  const state = harness({ transactionStatus: "confirmed", missingPayment: true });
  let remoteCalls = 0;
  state.sandbox.webpayFetch_ = () => { remoteCalls += 1; };
  const response = JSON.parse(state.sandbox.createWebpayTransaction_({ orderNumber: "ORDER-1" }).text);
  assert.equal(response.success, false);
  assert.equal(remoteCalls, 0);
});

test("commit promotes a confirmed payment without a remote request", () => {
  const state = harness({ transactionStatus: "pending", paymentStatus: "confirmed" });
  let remoteCalls = 0;
  state.sandbox.webpayFetch_ = () => { remoteCalls += 1; throw new Error("unexpected"); };
  assert.equal(state.sandbox.commitWebpayTransaction_("TOKEN-1"), true);
  assert.deepEqual(statuses(state), ["confirmed", "confirmed"]);
  assert.equal(remoteCalls, 0);
});

test("rebuild uses charged transaction amount after order amount changes", () => {
  const state = harness({ transactionStatus: "confirmed", missingPayment: true,
    transactionAmount: 12500, orderAmount: 99000 });
  state.sandbox.reconcileWebpayTransaction_("TOKEN-1");
  assert.equal(state.payments.rows[1][4], 12500);
});

test("rebuild uses charged transaction amount when the order disappeared", () => {
  const state = harness({ transactionStatus: "confirmed", missingPayment: true,
    transactionAmount: 12500 });
  state.sandbox.SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Hoja 1").rows.splice(1, 1);
  state.sandbox.reconcileWebpayTransaction_("TOKEN-1");
  assert.equal(state.payments.rows[1][4], 12500);
});

test("legacy transaction without authoritative amount is not invented", () => {
  const state = harness({ transactionStatus: "confirmed", missingPayment: true,
    transactionAmount: 0, orderAmount: 99000 });
  state.sandbox.reconcileWebpayTransaction_("TOKEN-1");
  assert.equal(state.payments.getLastRow(), 1);
  assert.equal(state.transactions.rows[1][6], "confirmed");
});

test("terminal legacy confirmation recovers amount through verified status", () => {
  const state = harness({ transactionStatus: "confirmed", missingPayment: true,
    transactionAmount: 0, orderAmount: 99000 });
  let remoteCalls = 0;
  state.sandbox.webpayFetch_ = () => {
    remoteCalls += 1;
    return { code: 200, body: {
      status: "AUTHORIZED", response_code: 0, amount: 12500, buy_order: "BUY-1",
      session_id: "SESSION-1", authorization_code: "AUTH", payment_type_code: "VD"
    } };
  };
  assert.equal(state.sandbox.reconcileWebpayTransaction_("TOKEN-1").status, "confirmed");
  assert.equal(remoteCalls, 1);
  assert.equal(state.transactions.rows[1][10], 12500);
  assert.equal(state.payments.rows[1][4], 12500);
  assert.equal(state.payments.rows[1][5], "confirmed");
});

test("Android polling preserves reconciled confirmation over inferior payment row", () => {
  const state = harness({ transactionStatus: "confirmed", missingPayment: true });
  state.payments.appendRow(["PAY-LATER", "ORDER-1", "2026-09-08 12:00:00", "webpay", 12500, "failed", ""]);
  const response = JSON.parse(state.sandbox.doGet({ parameter: { orderNumber: "ORDER-1" } }).text);
  assert.equal(response.paymentStatus, "confirmed");
});

test("admin batch reconciliation scans each financial sheet a constant number of times", () => {
  const state = harness({ transactionStatus: "confirmed", paymentStatus: "pending" });
  for (let index = 2; index <= 40; index++) {
    const order = `ORDER-${index}`;
    state.sandbox.SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Hoja 1").appendRow(
      [order, "now", "items", 1, 1000, "", "name", "pending_review"]);
    state.transactions.appendRow([`WPT-${index}`, order, `PAY-${index}`, `BUY-${index}`, `SESSION-${index}`,
      `TOKEN-${index}`, "confirmed", "2026-09-01 10:00:00", "now", "https://form.test", 1000]);
    state.payments.appendRow([`PAY-${index}`, order, "2026-09-01 10:00:00", "webpay", 1000, "pending", ""]);
  }
  state.transactions.reads = 0;
  state.payments.reads = 0;
  state.sandbox.doGet({ parameter: { action: "listOrders" } });
  assert.equal(state.transactions.reads, 1);
  assert.equal(state.payments.reads, 2); // índice de reconciliación + mapa final de respuesta.
});

test("remote terminal state replaces cached pending state in the same poll", () => {
  const state = harness();
  state.sandbox.webpayFetch_ = () => ({ code: 200, body: { status: "FAILED" } });
  assert.equal(state.sandbox.reconcileWebpayOrder_("ORDER-1", true), "failed");
  assert.deepEqual(statuses(state), ["failed", "failed"]);
});

test("admin list repairs confirmed transactions outside its visual 50-order window", () => {
  const state = harness({ transactionStatus: "confirmed", missingPayment: true,
    createdAt: "2026-08-01 10:00:00" });
  const orders = state.sandbox.SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Hoja 1");
  for (let index = 2; index <= 61; index++) {
    orders.appendRow([`ORDER-${index}`, "now", "items", 1, 1000, "", "name", "pending_review"]);
  }
  const response = JSON.parse(state.sandbox.doGet({ parameter: { action: "listOrders" } }).text);
  assert.equal(response.orders.length, 50);
  assert.equal(response.orders.some((order) => order.orderNumber === "ORDER-1"), false);
  assert.equal(state.payments.rows.find((row) => row[0] === "PAY-1")[5], "confirmed");
});

test("successful commit recovers authoritative amount for a legacy transaction", () => {
  const state = harness({ transactionStatus: "pending", missingPayment: true,
    transactionAmount: 0, orderAmount: 99000 });
  state.sandbox.webpayFetch_ = () => ({ code: 200, body: {
    status: "AUTHORIZED", response_code: 0, amount: 12500, buy_order: "BUY-1",
    session_id: "SESSION-1", authorization_code: "AUTH", payment_type_code: "VD"
  } });
  assert.equal(state.sandbox.commitWebpayTransaction_("TOKEN-1"), true);
  assert.equal(state.transactions.rows[1][10], 12500);
  assert.equal(state.payments.rows[1][4], 12500);
  assert.notEqual(state.payments.rows[1][4], 99000);
});

for (const terminal of ["failed", "cancelled"]) {
  test(`confirmed payment wins over ${terminal} transaction`, () => {
    const state = harness({ transactionStatus: terminal, paymentStatus: "confirmed" });
    assert.equal(state.sandbox.reconcileWebpayTransaction_("TOKEN-1").status, "confirmed");
    assert.deepEqual(statuses(state), ["confirmed", "confirmed"]);
  });
}

test("already synchronized terminal reconciliation is idempotent", () => {
  const state = harness({ transactionStatus: "confirmed", paymentStatus: "confirmed" });
  let remoteCalls = 0;
  state.sandbox.webpayFetch_ = () => { remoteCalls += 1; };
  state.sandbox.reconcileWebpayTransaction_("TOKEN-1");
  const writes = state.transactions.writes + state.payments.writes;
  state.sandbox.reconcileWebpayTransaction_("TOKEN-1");
  assert.equal(state.transactions.writes + state.payments.writes, writes);
  assert.equal(remoteCalls, 0);
});

test("hasConfirmedPayment observes a repaired payment", () => {
  const state = harness({ transactionStatus: "confirmed", paymentStatus: "pending" });
  state.sandbox.reconcileWebpayTransaction_("TOKEN-1");
  assert.equal(state.sandbox.hasConfirmedPayment_(state.payments, "ORDER-1"), true);
});

test("pending transaction can be cancelled under one lock", () => {
  const state = harness();
  assert.equal(state.sandbox.cancelWebpayTransaction_("TOKEN-1"), "cancelled");
  assert.deepEqual(statuses(state), ["cancelled", "cancelled"]);
  assert.deepEqual([state.lockStats.waits, state.lockStats.releases], [1, 1]);
});

test("cancellation rereads after lock and loses a race with confirmation", () => {
  const state = harness({ onWaitLock: ({ transactions, payments }) => {
    transactions.rows[1][6] = "confirmed";
    payments.rows[1][5] = "confirmed";
  } });
  assert.equal(state.sandbox.cancelWebpayTransaction_("TOKEN-1"), "confirmed");
  assert.deepEqual(statuses(state), ["confirmed", "confirmed"]);
  assert.deepEqual([state.lockStats.waits, state.lockStats.releases], [1, 1]);
});

test("confirmed payment blocks cancellation and repairs transaction", () => {
  const state = harness({ transactionStatus: "pending", paymentStatus: "confirmed" });
  assert.equal(state.sandbox.cancelWebpayTransaction_("TOKEN-1"), "confirmed");
  assert.deepEqual(statuses(state), ["confirmed", "confirmed"]);
});

test("repeated cancellation callbacks cannot degrade confirmation", () => {
  const state = harness({ transactionStatus: "confirmed", paymentStatus: "confirmed" });
  assert.equal(state.sandbox.cancelWebpayTransaction_("TOKEN-1"), "confirmed");
  const writes = state.transactions.writes + state.payments.writes;
  assert.equal(state.sandbox.cancelWebpayTransaction_("TOKEN-1"), "confirmed");
  assert.deepEqual(statuses(state), ["confirmed", "confirmed"]);
  assert.equal(state.transactions.writes + state.payments.writes, writes);
  assert.deepEqual([state.lockStats.waits, state.lockStats.releases], [2, 2]);
});

test("Webpay lock is released when the protected operation throws", () => {
  const state = harness();
  assert.throws(() => state.sandbox.withWebpayLock_(() => { throw new Error("boom"); }), /boom/);
  assert.deepEqual([state.lockStats.waits, state.lockStats.releases, state.lockStats.held], [1, 1, false]);
});

test("token callback lock timeout remains recoverable by polling", () => {
  const state = harness({ lockTimeouts: 2 });
  let remoteCalls = 0;
  state.sandbox.webpayFetch_ = (_url, method) => {
    remoteCalls += 1;
    return { code: 200, body: {
      status: "AUTHORIZED", response_code: 0, amount: 12500, buy_order: "BUY-1",
      session_id: "SESSION-1", authorization_code: "AUTH", payment_type_code: "VD"
    } };
  };
  assert.equal(state.sandbox.commitWebpayTransaction_("TOKEN-1"), null);
  assert.deepEqual(statuses(state), ["pending", "pending"]);
  assert.equal(remoteCalls, 0);
  assert.equal(state.sandbox.reconcileWebpayTransaction_("TOKEN-1").status, "confirmed");
  assert.deepEqual(statuses(state), ["confirmed", "confirmed"]);
  assert.equal(remoteCalls, 1); // status GET confirma sin repetir el commit PUT.
  assert.deepEqual([state.lockStats.waits, state.lockStats.releases], [3, 1]);
});

test("a transient cancellation lock timeout is retried deterministically", () => {
  const state = harness({ lockTimeouts: 1 });
  assert.equal(state.sandbox.cancelWebpayTransaction_("TOKEN-1"), "cancelled");
  assert.deepEqual(statuses(state), ["cancelled", "cancelled"]);
  assert.deepEqual([state.lockStats.waits, state.lockStats.releases], [2, 1]);
});

let passed = 0;
for (const { name, operation } of tests) {
  try {
    operation();
    passed += 1;
    console.log(`ok - ${name}`);
  } catch (error) {
    console.error(`not ok - ${name}`);
    throw error;
  }
}
console.log(`Total: ${tests.length}; Passed: ${passed}; Failed: ${tests.length - passed}`);
