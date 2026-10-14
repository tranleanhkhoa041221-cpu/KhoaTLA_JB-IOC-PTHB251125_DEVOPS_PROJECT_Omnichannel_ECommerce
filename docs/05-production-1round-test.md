# 5. Production 1-round test (máy cá nhân → `{{baseUrl}}` production)

> **Trạng thái:** tài liệu được sinh từ ĐÚNG `ecommerce_omnichannel_postman_collection.json` (101 request, không tạo collection mới). Cột **PASS** để ☐ — chỉ tick sau khi bạn chạy thật và đối chiếu. Tôi không SSH được VPS nên **chưa có bằng chứng PASS nào**.
> Chạy **từ máy cá nhân** (Postman). Mọi lệnh `docker …` ở cột Verify chạy **TRÊN VPS** (`ssh root@103.74.101.153`, `cd /opt/quickbite`).
> Phần test THẬT (email/SMS/VNPay sandbox) xem `docs/07-v19-real-email-sms-vnpay.md` — làm SAU khi vòng test cơ bản xanh.

## 0. CÀI POSTMAN TRÊN MÁY CÁ NHÂN (làm 1 lần)
1. Cài Postman Desktop. **Import** 2 file trong zip: `ecommerce_omnichannel_postman_collection.json` và `postman_environment_production.json`.
2. Góc phải trên chọn Environment **`QuickBite — production`** → sửa cột *Current value*:
   - `baseUrl` = `https://khoatla-ecommerce.duckdns.org` (không có `/` cuối)
   - `adminUsername` = `admin`
   - `adminPassword` = giá trị `ADMIN_DEFAULT_PASSWORD` trong `/opt/quickbite/.env` trên VPS (`grep ADMIN_DEFAULT_PASSWORD .env`)
   - `vnpHashSecret` = để trống (chỉ cần khi chạy folder IPN giả V1–V15; xem mục A)
   - `healthPath` = `/health`
   Bấm **Save** (Ctrl+S).
3. Kiểm tra nhanh: mở request #1 "Gateway Health" → **Send** → `200 {"status":"UP"}`. Không được thì chưa deploy xong (docs/DEPLOY-GUIDE).
4. **Collection Runner** (chuột phải collection → Run): chọn environment production, tick *Save responses*, **Delay = 300 ms**, bỏ tick *Persist responses for a session* nếu máy yếu; *Keep variable values* = BẬT. Chọn request theo lượt (mục E) bằng cách tick/bỏ tick.
5. Mỗi request đã có sẵn script Tests tự kiểm tra status và tự lưu ID/token vào **collection variables** (xem mục D). Xem kết quả ở tab *Test Results*; request đỏ = FAIL, copy lỗi gửi mình.
6. Biến test thật (chỉ khi test email/SMS thật, mục E-REAL): vào Collection → tab *Variables* sửa `realEmail`, `realPhone`, `realPhoneE164`.

