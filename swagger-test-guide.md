# Swagger Test Guide — QuickBite Production (tương đương 100% collection Postman)

Guide này đi **đúng thứ tự và đủ mọi request** (đã đối chiếu tự động 101/101) của `ecommerce_omnichannel_postman_collection.json`
(Health → Identity → Brand → Category → Product → Inventory → Order → Payment → Notification →
Bổ sung → Demo → Security → VNPay IPN → Cleanup). Mỗi bước ghi rõ: tab Swagger, endpoint, token, body, kết quả mong đợi, giá trị phải ghi lại.

> Khác biệt duy nhất so với Postman: Swagger không có script. Các việc Postman làm tự động
> (lưu biến, retry chờ Kafka, ký IPN HMAC, bắn 15 request song song) được thay bằng **ghi tay** hoặc **script PowerShell** ở Phần C.

---

## A. Chuẩn bị (làm 1 lần)

### A1. Code & `.env` (cần đã deploy bản mới)
Bản code phải có: (1) `ports 127.0.0.1:808x` cho 5 service, (2) nút **Authorize** (SecurityScheme `bearerAuth` trong 5 `OpenApiConfig`), (3) identity cho phép `/swagger-ui/**`. Push lên `main`, chờ Actions xanh, rồi trên VPS:

```bash
cd /opt/quickbite
grep -q '^GATEWAY_BASE_URL=' .env || echo 'GATEWAY_BASE_URL=https://khoatla-ecommerce.duckdns.org' >> .env
sed -i 's|^CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=https://khoatla-ecommerce.duckdns.org,http://localhost:8082,http://localhost:8083,http://localhost:8084,http://localhost:8085,http://localhost:8086|' .env
docker compose up -d
docker compose ps        # tất cả healthy
```

### A2. Tunnel SSH — PowerShell máy cá nhân, GIỮ MỞ SUỐT
```powershell
ssh -N -i $HOME\.ssh\qb_deploy -L 8082:127.0.0.1:8082 -L 8083:127.0.0.1:8083 -L 8084:127.0.0.1:8084 -L 8085:127.0.0.1:8085 -L 8086:127.0.0.1:8086 root@103.74.101.153
```

### A3. Mở 5 tab trình duyệt (mỗi service 1 tab)
| Tab | URL | Dùng cho |
|---|---|---|
| IDENTITY | `http://localhost:8082/swagger-ui/index.html` | register, login, profile, address, role |
| PRODUCT | `http://localhost:8083/swagger-ui/index.html` | brand, category, product |
| ORDER | `http://localhost:8084/swagger-ui/index.html` | **orders VÀ inventory** |
| PAYMENT | `http://localhost:8085/swagger-ui/index.html` | payments, vnpay |
| NOTIF | `http://localhost:8086/swagger-ui/index.html` | notifications |

**Ở MỖI tab:** ô **Servers** chọn `https://khoatla-ecommerce.duckdns.org` (API Gateway). Nếu chọn `http://localhost:808x` là gọi thẳng service, **bỏ qua gateway** → không còn giống Postman (không kiểm được rate limit/routing).

### A4. Cách đặt token (nút Authorize)
1. Gọi login ở tab IDENTITY → copy `data.accessToken`.
2. Ở tab cần dùng bấm **Authorize** → ô `bearerAuth` dán **chỉ token** (KHÔNG gõ chữ `Bearer`, Swagger tự thêm) → **Authorize** → Close.
3. **Mỗi tab Authorize riêng**, và khi đổi vai (admin ↔ customer) phải Authorize lại. Muốn bỏ token: Authorize → **Logout**.

### A5. Data Sheet (copy-paste; khác hẳn Postman để không bị 409)
| Biến | Giá trị |
|---|---|
| ADMIN | `admin` / mật khẩu: trên VPS `grep ADMIN_DEFAULT_PASSWORD /opt/quickbite/.env` |
| CUSTOMER A | `swcustomer01` / `Swagger@123` / `sw.customer01@gmail.com` / `Le Van Cuong` / `0933111222` |
| SELLER | `swseller01` / `Swagger@123` / `sw.seller01@gmail.com` / `Pham Thi Dung` / `0944333444` |
| CUSTOMER B | `swcustomer02` / `Swagger@123` / `sw.customer02@gmail.com` / `Le Van Dat` / `0933111223` |
| EMAIL THẬT (để nhận mail) | `REAL_EMAIL` ← **điền email của bạn** (chưa dùng cho tài khoản khác) |
| SĐT THẬT | `REAL_PHONE` dạng `09xxxxxxxx` và `REAL_PHONE_E164` dạng `+849xxxxxxxx` ← **điền số của bạn** |
| Brand | `Adidas` → sửa thành `Adidas Updated` |
| Category | `Balo & Tui Xach`; con `Balo Laptop` |
| Product | `Adidas Backpack Classic BP`, SKU1 `ADIDAS-BP-BLACK-ONE` (= `skuId`), SKU2 `ADIDAS-BP-NAVY-ONE` |
| Tồn kho | init `ADIDAS-BP-BLACK-ONE` = 100, restock +50 |

Chỉ `REAL_EMAIL`, `REAL_PHONE`, `REAL_PHONE_E164` là bạn phải tự điền (tôi không biết thông tin thật của bạn). Mọi giá trị khác copy nguyên.
Nếu 1 lần test trước đó đã tạo dữ liệu → 409. Đổi số cuối: `swcustomer03`, `sw.customer03@gmail.com`, `0933111224`, SKU `ADIDAS-BP-BLACK-TWO`...

### A6. Bảng ghi biến (Postman tự lưu, Swagger bạn ghi vào đây)
`customerToken` · `adminToken` · `customerBToken` · `userId` (=`data.id` login A) · `userBId` · `addressId` · `brandId` · `categoryId` · `productId` · `skuId`=`ADIDAS-BP-BLACK-ONE` · `orderId` · `orderCode` · `paymentId` · `vnpTxnRef` · `vnpAmount100` · `notificationId` · `offlineOrderId` · `idemOrderId` · `vnpOrderA/B` · `vnpPaymentA/B` · `refreshToken`.

---

## B. Luồng test — theo đúng thứ tự Postman

Quy ước: **Auth** = token cần Authorize ở tab đó. “—” = không cần token.

### 0. Health
| # | Tab/Endpoint | Auth | Kỳ vọng |
|---|---|---|---|
| 0.1 | Trình duyệt: `https://khoatla-ecommerce.duckdns.org/health` | — | `200`, có `UP` |

