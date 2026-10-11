# API Gateway — Omnichannel E-Commerce Platform

Single Point of Entry cho toàn hệ thống, xây bằng **Spring Cloud Gateway (reactive, Netty)**
theo đúng mục 2.1 đề bài: *"Đảm nhận việc Xác thực (Authentication Token Verification),
Rate Limiting (chống DDOS/Spam), Routing request đến các service tương ứng."*

## 1. Vì sao chọn Gateway reactive (WebFlux) thay vì Gateway MVC (servlet)?

Mọi request qua Gateway đều phải mở thêm 1 kết nối tới service phía sau (proxy) — nếu dùng
model blocking (1 thread/request) thì dưới tải cao (Flash Sale 500–1000 req/s theo mục 5 đề
bài), số lượng thread cần giữ đồng thời sẽ bùng nổ rất nhanh. Model reactive (event-loop) xử
lý được hàng nghìn connection đồng thời chỉ với vài chục thread — đúng bản chất công việc của
1 API Gateway (I/O-bound, không có logic nghiệp vụ nặng).

## 2. Bảng Route

| Route ID | Predicate | Đích (Eureka service-id) | Public? | Rate limit (req/s replenish / burst) |
|---|---|---|---|---|
| `identity-auth-public` | `/api/users/{register,login,refresh-token,validate-token}` | identity-user-service | ✅ Có | 5 / 10 (theo IP — chống brute-force) |
| `identity-user-service` | `/api/users/**` (còn lại) | identity-user-service | ❌ Cần JWT | 20 / 40 |
| `product-catalog-service` | `/api/catalog/**` | product-catalog-service | ✅ GET công khai, các method khác cần JWT | 100 / 200 |
| `payment-vnpay-webhook-public` | `/api/payments/vnpay/**` | payment-billing-service | ✅ Có (VNPay gọi server-to-server) | — |
| `payment-billing-service` | `/api/payments/**` | payment-billing-service | ❌ Cần JWT | 20 / 40 |
| `order-inventory-service` | `/api/orders/**`, `/api/inventory/**` | order-inventory-service | ❌ Cần JWT | 100 / 300 (chịu tải cao nhất — Flash Sale) |
| `notification-service` | `/api/notifications/**` | notification-service | ❌ Cần JWT | 20 / 40 |
| `*-docs` | `/docs/{service-id}/**` | tương ứng | ✅ Có | — (proxy sang `/v3/api-docs` của từng service) |

**Thứ tự khai báo route quan trọng**: `payment-vnpay-webhook-public` phải đứng **trước**
`payment-billing-service` trong `application.yml` vì Spring Cloud Gateway khớp route theo
thứ tự khai báo (first-match-wins) — nếu đảo ngược, route tổng quát hơn sẽ "nuốt" luôn
request webhook trước khi route đúng kịp xử lý.

## 3. Luồng xác thực (JWT)

```
Client --[Authorization: Bearer <token>]--> API Gateway
                                                |
                                   JwtAuthenticationGlobalFilter
                                   (order = HIGHEST_PRECEDENCE+10)
                                                |
                      1. Xoá mọi header X-User-Id/X-Username/X-User-Role
                         client tự gửi lên (chống giả mạo quyền)
                      2. Nếu path/method khớp public-endpoints -> cho qua
                      3. Ngược lại: verify chữ ký HS256 bằng CÙNG secret
                         với identity-user-service (jwt.secret-key)
                      4. Hợp lệ -> giải mã claims -> gắn lại header
                         X-User-Id / X-Username / X-User-Role (tin cậy)
                                                |
                                    Forward --lb://service-name--> Service phía sau
```

**Vì sao Gateway tự verify JWT bằng secret dùng chung thay vì gọi REST sang
`identity-user-service/api/users/validate-token` mỗi request?** Để tránh Gateway trở
thành "chatty caller" / bottleneck dưới tải cao — xác thực chữ ký HS256 cục bộ chỉ tốn
vài micro-giây, không cần thêm 1 vòng network mỗi request.

Service phía sau (Order, Payment, Notification...) hiện **chưa** tự verify lại JWT (ngoại
trừ identity-user-service có `SecurityConfig` riêng) — chúng có thể tin tưởng header
`X-User-Id/X-User-Role` do Gateway gắn vào vì đây là **đường vào DUY NHẤT** (các port của
service phía sau không nên mở ra Internet công cộng trong production — xem mục 4 đề bài về
UFW chỉ mở port 80/443/22).

## 4. Rate Limiting

Dùng `RequestRateLimiter` (thuật toán Token Bucket) của Spring Cloud Gateway, backend là
Redis **dùng chung** với `order-inventory-service` (đã có sẵn trong hạ tầng cho Distributed
Lock) nhưng tách `database: 1` để không lẫn namespace key. `KeyResolver` tự đọc và giải mã
`Authorization` header của chính request (không phụ thuộc thứ tự chạy trước/sau với
`JwtAuthenticationGlobalFilter`) — ưu tiên khoá theo username nếu có token, fallback theo IP.

Vượt hạn mức → HTTP `429 Too Many Requests`.

## 5. Chạy thử

```bash
# 1. Chạy trước: eureka-server, redis, và (tối thiểu) identity-user-service
./gradlew bootRun   # từ thư mục api-gateway, mặc định cổng 8080
```

Biến môi trường quan trọng (xem `application.yml`):

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `JWT_SECRET_KEY` | (đặt qua biến môi trường, tối thiểu 32 ký tự) | **PHẢI** giống hệt `identity-user-service` |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | |
| `CORS_ALLOWED_ORIGINS` | `*` | Đổi thành domain thật trước khi deploy VPS |

## 6. Test trên Postman

Import `postman/Api-Gateway.postman_collection.json` — bao gồm:
1. Health check + xem bảng route (`/actuator/gateway/routes`)
2. Luồng public: register → login (tự lưu `accessToken` vào biến collection) → duyệt sản phẩm
3. Case 401: gọi API cần auth mà không kèm token / kèm token giả mạo
4. Case public webhook VNPay không cần token
5. Case test rate limit (gọi liên tục route login > 10 lần → 429)
6. Case 503: tắt thử 1 service phía sau rồi gọi qua Gateway → nhận JSON lỗi rõ ràng thay vì
   crash — đúng kịch bản demo tính sẵn sàng ở mục 5 đề bài.

## 7. Monitoring

`/actuator/health`, `/actuator/prometheus`, `/actuator/gateway/routes` (read-only).