## 0b. BẢNG PHỦ TEST (đối chiếu đề → request #)
| Yêu cầu | Request # | Ghi chú |
|---|---|---|
| Register CUSTOMER / SELLER | 2, 3 (và 59 customer B) | |
| Duplicate username / email / phone | 61, 62, 63 → 409 | |
| Login / Refresh / Validate JWT | 4, 5 / 6 / 7 | |
| RBAC | 77 (CUSTOMER tạo Brand → 403), 87 (CUSTOMER refund → 403), 52–58 (Notification chỉ ADMIN), 98 (ADMIN đổi role) | |
| 401 / 403 | 14, 75, 76 → 401; 77, 64, 65, 87 → 403 | |
| Brand / Category / Product / SKU / Search / CRUD | 15–19 / 20–27 / 28–34 (31 = search, 32 = variant theo SKU, 33 = sellable-check) / xóa: 99–101 | |
| Inventory initialize / sellable / list | 35 / 33 / 37, 38; restock 36 | |
| Inventory **reserve / release** | không có endpoint public: reserve xảy ra khi **tạo đơn** (#39, kiểm bằng `inventories.reserved_qty`), release khi **hủy đơn** (#74, #97) hoặc payment FAILED (#92–#93) | verify bằng SQL ở cột Verify |
| Insufficient stock | 66 (409), 67 (kho không đổi) | |
| Order create / get / list | 39 / 40, 41 / 42 | |
| Idempotency + duplicate | 72, 73 (cùng `idempotencyKey` → cùng orderId) | |
| Cancel | 74, 97 | |
| Ownership | 64, 65 → 403 | |
| Payment create (PENDING từ event) | 43 | |
| VNPay create URL / thanh toán thật | 44 + E-REAL | cần credential |
| IPN / return | 50, 51, 84, 86, 91, 95 (giả lập có ký HMAC); IPN thật: E-REAL | |
| Query / Refund | 47 / 48 (400 khi chưa SUCCESS), 87, 94; refund thành công: E-REAL | |
| Payment authorization | 87 (403), 45/46 chỉ chủ sở hữu | |
| Notification | 52–58; email/SMS thật: E-REAL | |
| Order → Outbox → Kafka → Payment | 39 → 43; hạ tầng F1 | |
| Order → Outbox → Kafka → Notification | 39 → 58; hạ tầng F2 | |
| Notification STOP/START | 69, 70, 71 (+ VPS stop/start) | |
| Kafka restart / consumer restart / duplicate event | F5 / F6 / F7 | chạy trên VPS |
| Saga compensation | 74, 89–93 (payment FAILED → hủy đơn → hoàn kho) | |


## A. ENV CHECK theo nhóm API
| Nhóm API | Cần `.env` VPS? | Biến / giá trị | Restart |
|---|---|---|---|
| Identity (register/login/refresh/validate/RBAC/401/403) | Không sửa thêm | đã có từ lúc dựng: `JWT_SECRET_KEY`, `ADMIN_DEFAULT_PASSWORD`, `DB_PASS_*`, `INTERNAL_API_KEY` | — |
| Product/Catalog, Inventory | Không | — | — |
| Order (create/idempotency/cancel/ownership) + Outbox→Kafka | Không | Kafka KRaft nội bộ; `USE_REDIS_LOCK=true` mặc định | — |
| Payment: tạo Payment PENDING, get, logs, refund bị từ chối khi chưa SUCCESS | Không | — | — |
| Payment: `vnpay-url` 200, redirect thật, IPN thật, query, refund thành công | **Có** | `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL=https://khoatla-ecommerce.duckdns.org/api/payments/vnpay/return`, `VNPAY_IPN_URL=https://khoatla-ecommerce.duckdns.org/api/payments/vnpay/ipn` | `docker compose up -d payment-billing-service` |
| Payment: IPN giả (folder V1–V15, ký HMAC bằng `vnpHashSecret`) | Có (tạm) | `VNPAY_HASH_SECRET` + `VNPAY_IPN_ALLOWED_IPS=*` (xoá sau test) | như trên |
| Notification: log/Kafka consumer (mail/SMS tắt) | Không | `NOTIFICATION_MAIL_ENABLED=false`, `NOTIFICATION_SMS_ENABLED=false` (mặc định) | — |
| Notification: gửi EMAIL thật | **Có** | `NOTIFICATION_MAIL_ENABLED=true`, `MAIL_USERNAME`, `MAIL_PASSWORD` (App Password mới) | `… up -d notification-service` |
| Notification: gửi SMS thật | **Có** | `NOTIFICATION_SMS_ENABLED=true`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | `… up -d notification-service` |
**Thiếu credential ⇒ ghi `BLOCKED/SKIP`, không bịa, không tick PASS.**

## B. `.env` (VPS) khác Postman Environment (máy cá nhân)
| | `.env` trên VPS | Postman Environment `QuickBite — production` |
|---|---|---|
| Chứa | secret hạ tầng (DB, JWT, INTERNAL_API_KEY, VNPAY, MAIL, TWILIO) | `baseUrl=https://khoatla-ecommerce.duckdns.org`, `adminUsername=admin`, `adminPassword=<= ADMIN_DEFAULT_PASSWORD>`, `vnpHashSecret=<= VNPAY_HASH_SECRET>` (chỉ cho IPN giả) |
| Collection variable (nhập khi test thật) | — | `realEmail`, `realPhone` (0xxxxxxxxx), `realPhoneE164` (+84xxxxxxxxx). Mặc định = `customer01updated@example.com`, `0912345679`, `+84912345678` |
| Commit git | KHÔNG | KHÔNG |
Token/ID là collection variables tự sinh từ response — không nhập tay.

## C. DATA SHEET (copy-paste; đã nằm sẵn trong body Postman)
| Đối tượng | Dữ liệu |
|---|---|
| Customer A | `testcustomer01` / `Password123!` / `customer01@example.com` / `Nguyen Van A` / `0912345678` / CUSTOMER → #9 đổi sang `{{realEmail}}` / `{{realPhone}}` |
| Seller | `testseller01` / `Password123!` / `seller01@example.com` / `Tran Thi B` / `0987654321` / SELLER |
| Customer B | `testcustomer02` / `Password123!` / `customer02@example.com` / `0912345680` |
| Admin | `admin` / `ADMIN_DEFAULT_PASSWORD` |
| Địa chỉ | `Nguyen Van A`, `0912345678`, `TP Ho Chi Minh`, `Quan 1`, `Phuong Ben Nghe`, `123 Nguyen Hue`, isDefault `true` |
| Brand / Category | `Nike` / `Giày Thể Thao` (con: `Giày Chạy Bộ`) |
| Product / SKU | `Nike Air Max 270`, basePrice `2500000`, SKU `NIKE-AM270-BLK-42` |
| Tồn kho | init `100`, restock `+50` |
| Idempotency | `fixed-key-demo-0001` |
Chạy lần 2: register trả **409**, Brand/Product trùng có thể 409 → chạy folder Cleanup rồi chạy lại.

## D. ID / Token flow (tự sinh bởi test script)
| Biến | Gán ở request | Dùng ở |
|---|---|---|
| `customerToken` | #4 — 3. [PUBLIC] Đăng nhập CUSTOMER → lưu customerToken/userId | #8; #9; #10; #11; #12; #13; #39; #40; #41; #42; #43; #44; #45; #46; #47; #49; #66; #69; #72; #73; #74; #77; #80; #82; #8 |
| `accessToken` | #4 — 3. [PUBLIC] Đăng nhập CUSTOMER → lưu customerToken/userId | #7 |
| `refreshToken` | #4 — 3. [PUBLIC] Đăng nhập CUSTOMER → lưu customerToken/userId | #6 |
| `userId` | #4 — 3. [PUBLIC] Đăng nhập CUSTOMER → lưu customerToken/userId | #39; #66; #69; #72; #73; #80; #82; #89; #98 |
| `adminToken` | #5 — 3b. [PUBLIC] Đăng nhập ADMIN → lưu adminToken | #15; #16; #20; #21; #27; #28; #34; #35; #36; #37; #38; #48; #52; #53; #54; #55; #56; #57; #58; #67; #68; #70; #71; #79;  |
| `adminUserId` | #5 — 3b. [PUBLIC] Đăng nhập ADMIN → lưu adminToken | — |
| `addressId` | #11 — 9. Thêm địa chỉ → lưu addressId | #12; #13; #96 |
| `brandId` | #15 — 1. [ADMIN] Tạo Brand → lưu brandId | #16; #17; #28; #34; #101 |
| `categoryId` | #20 — 1. [ADMIN] Tạo Category gốc → lưu categoryId | #21; #22; #25; #27; #28; #31; #34; #100 |
| `productId` | #28 — 1. [ADMIN/SELLER] Tạo Product với variants → lưu productId, skuId | #29; #34; #99 |
| `skuId` | #28 — 1. [ADMIN/SELLER] Tạo Product với variants → lưu productId, skuId | #32; #33; #35; #36; #37; #39; #66; #67; #68; #69; #72; #73; #81; #82; #88; #89; #93 |
| `orderId` | #39 — 1. [CUSTOMER] Tạo đơn hàng → lưu orderId | #40; #43; #44; #58; #64; #65; #97 |
| `orderCode` | #39 — 1. [CUSTOMER] Tạo đơn hàng → lưu orderId | #41 |
| `paymentId` | #43 — 1. [AUTH] Xem Payment theo orderId (tự chờ Kafka tạo Payment) → lưu paymentId, vnpTxnRef | #45; #47; #48; #49 |
| `vnpTxnRef` | #43 — 1. [AUTH] Xem Payment theo orderId (tự chờ Kafka tạo Payment) → lưu paymentId, vnpTxnRef | #95 |
| `vnpAmount100` | #43 — 1. [AUTH] Xem Payment theo orderId (tự chờ Kafka tạo Payment) → lưu paymentId, vnpTxnRef | — |
| `notificationId` | #52 — 1. [ADMIN] Gửi test notification EMAIL | #55 |
| `customerBToken` | #60 — 2. Đăng nhập CUSTOMER B → customerBToken | #64; #65 |
| `userBId` | #60 — 2. Đăng nhập CUSTOMER B → customerBToken | — |
| `stockBefore` | #68 — 1. Kiểm tra tồn kho trước test | — |
| `offlineOrderId` | #69 — 2. [Sau khi TẮT notification-service] Đặt hàng vẫn thành công (201) | #71 |
| `idemOrderId` | #72 — 5. Idempotency: gửi lại cùng idempotencyKey → trả đơn cũ | #74 |
| `vnpStockBefore` | #81 — V1. [ADMIN] Ghi tồn kho trước khi đặt đơn A | — |
| `vnpOrderA` | #82 — V2. [CUSTOMER] Tạo đơn A (sẽ thanh toán THÀNH CÔNG) | #83; #85 |
| `vnpPaymentA` | #83 — V3. Chờ Kafka tạo Payment cho đơn A (tự retry) → lưu vnpTxnRef | #87 |
| `vnpStockMid` | #88 — V8. [ADMIN] Ghi tồn kho sau đơn A (trước đơn B) | — |
| `vnpOrderB` | #89 — V9. [CUSTOMER] Tạo đơn B (sẽ thanh toán THẤT BẠI) | #90; #92 |
| `vnpPaymentB` | #90 — V10. Chờ Kafka tạo Payment cho đơn B (tự retry) → lưu vnpTxnRef | #94 |

## E. KẾ HOẠCH CHẠY (4 lượt, xen kẽ với VPS)
| Lượt | Request | Việc ở VPS trước/sau |
|---|---|---|
| 1 | #1 → **#68** | — |
| 2 | **#69 → #70** | **Trước:** `cd /opt/quickbite && docker compose stop notification-service` |
| 3 | **#71 → #95** | **Trước:** `docker compose start notification-service`, chờ `healthy` (`docker compose ps`) |
| E-REAL | xem dưới | tuỳ chọn, cần credential — làm **TRƯỚC Cleanup** |
| 4 | **#96 → #101** (Cleanup) | — |
Giữ nguyên *Keep variable values* giữa các lượt (đừng Reset biến).

### E-REAL — Email thật, SMS thật, VNPay sandbox thật (chạy giữa lượt 3 và lượt 4)
Lý do đặt ở đây: (a) Cleanup #96 xóa địa chỉ mặc định nên **sau Cleanup không đặt được đơn mới**; (b) #48 phải chạy khi payment còn PENDING (đã xong ở lượt 3) — nếu trả tiền thật trước #48 thì #48 không còn là 400.
Điều kiện: đã làm credential theo `docs/07-v19-real-email-sms-vnpay.md` (VPS `.env` + restart service; Postman `realEmail/realPhone/realPhoneE164`). Thiếu phần nào ⇒ **BLOCKED** đúng phần đó, bỏ qua.
| Bước | Request | Làm gì | Expected | Verify (VPS) |
|---|---|---|---|---|
| R1 | #4 | login CUSTOMER lấy lại token | 200 | — |
| R2 | #9 | cập nhật profile → email = `{{realEmail}}`, phone = `{{realPhone}}` | 200 | `psql identity_user_db -c "select email,phone from users where username='testcustomer01'"` |
| R3 | #5 | login ADMIN | 200 | — |
| R4 | #52 | EMAIL test tới `{{realEmail}}` | 201 `SENT`; **hộp thư nhận mail** | `docker logs --tail 30 notification-service \| grep EMAIL` → `[EMAIL-SMTP] Da gui toi` |
| R5 | #53 | SMS test tới `{{realPhoneE164}}` | 201; **điện thoại nhận SMS** | `... \| grep SMS` → `[SMS-TWILIO] Da gui toi` |
| R6 | #39 | tạo đơn MỚI (`orderId` mới) | 201 PENDING; email + SMS `ORDER_CREATED` đến máy bạn | `select notification_type,channel,status from notification_logs where order_id=<orderId>` |
| R7 | #43 | chờ Payment PENDING | 200 | `payments` có `order_id` |
| R8 | #44 | lấy `data.paymentUrl` | 200 + URL | — |
| R9 | trình duyệt | mở `paymentUrl` → NCB → thẻ `9704198526191432198` / `NGUYEN VAN A` / `07/15` / OTP `123456` | redirect về `/api/payments/vnpay/return` với `vnp_ResponseCode=00` | `docker logs --tail 60 payment-billing-service \| grep -i ipn` |
| R10 | #45, #49 | xem payment + logs | `SUCCESS`; có log IPN/return | `payments.status='SUCCESS'`; đơn `CONFIRMED`; outbox `payment.completed`; notification `PAYMENT_COMPLETED` (cũng gửi email/SMS thật) |
| R11 | #47 | query VNPay | 200 | — |
| R12 | refund (tạo request mới trong Postman bằng cách Duplicate #48) | `POST /api/payments/{{paymentId}}/refund`, Bearer `{{adminToken}}`, body `{"amount": <đúng data.amount của payment ở R10>, "reason": "Test hoan tien sandbox", "createBy": "admin"}` | 200 (sandbox có thể từ chối — ghi nguyên mã trả về) | Payment: `select status,refunded_amount from payments where id=<paymentId>` → REFUNDED (hoàn đủ) hoặc SUCCESS + refunded_amount>0 (hoàn 1 phần). Outbox: `select topic,status from outbox_events where topic='payment.refunded' order by id desc limit 1` → PUBLISHED. Order (vài giây sau): `GET /api/orders/{orderId}` → **REFUNDED** (hoàn đủ) / **PARTIALLY_REFUNDED**; kho KHÔNG đổi. Notification: `GET /api/notifications/logs?orderId={orderId}` → có `PAYMENT_REFUNDED` (EMAIL/SMS/PUSH); email/SMS thật tới `realEmail`/`realPhone` |
| R13 | thẻ lỗi | lặp R6–R9 với đơn mới, ở trang NCB bấm Hủy | payment `FAILED`, đơn `CANCELLED`, kho hoàn | `orders`, `inventories`, outbox `payment.failed` |
Chi tiết từng bước + xử lý lỗi (`535`, `21608`, IPN bị chặn IP...) ở `docs/07`.
Sau E-REAL, ở lượt 4: **#97 (hủy đơn) bỏ tick nếu `orderId` hiện tại là đơn đã thanh toán thật** (đánh dấu SKIP); các request còn lại chạy bình thường.


## F. Từng request

#### 🏥 Health Checks

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 1 | GET | `{{healthPath}}`<br>_Gateway Health_ | — | — | — | — | 200 `{"status":"UP"}` | — | ☐ |

#### 👤 Identity & User

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 2 | POST | `/api/users/register`<br>_1. [PUBLIC] Đăng ký tài khoản CUSTOMER_ | —; `Content-Type: application/json` | `{ "username": "testcustomer01", "password": "Password123!", "email": "customer01@example.com", "fullName": "Nguyen Van A", "phone": "0912345678", "role": "CUSTOMER" }` | — | — | 201, `data` có id/username | `docker exec postgres psql -U postgres -d identity_user_db -c "select id,username,email,phone,role,left(password,4) pw from users where username='testcustomer01'"` → 1 hàng, role CUSTOMER, pw=`$2a$`/`$2b$` (BCrypt, không lưu plaintext) | ☐ |
| 3 | POST | `/api/users/register`<br>_2. [PUBLIC] Đăng ký tài khoản SELLER_ | —; `Content-Type: application/json` | `{ "username": "testseller01", "password": "Password123!", "email": "seller01@example.com", "fullName": "Tran Thi B", "phone": "0987654321", "role": "SELLER" }` | — | — | 201 (role SELLER) | `docker exec postgres psql -U postgres -d identity_user_db -c "select username,role from users where username='testseller01'"` → SELLER | ☐ |
| 4 | POST | `/api/users/login`<br>_3. [PUBLIC] Đăng nhập CUSTOMER → lưu customerToken/userId_ | —; `Content-Type: application/json` | `{ "username": "testcustomer01", "password": "Password123!" }` | — | — | 200, `accessToken`+`refreshToken`+`userId` | — | ☐ |
| 5 | POST | `/api/users/login`<br>_3b. [PUBLIC] Đăng nhập ADMIN → lưu adminToken_ | —; `Content-Type: application/json` | `{ "username": "{{adminUsername}}", "password": "{{adminPassword}}" }` | Postman Env `adminPassword` | `adminPassword` ← Postman Env | 200, `accessToken` (ADMIN) | — | ☐ |
| 6 | POST | `/api/users/refresh-token`<br>_4. [PUBLIC] Refresh Token_ | —; `Content-Type: application/json` | `{ "refreshToken": "{{refreshToken}}" }` | — | `refreshToken` ← #4 | 200, `accessToken` mới | `docker exec postgres psql -U postgres -d identity_user_db -c "select count(*) from refresh_tokens"` ≥ 1 | ☐ |
| 7 | GET | `/api/users/validate-token?token={{accessToken}}`<br>_5. [PUBLIC] Validate Token_ | — | — | — | `accessToken` ← #6 | 200, token hợp lệ | — | ☐ |
| 8 | GET | `/api/users/profile`<br>_6. Xem profile (cần token)_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4 | 200, profile testcustomer01 | — | ☐ |
| 9 | PUT | `/api/users/profile`<br>_7. Cập nhật profile_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "fullName": "Nguyen Van A Updated", "email": "{{realEmail}}", "phone": "{{realPhone}}" }` | Postman Env/Collection var `realEmail`,`realPhone` (chỉ khi test thật; không đặt thì dùng mặc định) | `customerToken` ← #4; `realEmail` ← (biến Collection); `realPhone` ← (biến Collection) | 200, profile đổi sang `{{realEmail}}`/`{{realPhone}}` (mặc định customer01updated@example.com / 0912345679) | — | ☐ |
| 10 | GET | `/api/users/addresses`<br>_8. Lấy danh sách địa chỉ_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4 | 200 (list rỗng lần đầu) | — | ☐ |
| 11 | POST | `/api/users/addresses`<br>_9. Thêm địa chỉ → lưu addressId_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "recipientName": "Nguyen Van A", "phone": "0912345678", "province": "TP Ho Chi Minh", "district": "Quan 1", "ward": "Phuong Ben Nghe", "addressDetail": "123 Nguyen Hue", "isDefault": true }` | — | `customerToken` ← #4 | 201, `data.id` | `docker exec postgres psql -U postgres -d identity_user_db -c "select count(*) from addresses"` ≥ 1 | ☐ |
| 12 | PUT | `/api/users/addresses/{{addressId}}`<br>_10. Cập nhật địa chỉ_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "recipientName": "Nguyen Van A", "phone": "0912345678", "province": "TP Ho Chi Minh", "district": "Quan 3", "ward": "Phuong 1", "addressDetail": "456 Vo Van Tan", "isDefault": true }` | — | `addressId` ← #11; `customerToken` ← #4 | 200 | — | ☐ |
| 13 | PUT | `/api/users/addresses/{{addressId}}/default`<br>_11. Đặt địa chỉ mặc định_ | `Bearer {{customerToken}}` | — | — | `addressId` ← #11; `customerToken` ← #4 | 200 | — | ☐ |
| 14 | GET | `/api/users/profile`<br>_14. [401] Token giả mạo → 401_ | `Bearer eyJhbGciOiJIUzI1NiJ9.invalid.token` | — | — | — | **401** | — | ☐ |

#### 📦 Product Catalog / Brand

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 15 | POST | `/api/catalog/brands`<br>_1. [ADMIN] Tạo Brand → lưu brandId_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "name": "Nike", "logoUrl": "https://example.com/nike-logo.png", "description": "Just Do It" }` | — | `adminToken` ← #5 | 201, `data.id` | `docker exec postgres psql -U postgres -d product_catalog_db -c "select id,name,slug from brands"` → Nike | ☐ |
| 16 | PUT | `/api/catalog/brands/{{brandId}}`<br>_2. [ADMIN] Cập nhật Brand_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "name": "Nike Updated", "logoUrl": "https://example.com/nike-logo-v2.png", "description": "Just Do It - Updated" }` | — | `adminToken` ← #5; `brandId` ← #15 | 200 | — | ☐ |
| 17 | GET | `/api/catalog/brands/{{brandId}}`<br>_3. [PUBLIC] Lấy Brand theo ID_ | — | — | — | `brandId` ← #15 | 200 | — | ☐ |
| 18 | GET | `/api/catalog/brands/slug/nike-updated`<br>_4. [PUBLIC] Lấy Brand theo slug (slug đổi thành nike-updated sau khi Cập nhật Brand)_ | — | — | — | — | 200 (URL `/brands/slug/nike-updated` vì #16 đã đổi tên/slug) | — | ☐ |
| 19 | GET | `/api/catalog/brands`<br>_5. [PUBLIC] Danh sách tất cả Brand_ | — | — | — | — | 200, list ≥ 1 | — | ☐ |

#### 📦 Product Catalog / Category

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 20 | POST | `/api/catalog/categories`<br>_1. [ADMIN] Tạo Category gốc → lưu categoryId_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "name": "Giày Thể Thao", "description": "Danh mục giày thể thao các loại", "parentId": null }` | — | `adminToken` ← #5 | 201, `data.id` | `docker exec postgres psql -U postgres -d product_catalog_db -c "select id,name,slug,parent_id from categories"` → giay-the-thao | ☐ |
| 21 | POST | `/api/catalog/categories`<br>_2. [ADMIN] Tạo Category con_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "name": "Giày Chạy Bộ", "description": "Giày chuyên dụng cho chạy bộ", "parentId": {{categoryId}} }` | — | `adminToken` ← #5; `categoryId` ← #20 | 201 (con của #20) | — | ☐ |
| 22 | GET | `/api/catalog/categories/{{categoryId}}`<br>_3. [PUBLIC] Lấy Category theo ID_ | — | — | — | `categoryId` ← #20 | 200 | — | ☐ |
| 23 | GET | `/api/catalog/categories/slug/giay-the-thao`<br>_4. [PUBLIC] Lấy Category theo slug_ | — | — | — | — | 200 (slug `giay-the-thao`) | — | ☐ |
| 24 | GET | `/api/catalog/categories/roots`<br>_5. [PUBLIC] Danh mục gốc (roots)_ | — | — | — | — | 200 | — | ☐ |
| 25 | GET | `/api/catalog/categories/{{categoryId}}/children`<br>_6. [PUBLIC] Danh mục con của Category_ | — | — | — | `categoryId` ← #20 | 200 (có Giày Chạy Bộ) | — | ☐ |
| 26 | GET | `/api/catalog/categories`<br>_7. [PUBLIC] Tất cả Category_ | — | — | — | — | 200 | — | ☐ |
| 27 | PUT | `/api/catalog/categories/{{categoryId}}`<br>_8. [ADMIN] Cập nhật Category_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "name": "Giày Thể Thao Updated", "description": "Mô tả mới", "parentId": null }` | — | `adminToken` ← #5; `categoryId` ← #20 | 200 | — | ☐ |

