# 06. Addendum v17 — những gì CÒN THIẾU so với đề bài (đã sửa) + hướng dẫn test

> Phạm vi: chỉ xử lý phần v16 còn thiếu/sai so với đề bài. **Mọi mục đều là kiểm tra TĨNH (đọc source/config, chạy script cú pháp). Chưa có mục nào được chạy trên VPS → không mục nào được đánh PASS thực tế.**
> Trạng thái dùng: `CONFIG ĐÚNG` = cấu hình khớp đề (chưa chạy) · `PARTIAL` · `FAIL` · `CHƯA VERIFY`.

## 1. Kết quả đối chiếu v16 → đề bài
| Hạng mục | Kết quả | Ghi chú |
|---|---|---|
| Firewall/Network (docs/01) | CONFIG ĐÚNG · CHƯA VERIFY | UFW 22/80/443, chỉ gateway bind `127.0.0.1:8080`, Nginx chặn `/actuator,/docs`. Lỗi lọt: Nginx từng nối `X-Forwarded-For` (client giả IP) — đã sửa từ v14 |
| CI/CD (docs/02, deploy.yml) | CONFIG ĐÚNG · CHƯA CHẠY | push→build/test→Docker→GHCR→SSH→pull→up -d --remove-orphans→verify+rollback. 5 secrets đúng tên. Không cần sửa |
| Monitoring (docs/03) | PARTIAL | Prometheus/Grafana/Loki/Promtail/Jaeger có file cấu hình; OpenTelemetry **không có trong source**, chỉ có overlay Java agent (chưa chạy). **Đã thêm** dashboard Grafana provision sẵn `quickbite-overview.json` (trước đó chỉ có datasource, không có dashboard) |
| Load test (docs/04, k6) | CONFIG ĐÚNG sau sửa · CHƯA CHẠY | **Lỗi đã sửa:** k6 `setup()` đăng ký 50 user (BCrypt, 1 vCPU) vượt timeout mặc định 60s → thêm `setupTimeout: 15m`. Header ghi STOCK 500/1000 lệch docs/04 → thống nhất STOCK=100 |
| Production 1-round (docs/05 + Postman) | PARTIAL → đã vá | Xem mục 2–3 |
| Inventory API | Chỉ có init / restock / get / list | **Không có endpoint reserve/release/sellable riêng.** Reserve = tạo đơn; release = hủy đơn/Saga; sellable = `GET /api/catalog/products/variants/sku/{sku}/sellable-check`. Đề bài hỏi "Reserve/Release" được test gián tiếp qua Order |

