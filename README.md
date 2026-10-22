# ArmaTuHandroll

Aplicación Android del proyecto **ArmaTuHandroll**.

## Nota sobre íconos de la app

Los íconos de launcher personalizados ya están integrados en el repositorio (mipmaps y recursos XML relacionados).

- No regenerarlos manualmente salvo que sea estrictamente necesario.
- Si se actualizan, hacerlo desde Android Studio (Image Asset) y validar todas las densidades antes de commitear.

## Webpay Plus (solo integración)

El backend versionado usa `UrlFetchApp` para crear, consultar y confirmar transacciones contra la API REST de integración de Transbank. El retorno sigue el flujo `Transbank → Cloudflare Worker → Google Apps Script → Android`; las credenciales y el monto nunca proceden de la app.

Antes de publicar manualmente `apps-script/Code.gs`, configurar estas **Script Properties**:

- `TRANSBANK_COMMERCE_CODE`: código de comercio de integración.
- `TRANSBANK_API_KEY`: llave de integración (secreta).
- `TRANSBANK_ENVIRONMENT=integration`: cualquier otro valor bloquea Webpay.
- `WEBPAY_RETURN_URL`: URL HTTPS pública del Cloudflare Worker a la que retorna Transbank.
- `WEBPAY_APP_URL`: URL HTTPS del deployment de Apps Script terminada en `/exec`; se usa exclusivamente para abrir el formulario hacia Webpay.
- `WEBPAY_CALLBACK_SECRET`: secreto aleatorio compartido exclusivamente con el Worker. Debe tener exactamente el mismo valor en ambos servicios y no debe versionarse.

En Cloudflare configurar las variables del Worker sin incluir valores sensibles en Git:

- `APPS_SCRIPT_URL`: la misma URL HTTPS de Apps Script terminada en `/exec`.
- `WEBPAY_CALLBACK_SECRET`: el mismo secreto configurado como Script Property.

Ambas pueden configurarse sin versionar su valor mediante `wrangler secret put
APPS_SCRIPT_URL` y `wrangler secret put WEBPAY_CALLBACK_SECRET` desde
`cloudflare-worker/`.

El Worker admite el retorno de Transbank por GET o POST, reenvía solamente los campos necesarios al Apps Script y ofrece el enlace `armatuhandroll://payment-return` después de procesar el resultado.

Crear manualmente la hoja `WEBPAY_TRANSACTIONS` con esta fila de encabezados (A:K):

```text
transaction_id | order_number | payment_id | buy_order | session_id | token | status | created_at | updated_at | form_url | amount
```

Para migrar una hoja legacy A:J antes de publicar el script, agregar manualmente la
columna K con el encabezado exacto `amount`. No reordenar ni sobrescribir A:J. Las
filas legacy continúan siendo legibles; cuando K está vacío, el backend solo puede
recuperar el monto desde la fila `PAYMENTS` asociada o desde una respuesta Webpay
autorizada y validada. Si ninguna evidencia histórica existe, no reconstruye un
importe usando el monto actual del pedido.

No se agregan columnas a `PAYMENTS`: conserva A:G y registra Webpay con método `webpay`, estado inicial `pending` y la columna G vacía. No se almacenan PAN, CVV ni otros datos de tarjeta. La devolución/reversa de un Webpay confirmado queda fuera de este sprint y deberá implementarse antes de permitir su rechazo operativo.

## Estructura relevante

- `app/src/main/res/drawable`
- `app/src/main/res/mipmap-anydpi-v26`
- `app/src/main/res/mipmap-mdpi`
- `app/src/main/res/mipmap-hdpi`
- `app/src/main/res/mipmap-xhdpi`
- `app/src/main/res/mipmap-xxhdpi`
- `app/src/main/res/mipmap-xxxhdpi`
