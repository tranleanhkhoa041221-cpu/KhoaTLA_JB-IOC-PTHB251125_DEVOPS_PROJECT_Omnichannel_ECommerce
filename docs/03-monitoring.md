# 3. Monitoring — đối chiếu đề bài, RAM, cách verify

## Hiện trạng source (kiểm tra thật, không suy đoán)
| Yêu cầu đề | Có trong source? | Ghi chú |
|---|---|---|
| Prometheus thu CPU/RAM/request rate | CÓ | 6 service có `micrometer-registry-prometheus`, expose `/actuator/prometheus`; `monitoring/prometheus.yml` scrape 6 target. **Lỗi đã sửa:** Gateway chặn `/actuator/prometheus` (401) → đã thêm pattern public (Nginx vẫn chặn từ Internet) |
| Grafana | CÓ (datasource Prometheus/Loki/Jaeger tự provision) | Có dashboard "QuickBite — Overview" tự provision (`monitoring/grafana/provisioning/dashboards/quickbite-overview.json`); có thể import thêm ID `4701` (JVM Micrometer) |
| Loki + Promtail (gom log) | CÓ | **Lỗi đã sửa:** Loki dùng config mặc định (dữ liệu nằm ngoài volume, không retention). Thêm `monitoring/loki-config.yml` retention 24h |
| Jaeger | CÓ container | |
| OpenTelemetry / Distributed tracing | **KHÔNG có trong source** (không dependency, không cấu hình) → Jaeger trống | Thêm `monitoring/docker-compose.tracing.yml` dùng **OpenTelemetry Java agent** (không sửa code). **Chưa kiểm chứng** |

## 2GB có đủ không?
Tổng `mem_limit` core hiện tại: postgres 160 + redis 64 + kafka 320 + 5 service×150 + order 190 = **1.484 MB**, cộng OS/Docker/Nginx ~150–200 MB ⇒ **~1,65 GB**.
| RAM VPS | Core | Monitoring | Kết luận |
|---|---|---|---|
| 1 GB (gói hiện tại) | chỉ chạy được nhờ swap/zram, chậm | **Không** bật gì | Monitoring gây OOM |
| 2 GB | Chạy được | **MIN**: Prometheus 128m + Grafana 128m (bắt buộc giữ swap) | **FULL không đủ** (thiếu ~0,6 GB). Không bật tracing |
| 4 GB | Thoải mái | **FULL** (+792m tổng monitoring) + **TRACING** | Khuyến nghị cho demo đầy đủ theo đề |
Đây là ước tính từ `mem_limit`, cần xác nhận bằng `docker stats` thực tế.

## Cách bật
```bash
cd /opt/quickbite
# MIN (>= 2GB)
docker compose -f docker-compose.production.yml -f monitoring/docker-compose.monitoring.yml up -d prometheus grafana
# FULL (>= 4GB)
docker compose -f docker-compose.production.yml -f monitoring/docker-compose.monitoring.yml --profile full up -d
# FULL + TRACING (>= 4GB)
bash infra/scripts/get-otel-agent.sh
docker compose -f docker-compose.production.yml -f monitoring/docker-compose.monitoring.yml -f monitoring/docker-compose.tracing.yml --profile full up -d
```
Cần `GRAFANA_PASSWORD` trong `.env`. Overlay tracing đặt `mem_limit` của CẢ 6 service = `MEM_SVC_TRACING` (mặc định 320m; heap vẫn theo `JAVA_OPTS_*`). Trên VPS 4GB chạy `cat profiles/4gb.env >> .env` trước (đặt 300m). Hướng dẫn chi tiết: `docs/08-v20-4gb-monitoring-loadtest.md`.

## Verify (chạy TRÊN VPS)
```bash
# 1. Prometheus scrape: 6/6 target "up"
docker exec prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=up' | grep -o '"job":"services"[^}]*}[^]]*"value":\[[^]]*\]' | head -8
docker exec prometheus wget -qO- http://localhost:9090/api/v1/targets | grep -o '"health":"[a-z]*"' | sort | uniq -c       # mong doi: 7 up (6 service + prometheus)
# 2. Grafana
curl -s http://127.0.0.1:3000/api/health                                       # "database":"ok"
#    May ca nhan: ssh -L 3000:127.0.0.1:3000 root@IP  -> http://localhost:3000 (admin / GRAFANA_PASSWORD) -> Connections > Data sources > Test (3 cai xanh)
# 3. Loki + Promtail (FULL)
docker exec loki wget -qO- http://localhost:3100/ready                        # ready
docker exec loki wget -qO- 'http://localhost:3100/loki/api/v1/label/container/values'   # liet ke ten container
#    Grafana > Explore > Loki: {container="order-inventory-service"} |= "orderCode"
# 4. Tracing (FULL + TRACING): goi vai API (Postman) roi
curl -s http://127.0.0.1:16686/api/services                                    # phai co api-gateway, order-inventory-service,...
#    ssh -L 16686:127.0.0.1:16686 root@IP -> http://localhost:16686 -> Service order-inventory-service -> Find Traces (co span gRPC/Kafka/JDBC)
# 5. CPU/RAM + OOM
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.CPUPerc}}'
free -m; docker inspect -f '{{.Name}} OOM={{.State.OOMKilled}} restarts={{.RestartCount}}' $(docker ps -q)
```
Tiêu chí: không container nào `OOMKilled=true`, swap không đầy, `MemPerc` < 90%.

## Trạng thái
| Phần | Trạng thái |
|---|---|
| Prometheus/Grafana/Loki/Promtail/Jaeger (file cấu hình) | Có, đã sửa lỗi scrape + Loki |
| Tracing OTel | **PARTIAL** — có overlay Java agent, chưa chạy; core source chưa tích hợp OTel |
| Bất kỳ mục nào ở "Verify" | **CHƯA VERIFY** (cần VPS ≥ 2GB/4GB) |
