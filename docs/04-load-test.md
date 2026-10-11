# 4. Load test / Flash Sale (k6 chạy trên MÁY CÁ NHÂN → HTTPS production)

File `load-test/k6-production-load-test.js` **đã có** trong v15; mình **sửa** (FULL file trong zip):
- Bỏ mật khẩu mặc định `Admin@123456` (bắt buộc `-e ADMIN_PASSWORD`).
- Số điện thoại unique mỗi lần chạy (cột `users.phone` unique → lần chạy thứ 2 trước đây làm user bị bỏ qua).
- `400` không còn bị tính là "hết hàng" (hết hàng = **409** `InsufficientStockException`).
- Sửa tên bảng trong lệnh verify: `inventories` (không phải `inventory_items`), bỏ dấu `'` thừa.
- Thêm `handleSummary` (RPS, p95, p99, error rate, đơn thành công, hết hàng, 429, dropped iterations) + xuất JSON.
Phát hiện quan trọng: **Nginx (50 r/s, `limit_conn 50`/IP) và Gateway (login 5/s/IP, order 100/s) sẽ chặn k6 từ 1 IP** → thêm `infra/scripts/loadtest-mode.sh on|off`.

## Chuẩn bị
**Trên VPS:** `bash /opt/quickbite/infra/scripts/loadtest-mode.sh on` (xong test: `... off`).
**Trên máy cá nhân:** cài k6 (`winget install k6`). Cần `ADMIN_DEFAULT_PASSWORD` của VPS.
Dữ liệu: `setup()` tự tạo Brand/Category/Product/SKU `FLASH-<runId>`/Inventory + 50 customer (mật khẩu `Password123!`, mỗi user 1 địa chỉ mặc định) → **không cần** chuẩn bị bằng Postman. (Nếu muốn tạo tay, dùng data mẫu trong `docs/05-production-1round-test.md` mục B.)

## Kịch bản tăng dần (cùng 1 SKU, STOCK=100 để buộc tranh tồn kho)
```powershell
$env:BASE="https://khoatla-ecommerce.duckdns.org"; $env:PW="<ADMIN_DEFAULT_PASSWORD>"
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=smoke    -e STOCK=100 load-test\k6-production-load-test.js
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=ramp100  -e STOCK=100 load-test\k6-production-load-test.js
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=ramp500  -e STOCK=100 load-test\k6-production-load-test.js
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=ramp1000 -e STOCK=100 load-test\k6-production-load-test.js
```
Chạy lần lượt; chỉ sang mức tiếp theo nếu mức trước không làm container OOM/restart. Giữa các lần chờ 2 phút để Kafka/Outbox xử lý hết.

## Verify TRÊN VPS sau mỗi lần (thay SKU in ra ở cuối k6)
```bash
docker exec postgres psql -U postgres -d order_inventory_db -c "SELECT sku, available_qty, reserved_qty, (available_qty+reserved_qty) total FROM inventories WHERE sku='FLASH-XXXX';"   # total = 100, available >= 0
docker exec postgres psql -U postgres -d order_inventory_db -c "SELECT status, count(*) FROM orders GROUP BY status;"
docker exec postgres psql -U postgres -d order_inventory_db -c "SELECT status, count(*) FROM outbox_events GROUP BY status;"       # PENDING ve 0 sau vai phut
docker exec postgres psql -U postgres -d payment_billing_db -c "SELECT status, count(*) FROM payments GROUP BY status;"            # so payment = so don thanh cong (Kafka)
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups                # LAG ve 0
docker exec redis redis-cli INFO memory | grep -E "used_memory_human|evicted_keys"; docker exec redis redis-cli --scan --pattern '*FLASH*' | head
docker stats --no-stream; free -m
docker inspect -f '{{.Name}} OOM={{.State.OOMKilled}} restarts={{.RestartCount}}' $(docker ps -q)
```
Số đơn thành công (k6 `orders_created`) phải bằng `100 - available_qty` và bằng số hàng `orders` thuộc SKU của run đó. Nếu `available_qty < 0` hoặc `available+reserved ≠ 100` ⇒ **FAIL FUNCTIONAL (oversell)**.

## Cách kết luận
- **PASS FUNCTIONAL**: không oversell (`available ≥ 0`, tổng = STOCK), số đơn = số lượng đã trừ, Outbox/Kafka/Payment đuổi kịp, không 5xx dây chuyền.
- **PASS LOAD REQUIREMENT** (đề: 500–1000 req/s): k6 đạt đủ RPS mục tiêu (`dropped_iterations ≈ 0`), `p95`/`p99` trong threshold, 429 = 0, 5xx ≈ 0, không OOMKilled/restart.
- Dự đoán (chưa chạy): trên VPS 1 vCPU/1GB, **FAIL LOAD** ở 500/1000 req/s gần như chắc chắn (1 vCPU xử lý JSON+JWT+gRPC+DB cho ~vài chục req/s); chỉ kỳ vọng PASS FUNCTIONAL ở smoke/ramp100 thấp. Muốn PASS LOAD cần VPS ≥ 4 vCPU/8GB + `profiles/standard.env`.

> **Cập nhật v20:** VPS đã nâng 4GB và người dùng chấp nhận test theo năng lực thực tế → xem `docs/08-v20-4gb-monitoring-loadtest.md` Phần B (scenario `custom`: `-e RATE=… -e HOLD=…`).