## 2. Lỗi tìm thấy trong v16 và cách sửa (file nằm trong zip v17)
| # | Lỗi | File sửa |
|---|---|---|
| 1 | **IPN bị chặn bởi danh sách IP cứng** (`VnPayCallbackController`): chỉ loopback + vài IP VNPay → mọi IPN từ máy cá nhân trả `RspCode 99`; IPN thật từ IP VNPay không có trong danh sách cũng bị từ chối. | `VnPayCallbackController.java`, `VnPayProperties.java`, `payment-billing-service/application.yml`, `docker-compose.production.yml`, `.env.production.example` — thêm biến `VNPAY_IPN_ALLOWED_IPS` (trống = danh sách mặc định; `*` = tắt kiểm tra IP, chỉ còn chữ ký HMAC) |
| 2 | Postman: request "Xem Payment theo ID" dùng `paymentId` **trước khi** được set; "Tạo URL VNPay" chạy ngay sau đặt đơn khi Payment (tạo qua Kafka) chưa tồn tại → 404 | Postman: folder Payment sắp lại (1. xem theo orderId **tự retry ≤20 lần/4s**, 2. vnpay-url, 3. xem theo ID) |
| 3 | docs/05 ghi `vnpay-url`=200, `refund`=2xx, `return`=2xx, `login #78`=429 (chỉ 1 request) — **sai**: `vnpay-url` = 500 nếu `VNPAY_HASH_SECRET` rỗng; refund Payment chưa SUCCESS = 400; return với chữ ký sai = 403; rate-limit cần > burst 10 | Postman đã sửa test (#51→403, #78 tự bắn 15 request song song→429, refund→400) |
| 4 | Không có cách test Saga thanh toán (payment.completed → CONFIRMED; payment.failed → CANCELLED + hoàn kho) khi chưa có VNPay thật | Thêm folder Postman **"🧾 VNPay giả lập IPN — Saga thanh toán"** (V1–V15), ký HMAC-SHA512 trong pre-request script |
| 5 | `USE_REDIS_LOCK` mặc định `false` → Redis Distributed Lock (đề bài mục 3, Giai đoạn 1) không chạy | mặc định `true` (compose + `.env.production.example`); vẫn có atomic UPDATE ở DB làm lớp thứ hai |
| 6 | DEPLOY-GUIDE không copy `monitoring/` lên VPS, không điền `GRAFANA_PASSWORD` | `DEPLOY-GUIDE.md` |

Không đổi: `deploy.yml`, Dockerfile, Nginx, `setup-vps.sh` (đã đúng đề bài sau kiểm tra tĩnh).

## 3. Test Saga thanh toán bằng IPN giả lập (máy cá nhân + VPS)
**Điều kiện:** tự đặt một secret bất kỳ (không cần đăng ký VNPay) — IPN do Postman tự ký bằng đúng secret này.
**TRÊN VPS** (`ssh root@103.74.101.153`):
```bash
cd /opt/quickbite
S=$(openssl rand -hex 24); echo "VNPAY_HASH_SECRET của bạn: $S"     # COPY giá trị này cho Postman
sed -i "s|^VNPAY_TMN_CODE=.*|VNPAY_TMN_CODE=QBDEMO01|; s|^VNPAY_HASH_SECRET=.*|VNPAY_HASH_SECRET=$S|; s|^VNPAY_IPN_ALLOWED_IPS=.*|VNPAY_IPN_ALLOWED_IPS=*|" .env
docker compose -f docker-compose.production.yml up -d payment-billing-service     # recreate, chờ healthy
docker compose -f docker-compose.production.yml ps payment-billing-service
```
**MÁY CÁ NHÂN (Postman):** Environment `QuickBite — production`: `baseUrl=https://khoatla-ecommerce.duckdns.org`, `adminUsername=admin`, `adminPassword=<ADMIN_DEFAULT_PASSWORD>`, `vnpHashSecret=<giá trị $S ở trên>`, `healthPath=/health`. Chạy cả Collection (Runner, delay 300 ms) hoặc riêng folder 🧾 sau khi folder Product/Inventory/Order đã chạy (cần `skuId`, `customerToken`, `adminToken`, `userId`).
| Bước | Expected | Verify TRÊN VPS |
|---|---|---|
| V3 | Payment PENDING của đơn A (Postman tự chờ Kafka) | `docker exec postgres psql -U postgres -d payment_billing_db -c "select id,order_id,status,vnp_txn_ref from payments order by id desc limit 3"` |
| V4 | `RspCode":"00"` | log: `docker logs --tail 20 payment-billing-service \| grep -i ipn` |
| V5 | Order A **CONFIRMED** (Outbox payment.completed → Kafka → Order) | `psql -d order_inventory_db -c "select id,status from orders order by id desc limit 4"`; `select status,count(*) from outbox_events group by status` (payment_billing_db & order_inventory_db) |
| V6 | `RspCode":"02"` (idempotent) | — |
| V7 / V14 | 403 (customer) / 400 (refund Payment FAILED) | — |
| V11 → V12 → V13 | IPN thất bại → Order B **CANCELLED** → tồn kho hoàn lại (**Saga compensation**) | `psql -d order_inventory_db -c "select sku,available_qty,reserved_qty from inventories where sku='NIKE-AM270-BLK-42'"` |
| V15 | `RspCode":"97"` (sai chữ ký) | — |
**Sau khi test** (TRÊN VPS) bắt buộc đóng lại: `sed -i 's|^VNPAY_IPN_ALLOWED_IPS=.*|VNPAY_IPN_ALLOWED_IPS=|' .env && docker compose -f docker-compose.production.yml up -d payment-billing-service`.
Giới hạn: refund thành công, `query`, và thanh toán qua trang VNPay thật **cần merchant sandbox VNPay thật** (`VNPAY_TMN_CODE`/`VNPAY_HASH_SECRET` do VNPay cấp; IP gọi IPN của VNPay phải nằm trong `VNPAY_IPN_ALLOWED_IPS`; xem log cảnh báo "IP … không thuộc danh sách"). Refund thành công **không thể** mô phỏng vì code gọi API VNPay thật.

## 4. NÂNG LÊN 2 GB hay 4 GB?
Số liệu (tính từ `mem_limit` trong compose, **chưa đo bằng `docker stats` thật**): core = postgres 160 + redis 64 + kafka 320 + 5×150 + order 190 = **1.484 MB**, cộng OS/Docker/Nginx ~150–200 MB ⇒ **~1,65–1,7 GB**.
| RAM | Core | Monitoring | Đánh giá |
|---|---|---|---|
| 1 GB (hiện tại) | chỉ nhờ swap 3 GB + zram, chậm, rủi ro OOM | không bật | không đáp ứng "ổn định" |
| 2 GB | chạy được nhưng sát (~85% mem_limit) và vẫn dựa swap | chỉ MIN: Prometheus + Grafana (+256 MB) | đủ cho **demo chức năng + monitoring tối thiểu**; KHÔNG đủ Loki/Promtail/Jaeger/tracing |
| 4 GB | thoải mái | FULL (+792 MB) + OTel agent: tổng mem_limit ≈ 3,3 GB (+ OS ~300 MB ≈ 3,6 GB trần, thực tế thấp hơn) | **đủ đúng đề bài** (Prometheus, Grafana, Loki, Promtail, Jaeger, OpenTelemetry) |
**Khuyến nghị: chọn 4 GB**, và ưu tiên gói có **≥ 2 vCPU** (4 vCPU nếu muốn thử 500+ req/s). Lý do: RAM không phải nút cổ chai duy nhất — 1 vCPU gần như chắc chắn không đạt 500–1000 req/s dù RAM bao nhiêu (ước tính, chưa đo). 2 GB chỉ nên chọn nếu bỏ yêu cầu Loki/Jaeger/OTel. Giá/gói cụ thể của AZVPS mình không biết — xem bảng giá trên portal; chỉ cần đủ RAM + vCPU như trên.
Sau khi nâng cấp 4 GB: `cat profiles/4gb.env >> .env` (`standard.env` chỉ dành cho ≥ 8 GB). Hướng dẫn từng bước: `docs/08-v20-4gb-monitoring-loadtest.md`. Rồi:
```bash
# TRÊN VPS
cd /opt/quickbite && bash infra/scripts/get-otel-agent.sh
docker compose -f docker-compose.production.yml -f monitoring/docker-compose.monitoring.yml -f monitoring/docker-compose.tracing.yml --profile full up -d
```
Truy cập Grafana/Jaeger qua SSH tunnel từ **máy cá nhân**: `ssh -L 3000:127.0.0.1:3000 -L 16686:127.0.0.1:16686 root@103.74.101.153` → http://localhost:3000 (dashboard "QuickBite — Overview" tự có), http://localhost:16686.

## 5. Lệnh chạy theo nơi
**TRÊN VPS:** `ufw status verbose`; `ss -tlnp`; `bash infra/scripts/verify-stack.sh`; `bash infra/scripts/smoke-test.sh`; `docker stats --no-stream`; `bash infra/scripts/loadtest-mode.sh on|off`.
**TRÊN MÁY CÁ NHÂN:** `Test-NetConnection 103.74.101.153 -Port 5432` (phải thất bại); `curl.exe -s https://khoatla-ecommerce.duckdns.org/health`; Postman Runner (mục 3); k6:
```powershell
$env:BASE="https://khoatla-ecommerce.duckdns.org"; $env:PW="<ADMIN_DEFAULT_PASSWORD>"
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=smoke   -e STOCK=100 load-test\k6-production-load-test.js
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=ramp100 -e STOCK=100 load-test\k6-production-load-test.js
# chỉ sang ramp500/ramp1000 nếu mức trước không làm container OOM/restart; nhớ chạy loadtest-mode on trên VPS trước và off sau
```

## 6. Final checklist (chỉ điền ☑ sau khi chạy thật)
| Hạng mục | Trạng thái hiện tại |
|---|---|
| Firewall/Network | CONFIG ĐÚNG · ☐ verify |
| CI/CD 5 secrets + deploy | CONFIG ĐÚNG · ☐ chạy |
| Monitoring MIN (2 GB) / FULL+tracing (4 GB) | PARTIAL (OTel chỉ là Java agent) · ☐ verify |
| k6 smoke → 100 → 500 → 1000 | script sẵn sàng · ☐ chạy · **PASS FUNCTIONAL: chưa biết · PASS LOAD (500–1000 req/s): dự đoán FAIL trên 1 vCPU/1 GB** |
| Postman 1-round (Identity/Catalog/Inventory/Order/Notification) | ☐ |
| Payment: IPN ký giả lập, Saga completed/failed | ☐ (cần mục 3) |
| Payment: vnpay-url thật, query, refund thành công | ☐ cần merchant sandbox VNPay |
| Outbox→Kafka→Payment / →Notification, tắt/bật Notification, restart Kafka | ☐ (docs/05 mục F) |
