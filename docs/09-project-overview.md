# 🚀 TỔNG QUAN & KỊCH BẢN DEMO — QuickBite Omnichannel E-Commerce

> **Tech stack**: Spring Boot 4.1.1 (Java 17) · Spring Cloud Gateway · Kafka KRaft · PostgreSQL 16 · Redis 7 · gRPC · Docker Compose · GitHub Actions → GHCR → SSH deploy · Nginx + Let's Encrypt · Prometheus · Grafana · Loki/Promtail · Jaeger (OpenTelemetry) · k6
> Domain: `https://khoatla-ecommerce.duckdns.org` · VPS 4GB RAM (103.74.101.153)
> Mọi mục dưới đây đối chiếu với source hiện tại; mục nào **chưa có bằng chứng chạy thật** thì ghi rõ "chưa verify".

---

## 1. SƠ ĐỒ KIẾN TRÚC

```mermaid
flowchart TD
    CLIENT["Client / Postman / Swagger"] -->|HTTPS 443| NGINX["Nginx: TLS, 50 r/s/IP, chặn /actuator /swagger"]
    NGINX -->|127.0.0.1:8080| GW["API Gateway :8080\nJWT, xoá X-User-*, rate limit"]
    GW --> IS["Identity :8082 / gRPC 9090"]
    GW --> PS["Product Catalog :8083 / gRPC 9091"]
    GW --> OS["Order + Inventory :8084"]
    GW --> PB["Payment + Billing :8085"]
    GW --> NS["Notification :8086"]
    GW --- REDIS[("Redis: rate limit + lock:inventory:sku")]
    OS --- REDIS
    OS == "gRPC giá, tồn" ==> PS
    OS == "gRPC địa chỉ" ==> IS
    NS == "gRPC contact" ==> IS
    OS -. "Outbox: order.created / order.cancelled" .-> K[("Kafka KRaft, 3 partition/topic")]
    PB -. "Outbox: payment.completed / failed / refunded" .-> K
    K -. "order.*" .-> PB
    K -. "payment.*" .-> OS
    K -. "order.* + payment.*" .-> NS
    VNPAY["VNPay Sandbox"] -->|"IPN (GET, public HTTPS)"| NGINX
    PB -->|"url / query / refund"| VNPAY
    IS & PS & OS & PB & NS --> PG[("PostgreSQL: 5 DB riêng")]
```

Thay đổi so với bản trước: topic `payment.refunded` (mới, xem mục **6B**); số partition là **3** (kiểm bằng `kafka-topics.sh --describe`), không phải 1.

---

### CÁCH NÓI THEO SƠ ĐỒ (đọc từ trên xuống, ~90 giây)

> **Mở đầu:** "Đây là hệ thống bán hàng đa kênh gồm 5 microservice sau 1 API Gateway. Em chia câu chuyện làm 3 lớp: **vào hệ thống**, **xử lý đồng bộ**, **xử lý bất đồng bộ**."

1. **Client → Nginx (HTTPS 443):** "Mọi request đi qua Nginx: kết thúc TLS bằng Let's Encrypt, giới hạn 50 request/giây/IP, và chặn `/actuator`, `/swagger` — nên Internet không thấy các endpoint quản trị."
2. **Nginx → API Gateway (127.0.0.1:8080):** "Gateway là cổng duy nhất, chỉ nghe localhost. Nó kiểm JWT, **xoá các header `X-User-*` do client tự gửi** rồi gắn lại từ token — nên không ai giả danh được. Rate limit tầng 2 theo user/route lưu ở Redis."
3. **Gateway → 5 service:** "Mỗi service có DB Postgres và role riêng, không dùng chung bảng."
4. **Order → Product, Identity (mũi tên gRPC đậm):** "Lúc đặt đơn, Order cần dữ liệu **chính xác tại thời điểm đó** nên gọi **đồng bộ** gRPC: Product trả giá và tồn, Identity trả địa chỉ mặc định, deadline 3 giây. Notification cũng gọi Identity để lấy email/SĐT khách."
5. **Redis lock (cạnh Order):** "Khi giữ kho, Order khoá theo SKU bằng Redis để 2 khách không cùng giành 1 món cuối — chống oversell."
6. **Order → Kafka (nét đứt, Outbox):** "Order lưu đơn và sự kiện vào **cùng 1 transaction**, rồi một job đẩy sang Kafka. Kafka có chết thì sự kiện vẫn nằm trong DB, không mất."
7. **Kafka → Payment → VNPay:** "Payment nghe `order.created`, tạo giao dịch chờ thanh toán. Khách thanh toán ở VNPay; VNPay gọi **IPN** về Nginx → Gateway → Payment, xác minh chữ ký HMAC rồi phát `payment.completed` hoặc `payment.failed`."
8. **Kafka → Order:** "Order nghe kết quả thanh toán: thành công thì xác nhận đơn và chốt kho; thất bại thì **bù trừ** — huỷ đơn và trả kho. Đây là Saga kiểu Choreography: không có 'nhạc trưởng', mỗi service tự phản ứng với sự kiện."
9. **Kafka → Notification:** "Notification nghe tất cả sự kiện để gửi email/SMS/push. Nó là bên **bất đồng bộ thuần**: tắt Notification thì đặt hàng vẫn thành công, bật lại thì xử lý bù."
10. **Hoàn tiền:** "Admin hoàn tiền → Payment phát `payment.refunded` → Order đổi trạng thái `REFUNDED`, Notification báo khách. Kho không tự cộng vì hàng chưa chắc đã về."
11. **Chốt:** "Đồng bộ ở chỗ cần đúng ngay (giá, tồn, địa chỉ), bất đồng bộ ở chỗ chịu được trễ (thanh toán, thông báo). Sau cùng, Prometheus/Grafana/Loki/Jaeger cho em nhìn thấy toàn bộ chuỗi này."

**Câu hỏi hay gặp:** *Vì sao không gọi gRPC cho Payment?* → thanh toán là quy trình dài (khách mở VNPay), không nên giữ kết nối đồng bộ. *Nếu Kafka chết?* → Outbox giữ sự kiện, Kafka lên lại thì đẩy tiếp. *Nếu Identity chết lúc đặt đơn?* → gRPC hết deadline, API trả 502/503/504 (không tạo đơn dở).

---