### 1. Identity (tab IDENTITY)
| # | Endpoint | Auth | Body / tham số | Kỳ vọng | Ghi lại |
|---|---|---|---|---|---|
| 1.1 | `POST /api/users/register` | — | `{"username":"swcustomer01","password":"Swagger@123","email":"sw.customer01@gmail.com","fullName":"Le Van Cuong","phone":"0933111222","role":"CUSTOMER"}` | 201, `success=true` | |
| 1.2 | `POST /api/users/register` | — | `{"username":"swseller01","password":"Swagger@123","email":"sw.seller01@gmail.com","fullName":"Pham Thi Dung","phone":"0944333444","role":"SELLER"}` | 201 | |
| 1.3 | `POST /api/users/login` | — | `{"username":"swcustomer01","password":"Swagger@123"}` | 200, có `accessToken` | `customerToken`, `refreshToken`, `userId`=`data.id` |
| 1.3b | `POST /api/users/login` | — | `{"username":"admin","password":"<ADMIN_DEFAULT_PASSWORD>"}` | 200 | `adminToken` |
| 1.4 | `POST /api/users/refresh-token` | — | `{"refreshToken":"<refreshToken>"}` | 200, có `accessToken` mới | |
| 1.5 | `GET /api/users/validate-token` | — | query `token` = `customerToken` | 200 | |
| 1.6 | `GET /api/users/profile` | customerToken | — | 200 | |
| 1.7 | `PUT /api/users/profile` | customerToken | `{"fullName":"Le Van Cuong Updated","email":"REAL_EMAIL","phone":"REAL_PHONE"}` | 200 | |
| 1.8 | `GET /api/users/addresses` | customerToken | — | 200 (rỗng) | |
| 1.9 | `POST /api/users/addresses` | customerToken | `{"recipientName":"Le Van Cuong","phone":"0933111222","province":"Ha Noi","district":"Quan Cau Giay","ward":"Phuong Dich Vong","addressDetail":"78 Tran Thai Tong","isDefault":true}` | 201 | `addressId`=`data.id` |
| 1.10 | `PUT /api/users/addresses/{id}` (`id`=addressId) | customerToken | `{"recipientName":"Le Van Cuong","phone":"0933111222","province":"Ha Noi","district":"Quan Dong Da","ward":"Phuong Lang Ha","addressDetail":"456 Lang Ha","isDefault":true}` | 200 | |
| 1.11 | `PUT /api/users/addresses/{id}/default` | customerToken | — | 200 | |
| 1.14 | `GET /api/users/profile` | Authorize token = `eyJhbGciOiJIUzI1NiJ9.invalid.token` | — | **401** | sau đó Authorize lại customerToken |

### 2. Brand (tab PRODUCT)
| # | Endpoint | Auth | Body | Kỳ vọng | Ghi lại |
|---|---|---|---|---|---|
| 2.1 | `POST /api/catalog/brands` | **adminToken** | `{"name":"Adidas","logoUrl":"https://example.com/adidas-logo.png","description":"Impossible is Nothing"}` | 201 | `brandId` |
| 2.2 | `PUT /api/catalog/brands/{id}` | adminToken | `{"name":"Adidas Updated","logoUrl":"https://example.com/adidas-logo-v2.png","description":"Impossible is Nothing - Updated"}` | 200 | slug mới từ response |
| 2.3 | `GET /api/catalog/brands/{id}` | — | | 200 | |
| 2.4 | `GET /api/catalog/brands/slug/{slug}` | — | slug = `slug` trong response 2.2 | 200 | |
| 2.5 | `GET /api/catalog/brands` | — | | 200 | |

### 3. Category (tab PRODUCT)
| # | Endpoint | Auth | Body | Kỳ vọng | Ghi lại |
|---|---|---|---|---|---|
| 3.1 | `POST /api/catalog/categories` | adminToken | `{"name":"Balo & Tui Xach","description":"Danh muc balo, tui xach, phu kien thoi trang","parentId":null}` | 201 | `categoryId`, slug |
| 3.2 | `POST /api/catalog/categories` | adminToken | `{"name":"Balo Laptop","description":"Balo chuyen dung cho laptop","parentId":<categoryId>}` | 201 | |
| 3.3 | `GET /api/catalog/categories/{id}` | — | | 200 | |
| 3.4 | `GET /api/catalog/categories/slug/{slug}` | — | slug từ 3.1 | 200 | |
| 3.5 | `GET /api/catalog/categories/roots` | — | | 200 | |
| 3.6 | `GET /api/catalog/categories/{id}/children` | — | | 200, có “Balo Laptop” | |
| 3.7 | `GET /api/catalog/categories` | — | | 200 | |
| 3.8 | `PUT /api/catalog/categories/{id}` | adminToken | `{"name":"Balo & Tui Xach Updated","description":"Mo ta moi","parentId":null}` | 200 | |

### 4. Product (tab PRODUCT) — như Postman, tạo bằng **adminToken** (SELLER cũng được)
| # | Endpoint | Auth | Body | Kỳ vọng | Ghi lại |
|---|---|---|---|---|---|
| 4.1 | `POST /api/catalog/products` | adminToken | xem khối A bên dưới | 201 | `productId`, `skuId`=`variants[0].sku` (phải là `ADIDAS-BP-BLACK-ONE`) |
| 4.2 | `GET /api/catalog/products/{id}` | — | | 200 | |
| 4.3 | `GET /api/catalog/products/slug/{slug}` | — | slug từ response 4.1 | 200 | |
| 4.4 | `GET /api/catalog/products/search` | — | `keyword=Adidas`, `categoryId=<categoryId>`, `page=0`, `size=10`, `sortBy=createdAt`, `sortDir=desc` | 200, thấy sản phẩm | |
| 4.5 | `GET /api/catalog/products/variants/sku/{sku}` | — | `ADIDAS-BP-BLACK-ONE` | 200 | |
| 4.6 | `GET /api/catalog/products/variants/sku/{sku}/sellable-check` | — | `quantity=2` | 200 | |
| 4.7 | `PUT /api/catalog/products/{id}` | adminToken | xem khối B | 200 | |

**Khối A** (4.1) — thay `categoryId`, `brandId` (đang là `0`) bằng số vừa ghi:
```json
{
  "name": "Adidas Backpack Classic BP",
  "description": "Balo Adidas co dien, ngan laptop 15 inch, chat lieu polyester ben bi.",
  "basePrice": 1250000,
  "categoryId": 0,
  "brandId": 0,
  "images": "https://example.com/adidas-backpack.jpg",
  "status": "ACTIVE",
  "variants": [
    {"sku": "ADIDAS-BP-BLACK-ONE", "price": 1250000, "stockQuantity": 100,
     "attributes": "{\"color\": \"Black\", \"size\": \"One Size\"}", "imageUrl": "https://example.com/adidas-bp-black.jpg"},
    {"sku": "ADIDAS-BP-NAVY-ONE", "price": 1300000, "stockQuantity": 50,
     "attributes": "{\"color\": \"Navy\", \"size\": \"One Size\"}", "imageUrl": "https://example.com/adidas-bp-navy.jpg"}
  ]
}
```
**Khối B** (4.7) — giống Postman (chỉ giữ 1 variant), cũng thay `categoryId`, `brandId`:
```json
{
  "name": "Adidas Backpack Classic BP - Special Edition",
  "description": "Phien ban dac biet",
  "basePrice": 1350000,
  "categoryId": 0,
  "brandId": 0,
  "images": "https://example.com/adidas-backpack-se.jpg",
  "status": "ACTIVE",
  "variants": [
    {"sku": "ADIDAS-BP-BLACK-ONE", "price": 1350000, "stockQuantity": 80,
     "attributes": "{\"color\": \"Black\", \"size\": \"One Size\"}", "imageUrl": "https://example.com/adidas-bp-black-se.jpg"}
  ]
}
```

