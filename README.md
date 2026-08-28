# ArmaTuHandroll

Aplicación Android del proyecto **ArmaTuHandroll**.

## Nota sobre íconos de la app

Los íconos de launcher personalizados ya están integrados en el repositorio (mipmaps y recursos XML relacionados).

- No regenerarlos manualmente salvo que sea estrictamente necesario.
- Si se actualizan, hacerlo desde Android Studio (Image Asset) y validar todas las densidades antes de commitear.

## Webpay Plus (solo integración)

El backend versionado usa `UrlFetchApp` para crear, consultar y confirmar transacciones contra la API REST de integración de Transbank. Android recibe solamente el token, la URL de formulario y una URL pública del Web App que construye el POST `token_ws`; las credenciales y el monto nunca proceden de la app.

Antes de publicar manualmente `apps-script/Code.gs`, configurar estas **Script Properties**:

- `TRANSBANK_COMMERCE_CODE`: código de comercio de integración.
- `TRANSBANK_API_KEY`: llave de integración (secreta).
- `TRANSBANK_ENVIRONMENT=integration`: cualquier otro valor bloquea Webpay.
- `WEBPAY_RETURN_URL`: URL HTTPS pública del deployment `/exec`; Transbank retorna a ella y también se usa para el puente `action=openWebpay`.

Crear manualmente la hoja `WEBPAY_TRANSACTIONS` con esta fila de encabezados (A:J):

```text
transaction_id | order_number | payment_id | buy_order | session_id | token | status | created_at | updated_at | form_url
```

No se agregan columnas a `PAYMENTS`: conserva A:G y registra Webpay con método `webpay`, estado inicial `pending` y la columna G vacía. No se almacenan PAN, CVV ni otros datos de tarjeta. La devolución/reversa de un Webpay confirmado queda fuera de este sprint y deberá implementarse antes de permitir su rechazo operativo.

## Estructura relevante

- `app/src/main/res/drawable`
- `app/src/main/res/mipmap-anydpi-v26`
- `app/src/main/res/mipmap-mdpi`
- `app/src/main/res/mipmap-hdpi`
- `app/src/main/res/mipmap-xhdpi`
- `app/src/main/res/mipmap-xxhdpi`
- `app/src/main/res/mipmap-xxxhdpi`
