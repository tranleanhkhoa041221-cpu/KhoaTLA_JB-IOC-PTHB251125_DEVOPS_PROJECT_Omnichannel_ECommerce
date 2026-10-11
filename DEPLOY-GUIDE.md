# HƯỚNG DẪN DEPLOY TỪ ĐẦU — QuickBite lên VPS AZVPS 4GB (Docker Compose + DuckDNS + Nginx + HTTPS + CI/CD)

> Bạn **chưa làm gì trên VPS** ⇒ làm tuần tự từ Bước 0. Mỗi bước có **"Đạt khi"** — chưa đạt thì dừng, gửi mình output.
> Ký hiệu: **[PC]** = PowerShell trên máy cá nhân (Windows). **[VPS]** = sau khi `ssh root@103.74.101.153`. **[WEB]** = trình duyệt.
> Domain: `khoatla-ecommerce.duckdns.org` · IP VPS: `103.74.101.153` · user: `root` (theo ảnh bạn gửi).
> Không dán mật khẩu/token vào chat, không commit `.env` lên git.

Tổng thời gian: ~1,5–2 giờ (lần khởi động đầu của stack mất 10–25 phút).

---
## BƯỚC 0 — Chuẩn bị (WEB, ~15 phút)
1. **AZVPS portal**: bấm **Bật** VPS (ảnh cũ đang Offline). Mục *Cài đặt* → xác nhận gói đã là **4GB RAM** và HĐH **Ubuntu 22.04** (nếu khác: cài lại OS thành Ubuntu 22.04). Dùng chức năng **đặt lại mật khẩu root**, lưu mật khẩu lại.
2. **DuckDNS** (https://www.duckdns.org, đăng nhập `tranleanhkhoa041221@gmail.com`): subdomain `khoatla-ecommerce` → ô *current ip* = `103.74.101.153` → **update ip**. Copy **token** (chỉ lưu ở máy bạn).
3. **Thu hồi secret cũ** (đã từng nằm trong source): Gmail App Password cũ (https://myaccount.google.com/apppasswords), Twilio Auth Token cũ, và **DuckDNS token cũ nếu từng dán ở đâu đó → bấm "regenerate token"**.
4. Máy Windows: mở **PowerShell**; kiểm tra `ssh -V` và `scp` có sẵn.
**Đạt khi:** VPS trạng thái *Online*; `nslookup khoatla-ecommerce.duckdns.org` ra `103.74.101.153`.

## BƯỚC 1 — SSH vào VPS, kiểm tra cấu hình [PC → VPS]
```powershell
ssh root@103.74.101.153
```
```bash
lsb_release -d; nproc; free -m; df -h /
```
**Đạt khi:** Ubuntu 22.04; RAM ~3900 MB; ổ đĩa còn trống **≥ 12 GB** (script swap 3GB cần `trống > 11GB`). Ghi lại số `nproc` (1/2/4 vCPU — ảnh hưởng load test).
Cập nhật: `apt-get update -y && apt-get upgrade -y` (nếu hỏi giữ config → giữ bản hiện tại). Nếu có yêu cầu reboot: `reboot`, đợi 1 phút, SSH lại.

## BƯỚC 2 — Đưa source lên VPS [PC → VPS]
Giải nén `fullproject-v20.zip` trên máy bạn (ra thư mục có `docker-compose.production.yml`), rồi **[PC]**, đứng cạnh file zip:
```powershell
scp .\fullproject-v20.zip root@103.74.101.153:/root/
```
**[VPS]**:
```bash
apt-get install -y unzip
mkdir -p /opt/quickbite-src && unzip -o /root/fullproject-v20.zip -d /opt/quickbite-src
cd /opt/quickbite-src && ls          # phai thay docker-compose.production.yml, infra, monitoring, profiles
# neu ls chi thay 1 thu muc con (vd v17) thi: cd ten-thu-muc-do
pwd                                   # ghi nho -> $SRC
```
**Đạt khi:** `ls` thấy `docker-compose.production.yml` và thư mục `infra`.

## BƯỚC 3 — Dựng hạ tầng VPS bằng script (swap 3GB + Docker + Nginx + HTTPS + UFW) [VPS]
```bash
cd /opt/quickbite-src        # (hoac thu muc con co infra/)
DOMAIN=khoatla-ecommerce.duckdns.org EMAIL=tranleanhkhoa041221@gmail.com bash infra/scripts/setup-vps.sh
```
Script lần lượt: tạo **swap 3GB + zram** → cài Docker/Nginx/Certbot → bật **UFW chỉ 22/80/443** → **kiểm tra DuckDNS trỏ đúng IP** (sai thì dừng, bạn sửa DuckDNS rồi chạy lại) → cấp chứng chỉ **Let's Encrypt** → cài cron DuckDNS (5 phút) + backup DB 02:30.
Tạo file token cho cron DuckDNS (thay `<TOKEN>` bằng token của bạn):
```bash
mkdir -p /etc/quickbite
printf 'DUCKDNS_SUBDOMAIN=khoatla-ecommerce\nDUCKDNS_TOKEN=<TOKEN>\n' > /etc/quickbite/duckdns.env
chmod 600 /etc/quickbite/duckdns.env
```
Kiểm tra:
```bash
free -m; swapon --show              # thay /swapfile 3G + zram
ufw status verbose                   # chi 22 (LIMIT), 80, 443 ALLOW
nginx -t && systemctl status nginx --no-pager | head -5
curl -I https://khoatla-ecommerce.duckdns.org     # 502 la BINH THUONG luc nay (chua co gateway) - mien co HTTPS hop le
```
**Đạt khi:** swap 3G có mặt; UFW đúng 3 cổng; `curl -I` trả `HTTP/2 502` (không lỗi chứng chỉ).
Lỗi thường gặp: *"chua tro ve"* → DuckDNS chưa update IP; Certbot fail → chờ 2 phút/kiểm tra port 80 (`ufw status`).

## BƯỚC 4 — Chuẩn bị thư mục chạy + file `.env` [VPS]
```bash
mkdir -p /opt/quickbite && cd /opt/quickbite
cp -r /opt/quickbite-src/docker-compose.production.yml /opt/quickbite-src/infra /opt/quickbite-src/profiles /opt/quickbite-src/monitoring /opt/quickbite/
cp /opt/quickbite-src/.env.production.example .env && chmod 600 .env
# sinh secret ngau nhien
for v in DB_SUPERUSER_PASSWORD DB_PASS_IDENTITY_USER DB_PASS_PRODUCT_CATALOG DB_PASS_ORDER_INVENTORY DB_PASS_PAYMENT_BILLING DB_PASS_NOTIFICATION JWT_SECRET_KEY INTERNAL_API_KEY ADMIN_DEFAULT_PASSWORD GRAFANA_PASSWORD; do
  sed -i "s|^$v=.*|$v=$(openssl rand -base64 36 | tr -d '/+=')|" .env; done
# nap preset 4GB (gioi han RAM tung container + JVM heap thap)
cat profiles/4gb.env >> .env
grep -E "^(DOMAIN|IMAGE_PREFIX|IMAGE_TAG|COMPOSE_FILE|CORS_ALLOWED_ORIGINS)=" .env
grep "^ADMIN_DEFAULT_PASSWORD=" .env       # GHI LAI: day la mat khau dang nhap admin
```
Sửa tay (`nano .env`):
- `IMAGE_PREFIX=ghcr.io/<github-username-viet-thuong>` (ví dụ `ghcr.io/khoatla`; **toàn chữ thường**; CI cũng tự ghi đè dòng này).
- `COMPOSE_FILE=docker-compose.production.yml` (giữ nguyên — **chỉ core trước**, monitoring bật sau ở Bước 9).
- VNPay/Mail/SMS: **để trống/false** (chỉ điền khi test thật — xem `docs/07-v19-real-email-sms-vnpay.md`).
> **Không đổi mật khẩu DB sau lần chạy đầu** (Postgres đã tạo role với mật khẩu cũ). Lỡ đổi: `docker compose down -v` rồi chạy lại (mất dữ liệu).
> Sao lưu file `.env` ra chỗ an toàn của bạn (mất `.env` = mất secret).
**Đạt khi:** `.env` có đủ giá trị (không còn dòng `DB_PASS_...=` trống), quyền `600`.

## BƯỚC 5 — Đưa Docker image lên VPS (VPS không build Gradle nổi → CI build) — chọn A hoặc B

### Cách A — GitHub Actions + GHCR (đúng đề bài CI/CD) [khuyến nghị]
**5A.1 Tạo repo GitHub [WEB]:** https://github.com/new → tên `quickbite` → **Private** → *không* tick README/.gitignore → Create.
**5A.2 Tạo SSH key deploy [PC]:**
```powershell
ssh-keygen -t ed25519 -f $HOME\.ssh\qb_deploy -N '""' -C "github-actions-deploy"
type $HOME\.ssh\qb_deploy.pub          # copy 1 dong nay
```
**5A.3 Đưa public key lên VPS [VPS]:**
```bash
mkdir -p /root/.ssh && chmod 700 /root/.ssh
echo '<DAN-NOI-DUNG-qb_deploy.pub-VAO-DAY>' >> /root/.ssh/authorized_keys
chmod 600 /root/.ssh/authorized_keys
```
Kiểm tra **[PC]**: `ssh -i $HOME\.ssh\qb_deploy root@103.74.101.153 "echo OK"` → in `OK` (không hỏi mật khẩu).
**5A.3b (nếu VPS tắt đăng nhập root bằng key):** `grep -E "^PermitRootLogin" /etc/ssh/sshd_config` — phải là `yes` hoặc `prohibit-password`.
**5A.4 Tạo GHCR token [WEB]:** GitHub → avatar → Settings → Developer settings → Personal access tokens → **Tokens (classic)** → Generate new token (classic) → tick **`read:packages`** → Expiration tuỳ ý → copy token (hiện 1 lần).
**5A.5 Tạo 5 GitHub Secrets [WEB]:** repo → Settings → Secrets and variables → Actions → *New repository secret*:
| Secret | Giá trị |
|---|---|
| `VPS_HOST` | `103.74.101.153` |
| `VPS_USER` | `root` |
| `VPS_SSH_KEY` | **toàn bộ** nội dung file private `$HOME\.ssh\qb_deploy` (`type $HOME\.ssh\qb_deploy`), gồm cả dòng `-----BEGIN/END OPENSSH PRIVATE KEY-----` |
| `GHCR_USER` | username GitHub của bạn |
| `GHCR_READ_TOKEN` | token classic ở 5A.4 |
**5A.6 Push source lên `main` [PC]** (trong thư mục đã giải nén; file `.gitignore` đã có, `.env` không bị commit):
```powershell
git init -b main
git add .
git commit -m "QuickBite v20"
git remote add origin https://github.com/<github-username>/quickbite.git
git push -u origin main
```
(Lần đầu GitHub hỏi đăng nhập qua trình duyệt/PAT — dùng PAT có quyền `repo`.)
**5A.7 Theo dõi [WEB]:** repo → tab **Actions** → workflow *CI/CD - build, push GHCR, deploy VPS*. 3 job chạy tuần tự: **build-test → docker-push → deploy**. Mất ~15–30 phút (job deploy chờ container healthy tối đa 25 phút; log in ra `chua san sang: ...` là bình thường).
**5A.8 Kiểm tra GHCR [WEB]:** GitHub → profile → **Packages**: thấy 6 image `api-gateway, identity-user-service, product-catalog-service, order-inventory-service, payment-billing-service, notification-service` (tag `latest` + mã commit).
**5A.9 Kiểm tra VPS [VPS]:** `cd /opt/quickbite && docker compose ps` (xem Bước 6).
Lỗi thường gặp: `Permission denied (publickey)` → sai `VPS_SSH_KEY`/chưa thêm public key; `unauthorized` khi pull → sai `GHCR_USER/GHCR_READ_TOKEN` hoặc IMAGE_PREFIX không chữ thường; `THIEU /opt/quickbite/.env` → chưa làm Bước 4. Workflow có **rollback** về IMAGE_TAG trước nếu stack không healthy.
Sau này cập nhật code: chỉ cần `git push origin main`.

### Cách B — Build trên máy bạn rồi đẩy thẳng qua SSH (không cần GitHub)
Cần Docker Desktop + Git Bash. **[PC]** trong thư mục source: `VPS=root@103.74.101.153 bash infra/scripts/push-images-local.sh`
**[VPS]** sửa `.env`: `IMAGE_PREFIX=quickbite`, `IMAGE_TAG=local`, rồi `cd /opt/quickbite && docker compose up -d`.

## BƯỚC 6 — Khởi động & chờ healthy [VPS]
(Cách A: CI đã làm giúp. Cách B: bạn tự chạy `docker compose up -d`.)
```bash
cd /opt/quickbite
watch -n 10 'docker compose ps; free -m | head -3'       # Ctrl+C de thoat
```
Thứ tự tự động: postgres → identity/product → kafka → order → payment → notification → gateway. Chờ **10–25 phút** đến khi tất cả `healthy`. Lỗi: `docker logs --tail 100 <ten-container>`.
**Đạt khi:** 9 container (postgres, redis, kafka, 6 service) `healthy`.

## BƯỚC 7 — Kiểm tra toàn bộ [VPS]
```bash
cd /opt/quickbite
bash infra/scripts/verify-stack.sh          # container, health, OOMKilled, RAM, port, Flyway, Kafka, Outbox
DOMAIN=khoatla-ecommerce.duckdns.org ADMIN_PASSWORD='<ADMIN_DEFAULT_PASSWORD>' bash infra/scripts/smoke-test.sh   # end-to-end qua HTTPS + kich ban tat Notification
curl -s https://khoatla-ecommerce.duckdns.org/health        # {"status":"UP"}
curl -s -o /dev/null -w "%{http_code}\n" https://khoatla-ecommerce.duckdns.org/actuator/health    # 404 (Nginx chan)
docker stats --no-stream; free -m
docker inspect -f '{{.Name}} OOM={{.State.OOMKilled}} restarts={{.RestartCount}}' $(docker ps -q)
```
**Firewall / mạng [VPS]:** `ufw status verbose` ; `ss -tlnp` — chỉ thấy `0.0.0.0:22/80/443` (sshd, nginx) và `127.0.0.1:8080` (gateway); **không** có 5432/6379/9092.
**Firewall từ máy cá nhân [PC]** (đều phải **thất bại/TcpTestSucceeded : False**):
```powershell
Test-NetConnection 103.74.101.153 -Port 5432
Test-NetConnection 103.74.101.153 -Port 6379
Test-NetConnection 103.74.101.153 -Port 9092
Test-NetConnection 103.74.101.153 -Port 8080
Test-NetConnection 103.74.101.153 -Port 443     # cai nay phai True
```
**JWT/Security [PC]:** `curl.exe -s -o NUL -w "%{http_code}" https://khoatla-ecommerce.duckdns.org/api/orders` → `401`.
**Đạt khi:** verify-stack không có dòng lỗi; smoke-test xanh; `/health` UP; port DB/Kafka/Redis đóng.

## BƯỚC 8 — Test chức năng bằng Postman từ máy cá nhân
Làm theo `docs/05-production-1round-test.md` (Postman environment: `baseUrl=https://khoatla-ecommerce.duckdns.org`, `adminPassword=<ADMIN_DEFAULT_PASSWORD>`). Email/SMS/VNPay thật: `docs/07-v19-real-email-sms-vnpay.md`.

## BƯỚC 9 — Bật Monitoring/Tracing (VPS 4GB) và Load test
Làm theo `docs/08-v20-4gb-monitoring-loadtest.md` (Phần A: monitoring 3 bậc; Phần B: load test theo năng lực VPS). Chỉ làm **sau khi Bước 7 đạt**.

---
## Xử lý sự cố nhanh [VPS]
| Triệu chứng | Cách xử lý |
|---|---|
| Container `OOMKilled=true`/restart liên tục | `docker logs`; tăng `MEM_SVC`/`MEM_ORDER` trong `.env` rồi `docker compose up -d`; tắt tracing/Promtail/Jaeger nếu đang bật |
| `unhealthy` khi mới khởi động | Bình thường vài phút; chỉ lo nếu > 30 phút |
| Gateway 502 | `curl -s 127.0.0.1:8080/actuator/health`; gateway chưa healthy |
| Login 429 | Rate limit 5 req/s/IP — chờ vài giây |
| Chứng chỉ | `certbot renew --dry-run` (timer tự gia hạn) |
| Lỗi Flyway/role DB | Do đổi mật khẩu DB sau lần chạy đầu → `docker compose down -v` rồi up lại (mất dữ liệu) |
| Hết dung lượng | `docker system prune -af` (cẩn thận), `df -h /` |
Tắt/bật Notification để demo: `docker compose stop notification-service` → đặt hàng vẫn 201 → `docker compose start notification-service`.
Backup: cron 02:30 → `/opt/quickbite/backups` (giữ 7 ngày). Khôi phục: `docker exec -i postgres pg_restore -U postgres -d <db> --clean < file.dump`.
