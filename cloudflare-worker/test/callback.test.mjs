import assert from "node:assert/strict";
import test from "node:test";
import { handleRequest } from "../src/index.mjs";

const env = {
  APPS_SCRIPT_URL: "https://apps-script.test/exec",
  WEBPAY_CALLBACK_SECRET: "test-secret"
};

function appsScriptResponse(paymentStatus = "confirmed", success = true) {
  return new Response(JSON.stringify({ success, paymentStatus }), {
    status: 200,
    headers: { "content-type": "application/json" }
  });
}

test("GET without token is a diagnostic endpoint", async () => {
  const response = await handleRequest(new Request("https://worker.test/"), env);
  assert.equal(response.status, 200);
  assert.match(await response.text(), /service available/);
});

test("GET token_ws forwards a confirmed callback without exposing credentials", async () => {
  let forwarded;
  const response = await handleRequest(
    new Request("https://worker.test/?token_ws=TOKEN-1"),
    env,
    async (url, options) => {
      forwarded = { url, options, body: JSON.parse(options.body) };
      return appsScriptResponse("confirmed");
    }
  );
  const html = await response.text();
  assert.equal(forwarded.url, env.APPS_SCRIPT_URL);
  assert.deepEqual(forwarded.body, {
    action: "processWebpayCallback",
    callbackSecret: "test-secret",
    tokenWs: "TOKEN-1",
    tbkToken: ""
  });
  assert.match(html, /Pago confirmado/);
  assert.match(html, /armatuhandroll:\/\/payment-return/);
  assert.doesNotMatch(html, /TOKEN-1|test-secret/);
});

test("POST form token_ws forwards a confirmed callback", async () => {
  let body;
  const request = new Request("https://worker.test/", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: "token_ws=TOKEN-2"
  });
  const response = await handleRequest(request, env, async (_url, options) => {
    body = JSON.parse(options.body);
    return appsScriptResponse("confirmed");
  });
  assert.equal(body.tokenWs, "TOKEN-2");
  assert.match(await response.text(), /Pago confirmado/);
});

test("TBK_TOKEN forwards cancellation", async () => {
  let body;
  const response = await handleRequest(
    new Request("https://worker.test/?TBK_TOKEN=CANCEL-1"),
    env,
    async (_url, options) => {
      body = JSON.parse(options.body);
      return appsScriptResponse("cancelled");
    }
  );
  assert.equal(body.tbkToken, "CANCEL-1");
  assert.equal(body.tokenWs, "");
  assert.match(await response.text(), /Pago cancelado/);
});

test("POST without callback token is rejected", async () => {
  const response = await handleRequest(new Request("https://worker.test/", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: "other=value"
  }), env);
  assert.equal(response.status, 400);
  assert.match(await response.text(), /Pago fallido/);
});

test("Apps Script rejection is shown as failed", async () => {
  const response = await handleRequest(
    new Request("https://worker.test/?token_ws=TOKEN-1"),
    env,
    async () => appsScriptResponse("pending", false)
  );
  assert.equal(response.status, 502);
  assert.match(await response.text(), /Pago fallido/);
});

test("invalid Apps Script response is shown as failed", async () => {
  const response = await handleRequest(
    new Request("https://worker.test/?token_ws=TOKEN-1"),
    env,
    async () => new Response("not-json")
  );
  assert.equal(response.status, 502);
  assert.match(await response.text(), /Pago fallido/);
});

test("ambiguous upstream failure remains pending", async () => {
  const response = await handleRequest(
    new Request("https://worker.test/?token_ws=TOKEN-1"),
    env,
    async () => { throw new Error("network"); }
  );
  assert.equal(response.status, 502);
  assert.match(await response.text(), /Pago pendiente/);
});
