const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

class FakeSheet {
  constructor(rows) {
    this.rows = rows.map((row) => row.slice());
    this.writes = 0;
  }
  getLastRow() { return this.rows.length; }
  getRange(row, column, rowCount = 1, columnCount = 1) {
    return {
      getValues: () => this.rows.slice(row - 1, row - 1 + rowCount)
        .map((values) => values.slice(column - 1, column - 1 + columnCount)),
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
  onWaitLock = null } = {}) {
  const transactions = new FakeSheet([
    ["id", "order", "payment", "buy", "session", "token", "status", "created", "updated", "url"],
    ["WPT-1", "ORDER-1", "PAY-1", "BUY-1", "SESSION-1", "TOKEN-1", transactionStatus, "now", "now", "https://form.test"]
  ]);
  const payments = new FakeSheet([
    ["id", "order", "date", "method", "amount", "status", "proof"],
    ...(!missingPayment ? [["PAY-1", "ORDER-1", "now", "webpay", 12500, paymentStatus, ""]] : [])
  ]);
  const orders = new FakeSheet([
    ["order", "date", "products", "quantity", "amount", "eta", "name", "status"],
    ["ORDER-1", "now", "items", 1, 12500, "", "name", "pending_review"]
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
          assert.equal(lockStats.held, false, "recursive ScriptLock acquisition");
          lockStats.held = true;
          lockStats.waits += 1;
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
