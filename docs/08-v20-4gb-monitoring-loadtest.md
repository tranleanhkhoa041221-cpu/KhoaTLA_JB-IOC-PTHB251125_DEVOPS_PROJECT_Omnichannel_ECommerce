# 8. VPS đã nâng 4GB: bật Monitoring + Load test theo năng lực VPS thực tế

> Tôi không SSH được VPS: mọi bước dưới đây **bạn** chạy. Mọi con số RAM là **ước tính từ `mem_limit`**, phải xác nhận bằng `docker stats`. Chưa chạy ⇒ trạng thái **CHƯA VERIFY**.
> Ký hiệu: **[VPS]** = sau `ssh root@103.74.101.153`, `cd /opt/quickbite`. **[PC]** = PowerShell trên máy cá nhân.

## PHẦN A — MONITORING & OBSERVABILITY trên VPS 4GB

### A0. Ngân sách RAM (từ `profiles/4gb.env`, trần mem_limit)
| Khối | MB |
|---|---|
| Postgres 256 + Redis 96 + Kafka 384 | 736 |
| 6 service × 300 (khi bật tracing; không tracing: 5×220 + order 260 = 1.360) | 1.800 |
| Monitoring FULL: Prometheus 128 + Grafana 128 + Loki 200 + Promtail 80 + Jaeger 256 | 792 |
| **Tổng trần mem_limit (có tracing)** | **3.328** |
| OS + Docker + Nginx | ~300 |
| **Tổng trần** | **~3,6 GB / 4 GB** — rất sát ⇒ dùng swap làm đệm, và bật **từng bậc**, đo ở mỗi bậc |
Thực tế dùng thấp hơn trần (JVM ít khi chạm mem_limit). Nếu sau bậc 3 mà `free -m` available < 300 MB hoặc swap dùng > 1 GB ⇒ tắt tracing (bậc 2) hoặc tắt Promtail/Jaeger, chạy MIN.

### A1. Kiểm tra VPS sau nâng cấp [VPS]
```bash
free -m; nproc; swapon --show; df -h /       # mong doi ~3900MB RAM, swap 3G con nguyen
docker compose ps                            # core stack van healthy
```
Nếu swap mất sau khi nâng cấp/reboot: `bash infra/scripts/setup-vps.sh` (idempotent) hoặc `swapon -a`.

### A2. Cập nhật file lên VPS
Cách 1 (CI/CD): push `main` — deploy.yml tự copy `docker-compose.production.yml, infra, monitoring, profiles` lên `/opt/quickbite`.
Cách 2 (tay) [PC]: `scp -r monitoring profiles infra docker-compose.production.yml root@103.74.101.153:/opt/quickbite/`

### A3. Nạp preset 4GB + chọn file compose [VPS]
```bash
cd /opt/quickbite
cat profiles/4gb.env >> .env                # key trung: dong CUOI thang
grep -q '^GRAFANA_PASSWORD=' .env || echo "GRAFANA_PASSWORD=$(openssl rand -base64 18 | tr -d '/+=')" >> .env
grep -E '^(GRAFANA_PASSWORD|MEM_|JAVA_OPTS)' .env | sed 's/PASSWORD=.*/PASSWORD=***/'
# Chon BAC 1 (monitoring, CHUA tracing):
sed -i '/^COMPOSE_FILE=/d;/^COMPOSE_PROFILES=/d' .env
echo 'COMPOSE_FILE=docker-compose.production.yml:monitoring/docker-compose.monitoring.yml' >> .env
echo 'COMPOSE_PROFILES=full' >> .env
docker compose config -q && echo "compose OK"
```
(`.env` đang dùng `COMPOSE_FILE` nên **mọi** lệnh `docker compose ...` sau này đều thấy cả monitoring; script deploy/loadtest cũng vậy.)

### A4. BẬC 1 — Prometheus + Grafana + Loki + Promtail + Jaeger (chưa tracing) [VPS]
```bash
docker compose up -d --remove-orphans
watch -n 10 'docker compose ps; free -m | head -3'      # cho tat ca healthy (core se recreate vi mem_limit moi: 5-15 phut)
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.CPUPerc}}'
docker inspect -f '{{.Name}} OOM={{.State.OOMKilled}} restarts={{.RestartCount}}' $(docker ps -q)
```
Đạt bậc 1 khi: tất cả `healthy`, không `OOM=true`, `free -m` available > 400 MB. Không đạt ⇒ dừng, đừng bật tiếp.

### A5. BẬC 2 — OpenTelemetry tracing (Java agent) [VPS]
```bash
bash infra/scripts/get-otel-agent.sh                    # tai opentelemetry-javaagent.jar vao /opt/quickbite/otel
ls -la otel/opentelemetry-javaagent.jar
sed -i 's|^COMPOSE_FILE=.*|COMPOSE_FILE=docker-compose.production.yml:monitoring/docker-compose.monitoring.yml:monitoring/docker-compose.tracing.yml|' .env
docker compose up -d --remove-orphans                   # 6 service restart tuan tu, JVM + agent: cham, cho 10-20 phut
docker compose ps; free -m; docker stats --no-stream
```
Mặc định lấy mẫu trace 20% (`OTEL_SAMPLE_RATIO=0.2`). Nếu thiếu RAM: `echo OTEL_SAMPLE_RATIO=0.05 >> .env` rồi `up -d`, hoặc quay lại bậc 1 (đổi `COMPOSE_FILE` về 2 file rồi `up -d --remove-orphans`).