#### 📦 Product Catalog / Product

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 28 | POST | `/api/catalog/products`<br>_1. [ADMIN/SELLER] Tạo Product với variants → lưu productId, skuId_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "name": "Nike Air Max 270", "description": "Giày thể thao Nike Air Max 270 cao cấp", "basePrice": 2500000, "categoryId": {{categoryId}}, "brandId": {{brandId}}, "images": "https://example.com/nike-am270.jpg", "status": "ACTIVE", "variants": [ { "sku": "NIKE-AM270-BLK-42", "price": 2500000, "stockQuantity": 100, "attributes": "{\"color\": \"Black\", \"size\": \"42\"}", "imageUrl": "https://example.com/nike-am270-black.jpg" }, { "sku": "NIKE-AM270-WHT-42", "price": 2600000, "stockQuantity": 50, "attributes": "{\"color\": \"White\", \"size\": \"42\"}", "imageUrl": "https://example.com/nike-am270-white.jpg" } ] }` | — | `adminToken` ← #5; `brandId` ← #15; `categoryId` ← #20 | 201, 2 variant; `data.variants[0].sku`=`NIKE-AM270-BLK-42` | `docker exec postgres psql -U postgres -d product_catalog_db -c "select id,name,slug,seller_id from products order by id desc limit 1"` ; `docker exec postgres psql -U postgres -d product_catalog_db -c "select sku from product_variants"` → 2 SKU; `seller_id` = id của admin (lấy từ JWT, không từ body) | ☐ |
| 29 | GET | `/api/catalog/products/{{productId}}`<br>_2. [PUBLIC] Lấy Product theo ID_ | — | — | — | `productId` ← #28 | 200 | — | ☐ |
| 30 | GET | `/api/catalog/products/slug/nike-air-max-270`<br>_3. [PUBLIC] Lấy Product theo slug_ | — | — | — | — | 200 | — | ☐ |
| 31 | GET | `/api/catalog/products/search?keyword=Nike&categoryId={{categoryId}}&page=0&size=10&sortBy=createdAt&sortDir=desc`<br>_4. [PUBLIC] Tìm kiếm Product (search + filter + page)_ | — | — | — | `categoryId` ← #20 | 200, có sản phẩm | — | ☐ |
| 32 | GET | `/api/catalog/products/variants/sku/{{skuId}}`<br>_5. [PUBLIC] Lấy Variant theo SKU_ | — | — | — | `skuId` ← #28 | 200 | — | ☐ |
| 33 | GET | `/api/catalog/products/variants/sku/{{skuId}}/sellable-check?quantity=2`<br>_6. [PUBLIC] Kiểm tra SKU có thể bán (sellable-check)_ | — | — | — | `skuId` ← #28 | 200, sellable=true (qty 2) | — | ☐ |
| 34 | PUT | `/api/catalog/products/{{productId}}`<br>_7. [ADMIN/SELLER] Cập nhật Product_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "name": "Nike Air Max 270 - Special Edition", "description": "Phiên bản đặc biệt", "basePrice": 2800000, "categoryId": {{categoryId}}, "brandId": {{brandId}}, "images": "https://example.com/nike-am270-se.jpg", "status": "ACTIVE", "variants": [ { "sku": "NIKE-AM270-BLK-42", "price": 2800000, "stockQuantity": 80, "attributes": "{\"color\": \"Black\", \"size\": \"42\"}", "imageUrl": "https://example.com/nike-am270-black-se.jpg" } ] }` | — | `adminToken` ← #5; `brandId` ← #15; `categoryId` ← #20; `productId` ← #28 | 200 | — | ☐ |

