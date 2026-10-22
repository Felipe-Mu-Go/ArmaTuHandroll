const APP_RETURN_URL = "armatuhandroll://payment-return";

export default {
  fetch(request, env) {
    return handleRequest(request, env);
  }
};

export async function handleRequest(request, env, fetchImpl = fetch) {
  const url = new URL(request.url);
  const fields = request.method === "POST"
    ? await readFormFields(request)
    : url.searchParams;
  const tokenWs = normalizedField(fields, "token_ws");
  const tbkToken = normalizedField(fields, "TBK_TOKEN");

  if (request.method === "GET" && !tokenWs && !tbkToken) {
    return new Response("Webpay callback service available", {
      status: 200,
      headers: { "content-type": "text/plain; charset=UTF-8" }
    });
  }
  if (request.method !== "GET" && request.method !== "POST") {
    return resultPage("failed", 405);
  }
  if (!tokenWs && !tbkToken) return resultPage("failed", 400);
  if (!env?.APPS_SCRIPT_URL || !env?.WEBPAY_CALLBACK_SECRET) {
    return resultPage("failed", 503);
  }

  let response;
  try {
    response = await fetchImpl(env.APPS_SCRIPT_URL, {
      method: "POST",
      headers: { "content-type": "application/json; charset=UTF-8" },
      body: JSON.stringify({
        action: "processWebpayCallback",
        callbackSecret: env.WEBPAY_CALLBACK_SECRET,
        tokenWs,
        tbkToken
      })
    });
  } catch {
    return resultPage("pending", 502);
  }

  if (!response.ok) return resultPage("failed", 502);
  let payload;
  try {
    payload = await response.json();
  } catch {
    return resultPage("failed", 502);
  }
  if (!payload || payload.success !== true) return resultPage("failed", 502);
  return resultPage(normalizePaymentStatus(payload.paymentStatus), 200);
}

async function readFormFields(request) {
  const contentType = request.headers.get("content-type") || "";
  const normalizedType = contentType.toLowerCase();
  if (normalizedType.includes("application/x-www-form-urlencoded")) {
    return new URLSearchParams(await request.text());
  }
  if (normalizedType.includes("multipart/form-data")) return request.formData();
  return new URLSearchParams();
}

function normalizedField(fields, name) {
  return String(fields.get(name) || "").trim();
}

function normalizePaymentStatus(status) {
  const normalized = String(status || "").trim().toLowerCase();
  if (normalized === "confirmed") return "confirmed";
  if (normalized === "cancelled") return "cancelled";
  if (["failed", "reversed", "nullified"].includes(normalized)) return "failed";
  return "pending";
}

function resultPage(status, httpStatus) {
  const copy = {
    confirmed: ["Pago confirmado", "Tu pago fue confirmado correctamente."],
    pending: ["Pago pendiente", "Aún estamos verificando el resultado de tu pago."],
    cancelled: ["Pago cancelado", "El pago fue cancelado y puedes intentarlo nuevamente."],
    failed: ["Pago fallido", "No fue posible completar el pago."]
  }[status];
  const html = `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${copy[0]}</title></head><body><main><h1>${copy[0]}</h1><p>${copy[1]}</p><p><a href="${APP_RETURN_URL}">Volver a ArmaTuHandroll</a></p></main></body></html>`;
  return new Response(html, {
    status: httpStatus,
    headers: {
      "content-type": "text/html; charset=UTF-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff"
    }
  });
}