### A6. Verify tự động + thủ công [VPS]
```bash
bash infra/scripts/verify-stack.sh            # muc 10: Prometheus >= 7 target up, Grafana, Loki, Jaeger, OOM/restart monitoring
# Prometheus scrape 6 service
docker exec prometheus wget -qO- http://localhost:9090/api/v1/targets | grep -o '"health":"[a-z]*"' | sort | uniq -c    # 7 up
# Grafana
curl -s http://127.0.0.1:3000/api/health
# Loki nhan log
docker exec loki wget -qO- http://localhost:3100/ready
docker exec loki wget -qO- 'http://localhost:3100/loki/api/v1/label/container/values'
# Public KHONG thay monitoring
ss -tlnp | grep -E ':(3000|9090|16686|3100|4317|4318)\s'     # chi 127.0.0.1 (3000, 16686, 4317, 4318); 9090/3100 khong publish
```

### A7. Mở giao diện qua SSH tunnel (UI KHÔNG public) [PC]
```powershell
ssh -L 3000:127.0.0.1:3000 -L 16686:127.0.0.1:16686 root@103.74.101.153
```
Giữ cửa sổ này mở. Trình duyệt máy bạn:
1. http://localhost:3000 → user `admin`, mật khẩu = `GRAFANA_PASSWORD` trong `.env` VPS → Dashboards → **QuickBite — Overview** (request rate, latency, JVM heap, CPU). Connections → Data sources → Test: Prometheus/Loki/Jaeger xanh.
2. Sinh traffic: chạy Postman Runner (docs/05) hoặc k6 smoke. Grafana thấy rate tăng.
3. Grafana → Explore → Loki: `{container="order-inventory-service"} |= "ORDER"` (đổi tên container theo danh sách label ở A6) → thấy log.
4. http://localhost:16686 → Service `api-gateway` / `order-inventory-service` → Find Traces → mở 1 trace tạo đơn (thấy span HTTP/gRPC/JDBC/Kafka tuỳ agent bắt được).
5. Chụp màn hình 4 thứ trên làm **bằng chứng**.
Kiểm tra từ máy cá nhân: `Test-NetConnection 103.74.101.153 -Port 3000` (và 9090, 16686) phải **thất bại**.

### A8. Kết luận Monitoring (điền sau khi chạy)
| Hạng mục | Trạng thái |
|---|---|
| File cấu hình Prometheus/Grafana/Loki/Promtail/Jaeger | Có trong source (kiểm tra tĩnh) — CHƯA VERIFY chạy |
| OpenTelemetry | **PARTIAL**: dùng Java agent (không sửa code); PASS chỉ khi Jaeger hiện trace thật |
| UI không public | Cấu hình bind 127.0.0.1 — PASS khi `Test-NetConnection` thất bại |
| 4GB không OOM | PASS khi `OOM=false`, restart không tăng, available RAM > 300 MB sau 30 phút |

---
## PHẦN B — LOAD TEST THEO NĂNG LỰC VPS THỰC TẾ (không ép 500–1000 req/s)

Mục tiêu: **đo xem VPS này chịu được bao nhiêu** và chứng minh **đúng nghiệp vụ** (không oversell). k6 chạy từ **máy cá nhân** (HTTPS), không chạy trên VPS. Dùng file có sẵn `load-test/k6-production-load-test.js` (scenario `custom` dùng `RATE` + `HOLD`, tự tạo brand/category/product/SKU `FLASH-<runId>`/inventory/USERS customer — không cần chuẩn bị Postman).

### B0. Chuẩn bị [PC] (1 lần)
```powershell
winget install k6          # mo PowerShell moi sau khi cai
k6 version
cd <thu-muc-giai-nen-fullproject>
$env:BASE="https://khoatla-ecommerce.duckdns.org"
$env:PW="<ADMIN_DEFAULT_PASSWORD trong .env VPS>"
curl.exe -s $env:BASE/health      # {"status":"UP"}
```
Nên làm **trước**: Postman 1-round (docs/05) xanh — nếu chức năng sai thì load test vô nghĩa.

### B1. Chuẩn bị VPS [VPS]
```bash
cd /opt/quickbite
bash infra/scripts/loadtest-mode.sh on         # noi rate-limit Nginx + Gateway (de k6 tu 1 IP khong bi 429)
docker stats --no-stream; free -m               # baseline
```
Khuyến nghị: để đo **năng lực thật**, tạm tắt tracing (đổi `COMPOSE_FILE` về 2 file → `docker compose up -d --remove-orphans`) vì OTel agent tốn CPU/RAM; ghi rõ trong báo cáo nếu để bật.
Mở **cửa sổ SSH thứ 2** để theo dõi: `watch -n 5 "docker stats --no-stream --format 'table {{.Name}}\t{{.MemPerc}}\t{{.CPUPerc}}'; free -m | head -3"`.