#### 📊 Inventory (ADMIN) — chạy TRƯỚC khi đặt hàng

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 35 | POST | `/api/inventory`<br>_1. [ADMIN] Khởi tạo tồn kho SKU_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "sku": "{{skuId}}", "productName": "Nike Air Max 270 Black Size 42", "quantity": 100 }` | — | `adminToken` ← #5; `skuId` ← #28 | 201, availableQty=100 | `docker exec postgres psql -U postgres -d order_inventory_db -c "select sku,available_qty,reserved_qty from inventories where sku='NIKE-AM270-BLK-42'"` → 100 / 0 | ☐ |
| 36 | PUT | `/api/inventory/{{skuId}}/restock`<br>_2. [ADMIN] Nhập thêm kho (restock)_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "quantity": 50 }` | — | `adminToken` ← #5; `skuId` ← #28 | 200, availableQty=150 | cùng lệnh #35 → 150 / 0 | ☐ |
| 37 | GET | `/api/inventory/{{skuId}}`<br>_3. [ADMIN] Xem tồn kho theo SKU_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `skuId` ← #28 | 200 | — | ☐ |
| 38 | GET | `/api/inventory?page=0&size=20`<br>_4. [ADMIN] Danh sách tồn kho (phân trang)_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5 | 200, list ≥ 1 | — | ☐ |

