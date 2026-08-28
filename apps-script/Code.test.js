const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

const sandbox = { console };
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(`${__dirname}/Code.gs`, "utf8"), sandbox);

let captured;
sandbox.UrlFetchApp = {
  fetch(url, options) {
    captured = { url, options };
    return { getResponseCode: () => 200, getContentText: () => '{"token":"token","url":"https://example.test"}' };
  }
};
const config = { commerceCode: "commerce", apiKey: "secret" };
const fetchResult = sandbox.webpayFetch_("https://api.test/transactions", "post", { amount: 12500 }, config);
assert.equal(fetchResult.code, 200);
assert.equal(captured.options.headers["Tbk-Api-Key-Id"], "commerce");
assert.equal(captured.options.headers["Tbk-Api-Key-Secret"], "secret");
assert.deepEqual(JSON.parse(captured.options.payload), { amount: 12500 });

const transaction = {
  status: "pending", amount: 12500, buyOrder: "PED-1-abc", sessionId: "WP-session"
};
let persistedStatus;
sandbox.findWebpayByToken_ = () => transaction;
sandbox.getWebpayConfig_ = () => config;
sandbox.updateWebpayResult_ = (_transaction, status) => { persistedStatus = status; };

function commitWith(overrides) {
  sandbox.webpayFetch_ = () => ({
    code: 200,
    body: Object.assign({
      status: "AUTHORIZED", response_code: 0, amount: 12500,
      buy_order: "PED-1-abc", session_id: "WP-session",
      authorization_code: "AUTH", payment_type_code: "VD"
    }, overrides)
  });
  persistedStatus = undefined;
  return sandbox.commitWebpayTransaction_("token");
}

function commitResponse(response) {
  sandbox.webpayFetch_ = () => response;
  persistedStatus = undefined;
  return sandbox.commitWebpayTransaction_("token");
}

assert.equal(commitWith({}), true);
assert.equal(persistedStatus, "confirmed");
assert.equal(commitWith({ amount: 1 }), false);
assert.equal(persistedStatus, "failed");
assert.equal(commitWith({ response_code: -1 }), false);
assert.equal(persistedStatus, "failed");
assert.equal(commitWith({ status: "FAILED" }), false);
assert.equal(persistedStatus, "failed");
assert.equal(commitResponse({ code: 500, body: {} }), false);
assert.equal(persistedStatus, undefined);
sandbox.webpayFetch_ = () => { throw new Error("timeout"); };
persistedStatus = undefined;
assert.equal(sandbox.commitWebpayTransaction_("token"), false);
assert.equal(persistedStatus, undefined);

let cancellationUpdate;
sandbox.findWebpayByToken_ = () => ({ status: "confirmed" });
sandbox.updateWebpayResult_ = (_transaction, status) => { cancellationUpdate = status; };
sandbox.webpayResultPage_ = message => message;
assert.equal(sandbox.handleWebpayReturn_({ TBK_TOKEN: "token" }), "Pago realizado correctamente");
assert.equal(cancellationUpdate, undefined);

sandbox.findWebpayByToken_ = () => ({ status: "pending" });
assert.equal(sandbox.handleWebpayReturn_({ TBK_TOKEN: "token" }), "Pago cancelado");
assert.equal(cancellationUpdate, "cancelled");

const paymentsSheet = {
  getLastRow: () => 2,
  getRange: () => ({ getValues: () => [["PAY-1", "PED-1", "date", "webpay", 12500, "pending"]] })
};
const webpaySheet = {
  getLastRow: () => 2,
  getRange: () => ({
    getValues: () => [[
      "WPT-1", "PED-1", "PAY-1", "PED-1-abc", "WP-session",
      "token", "pending", "created", "updated", "https://webpay.test/form"
    ]]
  })
};
const active = sandbox.findActiveWebpayInSheet_(webpaySheet, "PED-1", paymentsSheet);
assert.equal(active.token, "token");
assert.equal(active.amount, 12500);
assert.equal(active.formUrl, "https://webpay.test/form");

console.log("Webpay backend tests passed");
