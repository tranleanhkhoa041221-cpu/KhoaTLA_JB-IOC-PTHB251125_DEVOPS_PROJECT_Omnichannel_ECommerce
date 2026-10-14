# 7. Test THẬT: Email, SMS, VNPay sandbox (v19)

> Chạy SAU lượt 3 của docs/05 và TRƯỚC lượt 4 (Cleanup, vì Cleanup xóa địa chỉ mặc định nên không đặt đơn mới được nữa) — xem mục E-REAL trong docs/05. Mọi bước "VPS" chạy sau `ssh root@103.74.101.153` + `cd /opt/quickbite`; bước "Postman/trình duyệt" chạy trên máy cá nhân.
> Chưa có bằng chứng thực tế ⇒ KHÔNG tick PASS. Thiếu credential ⇒ ghi **BLOCKED/SKIP**.
> Mật khẩu/secret **không dán vào chat, không commit git**.

## 0. Thay đổi code/config v19 liên quan
- **VNPay giờ GMT+7**: `VnPayUtils.nowVn()` dùng `Asia/Ho_Chi_Minh` cho `vnp_CreateDate`/`vnp_ExpireDate`. Trước đó dùng múi giờ JVM (container = UTC) ⇒ VNPay sandbox báo hết hạn/sai giờ. Cần **build lại + deploy lại image payment-billing-service** (push `main` hoặc `push-images-local.sh`).
- Postman: #9 (profile), #52 (EMAIL), #53 (SMS) dùng biến `realEmail`, `realPhone`, `realPhoneE164`. Notification API là **ADMIN-only** nên dùng `adminToken`.

## 1. EMAIL THẬT (Gmail SMTP)
1. Tài khoản Gmail bật **Xác minh 2 bước** → https://myaccount.google.com/apppasswords → tạo App Password (16 ký tự, bỏ dấu cách). Thu hồi App Password cũ từng lộ.
2. **VPS** sửa `/opt/quickbite/.env`:
   ```
   NOTIFICATION_MAIL_ENABLED=true
   MAIL_USERNAME=<gmail-cua-ban>@gmail.com
   MAIL_PASSWORD=<app-password-16-ky-tu>
   ```