#### 🛒 Order (CUSTOMER)

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 39 | POST | `/api/orders`<br>_1. [CUSTOMER] Tạo đơn hàng → lưu orderId_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "idempotencyKey": "{{$guid}}", "customerId": {{userId}}, "note": "Giao giờ hành chính", "items": [ { "sku": "{{skuId}}", "quantity": 2 } ] }` | — | `customerToken` ← #4; `skuId` ← #28; `userId` ← #4 | **201**, status `PENDING`, `data.id`,`orderCode` | `docker exec postgres psql -U postgres -d order_inventory_db -c "select id,order_code,status,idempotency_key from orders order by id desc limit 1"` → PENDING; `docker exec postgres psql -U postgres -d order_inventory_db -c "select topic,status,count(*) from outbox_events group by 1,2"` → `order.created` PENDING→PUBLISHED sau vài giây; kho: available −2, reserved +2; Redis (nếu `USE_REDIS_LOCK=true`): `docker exec redis redis-cli --scan --pattern "*lock*"` → rỗng sau khi xong (lock đã nhả); Kafka: `docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order.created --from-beginning --max-messages 1 --timeout-ms 10000` | ☐ |
| 40 | GET | `/api/orders/{{orderId}}`<br>_2. Lấy đơn hàng theo ID_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4; `orderId` ← #39 | 200 | — | ☐ |
| 41 | GET | `/api/orders/code/{{orderCode}}`<br>_3. Lấy đơn hàng theo orderCode_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4; `orderCode` ← #39 | 200 | — | ☐ |
| 42 | GET | `/api/orders?page=0&size=10`<br>_4. Danh sách đơn hàng của tôi (phân trang)_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4 | 200, chỉ đơn của customer | — | ☐ |

#### 💳 Payment & Billing

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 43 | GET | `/api/payments/order/{{orderId}}`<br>_1. [AUTH] Xem Payment theo orderId (tự chờ Kafka tạo Payment) → lưu paymentId, vnpTxnRef_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4; `orderId` ← #39 | 200 sau ≤ 20 lần retry; status `PENDING` | `docker exec postgres psql -U postgres -d payment_billing_db -c "select id,order_id,status,vnp_txn_ref,amount from payments order by id desc limit 1"` → PENDING, order_id=orderId; Kafka lag: `docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group payment-billing-service` → LAG 0 | ☐ |
| 44 | POST | `/api/payments/order/{{orderId}}/vnpay-url`<br>_2. [AUTH] Tạo URL thanh toán VNPay (cần VNPAY_TMN_CODE + VNPAY_HASH_SECRET trong .env)_ | `Bearer {{customerToken}}` | — | VPS `.env`: `VNPAY_TMN_CODE`,`VNPAY_HASH_SECRET` (restart payment-billing-service) | `customerToken` ← #4; `orderId` ← #39 | 200 + `data.paymentUrl` **(chỉ khi `VNPAY_HASH_SECRET` đã đặt; để trống → 500 = BLOCKED)** | `docker exec postgres psql -U postgres -d payment_billing_db -c "select id,status,vnp_txn_ref,amount from payments where order_id=<orderId>"` → vẫn PENDING cho tới khi thanh toán thật; `docker logs --tail 30 payment-billing-service | grep -i vnpay` không có lỗi ký; URL bắt đầu `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_...` | ☐ |
| 45 | GET | `/api/payments/{{paymentId}}`<br>_3. [AUTH] Xem Payment theo ID_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4; `paymentId` ← #43 | 200 | `docker exec postgres psql -U postgres -d payment_billing_db -c "select id,order_id,status from payments where id=<paymentId>"` | ☐ |
| 46 | GET | `/api/payments?page=0&size=10`<br>_4. [AUTH] Danh sách thanh toán của tôi_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4 | 200 | `docker exec postgres psql -U postgres -d payment_billing_db -c "select count(*) from payments"` ≥ 1; chỉ trả payment của chính user (đối chiếu `orders.customer_id`) | ☐ |
| 47 | GET | `/api/payments/{{paymentId}}/query`<br>_5. [AUTH] Query VNPay transaction status (gọi VNPay sandbox THẬT — cần TMN_CODE/HASH_SECRET thật)_ | `Bearer {{customerToken}}` | — | VNPay sandbox THẬT | `customerToken` ← #4; `paymentId` ← #43 | **BLOCKED** nếu không có VNPay sandbox thật (test chấp nhận 200/400/500/502/503) | `docker logs --tail 30 payment-billing-service | grep -i querydr` ; `docker exec postgres psql -U postgres -d payment_billing_db -c "select type,response_code from payment_transaction_logs order by id desc limit 3"` | ☐ |
| 48 | POST | `/api/payments/{{paymentId}}/refund`<br>_6. [ADMIN] Hoàn tiền khi Payment chưa SUCCESS → 400 (refund thật cần thanh toán VNPay thật)_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "amount": 2500000, "reason": "Khách hàng đổi ý, hủy đơn theo yêu cầu", "createBy": "admin" }` | — | `adminToken` ← #5; `paymentId` ← #43 | **400** (Payment chưa SUCCESS) | `docker exec postgres psql -U postgres -d payment_billing_db -c "select status,refunded_amount from payments where id=<paymentId>"` → vẫn PENDING, refunded_amount=0 | ☐ |
| 49 | GET | `/api/payments/{{paymentId}}/logs`<br>_7. [AUTH] Xem transaction logs của Payment_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4; `paymentId` ← #43 | 200, ≥ 1 log | `docker exec postgres psql -U postgres -d payment_billing_db -c "select type,response_code,message from payment_transaction_logs order by id desc limit 5"` | ☐ |
| 50 | GET | `/api/payments/vnpay/ipn?vnp_TxnRef=TEST-ORDER-001&vnp_ResponseCode=00&vnp_SecureHash=FAKE`<br>_8. [PUBLIC] VNPay IPN webhook (no token)_ | — | — | VPS `.env`: `VNPAY_IPN_ALLOWED_IPS=*` (tạm) | — | HTTP 200, **không 401**; body `RspCode` = `97` (nếu `VNPAY_IPN_ALLOWED_IPS=*`) hoặc `99` (IP không thuộc whitelist) | `docker logs --tail 20 payment-billing-service | grep -i -E "IP|chu ky"` ; không có thay đổi trong bảng `payments` | ☐ |
| 51 | GET | `/api/payments/vnpay/return?vnp_TxnRef=TEST&vnp_ResponseCode=00&vnp_SecureHash=FAKE`<br>_9. [PUBLIC] VNPay Return URL (redirect sau khi thanh toán)_ | — | — | VPS `.env`: `VNPAY_HASH_SECRET` | — | **403** (chữ ký sai). Nếu `VNPAY_HASH_SECRET` rỗng → 500 (key HMAC rỗng) = BLOCKED | `docker logs --tail 20 payment-billing-service | grep -i "Sai chu ky"` ; không đổi trạng thái payment | ☐ |