### B2. Smoke [PC]
```powershell
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=smoke -e STOCK=100 load-test\k6-production-load-test.js
```
Ghi lại dòng SKU in ra cuối (`FLASH-xxxx`). Smoke fail ⇒ dừng, sửa chức năng.

### B3. Tăng dần theo NĂNG LỰC VPS (scenario custom) [PC]
Mỗi mức: tăng 20s → giữ `HOLD` → hạ 10s. Chạy lần lượt, nghỉ 2 phút giữa các mức (cho Kafka/Outbox xử lý xong):
```powershell
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=custom -e RATE=10  -e HOLD=60s -e STOCK=100 load-test\k6-production-load-test.js
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=custom -e RATE=25  -e HOLD=60s -e STOCK=100 load-test\k6-production-load-test.js
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=custom -e RATE=50  -e HOLD=60s -e STOCK=100 load-test\k6-production-load-test.js
k6 run -e BASE_URL=$env:BASE -e ADMIN_PASSWORD=$env:PW -e SCENARIO=custom -e RATE=100 -e HOLD=60s -e STOCK=100 load-test\k6-production-load-test.js
```
Dừng tăng khi gặp 1 trong: `p95 > 3s`, 5xx > 1%, `dropped_iterations` lớn, container `OOMKilled`/restart, `free -m` available < 150 MB. **Mức cao nhất vẫn sạch = năng lực đo được** của VPS này.
Vì STOCK=100 (cố ý, để tranh tồn kho trên cùng 1 SKU), phần lớn request sẽ nhận 409 "hết hàng" — đó là hành vi đúng. Muốn đo thông lượng đơn **thành công** thì thêm 1 lần `-e STOCK=3000 -e RATE=25`.
Tùy chọn các mức đề bài (chỉ để ghi nhận hành vi khi quá tải, **không** kỳ vọng đạt): `-e SCENARIO=ramp100|ramp500|ramp1000`.

### B4. Verify sau MỖI mức [VPS] (thay `FLASH-xxxx`)
```bash
docker exec postgres psql -U postgres -d order_inventory_db -c "SELECT sku, available_qty, reserved_qty, (available_qty+reserved_qty) total FROM inventories WHERE sku='FLASH-xxxx';"    # total = STOCK, available >= 0
docker exec postgres psql -U postgres -d order_inventory_db -c "SELECT status, count(*) FROM orders GROUP BY status;"
docker exec postgres psql -U postgres -d order_inventory_db -c "SELECT status, count(*) FROM outbox_events GROUP BY status;"      # PENDING ve 0 sau vai phut
docker exec postgres psql -U postgres -d payment_billing_db -c "SELECT status, count(*) FROM payments GROUP BY status;"
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups                # LAG ve 0
docker exec redis redis-cli INFO memory | grep -E "used_memory_human|evicted_keys"
docker stats --no-stream; free -m
docker inspect -f '{{.Name}} OOM={{.State.OOMKilled}} restarts={{.RestartCount}}' $(docker ps -q)
```

### B5. Bảng kết quả (điền từ summary k6 + verify)
| Mức RATE | RPS thực | p95 / p99 (ms) | error 5xx | 429 | đơn thành công | 409 hết hàng | dropped_iter | oversell? | OOM/restart | CPU max | RAM avail |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 10 | | | | | | | | | | | |
| 25 | | | | | | | | | | | |
| 50 | | | | | | | | | | | |
| 100 | | | | | | | | | | | |
Quy tắc: đơn thành công (`orders_created`) = `STOCK − available_qty` = số hàng `orders` của SKU; `available_qty ≥ 0`; `available+reserved = STOCK`.

### B6. Dọn dẹp [VPS]
```bash
bash infra/scripts/loadtest-mode.sh off        # BAT BUOC: tra rate-limit ve an toan
# (neu da tat tracing de do) dat lai COMPOSE_FILE 3 file roi: docker compose up -d --remove-orphans
```

### B7. Cách kết luận (2 dòng riêng)
- **PASS FUNCTIONAL** ⇔ ở mọi mức đã chạy: không oversell, số đơn khớp, Outbox/Kafka/Payment đuổi kịp, không OOM.
- **LOAD THEO ĐỀ (500–1000 req/s)**: ghi **KHÔNG TEST/FAIL** nếu chưa đạt thực tế — theo yêu cầu của bạn, tiêu chí cho VPS này là *"mức RATE cao nhất ổn định đo được"* (ví dụ: "ổn định tới 50 req/s, p95 = …"). Tuyệt đối không ghi 500–1000 nếu số đo không có.
Dự đoán (chưa đo): VPS 4GB nhưng chỉ 1–2 vCPU thì CPU là nút cổ chai, mức ổn định kỳ vọng ở hàng chục req/s.