3. **VPS**: `docker compose up -d notification-service` (chờ healthy).
4. **Postman**: đặt collection variable `realEmail` = hộp thư BẠN đọc được (ví dụ chính Gmail đó). Chạy #4 (login customer) → #9 (cập nhật profile sang realEmail) → #5 (login admin) → #52 (EMAIL test).
5. Expected: #52 → 201, status `SENT`; hộp thư nhận mail (kiểm cả Spam). **VPS**: `docker logs --tail 50 notification-service | grep EMAIL` → `[EMAIL-SMTP] Da gui toi <email>`. Nếu thấy `[EMAIL-DEV]` ⇒ mail vẫn đang TẮT (env chưa nạp, chưa restart).
6. Lỗi thường gặp: `535 Username and Password not accepted` ⇒ sai App Password / chưa bật 2FA; status `FAILED` + `error_message` trong `select id,channel,status,left(error_message,80) from notification_logs order by id desc limit 3` (DB `notification_db`).
7. Luồng sự kiện thật: tạo đơn mới (#39 hoặc #72) bằng customer đã đổi email ở #9 ⇒ email `ORDER_CREATED` đến realEmail (Notification lấy contact từ Identity qua gRPC `GetUserContact`, tức email trong profile).
PASS khi: có mail trong hộp thư + log `[EMAIL-SMTP]` + `notification_logs.status='SENT'`.

## 2. SMS THẬT (Twilio)
1. https://console.twilio.com → lấy `Account SID`, `Auth Token`, số gửi (`From`). Tài khoản **trial**: chỉ gửi tới số đã **Verified Caller ID** (Console → Phone Numbers → Verified Caller IDs → thêm số điện thoại của bạn, nhập mã xác minh).
2. Console → Messaging → Settings → **Geo permissions** → bật **Vietnam** (nếu không: lỗi 21408).
3. **VPS** `.env`:
   ```
   NOTIFICATION_SMS_ENABLED=true
   TWILIO_ACCOUNT_SID=<ACxxxxxxxx>
   TWILIO_AUTH_TOKEN=<token-moi>
   TWILIO_FROM_NUMBER=<+1xxxxxxxxxx>
   ```
   rồi `docker compose up -d notification-service`.
4. **Postman**: `realPhone` = số của bạn dạng nội địa (vd `0912345678`), `realPhoneE164` = `+84912345678` (bỏ số 0 đầu). Chạy #9 (profile) → #53 (SMS test, recipient = realPhoneE164).
5. Expected: #53 → 201; điện thoại nhận SMS (trial có tiền tố "Sent from your Twilio trial account"). **VPS**: `docker logs --tail 50 notification-service | grep SMS` → `[SMS-TWILIO] Da gui toi … | sid=SM… | status=queued`. `[SMS-DEV]` ⇒ SMS vẫn tắt.
6. Lỗi: `21608` (số chưa verify, trial), `21211` (số sai định dạng), `21408` (chưa bật geo Vietnam), `20003` (sai SID/token) — thấy trong `notification_logs.error_message`.
PASS khi: SMS đến máy + log `[SMS-TWILIO]` + status `SENT`.

## 3. VNPAY SANDBOX (thẻ test)
### 3.1 Chuẩn bị
1. Đăng ký merchant sandbox: https://sandbox.vnpayment.vn/devreg → email xác nhận sẽ cho **Terminal ID (vnp_TmnCode)** và **Secret Key (vnp_HashSecret)**.
2. Đăng nhập Merchant Admin sandbox (link trong email) → cấu hình **IPN URL** = `https://khoatla-ecommerce.duckdns.org/api/payments/vnpay/ipn` (HTTPS public, VNPay gọi bằng GET).
3. **VPS** `.env`:
   ```
   VNPAY_TMN_CODE=<TmnCode>
   VNPAY_HASH_SECRET=<HashSecret>
   VNPAY_RETURN_URL=https://khoatla-ecommerce.duckdns.org/api/payments/vnpay/return
   VNPAY_IPN_URL=https://khoatla-ecommerce.duckdns.org/api/payments/vnpay/ipn
   VNPAY_IPN_ALLOWED_IPS=
   ```
   (để trống whitelist trước; nếu log báo `IP … không thuộc danh sách` thì xem 3.4). Deploy bản v19 (đã sửa GMT+7) rồi `docker compose up -d payment-billing-service`.
4. Kiểm tra: `curl -s https://khoatla-ecommerce.duckdns.org/health` → UP.

### 3.2 Thanh toán THÀNH CÔNG
1. Postman: #4 (login) → … tạo đơn mới bằng #39 (hoặc dùng `orderId` hiện có ở trạng thái PENDING) → #43 (chờ Payment PENDING) → **#44** `POST /api/payments/order/{{orderId}}/vnpay-url` ⇒ 200, `data.paymentUrl`.
2. Copy `paymentUrl` → mở bằng trình duyệt → chọn **Thẻ nội địa NCB** → nhập:
   - Số thẻ `9704198526191432198`
   - Tên chủ thẻ `NGUYEN VAN A`
   - Ngày phát hành `07/15`
   - OTP `123456`
3. Kết quả: trình duyệt bị redirect về `…/api/payments/vnpay/return?...vnp_ResponseCode=00…` (JSON/trang kết quả).
4. Verify (VPS), chờ ≤ 1 phút:
   - `docker logs --tail 60 payment-billing-service | grep -i -E "ipn|vnpay"` → thấy IPN từ VNPay, xử lý `RspCode=00`.
   - `docker exec postgres psql -U postgres -d payment_billing_db -c "select id,order_id,status,vnp_transaction_no from payments order by id desc limit 1"` → `SUCCESS`.
   - `docker exec postgres psql -U postgres -d order_inventory_db -c "select id,status from orders order by id desc limit 1"` → `CONFIRMED`; tồn kho: reserved giảm (commit), available không hoàn.
   - Notification: `select notification_type,channel,status from notification_logs where order_id=<orderId>` → có `PAYMENT_COMPLETED` (và nhận email/SMS thật nếu đã bật ở mục 1–2).
   - Postman #45 (GET payment) → `SUCCESS`; #49 logs có bản ghi IPN/return.

### 3.3 Thanh toán THẤT BẠI / Saga bù trừ
Tạo đơn khác + #43 + #44 → mở URL → ở trang NCB bấm **Hủy giao dịch**, hoặc chọn các tình huống thẻ lỗi do VNPay công bố (thẻ không đủ số dư / thẻ chưa kích hoạt / thẻ bị khóa — lấy đúng số thẻ ở https://sandbox.vnpayment.vn/apis/vnpay-demo/ phần "Thông tin thẻ test").
Expected: payment `FAILED`, outbox `payment.failed`, đơn `CANCELLED`, `available` hoàn lại (Saga compensation).

### 3.4 IPN bị chặn do whitelist IP
Nếu `docker logs payment-billing-service` có `IP ... không thuộc danh sách` và payment vẫn PENDING: lấy IP nguồn trong log, thêm vào `.env` `VNPAY_IPN_ALLOWED_IPS=<ip1>,<ip2>` (nhiều IP cách nhau dấu phẩy), `up -d payment-billing-service`, rồi dùng portal VNPay gửi lại IPN hoặc tạo đơn mới. (Return URL của project vẫn tự cập nhật trạng thái khi bạn quay về từ trình duyệt.) Nếu muốn nhanh khi test, `*` tạm thời và **xoá lại sau test**.

### 3.5 Query / Refund
- **#47** `GET /api/payments/{id}/query` (gọi VNPay `querydr`) → 200 khi cấu hình đúng; payment đã SUCCESS.
- **Refund** (`POST /api/payments/{id}/refund`, ADMIN): chỉ thử với payment SUCCESS thật. Sandbox đôi khi từ chối refund tuỳ merchant — ghi nguyên mã trả về, không bịa PASS.
- Sau refund thành công, luồng sự kiện chuẩn `payment.refunded` (Payment → Outbox → Kafka) phải chạy: **Order** đổi `REFUNDED` (hoàn đủ) hoặc `PARTIALLY_REFUNDED` (hoàn 1 phần) — KHÔNG tự cộng kho; **Notification** gửi email/SMS `PAYMENT_REFUNDED` cho khách. Kiểm tra theo cột Verify của R12 (docs/05). Cần deploy bản có V3 (order-inventory) + V2 (notification) Flyway.
- Request #48 (refund khi payment chưa SUCCESS) vẫn phải trả **400**.

### 3.6 BLOCKED/SKIP
| Thiếu | Hệ quả | Ghi kết quả |
|---|---|---|
| `VNPAY_HASH_SECRET` | #44 trả 500 | BLOCKED |
| Merchant portal chưa đặt IPN URL | IPN không về, chỉ có return | PARTIAL |
| Gmail App Password | #52 FAILED/`EMAIL-DEV` | BLOCKED (email thật) |
| Twilio chưa verify số / geo | #53 FAILED | BLOCKED (SMS thật) |

## 4. Dọn dẹp sau test thật
```bash
# VPS: tắt gửi thật nếu không cần nữa, xoá whitelist '*'
sed -i 's/^NOTIFICATION_MAIL_ENABLED=.*/NOTIFICATION_MAIL_ENABLED=false/; s/^NOTIFICATION_SMS_ENABLED=.*/NOTIFICATION_SMS_ENABLED=false/' .env
docker compose up -d notification-service payment-billing-service
```
Đổi lại `realEmail/realPhone` về mặc định (hoặc để nguyên nếu vẫn dùng).