#### 🔔 Notification

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 52 | POST | `/api/notifications/test`<br>_1. [ADMIN] Gửi test notification EMAIL_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "channel": "EMAIL", "recipient": "{{realEmail}}", "subject": "Test Email từ QuickBite", "body": "Đây là email test từ Notification Service." }` | (thật) VPS `NOTIFICATION_MAIL_ENABLED=true`,`MAIL_USERNAME`,`MAIL_PASSWORD` + Postman `realEmail` | `adminToken` ← #5; `realEmail` ← (biến Collection) | 201, status `SENT`. Mail TẮT = log giả lập (KHÔNG phải email thật). Mail BẬT = hộp thư `{{realEmail}}` nhận thư thật; lỗi SMTP → status `FAILED` + error_message | `docker exec postgres psql -U postgres -d notification_db -c "select id,channel,status,notification_type,left(error_message,60) from notification_logs order by id desc limit 3"` → MANUAL_TEST/EMAIL/SENT | ☐ |
| 53 | POST | `/api/notifications/test`<br>_2. [ADMIN] Gửi test notification SMS_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "channel": "SMS", "recipient": "{{realPhoneE164}}", "body": "Test SMS từ QuickBite Notification Service." }` | (thật) VPS `NOTIFICATION_SMS_ENABLED=true`,`TWILIO_ACCOUNT_SID/AUTH_TOKEN/FROM_NUMBER` + Postman `realPhoneE164` | `adminToken` ← #5; `realPhoneE164` ← (biến Collection) | 201. SMS TẮT = chỉ log. SMS BẬT = điện thoại `{{realPhoneE164}}` nhận SMS thật (trial: số phải được Twilio verify); lỗi → `FAILED` + mã Twilio | — | ☐ |
| 54 | POST | `/api/notifications/test`<br>_3. [ADMIN] Gửi test notification PUSH (log console)_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "channel": "PUSH", "recipient": "customer:123", "subject": "Test Push", "body": "Push notification mô phỏng (log console)." }` | — | `adminToken` ← #5 | 201 (PUSH luôn mô phỏng log) | — | ☐ |
| 55 | GET | `/api/notifications/logs/{{notificationId}}`<br>_4. [ADMIN] Xem notification log theo ID_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `notificationId` ← #52 | 200 | — | ☐ |
| 56 | GET | `/api/notifications/logs?page=0&size=20`<br>_5. [ADMIN] Tìm kiếm notification logs (không filter)_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5 | 200, ≥ 3 log (3 test ở trên) | — | ☐ |
| 57 | GET | `/api/notifications/logs?channel=EMAIL&status=SENT&page=0&size=20`<br>_6. [ADMIN] Tìm kiếm logs theo channel + status_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5 | 200 (có thể 0 phần tử nếu không có EMAIL/SENT) | — | ☐ |
| 58 | GET | `/api/notifications/logs?orderId={{orderId}}&page=0&size=20`<br>_7. [ADMIN] Tìm kiếm logs theo orderId_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `orderId` ← #39 | 200, có log ORDER_CREATED của orderId (Kafka consumer đã chạy) | `docker exec postgres psql -U postgres -d notification_db -c "select notification_type,channel,status,order_id from notification_logs where order_id=<orderId>"` → ORDER_CREATED (EMAIL/SMS/PUSH tuỳ cấu hình), unique `idempotency_key` = `ORDER_CREATED:<orderId>:<channel>` | ☐ |

#### 🧪 Bổ sung kiểm thử production (trùng dữ liệu, ownership, hết hàng)

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 59 | POST | `/api/users/register`<br>_1. Đăng ký CUSTOMER B (testcustomer02)_ | —; `Content-Type: application/json` | `{ "username": "testcustomer02", "password": "Password123!", "email": "customer02@example.com", "fullName": "Test User", "phone": "0912345680", "role": "CUSTOMER" }` | — | — | 201 | — | ☐ |
| 60 | POST | `/api/users/login`<br>_2. Đăng nhập CUSTOMER B → customerBToken_ | —; `Content-Type: application/json` | `{ "username": "testcustomer02", "password": "Password123!" }` | — | — | 200, `customerBToken` | — | ☐ |
| 61 | POST | `/api/users/register`<br>_3. [409] Đăng ký trùng username (testseller01)_ | —; `Content-Type: application/json` | `{ "username": "testseller01", "password": "Password123!", "email": "dup.user@example.com", "fullName": "Test User", "phone": "0900000003", "role": "CUSTOMER" }` | — | — | **409** trùng username | `docker exec postgres psql -U postgres -d identity_user_db -c "select count(*) from users where username='testseller01'"` = 1 (không tạo trùng) | ☐ |
| 62 | POST | `/api/users/register`<br>_4. [409] Đăng ký trùng email (seller01@example.com)_ | —; `Content-Type: application/json` | `{ "username": "testdup_email", "password": "Password123!", "email": "seller01@example.com", "fullName": "Test User", "phone": "0900000004", "role": "CUSTOMER" }` | — | — | **409** trùng email | `docker exec postgres psql -U postgres -d identity_user_db -c "select count(*) from users where email='seller01@example.com'"` = 1 | ☐ |
| 63 | POST | `/api/users/register`<br>_5. [409] Đăng ký trùng phone (0987654321)_ | —; `Content-Type: application/json` | `{ "username": "testdup_phone", "password": "Password123!", "email": "dup.phone@example.com", "fullName": "Test User", "phone": "0987654321", "role": "CUSTOMER" }` | — | — | **409** trùng phone | `docker exec postgres psql -U postgres -d identity_user_db -c "select count(*) from users where phone='0987654321'"` = 1 | ☐ |
| 64 | GET | `/api/orders/{{orderId}}`<br>_6. [403] B xem đơn của A (ownership)_ | `Bearer {{customerBToken}}` | — | — | `customerBToken` ← #60; `orderId` ← #39 | **403** (ownership) | — | ☐ |
| 65 | PUT | `/api/orders/{{orderId}}/cancel`<br>_7. [403] B hủy đơn của A (ownership)_ | `Bearer {{customerBToken}}`; `Content-Type: application/json` | `{ "reason": "B co huy don cua A" }` | — | `customerBToken` ← #60; `orderId` ← #39 | **403** (ownership) | — | ☐ |
| 66 | POST | `/api/orders`<br>_8. [409] Đặt hàng vượt tồn kho (quantity 999999)_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "idempotencyKey": "{{$guid}}", "customerId": {{userId}}, "items": [{"sku": "{{skuId}}", "quantity": 999999}] }` | — | `customerToken` ← #4; `skuId` ← #28; `userId` ← #4 | **409** InsufficientStock | `docker exec postgres psql -U postgres -d order_inventory_db -c "select sku,available_qty,reserved_qty from inventories where sku='NIKE-AM270-BLK-42'"` → không đổi so với trước #66; không có order mới | ☐ |
| 67 | GET | `/api/inventory/{{skuId}}`<br>_9. Tồn kho KHÔNG đổi sau đơn bị từ chối_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `skuId` ← #28 | 200, tồn kho không đổi | — | ☐ |

