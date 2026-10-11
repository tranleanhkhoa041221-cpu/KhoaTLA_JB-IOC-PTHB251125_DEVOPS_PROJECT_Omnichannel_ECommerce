# 2. CI/CD — GitHub Actions → GHCR → VPS

File đã sửa **FULL**: `.github/workflows/deploy.yml`. Bản v15 có 4 lỗi:
1. Push image bằng `GHCR_READ_TOKEN` (chỉ có `read:packages`) → **push sẽ bị 403**. Nay dùng `GITHUB_TOKEN` + `permissions: packages: write`.
2. `IMAGE_PREFIX` dùng `github.repository_owner` **chưa lowercase** → GHCR từ chối nếu username có chữ hoa. Nay ép lowercase.
3. Deploy không copy `docker-compose.production.yml`/`infra` lên VPS → VPS chạy compose cũ. Nay có bước `scp-action` (không đụng `.env`).
4. Chỉ đợi `api-gateway`, không kiểm HTTPS công khai, không rollback. Nay đợi cả 9 container + OOMKilled + `https://$DOMAIN/health`, và rollback về tag cũ nếu hỏng.
(`-x test` vẫn giữ để pipeline không phụ thuộc DB/Kafka; thêm bước `test` không chặn deploy.)

## Bảng đối chiếu (đã kiểm tra tĩnh)
| Thành phần | Giá trị | Khớp với |
|---|---|---|
| Matrix Actions | 6 service: api-gateway, identity-user-service, product-catalog-service, order-inventory-service, payment-billing-service, notification-service | tên thư mục có `Dockerfile`; tên service trong compose |
| Image | `ghcr.io/<owner lowercase>/<service>:<sha>` và `:latest` | compose: `${IMAGE_PREFIX}/<service>:${IMAGE_TAG}` |
| `.env` trên VPS | `IMAGE_PREFIX=ghcr.io/<owner>`, `IMAGE_TAG=<sha>` | workflow tự ghi 2 biến này |
| Dockerfile | multi-stage, context = thư mục service | `docker/build-push-action` `context: <service>` |
| VPS | pull bằng `GHCR_USER` + `GHCR_READ_TOKEN` | secrets bên dưới |

## Tạo từng secret (GitHub → repo → Settings → Secrets and variables → Actions → New repository secret)
### Bước 0. Đưa code lên GitHub
Tạo repo (private OK), `git init && git add . && git commit -m init && git branch -M main && git remote add origin https://github.com/<user>/<repo>.git && git push -u origin main`. Kiểm tra `.env`, `.env.*` **không** nằm trong commit (chỉ `.env.production.example`).

### 1) `VPS_HOST`
Giá trị: IP VPS (`103.74.101.153`) hoặc `khoatla-ecommerce.duckdns.org`.
### 2) `VPS_USER`
Giá trị: `root`.
### 3) `VPS_SSH_KEY` — tạo SSH key chuyên cho CI
Trên máy cá nhân (PowerShell):
```powershell
ssh-keygen -t ed25519 -C "github-actions-quickbite" -f $HOME\.ssh\qb_deploy -N '""'
type $HOME\.ssh\qb_deploy.pub | ssh root@103.74.101.153 "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
ssh -i $HOME\.ssh\qb_deploy root@103.74.101.153 "echo SSH_KEY_OK"      # phai in SSH_KEY_OK, khong hoi mat khau
Get-Content $HOME\.ssh\qb_deploy -Raw | Set-Clipboard                   # copy PRIVATE key
```
Dán **toàn bộ** nội dung (gồm dòng `-----BEGIN OPENSSH PRIVATE KEY-----` … `-----END OPENSSH PRIVATE KEY-----`, có xuống dòng cuối) vào ô Secret `VPS_SSH_KEY`. Không bao giờ commit file này.
### 4) `GHCR_USER`
Giá trị: username GitHub, **viết thường**.
### 5) `GHCR_READ_TOKEN` — token cho VPS pull image
GitHub → avatar → Settings → Developer settings → Personal access tokens → **Tokens (classic)** → Generate new token (classic) → Note `quickbite-vps-pull`, Expiration theo ý bạn (ghi nhớ ngày hết hạn), tick **chỉ `read:packages`** → Generate → copy `ghp_...` (chỉ hiện 1 lần) → dán vào Secret.
(Tạo xong cả 5 secret: trang Actions secrets phải liệt kê đúng 5 tên.)

## Chuẩn bị VPS TRƯỚC lần deploy đầu
```bash
mkdir -p /opt/quickbite && cd /opt/quickbite
cp /opt/quickbite-src/.env.production.example .env && chmod 600 .env   # điền secret (xem DEPLOY-GUIDE.md bước 4)
```
Workflow không tự tạo `.env` (sẽ dừng với thông báo nếu thiếu).

## Chạy & kiểm tra
1. Push lên `main` (hoặc tab Actions → workflow → Run workflow).
2. Cả 3 job xanh: `build-test` ×6, `docker-push` ×6, `deploy`.
3. GitHub → profile → Packages: thấy 6 package. Nếu VPS báo `denied` khi pull: Package → Package settings → Manage Actions access / đảm bảo tài khoản của PAT có quyền đọc.
4. Trên VPS: `cd /opt/quickbite && grep -E '^IMAGE_' .env && docker compose -f docker-compose.production.yml ps` — tag = commit SHA mới nhất.
5. Từ máy cá nhân: `curl.exe -s https://khoatla-ecommerce.duckdns.org/health`.

Trạng thái: file workflow **đã sửa**, nhưng **CHƯA CHẠY THỰC TẾ** (cần repo + secrets của bạn). `docker compose config` cũng chưa chạy được trong sandbox.