### 5. Inventory (tab **ORDER**, adminToken) — BẮT BUỘC trước khi đặt hàng
> `stockQuantity` của Product **không** tạo tồn kho. Chưa init → đặt đơn lỗi `404 Khong tim thay ton kho cho SKU`.

| # | Endpoint | Auth | Body | Kỳ vọng |
|---|---|---|---|---|
| 5.1 | `POST /api/inventory` | adminToken | `{"sku":"ADIDAS-BP-BLACK-ONE","productName":"Adidas Backpack Classic BP Black","quantity":100}` | 200/201, `availableQty`=**100** |
| 5.2 | `PUT /api/inventory/{sku}/restock` | adminToken | `{"quantity":50}` | 200 (available 150) |
| 5.3 | `GET /api/inventory/{sku}` | adminToken | | 200 |
| 5.4 | `GET /api/inventory` | adminToken | `page=0`, `size=20` | 200 |

### 6. Order (tab ORDER, customerToken) — chuyển Authorize sang customerToken
Mỗi lần tạo đơn cần `idempotencyKey` **mới** (UUID: https://www.uuidgenerator.net hoặc PowerShell `[guid]::NewGuid()`).

| # | Endpoint | Body / tham số | Kỳ vọng | Ghi lại |
|---|---|---|---|---|
| 6.1 | `POST /api/orders` | `{"idempotencyKey":"<UUID mới>","customerId":<userId>,"note":"Giao gio hanh chinh","items":[{"sku":"ADIDAS-BP-BLACK-ONE","quantity":2}]}` | **201**, `status`=`PENDING` | `orderId`=`data.id`, `orderCode` |
| 6.2 | `GET /api/orders/{id}` | id=orderId | 200 | |
| 6.3 | `GET /api/orders/code/{code}` | code=orderCode | 200 | |
| 6.4 | `GET /api/orders` | `page=0`, `size=10` | 200, chỉ đơn của A | |

`customerId` bị service ghi đè theo token với người dùng thường; vẫn điền đúng `userId` thật cho khớp Postman.

### 7. Payment (tab PAYMENT, Servers = gateway)

#### 7.0 Cấu hình VNPay trước khi test (TMN code + Hash secret)
**Vì sao bắt buộc.** Compose truyền `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` vào payment-billing-service; để trống thì giá trị là **chuỗi rỗng**. Theo code (`VnPayUtils.hmacSHA512`), khóa rỗng làm việc ký chữ ký lỗi, nên (suy ra từ đọc code, chưa chạy thật):
| Bước | `.env` trống | Đã cấu hình |
|---|---|---|
| 7.2 `vnpay-url` | lỗi 5xx (không phải 200) | 200, có `paymentUrl` |
| 7.5 `query` | lỗi | 200 nếu credential VNPay thật; 4xx/5xx nếu secret tự đặt |
| 7.8 IPN giả | `RspCode=99` hoặc 5xx | `RspCode=97`, khác 401 |
| 7.9 Return giả | lỗi 5xx (không phải 403) | **403** |
| Mục 12 (IPN ký HMAC) | không dùng được | chạy được |
Các bước **không** cần cấu hình: 7.1, 7.3, 7.4, 7.6 (400 vì payment chưa SUCCESS, kiểm tra trạng thái trước khi gọi VNPay), 7.7.

**Bước 1 — Đăng ký Merchant sandbox** tại `https://sandbox.vnpayment.vn/devreg/` ("ĐĂNG KÝ MERCHANT MÔI TRƯỜNG TEST"). Form có 6 ô theo thứ tự trên màn hình (nhãn ô bị che bởi nội dung, thứ tự suy ra từ icon/vị trí):
| Ô | Điền |
|---|---|
| 1. Tên merchant/website | `Omnichannel_ECommerce` |
| 2. URL website | `https://khoatla-ecommerce.duckdns.org` |
| 3. Email nhận thông tin | email của bạn **đang dùng được** (VNPay gửi thông tin cấp phát về đây) |
| 4–5. Mật khẩu + nhập lại | mật khẩu **riêng** cho cổng VNPay (không dùng lại mật khẩu admin, SSH, Gmail) |
| 6. Mã xác nhận | gõ đúng chữ trong ảnh captcha (bấm biểu tượng làm mới nếu khó đọc), rồi **Đăng ký** |
Điền như màn hình bạn gửi là **hợp lý**: tên, URL HTTPS đúng domain production, email, hai ô mật khẩu, captcha.

**Bước 2 — Nhận credential.** Theo chú thích trong `application.yml`, sau khi VNPay duyệt họ cấp **`vnp_TmnCode`** (mã website) và **`vnp_HashSecret`** (khóa bí mật). Kiểm tra hộp thư (cả Spam). Mình chưa thể xác nhận nội dung email hay giao diện cổng merchant hiện tại của VNPay, nên nếu thấy khác với mô tả này, theo hướng dẫn trên email của VNPay. Chưa có credential → dùng **Chế độ B** bên dưới và ghi **BLOCKED** cho 7.5, 7.10.

**Bước 3A — Có credential thật (test thanh toán thật + mục 12).** Trên VPS:
```bash
ssh root@<IP VPS>
cd /opt/quickbite
nano .env      # điền/sửa các dòng sau, KHÔNG dán secret lên chat/git
#   VNPAY_TMN_CODE=<TmnCode VNPay cấp>
#   VNPAY_HASH_SECRET=<HashSecret VNPay cấp>
#   VNPAY_RETURN_URL=https://khoatla-ecommerce.duckdns.org/api/payments/vnpay/return
#   VNPAY_IPN_URL=https://khoatla-ecommerce.duckdns.org/api/payments/vnpay/ipn
chmod 600 .env
docker compose up -d payment-billing-service        # recreate để nhận biến mới
```
Kiểm tra đã nhận (không in secret):
```bash
docker exec payment-billing-service sh -c 'echo "TMN=$VNPAY_TMN_CODE"; [ -n "$VNPAY_HASH_SECRET" ] && echo "HASH_SECRET=da-co-gia-tri" || echo "HASH_SECRET=TRONG"'
docker compose ps payment-billing-service            # healthy
```
Trên cổng merchant sandbox của VNPay, khai báo IPN URL = `https://khoatla-ecommerce.duckdns.org/api/payments/vnpay/ipn` (vị trí menu tùy giao diện VNPay).
**Về IP:** code chỉ nhận IPN từ danh sách IP mặc định của VNPay (+ loopback). Nếu IPN thật bị từ chối (log có `khong thuoc danh sach IP`), thêm tạm `VNPAY_IPN_ALLOWED_IPS=*` vào `.env`, recreate service, test xong **xóa dòng đó**.

**Bước 3B — Chưa có credential (chỉ mô phỏng, KHÔNG thanh toán thật được).** Chỉ để chạy 7.8, 7.9 và mục 12 (bước ký IPN gọi thẳng server của bạn, không gọi VNPay):
```bash
cd /opt/quickbite
echo "VNPAY_HASH_SECRET=$(openssl rand -hex 24)" >> .env     # khóa TỰ SINH, chỉ dùng nội bộ
echo "VNPAY_TMN_CODE=LOCALTEST" >> .env
echo "VNPAY_IPN_ALLOWED_IPS=*" >> .env                        # tạm, mô phỏng IPN từ máy cá nhân
docker compose up -d payment-billing-service
grep VNPAY_HASH_SECRET .env      # copy giá trị này vào script ipn.ps1 / biến Postman vnpHashSecret
```
Ở chế độ này 7.2 trả `paymentUrl` nhưng không thanh toán được (TmnCode không có thật). Test xong xóa 3 dòng trên khỏi `.env` rồi recreate service.

#### 7.1–7.9 (tab PAYMENT)
| # | Endpoint | Auth | Cần VNPAY config? | Kỳ vọng (đã cấu hình) | Ghi lại |
|---|---|---|---|---|---|
| 7.1 | `GET /api/payments/order/{orderId}` | customerToken | Không | Payment do Kafka tạo bất đồng bộ: **Execute lại mỗi ~5 giây (tối đa 20 lần)** tới khi `200` có `id`; `status`=`PENDING` | `paymentId`, `vnpTxnRef`, `amount` → `vnpAmount100` = amount×100 |
| 7.2 | `POST /api/payments/order/{orderId}/vnpay-url` | customerToken | **Có** | 200, có `paymentUrl` | |
| 7.3 | `GET /api/payments/{paymentId}` | customerToken | Không | 200 | |
| 7.4 | `GET /api/payments` (`page=0`, `size=10`) | customerToken | Không | 200 | |
| 7.5 | `GET /api/payments/{paymentId}/query` | customerToken | **Có** (credential thật) | 200 nếu sandbox thật; 400/500/502/503 nếu secret tự đặt | |
| 7.6 | `POST /api/payments/{paymentId}/refund` | **adminToken** | Không | body `{"amount":2500000,"reason":"Khach hang doi y, huy don theo yeu cau","createBy":"admin"}` → **400** (payment chưa SUCCESS) | |
| 7.7 | `GET /api/payments/{paymentId}/logs` | customerToken | Không | 200 | |
| 7.8 | `GET /api/payments/vnpay/ipn` (query `vnp_TxnRef=TEST-ORDER-001`, `vnp_ResponseCode=00`, `vnp_SecureHash=FAKE`) | — | **Có** (secret ≠ rỗng) | HTTP 200, `RspCode=97`; chỉ cần **khác 401** | |
| 7.9 | `GET /api/payments/vnpay/return` (query `vnp_TxnRef=TEST`, `vnp_ResponseCode=00`, `vnp_SecureHash=FAKE`) | — | **Có** (secret ≠ rỗng) | **403** | |

#### 7.10 Thanh toán VNPay sandbox THẬT (cần Bước 3A với credential thật)
1. Lấy `paymentUrl` ở 7.2, dán vào trình duyệt thường (hết hạn sau 15 phút).
2. Chọn **Thẻ nội địa NCB** (thẻ test công khai của VNPay sandbox — đối chiếu lại với tài liệu VNPay đang hiển thị): số thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.
3. Trình duyệt được redirect về `/api/payments/vnpay/return?...vnp_ResponseCode=00`.
4. `GET /api/payments/order/{orderId}` → `status=SUCCESS` **chỉ khi VNPay gọi được IPN về VPS** (đã khai báo IPN URL ở cổng VNPay và IP của VNPay được chấp nhận, xem 7.0). Nếu vẫn `PENDING`: `docker logs --since 10m payment-billing-service | grep -iE "ipn|vnpay"`. Order (tab ORDER) → `CONFIRMED`.
5. Thử lại `refund` (7.6) bằng adminToken khi payment đã SUCCESS: sandbox có thể từ chối, ghi nguyên mã trả về. Nếu thành công: sau vài giây `GET /api/orders/{id}` (tab ORDER) phải là **REFUNDED** (hoàn đủ) hoặc **PARTIALLY_REFUNDED**, và `GET /api/notifications/logs?orderId={id}` có loại `PAYMENT_REFUNDED`. Kho không tự đổi (restock thủ công: `PUT /api/inventory/{sku}/restock`).

### 8. Notification (tab NOTIF, **adminToken**)
| # | Endpoint | Body | Kỳ vọng | Ghi lại |
|---|---|---|---|---|
| 8.1 | `POST /api/notifications/test` | `{"channel":"EMAIL","recipient":"REAL_EMAIL","subject":"Test Email tu QuickBite","body":"Day la email test tu Notification Service."}` | **201**; kiểm tra hộp thư thật | `notificationId`=`data.id` |
| 8.2 | `POST /api/notifications/test` | `{"channel":"SMS","recipient":"REAL_PHONE_E164","body":"Test SMS tu QuickBite Notification Service."}` | 201; SMS thật về máy | |
| 8.3 | `POST /api/notifications/test` | `{"channel":"PUSH","recipient":"customer:123","subject":"Test Push","body":"Push notification mo phong (log console)."}` | 201 | |
| 8.4 | `GET /api/notifications/logs/{id}` | id=notificationId | 200 | |
| 8.5 | `GET /api/notifications/logs` | `page=0`, `size=20` | 200 | |
| 8.6 | `GET /api/notifications/logs` | `channel=EMAIL`, `status=SENT`, `page=0`, `size=20` | 200 | |
| 8.7 | `GET /api/notifications/logs` | `orderId=<orderId>`, `page=0`, `size=20` | 200, có log đơn | |

Email/SMS thật cần `.env` VPS: `NOTIFICATION_MAIL_ENABLED=true` + `MAIL_*`, `NOTIFICATION_SMS_ENABLED=true` + `TWILIO_*`; sửa xong `docker compose up -d notification-service`. Không có credential → ghi **BLOCKED**, không bịa.

### 9. Bổ sung kiểm thử (trùng dữ liệu, ownership, hết hàng)
| # | Tab / Endpoint | Auth | Body | Kỳ vọng |
|---|---|---|---|---|
| 9.1 | IDENTITY `POST /api/users/register` | — | `{"username":"swcustomer02","password":"Swagger@123","email":"sw.customer02@gmail.com","fullName":"Le Van Dat","phone":"0933111223","role":"CUSTOMER"}` | 201 (hoặc 409 nếu đã chạy trước) |
| 9.2 | IDENTITY `POST /api/users/login` | — | `{"username":"swcustomer02","password":"Swagger@123"}` | 200 → `customerBToken`, `userBId` |
| 9.3 | IDENTITY `POST /api/users/register` | — | `{"username":"swseller01","password":"Swagger@123","email":"sw.dup.user@gmail.com","fullName":"Test User","phone":"0900000013","role":"CUSTOMER"}` | **409** trùng username |
| 9.4 | IDENTITY `POST /api/users/register` | — | `{"username":"swdup_email","password":"Swagger@123","email":"sw.seller01@gmail.com","fullName":"Test User","phone":"0900000014","role":"CUSTOMER"}` | **409** trùng email |
| 9.5 | IDENTITY `POST /api/users/register` | — | `{"username":"swdup_phone","password":"Swagger@123","email":"sw.dup.phone@gmail.com","fullName":"Test User","phone":"0944333444","role":"CUSTOMER"}` | **409** trùng phone |
| 9.6 | ORDER `GET /api/orders/{orderId}` | **customerBToken** (đơn của A) | | **403** |
| 9.7 | ORDER `PUT /api/orders/{orderId}/cancel` | customerBToken | `{"reason":"B co huy don cua A"}` | **403** |
| 9.8 | ORDER `POST /api/orders` | customerToken | `{"idempotencyKey":"<UUID mới>","customerId":<userId>,"items":[{"sku":"ADIDAS-BP-BLACK-ONE","quantity":999999}]}` | **409** hết hàng |
| 9.9 | ORDER `GET /api/inventory/ADIDAS-BP-BLACK-ONE` | adminToken | | 200; `availableQty`+`reservedQty` > 0, không đổi do đơn bị từ chối |

### 10. Kịch bản Demo theo đề bài
| # | Việc | Kỳ vọng |
|---|---|---|
| 10.1 | ORDER `GET /api/inventory/ADIDAS-BP-BLACK-ONE` (admin) | ghi `stockBefore`=`availableQty` |
| 10.2a | **VPS:** `cd /opt/quickbite && docker compose stop notification-service` | |
| 10.2b | ORDER `POST /api/orders` (customerToken) `{"idempotencyKey":"<UUID mới>","customerId":<userId>,"note":"demo tat Notification","items":[{"sku":"ADIDAS-BP-BLACK-ONE","quantity":1}]}` | **201** dù Notification đã tắt → ghi `offlineOrderId` |
| 10.3 | NOTIF `GET /api/notifications/logs` (admin) | **502/503/504** chỉ riêng Notification — Swagger tab NOTIF đã mất kết nối vì service tắt, nên gọi qua gateway bằng PowerShell (xem ghi chú ngay dưới bảng) |
| 10.4a | **VPS:** `docker compose start notification-service`, chờ 1–3 phút tới `healthy` (`docker compose ps`) | |
| 10.4b | NOTIF `GET /api/notifications/logs?orderId=<offlineOrderId>&page=0&size=20` | `200` có log (Notification nhận event tồn đọng); chưa thấy thì Execute lại mỗi 5 giây (tối đa 20 lần) |
| 10.5 | ORDER `POST /api/orders` (customer) `{"idempotencyKey":"fixed-key-sw-0001","customerId":<userId>,"items":[{"sku":"ADIDAS-BP-BLACK-ONE","quantity":1}]}` | 200/201 → ghi `idemOrderId` |
| 10.6 | Gửi **lại y nguyên** body 10.5 | trả **cùng** `id` = `idemOrderId` |
| 10.7 | ORDER `PUT /api/orders/{idemOrderId}/cancel` `{"reason":"demo saga"}` | 200; tồn kho hoàn lại (kiểm tra `availableQty`) |

> Ghi chú 10.3: khi Notification tắt, cổng tunnel `localhost:8086` không còn service nào để trả lời, còn mục tiêu của bước này là kiểm tra **qua gateway**. Dùng PowerShell 7 (hoặc `curl.exe`):
> `Invoke-WebRequest https://khoatla-ecommerce.duckdns.org/api/notifications/logs -Headers @{Authorization="Bearer <adminToken>"} -SkipHttpErrorCheck | Select-Object StatusCode`
> Kỳ vọng 502/503/504. Trong khi đó đơn ở 10.2b vẫn tạo được.

### 11. Security & Edge cases
| # | Việc | Kỳ vọng |
|---|---|---|
| 11.1 | ORDER Authorize → **Logout**, `GET /api/orders` | **401** |
| 11.2 | ORDER Authorize token = `eyJhbGciOiJIUzI1NiJ9.invalid.token`, `GET /api/orders` | **401** |
| 11.3 | PRODUCT Authorize **customerToken**, `POST /api/catalog/brands` `{"name":"Fake Brand"}` | **403** |
| 11.4 | Rate limit login: **chạy script C2** (Swagger không bắn song song được) | có **429** |
| 11.5 | Notification đang tắt → xem 10.3 | 502/503 |
| 11.6 | ORDER `POST /api/orders` (customer) `{"idempotencyKey":"<UUID mới>","customerId":<userId>,"items":[]}` | **400** |

### 12. VNPay giả lập IPN — Saga thanh toán
Cần trước: làm xong **mục 7.0** (Bước 3A hoặc 3B): `.env` có `VNPAY_HASH_SECRET` và **tạm** `VNPAY_IPN_ALLOWED_IPS=*` (xong nhớ xóa), đã `docker compose up -d payment-billing-service`. Secret dùng để ký ở script C1 phải **bằng đúng** `VNPAY_HASH_SECRET` trên VPS. Dùng **script C1** để ký HMAC-SHA512 (Swagger không tính được).

| # | Việc | Kỳ vọng |
|---|---|---|
| 12.V1 | ORDER `GET /api/inventory/{sku}` (admin) | ghi `vnpStockBefore`=`availableQty` |
| 12.V2 | ORDER `POST /api/orders` (customer) body `{"idempotencyKey":"<UUID mới>","customerId":<userId>,"note":"VNPay IPN thanh cong","items":[{"sku":"ADIDAS-BP-BLACK-ONE","quantity":1}]}` | 201 → `vnpOrderA` |
| 12.V3 | PAYMENT `GET /api/payments/order/{vnpOrderA}` (customer), lặp tới có `200` | ghi `vnpTxnRef`, `amount`→`vnpAmount100`, `id`→`vnpPaymentA` |
| 12.V4 | **Script C1** (không `-Repeat`) với `-Code 00 -Status 00` | `RspCode = 00` |
| 12.V5 | ORDER `GET /api/orders/{vnpOrderA}` (customer), lặp mỗi 4 giây (≤20 lần) | `status` = **CONFIRMED** |
| 12.V6 | Chạy script C1 với **`-Repeat`** (gọi lại đúng query V4) | `RspCode = 02` (idempotent) |
| 12.V7 | PAYMENT `POST /api/payments/{vnpPaymentA}/refund` với **customerToken** `{"amount":1000,"reason":"customer khong duoc phep","createBy":"customer"}` | **403** |
| 12.V8 | ORDER `GET /api/inventory/{sku}` (admin) | ghi `vnpStockMid`=`availableQty` |
| 12.V9 | ORDER `POST /api/orders` (customer) `{"idempotencyKey":"<UUID mới>","customerId":<userId>,"note":"VNPay IPN that bai","items":[{"sku":"ADIDAS-BP-BLACK-ONE","quantity":1}]}` | 201 → `vnpOrderB` |
| 12.V10 | PAYMENT `GET /api/payments/order/{vnpOrderB}` lặp tới `200` | ghi `vnpTxnRef`, `vnpAmount100`, `vnpPaymentB` |
| 12.V11 | **Script C1** (đơn B) với `-Code 24 -Status 02` | `RspCode = 00` |
| 12.V12 | ORDER `GET /api/orders/{vnpOrderB}` lặp tới | `status` = **CANCELLED** |
| 12.V13 | ORDER `GET /api/inventory/{sku}` (admin) | `availableQty` **bằng** `vnpStockMid` (Saga hoàn kho) |
| 12.V14 | PAYMENT `POST /api/payments/{vnpPaymentB}/refund` (**adminToken**) `{"amount":1000,"reason":"payment that bai khong the hoan","createBy":"admin"}` | **400** |
| 12.V15 | **Script C1** với `-Bad` (chữ ký sai) trên `vnpTxnRef` | `RspCode = 97` |

### 13. Test sự cố hạ tầng: Kafka, Outbox, restart, trùng sự kiện, Saga
Chạy **sau mục 12** và **trước Cleanup** (khi còn địa chỉ mặc định). Mỗi lần đặt đơn dùng đúng body **6.1** nhưng với `idempotencyKey` **mới** (UUID). Tab Swagger vẫn dùng bình thường vì chỉ tắt Kafka hoặc consumer (không tắt Order). Mục này **không** có trong Postman và cũng không tự động hoá được; đây là kịch bản thủ công (Swagger + VPS).

**13.0 Chuẩn bị (VPS, chạy 1 lần mỗi phiên SSH):**
```bash
cd /opt/quickbite
dc(){ docker compose -f docker-compose.production.yml "$@"; }      # KHONG phu thuoc COMPOSE_FILE trong .env
q_ord(){ docker exec postgres psql -U order_inventory -d order_inventory_db -c "$1"; }
q_pay(){ docker exec postgres psql -U payment_billing -d payment_billing_db -c "$1"; }
q_ntf(){ docker exec postgres psql -U notification -d notification_db -c "$1"; }
dlt(){ docker exec kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic "$1" 2>&1 | tail -3; }
```
Ghi số **trước khi bắt đầu** (để đối chiếu cuối mục):
```bash
q_ord "select status,count(*) from outbox_events group by status"
q_pay "select count(*) from payments"
q_ntf "select count(*) from notification_logs"
dlt order.created.DLT          # offset hien tai cua DLT (neu topic chua ton tai: bo qua)
```

**13.1 Kafka tắt NGẮN (khoảng 60–90 giây): đơn vẫn tạo được, Outbox giữ sự kiện, Kafka bật lại thì sự kiện không mất**
| # | Việc | Kỳ vọng |
|---|---|---|
| 13.1a | **VPS:** `dc stop kafka` | |
| 13.1b | ORDER `POST /api/orders` (customerToken, body 6.1, `idempotencyKey` mới) | **201** (đơn lưu DB, sự kiện nằm ở Outbox). Ghi `kafkaOrderId`. Nếu 5xx: ghi nguyên mã và `dc logs --since 2m order-inventory-service` (đó là kết quả thật) |
| 13.1c | **VPS:** `q_ord "select id,aggregate_id,status,retry_count from outbox_events order by id desc limit 3"` | dòng của `kafkaOrderId` ở `PENDING` (hoặc `retry_count` tăng dần) |
| 13.1d | Chờ đủ 60–90 giây rồi **VPS:** `dc start kafka`; `watch -n 5 'docker compose -f docker-compose.production.yml ps kafka'` tới `healthy` | |
| 13.1e | Chờ 1–2 phút, **VPS:** `q_ord "select status,count(*) from outbox_events group by status"` | không còn `PENDING`, dòng đó là `PUBLISHED` |
| 13.1f | **VPS:** `q_pay "select order_id,status from payments order by id desc limit 3"` | có Payment của `kafkaOrderId` (`PENDING`), tổng tăng đúng 1 |
| 13.1g | **VPS:** `q_ntf "select order_id,channel,status from notification_logs where order_id=<kafkaOrderId>"` | 3 dòng (EMAIL, SMS, PUSH) |
PASS khi: đơn tạo được lúc Kafka tắt, Outbox về `PUBLISHED`, Payment và Notification đều xử lý **đúng một lần**.

**13.1h Kafka tắt DÀI (tuỳ chọn, 10 phút trở lên) — kiểm tra Outbox không bao giờ mất event (cần bản có `fix3`)**
Bản trước `fix3` có `MAX_RETRY = 5`: sau 5 lần publish thất bại event chuyển `FAILED` và không bao giờ được gửi lại. Bản `fix3` bỏ giới hạn đó: lỗi tạm thời luôn giữ `PENDING`, publisher tạm dừng theo backoff (1s, 2s, 4s… tối đa 30s) và chỉ gửi thăm dò 1 event cho tới khi Kafka phục hồi.
| # | Việc | Kỳ vọng |
|---|---|---|
| 13.1h-a | **VPS:** `dc stop kafka`, giữ tắt **10 phút** | |
| 13.1h-b | ORDER `POST /api/orders` (body 6.1, `idempotencyKey` mới) lúc đầu, rồi thêm 1 đơn nữa sau 5 phút | cả hai **201** (Order không phụ thuộc Kafka). Ghi `longOrderId1`, `longOrderId2` |
| 13.1h-c | **VPS:** `q_ord "select id,aggregate_id,status,retry_count from outbox_events order by id desc limit 4"` | `status` luôn **PENDING**, `retry_count` tăng dần, **không có `FAILED`** |
| 13.1h-d | **VPS:** chạy khối lệnh ngay dưới bảng | thấy dòng "Kafka loi tam thoi ... tam dung N ms" với N tăng tới 30000; không có ERROR "that bai sau" |
| 13.1h-e | **VPS:** `dc start kafka`, đợi `healthy`, chờ 1–2 phút | log có "Kafka da phuc hoi"; cả hai event `PUBLISHED` đúng thứ tự id |
| 13.1h-f | **VPS:** `q_pay "select order_id,status from payments order by id desc limit 3"` và `q_ntf "select order_id,count(*) from notification_logs group by order_id order by order_id desc limit 3"` | có Payment cho `longOrderId1` và `longOrderId2`; mỗi đơn 3 log Notification |
| 13.1h-g | **VPS:** `docker stats --no-stream` và `q_ord "select count(*) from pg_stat_activity where datname='order_inventory_db'"` trong lúc Kafka còn tắt | không có container `OOMKilled`; số kết nối DB không tăng bất thường (breaker chỉ cho 1 event thăm dò) |
```bash
dc logs --since 10m order-inventory-service | grep -i "Outbox"
```
Nếu cần thử lại trường hợp event đã `FAILED` từ bản cũ: bản `fix3` tự đưa các event `FAILED` có `retry_count >= 5` về `PENDING` đúng 1 lần khi service khởi động (log "da dua N event FAILED ... ve PENDING").
Event bị chuyển `FAILED` ngay (không retry) chỉ khi lỗi vĩnh viễn: payload không phải JSON, record quá lớn, serialization lỗi, topic không hợp lệ. Kiểm tra: `q_ord "select id,event_type,status,retry_count from outbox_events where status='FAILED'"`, kết quả đúng là 0 dòng.

**13.2 Restart consumer: sự kiện được retry, không tạo dữ liệu trùng**
| # | Việc | Kỳ vọng |
|---|---|---|
| 13.2a | ORDER `POST /api/orders` (body 6.1, `idempotencyKey` mới) → ghi `restartOrderId` | 201 |
| 13.2b | **VPS (ngay sau 13.2a):** `dc restart payment-billing-service`; đợi `healthy` | |
| 13.2c | **VPS:** `q_pay "select id,order_id,status from payments where order_id=<restartOrderId>"` | đúng **1** dòng (chờ 1–2 phút nếu chưa có: event được xử lý sau khi service lên) |
| 13.2d | **VPS:** lặp 13.2a–13.2c với `notification-service`: `dc restart notification-service`, rồi `q_ntf "select order_id,channel,status from notification_logs where order_id=<id mới>"` | đúng 3 dòng |
| 13.2e | **VPS:** chạy khối lệnh ngay dưới bảng | offset DLT **không tăng** so với 13.0; không có ERROR mới |
```bash
dlt order.created.DLT
dc logs --since 10m payment-billing-service notification-service | grep -iE "ERROR|Exception" | tail
```
Lưu ý: bảng `payments` có ràng buộc UNIQUE `order_id` và `notification_logs` có UNIQUE `idempotency_key`, nên truy vấn "tìm dòng trùng" luôn ra 0 dòng dù code xử lý thế nào. Vì vậy bằng chứng chính là **DLT không tăng** và **log không có lỗi**.

**13.3 Trùng sự kiện (duplicate event) — phát lại trực tiếp**
Lấy đúng payload của một đơn đã xử lý và gửi lại vào topic `order.created` (thay `<id>` bằng `kafkaOrderId` hoặc `restartOrderId`):
```bash
docker exec postgres psql -U order_inventory -d order_inventory_db -Atc \
 "select payload from outbox_events where aggregate_id='<id>' and event_type='OrderCreatedEvent'" \
 | docker exec -i kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic order.created
sleep 20
q_pay "select count(*) from payments where order_id=<id>"                     # van = 1
q_ntf "select count(*) from notification_logs where order_id=<id>"           # van = 3
dc logs --since 2m payment-billing-service | grep -i "da ton tai"            # Payment bo qua event trung
dlt order.created.DLT                                                       # khong tang
```
Kỳ vọng theo code: Payment ghi log "đã tồn tại - bỏ qua" và không tạo dòng mới; Notification dùng khoá `idempotency_key` (loại + orderId + kênh) nên không thêm log. (Hành vi này mình đọc từ code, chưa chạy thật.) Nếu event trùng bị đẩy sang DLT, ghi lại: dữ liệu vẫn không trùng nhờ ràng buộc UNIQUE.
Bằng chứng bổ sung ở tầng API: đơn hàng trùng `idempotencyKey` (mục 10.5–10.6) và IPN lặp lại trả `RspCode 02` (mục 12.V6).

**13.4 Saga compensation**
Làm lại có kiểm tra đối chiếu số: 10.7 (huỷ đơn PENDING hoàn kho), 12.V12–12.V13 (thanh toán thất bại → đơn `CANCELLED` + hoàn kho, `availableQty` bằng `vnpStockMid`), và 7.10 nếu có VNPay sandbox thật. Kiểm tra DB sau khi huỷ: `q_ord "select sku,available_qty,reserved_qty from inventories where sku='ADIDAS-BP-BLACK-ONE'"` (`available_qty` quay về đúng giá trị trước khi đặt đơn đó).

**Bảng kết quả mục 13 (điền khi chạy thật; không điền PASS nếu chưa có bằng chứng)**
| Mục | Kết quả (PASS/FAIL/BLOCKED) | Bằng chứng (lệnh + output) |
|---|---|---|
| 13.1 Kafka tắt ngắn | | |
| 13.1h Kafka tắt dài (cần bản `fix3`) | | |
| 13.2 Restart payment / notification | | |
| 13.3 Phát lại event trùng | | |
| 13.4 Saga hoàn kho | | |

### 14. Cleanup (chạy CUỐI CÙNG, theo thứ tự này)
| # | Tab / Endpoint | Auth | Kỳ vọng |
|---|---|---|---|
| 14.1 | IDENTITY `DELETE /api/users/addresses/{addressId}` | customerToken | 200/204 |
| 14.2 | ORDER `PUT /api/orders/{orderId}/cancel` `{"reason":"Toi muon doi dia chi giao hang"}` | customerToken | 200 (hoặc 4xx nếu đơn đã thanh toán/hủy, ghi mã) |
| 14.3 | IDENTITY `PUT /api/users/{userId}/role` query `role=SELLER` | adminToken | 200 |
| 14.4 | PRODUCT `DELETE /api/catalog/products/{productId}` | adminToken | 200/204 |
| 14.5 | PRODUCT `DELETE /api/catalog/categories/{categoryId}` | adminToken | 200/204 (xóa Category con trước nếu bị chặn) |
| 14.6 | PRODUCT `DELETE /api/catalog/brands/{brandId}` | adminToken | 200/204 |

---

## C. Script thay phần Postman làm tự động (PowerShell, máy cá nhân)

### C1. `ipn.ps1` — ký và gọi IPN (thay pre-request script V4/V6/V11/V15)
Secret = `VNPAY_HASH_SECRET` trong `.env` VPS (`grep VNPAY_HASH_SECRET /opt/quickbite/.env`); đừng commit. `-Repeat` gọi lại **đúng query lần trước** (như Postman V6 dùng lại `ipnQuery`).

```powershell
param(
  [string]$Secret, [string]$TxnRef, [string]$Amount100,
  [string]$Code = '00', [string]$Status = '00',
  [switch]$Bad, [switch]$Repeat,
  [string]$Base = 'https://khoatla-ecommerce.duckdns.org'
)
$last = Join-Path $env:TEMP 'ipn_last_url.txt'
if ($Repeat) {
  $url = Get-Content $last -Raw
} else {
  if (-not ($Secret -and $TxnRef -and $Amount100)) { throw 'Thieu -Secret -TxnRef -Amount100' }
  $p = @{
    vnp_Amount = $Amount100; vnp_BankCode = 'NCB'; vnp_CardType = 'ATM'
    vnp_PayDate = (Get-Date -Format 'yyyyMMddHHmmss'); vnp_ResponseCode = $Code
    vnp_TransactionNo = ('14' + ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()).Substring(5))
    vnp_TransactionStatus = $Status; vnp_TxnRef = $TxnRef
  }
  $keys = [string[]]$p.Keys; [Array]::Sort($keys, [StringComparer]::Ordinal)
  $hashData = ($keys | ForEach-Object { "$_=" + [uri]::EscapeDataString($p[$_]) }) -join '&'
  $h = New-Object System.Security.Cryptography.HMACSHA512 (,[Text.Encoding]::UTF8.GetBytes($Secret))
  $sig = -join ($h.ComputeHash([Text.Encoding]::UTF8.GetBytes($hashData)) | ForEach-Object { $_.ToString('x2') })
  if ($Bad) { $sig = 'deadbeef' }
  $url = "$Base/api/payments/vnpay/ipn?$hashData&vnp_SecureHash=$sig"
  Set-Content -Path $last -Value $url -NoNewline
}
Invoke-RestMethod -Uri $url -Method Get | ConvertTo-Json
```
Ví dụ:
```powershell
.\ipn.ps1 -Secret "<VNPAY_HASH_SECRET>" -TxnRef "<vnpTxnRef đơn A>" -Amount100 "<vnpAmount100 đơn A>" -Code 00 -Status 00   # 12.V4 -> RspCode 00
.\ipn.ps1 -Repeat                                                                                                      # 12.V6 -> RspCode 02
.\ipn.ps1 -Secret "<VNPAY_HASH_SECRET>" -TxnRef "<vnpTxnRef đơn B>" -Amount100 "<vnpAmount100 đơn B>" -Code 24 -Status 02 # 12.V11 -> 00
.\ipn.ps1 -Secret "x" -TxnRef "<vnpTxnRef>" -Amount100 "1000" -Bad                                                      # 12.V15 -> 97
```
Giá trị trong `<...>` ở lệnh script là biến runtime bạn ghi ở A6 (không phải dữ liệu cố định), và secret lấy từ VPS.

### C2. `ratelimit.ps1` — 15 login song song, kiểm tra 429 (thay 11.4)
```powershell
$base = 'https://khoatla-ecommerce.duckdns.org'
$jobs = 1..15 | ForEach-Object {
  Start-Job -ArgumentList $base -ScriptBlock {
    param($b)
    try {
      (Invoke-WebRequest -Uri "$b/api/users/login" -Method Post -ContentType 'application/json' `
        -Body '{"username":"x","password":"y"}' -UseBasicParsing).StatusCode
    } catch { [int]$_.Exception.Response.StatusCode }
  }
}
$codes = $jobs | Receive-Job -Wait -AutoRemoveJob
$codes | Group-Object | Select-Object Name, Count
```
Đạt khi trong kết quả có mã **429** (login 5 req/s, burst 10 mặc định).

---

## D. Đối chiếu Swagger ↔ Postman
- Swagger đi qua **cùng API Gateway** (server production) nên kết quả nghiệp vụ giống Postman nếu cùng request.
- Postman tự động, Swagger làm tay: lưu biến, retry (7.1, 10.4b, 12.V3/V5/V10/V12), `{{$guid}}`.
- Phải dùng script: IPN HMAC (C1), 15 request song song (C2).
- Phải dùng lệnh VPS: stop/start Notification (10.2a, 10.4a), `.env` (email/SMS/VNPay).
- Test sự cố hạ tầng (Kafka tắt/bật, restart consumer, phát lại event trùng): **mục 13** của guide này — làm tay trên VPS, không có trong Postman.

## E. Lỗi thường gặp
| Lỗi | Nguyên nhân | Cách xử lý |
|---|---|---|
| Không thấy nút Authorize | Chưa deploy bản có `bearerAuth` | Push bản mới, chờ Actions |
| `401` dù đã Authorize | Gõ thừa chữ `Bearer`, hoặc Authorize ở tab khác | Dán chỉ token, Authorize đúng tab |
| `Failed to fetch` / CORS | `CORS_ALLOWED_ORIGINS` thiếu `http://localhost:808x`, hoặc chọn nhầm server | Sửa `.env`, recreate `api-gateway`; chọn server production |
| Tunnel `Connection refused` | Chưa deploy `ports 127.0.0.1:808x` | Deploy compose mới |
| `403` ở Brand/Category | Dùng token CUSTOMER/SELLER | Dùng **adminToken** |
| `404 Khong tim thay ton kho` | Chưa init inventory | Làm Mục 5 |
| `409` | Dữ liệu trùng | Đổi số cuối (A5) |
| IPN trả `97` ngoài ý muốn | Sai secret hoặc sai `vnp_Amount` | Secret = `VNPAY_HASH_SECRET` VPS; amount = `amount`×100 |
| IPN bị từ chối | IP ngoài whitelist | Tạm `VNPAY_IPN_ALLOWED_IPS=*` |
| 7.2 trả 5xx, 7.9 không ra 403 | `VNPAY_HASH_SECRET` (hoặc TMN code) trong `.env` đang trống | Làm mục 7.0, recreate `payment-billing-service` |
| 7.10 `paymentUrl` mở ra báo lỗi merchant | `VNPAY_TMN_CODE` sai/chưa phải credential thật | Dùng đúng TmnCode VNPay cấp (Bước 3A) |