## 2. NGINX
Reverse proxy `443 → 127.0.0.1:8080`, TLS (Let's Encrypt), rate limit tầng 1, chặn `/actuator|docs|swagger-ui|v3/api-docs` (trả 404), ghi đè `X-Forwarded-For` chống giả IP.
File: `infra/nginx/quickbite-limits.conf` (zone + upstream) và `infra/nginx/quickbite.conf.template` (vhost).
```nginx
limit_req_zone $binary_remote_addr zone=qb_api:10m rate=50r/s;
limit_req zone=qb_api burst=200 nodelay;   # quá burst → 503
client_max_body_size 2m;                    # >2MB → 413
```

## 3. VÌ SAO DOCKER
Môi trường đồng nhất; `docker compose up -d` dựng cả stack; `mem_limit` từng container (VPS 4GB, preset `profiles/4gb.env`); rollback bằng tag image (`:<git sha>`); build Gradle chạy trên GitHub runner, VPS chỉ `docker pull`.

## 4. DEPLOY
`push main` → GitHub Actions (build/test → docker build+push GHCR → SSH VPS: `docker compose pull` → `up -d --remove-orphans` → kiểm health). Lần đầu: `infra/scripts/setup-vps.sh` (swap, Docker, Nginx, certbot, UFW 22/80/443). Chi tiết từng bước: `DEPLOY-GUIDE.md`.

## 5. TIỀN: ÂM / TỐI THIỂU / TỐI ĐA
Dự án dùng VNPay, không có ví nội bộ. Hoàn tiền kiểm tra trong `PaymentServiceImpl.refund()`: payment phải `SUCCESS`; `amount ≤ amount_goc − refundedAmount`; VNPay nhân 100 khi gửi (`vnp_Amount`). Số âm bị chặn bởi validation của `RefundPaymentRequest`. *(Ngưỡng tối thiểu của sandbox: chưa kiểm chứng trong source, không khẳng định.)*

---

## 6. SAGA

**Happy path**
1. `POST /api/orders` → gRPC Identity (địa chỉ mặc định) + gRPC Product (giá/tồn) → reserve kho (Redis lock) → lưu Order `PENDING` + Outbox **cùng 1 transaction**.
2. Outbox → Kafka `order.created` → Payment tạo Payment `PENDING`; Notification gửi email/SMS "đặt hàng".
3. Khách gọi `POST /api/payments/order/{orderId}/vnpay-url` → thanh toán ở VNPay → IPN/Return → verify HMAC-SHA512 → Payment `SUCCESS` + Outbox `payment.completed`.
4. Order nhận → `CONFIRMED` + `commitStock()`; Notification báo thanh toán thành công.

**Thanh toán lỗi (bù trừ):** `payment.failed` → Order `CANCELLED` + `releaseStock()`; Notification báo lỗi.

**Timeout:** `SagaCompensationJob` (mỗi 5 phút) huỷ đơn `PENDING` > 30 phút, trả kho, phát `order.cancelled`.

### 6B. LUỒNG HOÀN TIỀN (REFUND) MỚI — ghi rõ từng bước

> Đây là luồng **mới** so với bản trước. Trước đây hoàn tiền chỉ đổi dữ liệu trong Payment: đơn vẫn nằm ở `CONFIRMED` dù tiền đã trả lại, và khách không được báo. Nay hoàn tiền là một **Saga choreography** hoàn chỉnh: Payment phát event, Order và Notification cùng nghe.

**Ai gọi, gọi gì:** chỉ **ADMIN** gọi `POST /api/payments/{paymentId}/refund` (chặn ở `SecurityConfig`: `/api/payments/*/refund` → `hasRole(ADMIN)`; customer gọi → `403`).
Body: `{"amount": <số tiền>, "reason": "<lý do>", "createBy": "admin"}` — `amount` ≥ 0.01 (`@DecimalMin`), `reason` bắt buộc (`@NotBlank`), sai → `400`.

**Điều kiện được hoàn (kiểm trong `PaymentServiceImpl.refund()`):**
1. Payment phải ở trạng thái `SUCCESS` (chưa thanh toán, `FAILED`, hoặc đã `REFUNDED` hết → `400`).
2. `amount ≤ amount_gốc − refunded_amount` (hoàn vượt phần còn lại → `400`).

**Các bước (mỗi dòng là 1 service):**
```
[1] Admin ─ POST /api/payments/{id}/refund ──────────────► Payment & Billing
[2] Payment ─ gọi VNPay Refund API (ký HMAC-SHA512) ─────► VNPay Sandbox
        ├─ ghi PaymentTransactionLog type=REFUND (kể cả khi VNPay từ chối)
        └─ vnp_ResponseCode ≠ "00"  →  trả 502, KHÔNG đổi gì, KHÔNG phát event
[3] VNPay trả "00":
        refunded_amount += amount
        refunded_amount ≥ amount_gốc ?  → Payment.status = REFUNDED
                                       :  → Payment.status giữ SUCCESS (hoàn 1 phần, còn hoàn tiếp được)
[4] CÙNG 1 transaction DB: ghi Payment + OutboxEvent "payment.refunded"  (Outbox, như order.created)
[5] Outbox publisher ─────────────► Kafka topic payment.refunded (3 partition)
        ├──► Order & Inventory ─ applyRefund():
        │       đơn đang CONFIRMED / SHIPPING / COMPLETED / PARTIALLY_REFUNDED
        │         fullyRefunded=false → PARTIALLY_REFUNDED
        │         fullyRefunded=true  → REFUNDED
        │       đơn PENDING / CANCELLED → bỏ qua (ghi log WARN, không đổi)
        │       đã ở đúng trạng thái đích → bỏ qua (event trùng, idempotent)
        │       KHÔNG cộng kho
        └──► Notification ─ handlePaymentRefunded():
                lấy email/SĐT khách qua gRPC Identity, gửi EMAIL + SMS + PUSH
                loại thông báo PAYMENT_REFUNDED
```

**Nội dung event `payment.refunded`** (`PaymentRefundedEvent`, cấu trúc giống nhau ở 3 service): `orderId`, `customerId`, `orderCode`, `transactionId`, `refundAmount` (lần này), `totalRefundedAmount` (cộng dồn), `paymentAmount` (gốc), `fullyRefunded` (true/false), `reason`.

**Bảng trạng thái sau khi hoàn (ví dụ đơn 1.000.000 VND đã thanh toán):**
| Lần hoàn | `refunded_amount` | Payment | Order | Kho | Thông báo |
|---|---|---|---|---|---|
| (trước khi hoàn) | 0 | `SUCCESS` | `CONFIRMED` | không đổi | — |
| Hoàn 400.000 | 400.000 | `SUCCESS` | `PARTIALLY_REFUNDED` | không đổi | 1 bộ EMAIL/SMS/PUSH "hoàn 1 phần" |
| Hoàn thêm 600.000 | 1.000.000 | `REFUNDED` | `REFUNDED` | không đổi | 1 bộ "hoàn toàn bộ" |
| Hoàn thêm 1 lần nữa | — | — | — | — | `400` (Payment không còn `SUCCESS`) |

**Chống trùng:** Order bỏ qua event nếu đơn đã ở trạng thái đích. Notification dùng khoá idempotency gồm *tổng đã hoàn*: Kafka giao lặp cùng 1 lần hoàn → không gửi trùng; 2 lần hoàn khác số tiền → được báo 2 lần.
**Lỗi tạm thời:** Order **không** nuốt lỗi optimistic-lock của `applyRefund` — để Spring Kafka retry, tránh mất event (đơn kẹt `CONFIRMED` dù tiền đã hoàn).
**Chính sách kho:** hoàn tiền ≠ hàng đã về kho nên **không tự cộng kho**; khi hàng thật sự về, admin gọi `PUT /api/inventory/{sku}/restock`. (Khác với *huỷ đơn* `CONFIRMED` bằng `cancelOrder`: huỷ = hàng chưa giao nên có cộng lại `available`.)
**Thay đổi DB đi kèm (Flyway):** `order-inventory-service` `V3__add_refund_order_statuses.sql` (mở rộng CHECK trạng thái đơn thêm `PARTIALLY_REFUNDED`, `REFUNDED`); `notification-service` `V2__add_payment_refunded_type.sql` (thêm loại `PAYMENT_REFUNDED`). Payment đã có sẵn cột `refunded_amount` và trạng thái `REFUNDED` từ `V1`. Mỗi version chỉ được có **1 file** — 2 file cùng version làm Flyway báo lỗi và service không khởi động.
**Hạn chế đã biết (nói thật):** (a) VNPay được gọi *trước* khi commit DB; nếu DB lỗi ngay sau khi VNPay đã hoàn thì tiền đã đi mà hệ thống chưa ghi — cần đối soát bằng `GET /api/payments/{id}/query` và transaction log; (b) chưa có Shipment/Return service nên chưa tự động hoá việc trả hàng → cộng kho; (c) chưa có bằng chứng chạy thật end-to-end — xem D4 và `swagger-test-guide.md` mục 7.11.

---

## 7. KHO: RESERVE → COMMIT
| Thời điểm | available | reserved |
|---|---|---|
| Ban đầu 100 | 100 | 0 |
| Đặt 3 (PENDING) | 97 | 3 |
| Thanh toán OK (CONFIRMED) | 97 | 0 |
| Thanh toán lỗi / hết hạn (CANCELLED) | 100 | 0 |
| Hoàn tiền (REFUNDED) | **không đổi** | không đổi |

Lưu ý hành vi hiện có: **khách/admin huỷ** đơn `CONFIRMED` (`cancelOrder`) cộng lại `available` (đơn bị huỷ = hàng chưa giao). Hoàn tiền thì không.

## 8. HOÀN TIỀN & ĐỔI TRẢ (chính sách)
Luồng kỹ thuật đầy đủ: mục **6B**. Tóm tắt chính sách:
- Hoàn tiền ≠ hàng đã về kho → **không tự cộng kho** khi refund (tránh "hàng ảo"). Khi hàng thật sự về: admin `PUT /api/inventory/{sku}/restock` (thủ công).
- Chưa có Shipment/Return service; tự động hoá luồng trả hàng là hướng phát triển.
- Trạng thái đơn: `PENDING → CONFIRMED → (SHIPPING → COMPLETED) → PARTIALLY_REFUNDED → REFUNDED`; `CANCELLED` là nhánh huỷ (đơn `PENDING`/`CONFIRMED` mới huỷ được; đơn đã hoàn tiền thì không huỷ được nữa).
- Hoàn nhiều lần 1 phần: mỗi lần phát 1 event; Payment giữ `SUCCESS` cho tới khi hoàn đủ rồi mới `REFUNDED`. Khoá idempotency thông báo gồm *tổng đã hoàn* → không gửi trùng khi Kafka giao lặp, nhưng mỗi lần hoàn khác nhau đều được báo.

## 9. RATE LIMIT
Tầng 1 Nginx (50 r/s/IP, burst 200 → 503). Tầng 2 Gateway Token Bucket Redis (hết token → 429): login/register `LOGIN_RATE_REPLENISH=5` (burst 10); `/api/users/**` 20/s (burst 40); `/api/catalog/**` 100/s (burst 200); `/api/orders`+`/api/inventory` `ORDER_RATE_REPLENISH=100` (burst `ORDER_RATE_BURST=300`). Hai tầng **bổ sung nhau**: Nginx chặn flood theo IP trước khi vào Spring; Gateway giới hạn theo user/route.

> Demo đặt hàng / thanh toán / thông báo / hoàn tiền / Grafana / Jaeger / k6: xem **PHẦN DEMO TỪNG BƯỚC (D0–D8)** ở cuối tài liệu.

---

## 10. PHẦN KHÓ & CÁCH XỬ LÝ
1. **Outbox orderId = null** → `saveAndFlush()` trước khi dựng event.
2. **Race idempotency** → bắt `DataIntegrityViolationException`, hoàn kho request thua, trả lại đơn thắng.
3. **Consumer nuốt lỗi mất event** → bỏ try/catch, để `DefaultErrorHandler` retry; **cũng áp dụng cho `applyRefund`** (không nuốt optimistic-lock).
4. **VNPay return URL lộ dữ liệu** → bắt buộc chữ ký hợp lệ mới trả data.
5. **gRPC không deadline → cạn thread** → deadline 3s + retry 1 lần; lỗi → 502/503/504.
6. **Refund làm trạng thái đơn "nói dối" + khách không biết** → `payment.refunded` qua Outbox, Order + Notification cùng nghe.
7. **Flyway trên Spring Boot 4** → cần `spring-boot-starter-flyway` + `flyway-database-postgresql`; thiếu thì Flyway không chạy / "Unsupported Database", Hibernate validate báo thiếu bảng.


---

# PHẦN DEMO TỪNG BƯỚC (làm theo đúng thứ tự)

Quy ước: **[PC]** máy cá nhân · **[VPS]** SSH vào VPS · **[UI]** thao tác trên giao diện. Số `#n` là số thứ tự request trong Postman collection hiện có (`docs/05`), không tạo request mới.

## D0. Chuẩn bị trước giờ demo (làm 1 lần, ~15 phút)
> Bật Prometheus/Loki/Jaeger và kiểm tra từng nguồn trong Grafana: làm **D4B** trước khi demo D5/D6.

1. **[VPS]** Stack khoẻ: `cd /opt/quickbite && docker compose ps` → tất cả `healthy`; `bash infra/scripts/verify-stack.sh`.
2. **[VPS]** Để demo tracing chắc chắn có trace: mặc định chỉ lấy mẫu 20%. Tạm tăng: `echo OTEL_SAMPLE_RATIO=1 >> .env && docker compose up -d` (tốn thêm RAM; demo xong xoá dòng này rồi `up -d`).
3. **[PC]** Mở **2 cửa sổ** (giữ nguyên, không đóng):
   - SSH tunnel monitoring: `ssh -L 3000:127.0.0.1:3000 -L 16686:127.0.0.1:16686 root@103.74.101.153`
   - SSH thường vào VPS để chạy lệnh verify (có thể dùng sẵn cửa sổ ở bước 1).
4. **[PC]** Trình duyệt mở sẵn 3 tab: Grafana `http://localhost:3000` (admin / `GRAFANA_PASSWORD` trong `.env` VPS), Jaeger `http://localhost:16686`, và Postman (collection + environment production, `baseUrl=https://khoatla-ecommerce.duckdns.org`, `adminUsername`/`adminPassword` đã nhập).
5. **[PC]** Chạy lần lượt trong Postman: **#2** đăng ký CUSTOMER → **#4** login (lưu `customerToken`, `userId`) → **#5** login ADMIN (lưu `adminToken`) → **#11** thêm địa chỉ mặc định (bắt buộc, vì Order gọi gRPC lấy địa chỉ) → **#15** tạo Brand → **#20** tạo Category → **#28** tạo Product (lưu `skuId`) → **#35** khởi tạo tồn kho. *(Nếu đã chạy `seed_data_production.sql` thì có sẵn sản phẩm/kho, nhưng vẫn cần user + địa chỉ.)*
6. Để nhận **email/SMS thật**: trong Postman đặt `realEmail`, `realPhone`, `realPhoneE164` (xem `docs/07`) và đăng ký customer bằng email/SĐT thật của bạn.

## D1. Luồng ĐẶT HÀNG (đồng bộ)

| Bước | Làm gì | Cái cần chỉ ra | Cách nói |
|---|---|---|---|
| 1 | **[PC]** Postman **#39** `POST /api/orders` (body: `idempotencyKey` = `{{$guid}}`, `customerId`, `items:[{sku, quantity:2}]`) | `201`, `status: PENDING`, có `orderCode`; ghi lại `orderId` | "Đơn tạo xong ở trạng thái chờ thanh toán; kho đã được **giữ** chứ chưa trừ hẳn." |
| 2 | **[PC]** Bấm gửi lại **đúng body đó** (cùng `idempotencyKey`) | Trả về **cùng đơn**, không tạo đơn thứ 2 | "Client retry mạng yếu cũng không đặt trùng — nhờ idempotencyKey + unique constraint." |
| 3 | **[VPS]** `docker exec postgres psql -U postgres -d order_inventory_db -c "select sku,available_qty,reserved_qty from inventories where sku='<skuId>'"` | `available` giảm 2, `reserved` tăng 2 | "Reserve: available trừ, reserved cộng. Khi thanh toán xong mới chốt." |
| 4 | **[VPS]** `docker exec postgres psql -U postgres -d order_inventory_db -c "select id,event_type,topic,status from outbox_events order by id desc limit 3"` | Có `order.created`, `status` chuyển `PENDING → PUBLISHED` sau ~1–2 giây | "Đơn và sự kiện nằm cùng 1 transaction (Outbox); job đẩy sang Kafka." |
| 5 | **[VPS]** `docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order.created --from-beginning --max-messages 1` | JSON sự kiện chứa `orderId`, `orderCode`, items | "Sự kiện thật trên Kafka." |
| 6 | **[VPS]** `docker exec postgres psql -U postgres -d payment_billing_db -c "select id,order_id,status,amount from payments order by id desc limit 1"` | Có Payment `PENDING` cho đơn vừa tạo | "Payment tự tạo giao dịch nhờ nghe `order.created` — Order không gọi Payment trực tiếp." |
| 7 | **[UI]** Jaeger → Service `api-gateway` → Find Traces → mở trace `POST /api/orders` | Gateway → order-inventory → 2 span gRPC (identity, product) → JDBC | "Toàn bộ hành trình 1 request, chậm ở đâu nhìn thấy ngay." |

Thử **đặt quá tồn**: dùng **#35** khởi tạo SKU tồn 1 rồi đặt `quantity: 5` → lỗi nghiệp vụ (hết hàng), kho không âm.

## D2. Luồng THANH TOÁN (VNPay Sandbox)

1. **[PC]** Postman **#44** `POST /api/payments/order/{{orderId}}/vnpay-url` (Bearer `customerToken`) → copy `data.paymentUrl`. URL hết hạn theo `expireAt`.
2. **[PC]** Mở `paymentUrl` trong trình duyệt → chọn ngân hàng **NCB** → thẻ test: số `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.
3. VNPay chuyển trình duyệt về `.../api/payments/vnpay/return` (hiển thị cho khách) **và** gọi **IPN** server-to-server (nguồn xác nhận chính thức).
4. **[VPS]** Sau vài giây:
   - `docker exec postgres psql -U postgres -d payment_billing_db -c "select id,status,vnp_transaction_no,paid_at from payments order by id desc limit 1"` → `SUCCESS`
   - `docker exec postgres psql -U postgres -d order_inventory_db -c "select order_code,status from orders order by id desc limit 1"` → `CONFIRMED`
   - Kho: `reserved_qty` về 0, `available_qty` giữ nguyên (đã **chốt** trừ).
5. **[PC]** Postman: `GET /api/payments/order/{{orderId}}` và `GET /api/payments/{{paymentId}}/logs` → thấy log `CREATE_PAYMENT_URL`, `RETURN_URL`/`IPN_CALLBACK`.
6. **Thanh toán lỗi (để chỉ Saga bù trừ):** tạo đơn mới → mở URL → bấm **Huỷ** ở trang VNPay → đơn `CANCELLED`, kho **được trả lại** (`available` tăng lại).
> Cách nói: "Order không biết VNPay. Payment xác minh chữ ký rồi phát sự kiện; Order tự quyết định xác nhận hay bù trừ."

## D3. Luồng THÔNG BÁO

1. **Thông báo thật:** sau D2 bước 4, kiểm tra hộp thư (`realEmail`) và điện thoại (`realPhone`) — nhận email/SMS *thanh toán thành công* (và *đặt hàng*).
2. **[PC]** Postman (ADMIN, `adminToken`): `GET /api/notifications/logs?orderId={{orderId}}` → các dòng `ORDER_CREATED`, `PAYMENT_COMPLETED` × kênh `EMAIL/SMS/PUSH`, `status: SENT`.
3. **[VPS]** `docker exec postgres psql -U postgres -d notification_db -c "select notification_type,channel,status,recipient from notification_logs where order_id=<orderId> order by id"`.
4. **Chứng minh bất đồng bộ (đáng demo nhất):**
   - **[VPS]** `docker stop notification-service`
   - **[PC]** Tạo đơn mới (#39) → vẫn `201` thành công.
   - **[VPS]** `docker start notification-service` → đợi healthy (~1–2 phút) → chạy lại lệnh ở bước 3 cho đơn mới: thông báo xuất hiện **bù** (Kafka giữ offset).
   > "Notification chết không làm hỏng đặt hàng; sự kiện nằm chờ trên Kafka."
5. Gửi thử thủ công: `POST /api/notifications/test` (body: `channel`, `recipient`, `subject`, `body`).

## D4. HOÀN TIỀN (luồng mới: Payment → Outbox → Kafka → Order + Notification)

Điều kiện: có 1 đơn đã thanh toán thật ở D2 (Payment `SUCCESS`, Order `CONFIRMED`). Sơ đồ & luật: mục **6B**. Ghi lại `paymentId`, `orderId`, `amount` (Postman `GET /api/payments/order/{{orderId}}`).

1. **[PC]** Postman **#48** (hoặc Swagger tab PAYMENT) `POST /api/payments/{{paymentId}}/refund`, token **ADMIN**. Hoàn **1 phần** trước, ví dụ `amount` bằng khoảng 40% `amount`:
   `{"amount": <40% amount>, "reason": "Khach tra hang 1 phan", "createBy": "admin"}` → `200`, `data.refundedAmount` = số vừa hoàn, `data.status` vẫn `SUCCESS`.
   Sandbox từ chối → `502` + mã VNPay; khi đó **không có** event và đơn không đổi — ghi nguyên mã trả về, đừng gọi là PASS.
2. **[VPS]** Outbox của Payment đã đẩy sang Kafka:
   `docker exec postgres psql -U payment_billing -d payment_billing_db -c "select id,topic,status from outbox_events where topic='payment.refunded' order by id desc limit 1"` → `PUBLISHED`.
3. **[VPS]** Order đổi trạng thái (vài giây):
   `docker exec postgres psql -U order_inventory -d order_inventory_db -c "select order_code,status from orders where id=<orderId>"` → `PARTIALLY_REFUNDED`.
   Kho **không đổi**: `select sku,available_qty,reserved_qty from inventories where sku='<sku>'` giống trước khi hoàn.
4. **[PC]** Postman (ADMIN) `GET /api/notifications/logs?orderId={{orderId}}` → có `PAYMENT_REFUNDED` × `EMAIL/SMS/PUSH`, `status: SENT` (hoặc kiểm tra email/SMS thật nếu đã bật ở D3).
5. Hoàn **phần còn lại** (`amount − refundedAmount`) → `data.status` = `REFUNDED`; sau vài giây Order = `REFUNDED`; có thêm 1 bộ thông báo "hoàn toàn bộ".
6. Chứng minh các chốt chặn: hoàn thêm lần nữa → `400`; gọi bằng token CUSTOMER → `403`; `amount: 0` hoặc thiếu `reason` → `400`.
7. **[VPS]** Log: `docker logs --since 10m payment-billing-service | grep -i refund`; `docker logs --since 10m order-inventory-service | grep -i "payment.refunded"`; `docker logs --since 10m notification-service | grep -i "payment.refunded"`.
8. **[UI]** Jaeger (kỳ vọng, chưa kiểm chứng): trace `POST /api/payments/{id}/refund` có span gọi ra VNPay + JDBC (update payment + insert outbox) **và, sau ~1–2 giây, span Kafka producer `payment.refunded` rồi consumer ở `order-inventory-service` + `notification-service` trong CÙNG trace** (nhờ cột `outbox_events.trace_parent`, migration `V3__outbox_trace_parent.sql` ở payment, `V4__outbox_trace_parent.sql` ở order-inventory; job Outbox đặt lại traceparent làm parent khi publish). Khi demo đặt `OTEL_SAMPLE_RATIO=1.0`. Event cũ (trước migration) có `trace_parent` NULL nên vẫn đứt trace — bình thường.
> Cách nói: "Hoàn tiền không chỉ trả tiền: Payment phát một sự kiện, Order tự cập nhật trạng thái đơn, Notification tự báo khách — cả ba không gọi trực tiếp nhau. Kho tách riêng vì tiền về chưa chắc hàng đã về."
*Trạng thái: chưa chạy thật end-to-end; sandbox VNPay có thể từ chối refund. Nếu bị chặn, chạy kịch bản mô phỏng event ở `swagger-test-guide.md` mục 7.11-C để kiểm Order + Notification riêng.*

## D4B. BẬT & TEST CẢ 3 NGUỒN TRONG GRAFANA (Prometheus + Loki + Jaeger)

Grafana chỉ là "màn hình": nó **không tự sinh dữ liệu**. Màn hình Explore báo *No data* nghĩa là nguồn bên dưới chưa chạy, chưa có traffic, hoặc bạn chưa chọn đúng truy vấn. Làm lần lượt 5 bước dưới đây.

### Bước 1 — Bật đủ stack [VPS]
Loki, Promtail, Jaeger nằm trong profile `full`; tracing cần file compose thứ 3 + agent. Kiểm tra:
```bash
cd /opt/quickbite
grep -E '^(COMPOSE_FILE|COMPOSE_PROFILES|OTEL_SAMPLE_RATIO)=' .env
docker compose ps --format 'table {{.Name}}\t{{.Status}}' | grep -E 'prometheus|grafana|loki|promtail|jaeger'
```
Phải thấy `COMPOSE_FILE=` có **3 file** (`docker-compose.production.yml`, `monitoring/docker-compose.monitoring.yml`, `monitoring/docker-compose.tracing.yml`), `COMPOSE_PROFILES=full`, và 5 container monitoring đều `Up`. Thiếu thì đặt lại và bật (thứ tự bật theo `docs/08` A4 → A5; RAM phải ổn ở bậc 1 trước khi bật tracing):
```bash
sed -i '/^COMPOSE_FILE=/d;/^COMPOSE_PROFILES=/d' .env
echo 'COMPOSE_FILE=docker-compose.production.yml:monitoring/docker-compose.monitoring.yml:monitoring/docker-compose.tracing.yml' >> .env
echo 'COMPOSE_PROFILES=full' >> .env
bash infra/scripts/get-otel-agent.sh && ls -la otel/opentelemetry-javaagent.jar
docker compose up -d --remove-orphans          # 6 service restart tuần tự, chờ 10–20 phút
docker compose ps; free -m
```

### Bước 2 — Kiểm tra agent tracing đã gắn vào JVM [VPS]
```bash
docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' order-inventory-service | grep -E 'JAVA_TOOL_OPTIONS|OTEL_SERVICE_NAME|OTEL_EXPORTER_OTLP_ENDPOINT'
docker logs order-inventory-service 2>&1 | grep -i -m2 opentelemetry
```
Phải thấy `-javaagent:/otel/opentelemetry-javaagent.jar`, `OTEL_SERVICE_NAME=order-inventory-service`, endpoint `http://jaeger:4317` và dòng log của agent. Không thấy ⇒ tracing **chưa bật** (quay lại bước 1).

### Bước 3 — Sinh dữ liệu thật [PC]
Chạy Postman **#2 → #4 → #5 → #11 → #15 → #20 → #28 → #35**, rồi **#39 (tạo đơn) 5–10 lần** (mỗi lần `idempotencyKey` mới), thêm **#44** + thanh toán 1 đơn (D2). Không có traffic thì Prometheus/Loki/Jaeger đều trống. Sau đó **[VPS]** kiểm tra Jaeger đã nhận trace:
```bash
curl -s http://127.0.0.1:16686/api/services          # phải liệt kê api-gateway, order-inventory-service, ...
```
Danh sách rỗng hoặc chỉ có `jaeger-all-in-one` ⇒ chưa có trace (kiểm tra lại bước 2, sample ratio: `OTEL_SAMPLE_RATIO=1` ở D0).

### Bước 4 — Kiểm tra 3 data source [UI]
Grafana → **Connections → Data sources** → bấm lần lượt **Prometheus**, **Loki**, **Jaeger** → cuối trang **Save & test** → phải báo xanh (thành công). Cả 3 do file provisioning tạo sẵn (Prometheus là mặc định). Đỏ ⇒ container tương ứng chưa chạy (xem bước 1).

### Bước 5 — Test từng nguồn trong **Explore**
Đặt time range **Last 15 minutes** (góc phải trên). Chọn data source ở ô trên cùng bên trái (đúng màn hình bạn đang mở: *Jaeger / Loki / Prometheus*). Bấm nút xanh **Run query** (hoặc Shift+Enter).

**5A. Prometheus** — chọn *Prometheus*, đổi chế độ từ *Builder* sang **Code**, dán từng câu:

| # | Câu PromQL | Kết quả đúng | Ý nghĩa khi demo |
|---|---|---|---|
| 1 | `up{job="services"}` | 6 dòng, giá trị 1 | 6 service đều được scrape |
| 2 | `sum by (instance) (rate(http_server_requests_seconds_count[2m]))` | req/s từng service (tăng khi bạn tạo đơn) | Tải theo service |
| 3 | `sum by (instance) (rate(spring_cloud_gateway_requests_seconds_count[2m]))` | req/s của `api-gateway` | Tải ở cổng vào |
| 4 | `sum(rate(http_server_requests_seconds_count{instance="order-inventory-service:8084",method="POST",uri="/api/orders"}[2m]))` | >0 khi đang tạo đơn | Tốc độ tạo đơn |
| 5 | `sum by (instance) (rate(http_server_requests_seconds_count{status=~"5.."}[2m]))` | 0 (hoặc không có dòng) | Lỗi 5xx |
| 6 | `sum by (instance, status) (rate(http_server_requests_seconds_count{status=~"4.."}[2m]))` | 400/401/409/429 khi có test lỗi | Lỗi phía client, rate limit |
| 7 | `sum by (instance) (rate(http_server_requests_seconds_sum[2m])) / sum by (instance) (rate(http_server_requests_seconds_count[2m]))` | giây/request trung bình | Độ trễ trung bình |
| 8 | `max by (instance) (http_server_requests_seconds_max)` | độ trễ lớn nhất gần đây | Đỉnh độ trễ |
| 9 | `sum by (instance) (jvm_memory_used_bytes{area="heap"})` và `sum by (instance) (jvm_memory_max_bytes{area="heap"})` | used < max | Heap JVM |
| 10 | `sum by (instance) (rate(jvm_gc_pause_seconds_sum[2m]))` | gần 0 | Thời gian GC |
| 11 | `jvm_threads_live_threads` | vài chục luồng/service | Luồng JVM |
| 12 | `process_cpu_usage * 100` và `system_cpu_usage * 100` | % CPU | Nút cổ chai CPU |
| 13 | `hikaricp_connections_active`, `hikaricp_connections_pending`, `hikaricp_connections_max` | active < max, pending ≈ 0 | Pool DB |
| 14 | `max by (instance) (kafka_consumer_fetch_manager_records_lag_max)` | về 0 sau đợt tải | Consumer lag |
| 15 | `process_uptime_seconds` | nhỏ bất thường = vừa restart | Phát hiện restart |

Ghi chú trung thực: (a) **không có p95/p99** trong Prometheus vì source chưa bật histogram — p95/p99 lấy từ **k6** (D7); (b) **không có** metric RAM/OOM theo container (không có cAdvisor) — dùng `docker stats` / `docker inspect ... OOMKilled` trên VPS; (c) Prometheus scrape mỗi 30 giây nên dùng cửa sổ `[2m]`; (d) câu nào *No data*: vào **Metric browser**/gõ tên metric để xem nhãn thật (`uri`, `instance`) rồi chỉnh câu truy vấn.

**5B. Loki** — chọn *Loki*, chế độ **Code**:

| # | Câu LogQL | Thấy gì |
|---|---|---|
| 1 | `{container="order-inventory-service"}` | Log của Order (Label browser liệt kê đủ giá trị `container`) |
| 2 | `{container="order-inventory-service"} \|= "payment.completed"` | Order nhận kết quả thanh toán |
| 3 | `{container="order-inventory-service"} \|= "payment.refunded"` | Order nhận sự kiện hoàn tiền |
| 4 | `{container="payment-billing-service"} \|= "IPN"` | Payment xử lý IPN VNPay |
| 5 | `{container="notification-service"} \|= "payment.completed"` | Notification nhận sự kiện |
| 6 | `{container=~"api-gateway\|.*-service"} \|~ "(?i)error\|exception"` | Mọi lỗi của 6 service |
| 7 | `sum by (container) (count_over_time({container=~"api-gateway\|.*-service"} \|~ "(?i)error" [5m]))` | Số dòng lỗi theo service (dạng biểu đồ) |

**5C. Jaeger** (màn hình bạn đang mở) — chọn data source *Jaeger*:
1. Ở hàng tab của query, chọn **Search** (có 3 chế độ: *Search*, *TraceID*, *Dependency graph*; ảnh bạn chụp đang bị menu che tab Search).
2. **Service** → chọn `api-gateway` (hoặc `order-inventory-service`). Danh sách trống ⇒ chưa có trace (làm lại bước 2–3).
3. **Operation** để *All*; **Limit** 20; có thể đặt **Min duration** (ví dụ `100ms`) để tìm request chậm.
4. Bấm **Run query** → hiện danh sách trace → bấm 1 trace → **waterfall** các span (gateway → order → gRPC identity/product → JDBC → …). Bấm vào 1 span để xem *Tags* (`rpc.system`, `db.system`, `messaging.system`…, tuỳ agent bắt được).
5. Tab **TraceID**: dán Trace ID (copy từ danh sách trace) rồi Shift+Enter.
6. Tab **Dependency graph**: sơ đồ service gọi service. Nếu trống dù đã có trace, ghi nhận "chưa hiển thị" và dùng giao diện Jaeger gốc `http://localhost:16686` (tab *System Architecture*).

**5D. Xem 3 nguồn cạnh nhau (rất hợp để demo):** bấm **Split** (góc trên) → bên trái chọn *Prometheus* (câu #2), bên phải chọn *Loki* (câu #2) hoặc *Jaeger*; hai bên dùng chung khoảng thời gian. Nói: "Metrics báo *có chuyện*, log cho biết *chuyện gì*, trace chỉ ra *chậm/lỗi ở đâu*."

**Chốt PASS cho Monitoring (chỉ khi có bằng chứng):** 5A#1 ra 6 dòng bằng 1 · 5B#1 ra log · 5C ra ≥1 trace có waterfall nhiều service · chụp 3 ảnh làm bằng chứng. Thiếu cái nào ghi PARTIAL.

## D5. DASHBOARD GRAFANA (QuickBite — Overview)

**[UI]** `http://localhost:3000` → Dashboards → **QuickBite — Overview**. Có 7 panel (đúng như source):

| Panel | Nhìn gì | Khi nào thấy rõ |
|---|---|---|
| Service UP | 6 service = 1 | Luôn |
| HTTP request rate (req/s) | Cột/đường của `api-gateway`, `order-inventory-service` tăng khi tạo đơn/chạy k6 | Khi đặt hàng liên tục |
| Latency TB (s) | Độ trễ trung bình theo service | Chạy k6 |
| JVM heap used | Heap từng JVM; không tăng vô hạn | Chạy k6 |
| CPU process (%) | CPU từng service | Chạy k6 |
| HikariCP connections active | Kết nối DB đang dùng | Chạy k6 |
| Kafka consumer lag (max) | Về 0 = consumer theo kịp | Sau mỗi đợt tải |

Thao tác: đặt time range **Last 15 minutes**, auto-refresh **10s** → chạy D1 vài lần (hoặc k6 ở D7) → chỉ vào panel request rate nhích lên.
**Logs (Loki):** Grafana → Explore → datasource **Loki** → chạy:
- `{container="order-inventory-service"}` rồi thêm `|= "orderCode"` hoặc `|= "payment.completed"`;
- `{container="payment-billing-service"} |= "IPN"`;
- `{container="notification-service"} |= "payment.refunded"`.
Nhãn `container` là tên container (xem danh sách: Explore → Label browser → `container`). Trước khi demo hãy kiểm tra các nhãn này có thật trên máy bạn.
**Prometheus:** không public; kiểm tra bằng Explore (D4B bước 5A). Kiểm tra scrape từ VPS: **[VPS]** `docker exec prometheus wget -qO- http://localhost:9090/api/v1/targets | grep -o '"health":"[a-z]*"' | sort | uniq -c` → 7 `up`.
> Cách nói: "Metrics trả lời *hệ thống có khoẻ không*, log trả lời *chuyện gì đã xảy ra*, trace trả lời *chậm ở đâu*."

## D6. TRACING (Jaeger + OpenTelemetry) — xem TẤT CẢ loại span

OpenTelemetry Java agent được gắn vào JVM của 6 service (`monitoring/docker-compose.tracing.yml`), gửi OTLP gRPC → Jaeger `:4317`; không sửa code nghiệp vụ. **[UI]** `http://localhost:16686`.

1. **Danh sách service:** dropdown *Service* phải có 6 mục: `api-gateway`, `identity-user-service`, `product-catalog-service`, `order-inventory-service`, `payment-billing-service`, `notification-service`. Thiếu cái nào ⇒ service đó chưa gắn agent/chưa có traffic.
2. **Trace đặt hàng (đồng bộ):** Service `api-gateway` → Lookback *Last 15 Minutes* → Find Traces → mở trace `POST /api/orders`. Đọc từ trên xuống: gateway → order-inventory-service → 2 span **gRPC** (sang identity và product) → span **JDBC** (insert order, outbox) → có thể có span **Redis** (lock kho).
3. **Lọc theo loại span** (ô *Tags*; nếu tag không ra kết quả, bỏ Tags và lọc bằng Operation):
   - gRPC: `rpc.system=grpc`
   - Database: `db.system=postgresql`
   - Redis: `db.system=redis`
   - Kafka: `messaging.system=kafka`
4. **Trace bất đồng bộ (trace riêng):** Service `order-inventory-service` → chọn operation của Outbox publisher; rồi Service `payment-billing-service` → span consume `order.created` → JDBC insert payment. Các trace này được **liên kết** qua header Kafka (hiển thị dạng *follows from*), không nằm chung 1 cây với `POST /api/orders` — đó là hành vi đúng của luồng bất đồng bộ.
5. **Trace thanh toán:** Service `payment-billing-service` → span request IPN (`GET /api/payments/vnpay/ipn`) → JDBC update + insert outbox.
6. **So sánh:** chọn 2 trace → *Compare* để chỉ trace nhanh vs chậm.
> Lưu ý trung thực: chỉ có trace khi **sample** (mặc định 20%, đã đặt 100% ở D0.2). Span nào hiện ra phụ thuộc agent; nếu một loại (ví dụ Kafka) không thấy, ghi rõ "chưa thấy", đừng khẳng định. Kết luận OpenTelemetry chỉ PASS khi Jaeger hiện trace thật (xem `docs/08` A8).

## D7. K6 (load test, chạy từ máy cá nhân)

**Trước khi chạy**
1. **[PC]** Cài k6: `winget install k6` → mở PowerShell mới → `k6 version`. Đặt biến: `$env:BASE="https://khoatla-ecommerce.duckdns.org"; $env:PW="<ADMIN_DEFAULT_PASSWORD trong .env VPS>"`.
2. **[VPS]** `cd /opt/quickbite && bash infra/scripts/loadtest-mode.sh on` (nới rate limit để 1 IP không bị 429). **Bắt buộc `off` sau khi xong.**
3. **[VPS]** (cửa sổ SSH thứ 2) theo dõi: `watch -n 5 "docker stats --no-stream --format 'table {{.Name}}\t{{.MemPerc}}\t{{.CPUPerc}}'; free -m | head -3"`. **[UI]** Mở Grafana (D5) time range *Last 15 minutes*, refresh 5s.

**Chạy (tăng dần, nghỉ 2 phút giữa các mức)**
```powershell
# 1) Smoke — chỉ để xác nhận chức năng
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=smoke -e STOCK=100 load-test\k6-production-load-test.js
# 2) Tăng tải theo năng lực VPS (RATE = số request /api/orders mỗi giây ở mức ổn định)
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=custom -e RATE=10  -e HOLD=60s -e STOCK=100 load-test\k6-production-load-test.js
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=custom -e RATE=25  -e HOLD=60s -e STOCK=100 load-test\k6-production-load-test.js
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=custom -e RATE=50  -e HOLD=60s -e STOCK=100 load-test\k6-production-load-test.js
```
Dừng tăng khi: p95 > 3s, 5xx > 1%, `dropped_iterations` lớn, container OOMKilled/restart, hoặc `free -m` available < 150 MB. Mức cao nhất vẫn sạch = **năng lực đo được** của VPS này.

**Đọc kết quả k6:** `http_reqs` (rate = RPS thực) · `http_req_duration` p(95)/p(99) · `orders_created` (đơn thành công) · `orders_sold_out` (400/409 = hết hàng, **hợp lệ**) · `orders_rate_limited` (429) · `orders_error_5xx`. Dòng cuối in `FLASH-xxxx` = SKU của lượt chạy.

**Sau mỗi mức [VPS] (kiểm tra không oversell)** — thay `FLASH-xxxx`:
```bash
docker exec postgres psql -U postgres -d order_inventory_db -c "select sku,available_qty,reserved_qty,(available_qty+reserved_qty) total from inventories where sku='FLASH-xxxx'"   # total = STOCK, available >= 0
docker exec postgres psql -U postgres -d order_inventory_db -c "select status,count(*) from orders group by status"
docker exec postgres psql -U postgres -d order_inventory_db -c "select status,count(*) from outbox_events group by status"   # PENDING về 0
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups          # LAG về 0
docker inspect -f '{{.Name}} OOM={{.State.OOMKilled}} restarts={{.RestartCount}}' $(docker ps -q)
```
Quy tắc đạt: `orders_created` = `STOCK − available_qty`; `available_qty ≥ 0`; `available+reserved = STOCK`.

**Trong lúc chạy, chỉ trên Grafana:** request rate của `api-gateway`/`order-inventory-service` tăng, latency/heap/CPU nhích lên, HikariCP active tăng, Kafka lag tăng rồi về 0. **Trên Jaeger:** lọc trace `POST /api/orders` có thời gian dài nhất để chỉ nút cổ chai.

**Dọn dẹp [VPS]:** `bash infra/scripts/loadtest-mode.sh off`; nếu đã đặt `OTEL_SAMPLE_RATIO=1` ở D0.2 thì xoá dòng đó khỏi `.env` rồi `docker compose up -d`.

**Kết luận (2 dòng riêng, theo số đo thật):** *PASS FUNCTIONAL* khi không oversell + số đơn khớp + Outbox/Kafka đuổi kịp + không OOM. *Load theo đề 500–1000 req/s:* ghi KHÔNG TEST/FAIL nếu không đạt; báo cáo "ổn định tới N req/s, p95 = X ms" bằng số đo thật. Bảng điền kết quả: `docs/08` mục B5.

## D8. KỊCH BẢN 10 PHÚT (tóm tắt)
1. (1′) Nói theo sơ đồ. 2. (2′) D1 đặt hàng + idempotency + Outbox/Kafka. 3. (2′) D2 thanh toán VNPay thẻ test → đơn CONFIRMED. 4. (1′) D3 nhận email/SMS thật + tắt/bật Notification. 5. (1′) D4 hoàn tiền → PARTIALLY_REFUNDED → REFUNDED (mục 6B). 6. (1.5′) D4B/D5 Grafana: Prometheus + Loki + dashboard. 7. (1.5′) D6 Jaeger waterfall. 8. (1′) D7 mở kết quả k6 đã chạy sẵn (đừng chạy live nếu VPS yếu) và nêu kết luận trung thực.

---
*Cập nhật: 2026-10-14. Gồm luồng hoàn tiền mới (6B, 8, D4) và test đầy đủ Grafana/Prometheus/Loki (D4B, D5), tracing Jaeger (D6), k6 (D7). Chưa có bằng chứng chạy thật cho: refund → REFUNDED end-to-end, monitoring/tracing, load test (chờ log từ VPS).*