# 1. Firewall & Network — kiểm tra và xác minh

## Kết quả kiểm tra tĩnh (đọc cấu hình, CHƯA chạy trên VPS)
| Hạng mục | Trạng thái | Căn cứ |
|---|---|---|
| UFW chỉ mở 22/80/443 (22 dùng `limit`) | Cấu hình ĐÚNG | `infra/scripts/setup-vps.sh` mục 3 |
| Postgres/Redis/Kafka/gRPC/service nội bộ không publish port | Cấu hình ĐÚNG | `docker-compose.production.yml`: chỉ `api-gateway` có `ports:` = `127.0.0.1:8080:8080` |
| Gateway là entry point duy nhất | Cấu hình ĐÚNG | Nginx (80/443) → `127.0.0.1:8080` |
| Grafana/Jaeger không lộ Internet | Cấu hình ĐÚNG | `monitoring/*.yml` bind `127.0.0.1` |
| `/actuator`, `/docs`, `/swagger-ui` bị chặn ngoài Internet | Cấu hình ĐÚNG | `infra/nginx/quickbite.conf.template` trả 404 |
| Xác minh thực tế trên VPS và từ máy cá nhân | **CHƯA VERIFY** | chạy các lệnh bên dưới |

Lưu ý: Docker tự chèn rule iptables và **bỏ qua UFW** nếu publish `0.0.0.0:PORT`. Vì vậy cách an toàn là bind `127.0.0.1` (đã làm) chứ không dựa vào UFW.

## A. Chạy TRÊN VPS (ssh root@IP)
```bash
ufw status verbose                      # Status: active; 22 ALLOW, 80 ALLOW, 443 ALLOW; Default: deny (incoming)
ss -tlnp                                # chỉ thấy :22, :80, :443 (0.0.0.0/[::]) và 127.0.0.1:8080 (docker-proxy)
docker ps --format 'table {{.Names}}\t{{.Ports}}'   # chỉ api-gateway có 127.0.0.1:8080->8080; còn lại KHÔNG có "0.0.0.0:"
ss -tlnH | awk '{print $4}' | grep -E ':(5432|6379|9092|9093|8082|8083|8084|8085|8086|9090|9091|3000|16686)$' | grep -v '^127\.' \
  && echo "LOI: port noi bo dang public" || echo "OK: khong co port noi bo public"
bash /opt/quickbite/infra/scripts/verify-stack.sh    # muc 4 cung kiem tra port
```
## B. Chạy TỪ MÁY CÁ NHÂN (Windows PowerShell) — thay IP/domain của bạn
```powershell
$ip = "103.74.101.153"
foreach ($p in 22,80,443)                                   { "{0,6} -> {1}" -f $p,(Test-NetConnection $ip -Port $p -WarningAction SilentlyContinue).TcpTestSucceeded }   # mong doi: True
foreach ($p in 5432,6379,9092,9093,8080,8082,8083,8084,8085,8086,9090,9091,3000,16686) { "{0,6} -> {1}" -f $p,(Test-NetConnection $ip -Port $p -WarningAction SilentlyContinue).TcpTestSucceeded }  # mong doi: False
curl.exe -s -o NUL -w "%{http_code}`n" https://khoatla-ecommerce.duckdns.org/actuator/health     # mong doi: 404
curl.exe -s https://khoatla-ecommerce.duckdns.org/health                                         # mong doi: {"status":"UP"...}
curl.exe -sI http://khoatla-ecommerce.duckdns.org | findstr /i "HTTP Location"                   # mong doi: 301 -> https
```
(Linux/macOS có thể dùng `nmap -Pn -p 22,80,443,5432,6379,9092,8080 <IP>`: chỉ 22/80/443 là `open`.)

## C. Nếu sai
| Triệu chứng | Sửa |
|---|---|
| UFW `inactive` | `ufw default deny incoming; ufw default allow outgoing; ufw allow 22/tcp; ufw allow 80/tcp; ufw allow 443/tcp; ufw --force enable` |
| Có `0.0.0.0:xxxx->` trong `docker ps` | Xóa dòng `ports:` đó khỏi compose (hoặc đổi `127.0.0.1:xxxx:xxxx`) rồi `docker compose -f docker-compose.production.yml up -d` |
| `/actuator/health` công khai trả 200 | Nginx chưa nạp template: `nginx -t && systemctl reload nginx`; kiểm tra `/etc/nginx/sites-enabled/quickbite` có khối `location ~ ^/(actuator|docs|...)` |