#### 🎯 Kịch bản Demo theo đề bài

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 68 | GET | `/api/inventory/{{skuId}}`<br>_1. Kiểm tra tồn kho trước test_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `skuId` ← #28 | 200, ghi `stockBefore` | — | ☐ |
| 69 | POST | `/api/orders`<br>_2. [Sau khi TẮT notification-service] Đặt hàng vẫn thành công (201)_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "idempotencyKey": "{{$guid}}", "customerId": {{userId}}, "note": "demo tat Notification", "items": [{"sku": "{{skuId}}", "quantity": 1}] }` | VPS: `docker compose ... stop notification-service` TRƯỚC khi gửi | `customerToken` ← #4; `skuId` ← #28; `userId` ← #4 | **201** dù Notification đang tắt | `docker exec postgres psql -U postgres -d order_inventory_db -c "select topic,status,count(*) from outbox_events group by 1,2"` ; Kafka: `docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group notification-service` → LAG > 0 khi Notification tắt | ☐ |
| 70 | GET | `/api/notifications/logs`<br>_3. [Notification đang tắt] Gateway trả 502/503 cho riêng Notification_ | `Bearer {{adminToken}}` | — | VPS: Notification đang tắt | `adminToken` ← #5 | **502/503/504** (chỉ riêng Notification) | — | ☐ |
| 71 | GET | `/api/notifications/logs?orderId={{offlineOrderId}}&page=0&size=20`<br>_4. [Sau khi BẬT lại] Notification tự nhận event đã tồn đọng (đơn tạo lúc Notification tắt)_ | `Bearer {{adminToken}}` | — | VPS: `start notification-service` + chờ healthy TRƯỚC khi gửi | `adminToken` ← #5; `offlineOrderId` ← #69 | 200 + `totalElements ≥ 1` cho đơn tạo lúc Notification tắt (tự retry ≤ 20×5s) | cùng lệnh consumer-group → LAG về 0; `docker exec postgres psql -U postgres -d notification_db -c "select order_id,notification_type,status from notification_logs where order_id=<offlineOrderId>"` | ☐ |
| 72 | POST | `/api/orders`<br>_5. Idempotency: gửi lại cùng idempotencyKey → trả đơn cũ_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "idempotencyKey": "fixed-key-demo-0001", "customerId": {{userId}}, "items": [{"sku": "{{skuId}}", "quantity": 1}] }` | — | `customerToken` ← #4; `skuId` ← #28; `userId` ← #4 | 200/201, lưu `idemOrderId` | `docker exec postgres psql -U postgres -d order_inventory_db -c "select count(*) from orders where idempotency_key='fixed-key-demo-0001'"` = 1 | ☐ |
| 73 | POST | `/api/orders`<br>_6. Idempotency lần 2 phải trùng orderId_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "idempotencyKey": "fixed-key-demo-0001", "customerId": {{userId}}, "items": [{"sku": "{{skuId}}", "quantity": 1}] }` | — | `customerToken` ← #4; `skuId` ← #28; `userId` ← #4 | 200/201, **cùng orderId** với #72 | cùng lệnh #72 vẫn = 1 | ☐ |
| 74 | PUT | `/api/orders/{{idemOrderId}}/cancel`<br>_7. Hủy đơn PENDING (Saga bù trừ kho)_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "reason": "demo saga" }` | — | `customerToken` ← #4; `idemOrderId` ← #72 | 200, đơn → CANCELLED, kho hoàn lại | `docker exec postgres psql -U postgres -d order_inventory_db -c "select status from orders where id=<idemOrderId>"` → CANCELLED; kho hoàn lại; outbox có `order.cancelled` | ☐ |

#### 🔒 Security & Edge Cases

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 75 | GET | `/api/orders`<br>_1. [401] Gọi protected API không có token_ | — | — | — | — | **401** | — | ☐ |
| 76 | GET | `/api/orders`<br>_2. [401] Token giả mạo_ | `Bearer eyJhbGciOiJIUzI1NiJ9.invalid.token` | — | — | — | **401** | — | ☐ |
| 77 | POST | `/api/catalog/brands`<br>_3. [403] CUSTOMER cố tạo Brand (admin-only)_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{"name": "Fake Brand"}` | — | `customerToken` ← #4 | **403** | — | ☐ |
| 78 | POST | `/api/users/login`<br>_4. [429] Rate limit login (tự bắn 15 request song song rồi kiểm tra 429)_ | —; `Content-Type: application/json` | `{"username": "test", "password": "wrong"}` | — | — | **429** (15 request song song > burst 10) | `docker logs --tail 30 api-gateway` (RequestRateLimiter) | ☐ |
| 79 | GET | `/api/notifications/logs`<br>_5. [502/503] Service phía sau tắt → xem folder Kịch bản Demo_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5 | 502/503/504 chỉ khi Notification đang tắt (không có assertion) | — | ☐ |
| 80 | POST | `/api/orders`<br>_6. [400] Validation error — tạo order thiếu items_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "idempotencyKey": "{{$guid}}", "customerId": {{userId}}, "items": [] }` | — | `customerToken` ← #4; `userId` ← #4 | **400** | — | ☐ |

