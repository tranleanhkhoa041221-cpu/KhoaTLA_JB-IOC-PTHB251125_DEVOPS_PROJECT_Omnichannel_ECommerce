#!/usr/bin/env bash
# =============================================================================
# Chay 1 lan tren VPS moi (Ubuntu 22.04, root):  DOMAIN=ten.duckdns.org EMAIL=ban@mail.com bash setup-vps.sh
# Tao swap 3GB + zram, cai Docker + Nginx + Certbot, UFW (22/80/443), log rotation, cron backup.
# =============================================================================
set -euo pipefail
: "${DOMAIN:?vd DOMAIN=quickbite-demo.duckdns.org}"; : "${EMAIL:?can email cho Lets Encrypt}"
APP_DIR=/opt/quickbite

echo "== 1. Swap ${SWAP_GB:-3}GB + zram (BAT BUOC voi VPS 1GB RAM) =="
SWAP_GB="${SWAP_GB:-3}"
if ! swapon --show | grep -q '/swapfile'; then
  avail_gb=$(df -BG --output=avail / | tail -1 | tr -dc '0-9')
  [ "$avail_gb" -gt $((SWAP_GB + 8)) ] || { echo "Dia con ${avail_gb}GB, khong du cho swap ${SWAP_GB}GB + images. Dung lai."; exit 1; }
  fallocate -l "${SWAP_GB}G" /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=$((SWAP_GB*1024))
  chmod 600 /swapfile && mkswap /swapfile && swapon --priority 10 /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw,pri=10 0 0' >> /etc/fstab
fi
# zram: swap nen trong RAM (uu tien cao hon swapfile tren dia) -> nhanh hon nhieu khi RAM thieu
DEBIAN_FRONTEND=noninteractive apt-get install -y zram-tools || true
if [ -f /etc/default/zramswap ]; then
  sed -i 's/^#\?ALGO=.*/ALGO=zstd/; s/^#\?PERCENT=.*/PERCENT=50/; s/^#\?PRIORITY=.*/PRIORITY=100/' /etc/default/zramswap
  systemctl restart zramswap || true
fi
printf 'vm.swappiness=60\nvm.vfs_cache_pressure=80\nvm.overcommit_memory=1\nvm.page-cluster=0\n' > /etc/sysctl.d/99-quickbite.conf
sysctl --system >/dev/null
swapon --show; free -m

echo "== 2. Goi co ban =="
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl jq gettext-base ufw nginx certbot python3-certbot-nginx
command -v docker >/dev/null || curl -fsSL https://get.docker.com | sh
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'JSON'
{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
JSON
systemctl restart docker; systemctl enable docker

echo "== 3. Firewall: chi 22/80/443 =="
ufw default deny incoming; ufw default allow outgoing
ufw limit 22/tcp; ufw allow 80/tcp; ufw allow 443/tcp
ufw --force enable
# Cac port DB/Kafka/Redis KHONG duoc publish trong compose; Gateway bind 127.0.0.1 nen Docker khong bypass UFW.

echo "== 3b. Kiem tra DNS DuckDNS tro dung ve VPS nay (Certbot can buoc nay) =="
MYIP=$(curl -fsS -4 https://api.ipify.org || true)
DNSIP=$(getent ahostsv4 "$DOMAIN" | awk '{print $1; exit}' || true)
echo "IP VPS: ${MYIP:-?}  |  $DOMAIN -> ${DNSIP:-chua resolve}"
if [ -z "$DNSIP" ] || [ "$DNSIP" != "$MYIP" ]; then
  echo "LOI: $DOMAIN chua tro ve $MYIP. Vao https://www.duckdns.org, dat current ip = $MYIP, doi 1-2 phut roi chay lai."; exit 1
fi

echo "== 4. Nginx + HTTPS =="
mkdir -p $APP_DIR
cp "$(dirname "$0")/../nginx/quickbite-limits.conf" /etc/nginx/conf.d/quickbite-limits.conf
export DOMAIN
envsubst '${DOMAIN}' < "$(dirname "$0")/../nginx/quickbite.conf.template" > /etc/nginx/sites-available/quickbite
ln -sf /etc/nginx/sites-available/quickbite /etc/nginx/sites-enabled/quickbite
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
certbot --nginx -d "$DOMAIN" -m "$EMAIL" --agree-tos --no-eff-email --redirect -n
systemctl enable --now certbot.timer || true

echo "== 5. Cron: DuckDNS (5 phut) + backup DB (02:30 hang ngay) =="
install -m 755 "$(dirname "$0")/duckdns-update.sh" /usr/local/bin/duckdns-update.sh
install -m 755 "$(dirname "$0")/backup-db.sh" /usr/local/bin/qb-backup-db.sh
( crontab -l 2>/dev/null | grep -v -e duckdns-update -e qb-backup-db
  echo "*/5 * * * * /usr/local/bin/duckdns-update.sh >/dev/null 2>&1"
  echo "30 2 * * * /usr/local/bin/qb-backup-db.sh >> /var/log/qb-backup.log 2>&1" ) | crontab -
echo "Xong. Tiep theo: dien $APP_DIR/.env, tao /etc/quickbite/duckdns.env, roi 'docker compose up -d' (can COMPOSE_FILE trong .env, xem .env.production.example)."