#### 🧾 VNPay giả lập IPN — Saga thanh toán (cần VNPAY_TMN_CODE/HASH_SECRET + VNPAY_IPN_ALLOWED_IPS=* tạm thời)

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 81 | GET | `/api/inventory/{{skuId}}`<br>_V1. [ADMIN] Ghi tồn kho trước khi đặt đơn A_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `skuId` ← #28 | 200 | — | ☐ |
| 82 | POST | `/api/orders`<br>_V2. [CUSTOMER] Tạo đơn A (sẽ thanh toán THÀNH CÔNG)_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "idempotencyKey": "{{$guid}}", "customerId": {{userId}}, "note": "VNPay IPN thanh cong", "items": [{"sku": "{{skuId}}", "quantity": 1}] }` | — | `customerToken` ← #4; `skuId` ← #28; `userId` ← #4 | 201 PENDING | — | ☐ |
| 83 | GET | `/api/payments/order/{{vnpOrderA}}`<br>_V3. Chờ Kafka tạo Payment cho đơn A (tự retry) → lưu vnpTxnRef_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4; `vnpOrderA` ← #82 | 200, Payment PENDING (retry) | `docker exec postgres psql -U postgres -d payment_billing_db -c "select order_id,status,vnp_txn_ref from payments where order_id=<vnpOrderA>"` → PENDING | ☐ |
| 84 | GET | `/api/payments/vnpay/ipn?{{ipnQuery}}`<br>_V4. [PUBLIC] IPN ký HMAC-SHA512 — THANH CONG (đơn A)_ | — | — | Postman Env `vnpHashSecret` = `VNPAY_HASH_SECRET` trên VPS + `VNPAY_IPN_ALLOWED_IPS=*` | `ipnQuery` ← pre-request của chính request (ký HMAC với `vnpHashSecret`) | 200, `RspCode`=`00` | `docker exec postgres psql -U postgres -d payment_billing_db -c "select status,vnp_transaction_no from payments where order_id=<vnpOrderA>"` → SUCCESS; outbox payment.completed | ☐ |
| 85 | GET | `/api/orders/{{vnpOrderA}}`<br>_V5. Chờ Saga: đơn A → CONFIRMED (tự retry)_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4; `vnpOrderA` ← #82 | 200, đơn A `CONFIRMED` (retry ≤ 20×4s) | `docker exec postgres psql -U postgres -d order_inventory_db -c "select id,status from orders where id=<vnpOrderA>"` → CONFIRMED | ☐ |
| 86 | GET | `/api/payments/vnpay/ipn?{{ipnQuery}}`<br>_V6. [PUBLIC] IPN lặp lại cùng giao dịch → RspCode 02 (idempotent)_ | — | — | — | `ipnQuery` ← pre-request của chính request (ký HMAC với `vnpHashSecret`) | 200, `RspCode`=`02` | `docker exec postgres psql -U postgres -d payment_billing_db -c "select count(*) from payments where order_id=<vnpOrderA>"` = 1 (không nhân đôi); không thêm outbox event | ☐ |
| 87 | POST | `/api/payments/{{vnpPaymentA}}/refund`<br>_V7. [CUSTOMER] Refund bị chặn 403 (chỉ ADMIN)_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "amount": 1000, "reason": "customer khong duoc phep", "createBy": "customer" }` | — | `customerToken` ← #4; `vnpPaymentA` ← #83 | **403** | — | ☐ |
| 88 | GET | `/api/inventory/{{skuId}}`<br>_V8. [ADMIN] Ghi tồn kho sau đơn A (trước đơn B)_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `skuId` ← #28 | 200 | — | ☐ |
| 89 | POST | `/api/orders`<br>_V9. [CUSTOMER] Tạo đơn B (sẽ thanh toán THẤT BẠI)_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "idempotencyKey": "{{$guid}}", "customerId": {{userId}}, "note": "VNPay IPN that bai", "items": [{"sku": "{{skuId}}", "quantity": 1}] }` | — | `customerToken` ← #4; `skuId` ← #28; `userId` ← #4 | 201 PENDING | — | ☐ |
| 90 | GET | `/api/payments/order/{{vnpOrderB}}`<br>_V10. Chờ Kafka tạo Payment cho đơn B (tự retry) → lưu vnpTxnRef_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4; `vnpOrderB` ← #89 | 200 (retry) | — | ☐ |
| 91 | GET | `/api/payments/vnpay/ipn?{{ipnQuery}}`<br>_V11. [PUBLIC] IPN ký HMAC — THAT BAI (ResponseCode 24, đơn B)_ | — | — | Postman Env `vnpHashSecret` | `ipnQuery` ← pre-request của chính request (ký HMAC với `vnpHashSecret`) | 200, `RspCode`=`00` (ghi nhận FAILED) | `docker exec postgres psql -U postgres -d payment_billing_db -c "select status,failure_reason from payments where order_id=<vnpOrderB>"` → FAILED; outbox `payment.failed` | ☐ |
| 92 | GET | `/api/orders/{{vnpOrderB}}`<br>_V12. Chờ Saga bù trừ: đơn B → CANCELLED (tự retry)_ | `Bearer {{customerToken}}` | — | — | `customerToken` ← #4; `vnpOrderB` ← #89 | 200, đơn B `CANCELLED` (retry) | `docker exec postgres psql -U postgres -d order_inventory_db -c "select id,status from orders where id=<vnpOrderB>"` → CANCELLED | ☐ |
| 93 | GET | `/api/inventory/{{skuId}}`<br>_V13. [ADMIN] Tồn kho đã được hoàn lại (Saga compensation)_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `skuId` ← #28 | 200, tồn kho hoàn lại | `docker exec postgres psql -U postgres -d order_inventory_db -c "select available_qty,reserved_qty from inventories where sku='NIKE-AM270-BLK-42'"` → available = vnpStockMid − 0 sau khi hoàn đơn B (so với #88 + trừ đơn A đã CONFIRMED) | ☐ |
| 94 | POST | `/api/payments/{{vnpPaymentB}}/refund`<br>_V14. [ADMIN] Refund payment B (FAILED) → 400_ | `Bearer {{adminToken}}`; `Content-Type: application/json` | `{ "amount": 1000, "reason": "payment that bai khong the hoan", "createBy": "admin" }` | — | `adminToken` ← #5; `vnpPaymentB` ← #90 | **400** | — | ☐ |
| 95 | GET | `/api/payments/vnpay/ipn?vnp_TxnRef={{vnpTxnRef}}&vnp_Amount=1000&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_SecureHash=deadbeef`<br>_V15. [PUBLIC] IPN sai chữ ký → RspCode 97_ | — | — | — | `vnpTxnRef` ← #90 | 200, `RspCode`=`97` | `docker logs --tail 20 payment-billing-service | grep -i "Sai chu ky"` | ☐ |

#### 🧹 Cleanup (chạy CUỐI CÙNG)

| STT | Method | URL | Auth / Headers | Body = DATA MẪU | ENV cần điền | ID/token lấy từ đâu | Expected | Verify DB/Kafka/Redis | PASS |
|---|---|---|---|---|---|---|---|---|---|
| 96 | DELETE | `/api/users/addresses/{{addressId}}`<br>_Xóa địa chỉ (sau khi đã đặt hàng xong — Order cần địa chỉ mặc định)_ | `Bearer {{customerToken}}` | — | — | `addressId` ← #11; `customerToken` ← #4 | 200 | — | ☐ |
| 97 | PUT | `/api/orders/{{orderId}}/cancel`<br>_5. Hủy đơn hàng_ | `Bearer {{customerToken}}`; `Content-Type: application/json` | `{ "reason": "Tôi muốn đổi địa chỉ giao hàng" }` | — | `customerToken` ← #4; `orderId` ← #39 | 200 (đơn `orderId` → CANCELLED) | — | ☐ |
| 98 | PUT | `/api/users/{{userId}}/role?role=SELLER`<br>_13. [ADMIN] Đổi role user_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `userId` ← #4 | 200, role SELLER | — | ☐ |
| 99 | DELETE | `/api/catalog/products/{{productId}}`<br>_8. [ADMIN/SELLER] Xóa Product_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `productId` ← #28 | 200 | — | ☐ |
| 100 | DELETE | `/api/catalog/categories/{{categoryId}}`<br>_9. [ADMIN] Xóa Category_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `categoryId` ← #20 | 200 | — | ☐ |
| 101 | DELETE | `/api/catalog/brands/{{brandId}}`<br>_6. [ADMIN] Xóa Brand_ | `Bearer {{adminToken}}` | — | — | `adminToken` ← #5; `brandId` ← #15 | 200 | — | ☐ |

## G. Kịch bản hạ tầng (chạy TRÊN VPS, xen kẽ)
| # | Kịch bản | Lệnh VPS | Postman | Expected | Verify |
|---|---|---|---|---|---|
| F1 | Order → Outbox → Kafka → Payment | — | #39 rồi #43 | Payment PENDING, `order_id` = orderId | outbox `order.created` PUBLISHED; consumer-group `payment-billing-service` LAG 0 |
| F2 | Order → Outbox → Kafka → Notification | — | #58 | có `notification_logs` ORDER_CREATED | LAG `notification-service` = 0 |
| F3 | Notification STOP, đơn vẫn OK | `docker compose stop notification-service` | #69 (201), #70 (5xx) | Order thành công | `docker compose ps` |
| F4 | Notification START ⇒ xử lý | `… start notification-service` | #71 | log của `offlineOrderId` xuất hiện | LAG → 0 |
| F5 | Kafka restart không mất event | `docker compose restart kafka` (chờ healthy) rồi chạy lại #39 | #39, #43 | đơn mới tạo được, outbox đẩy bù | outbox PENDING → 0 |
| F6 | Consumer restart ⇒ retry | `docker compose stop payment-billing-service` → #39 → `start payment-billing-service` | #39, #43 | Payment được tạo sau khi service lên lại | `select * from payments order by id desc limit 3` |
| F7 | Duplicate event | `docker exec -i kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order.created --from-beginning --max-messages 1 --timeout-ms 10000 > /tmp/ev.json`; rồi gửi lại cùng payload bằng `kafka-console-producer.sh --topic order.created` (giữ nguyên key) | — | KHÔNG nhân đôi Payment/Notification (consumer có `existsByOrderId` + `idempotency_key` unique) | `select count(*) from payments where order_id=<id>` = 1; `select count(*) from notification_logs where order_id=<id> and notification_type='ORDER_CREATED'` không tăng |
| F8 | Saga compensation (hủy) | — | #74, #97 | kho hoàn lại | `inventories` available tăng, reserved giảm |
| F9 | Saga: payment FAILED ⇒ hủy đơn + hoàn kho | — | folder V (#89–#93) hoặc thẻ lỗi VNPay (docs/07) | đơn CANCELLED, kho hoàn | `orders`, `inventories`, outbox `payment.failed` |
| F10 | DLT | `docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list \| grep DLT` | — | chỉ có khi có message lỗi | |

## H. Checklist cuối (điền sau khi chạy)
| Hạng mục | Kết quả |
|---|---|
| Identity (register, 409×3, login, refresh, validate, 401, 403) | ☐ |
| Catalog | ☐ |
| Inventory + 409 vượt tồn | ☐ |
| Order (create, idempotency, cancel, ownership) | ☐ |
| Payment (get/list/logs, refund bị chặn) | ☐ |
| Notification (ADMIN-only, logs) | ☐ |
| F1–F10 | ☐ |
| Email thật / SMS thật / VNPay sandbox thật (docs/07) | ☐ BLOCKED nếu thiếu credential |
