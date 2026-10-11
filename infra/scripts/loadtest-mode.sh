#!/usr/bin/env bash
# Bat/tat che do LOAD TEST tren VPS:  bash infra/scripts/loadtest-mode.sh on|off
# on : noi gioi han rate-limit cua Nginx (50r/s/IP) va Gateway (login 5/s, order 100/s) de k6 tu 1 IP khong bi 429/503.
# off: tra ve gia tri an toan. LUON chay `off` sau khi test xong.
set -euo pipefail
cd /opt/quickbite
C="docker compose"   # file nap do COMPOSE_FILE trong .env (giu monitoring/tracing)
setenv(){ if grep -qE "^$1=" .env; then sed -i "s|^$1=.*|$1=$2|" .env; else echo "$1=$2" >> .env; fi; }
grep -qE "^COMPOSE_FILE=" .env || setenv COMPOSE_FILE docker-compose.production.yml
case "${1:-}" in
  on)
    setenv LOGIN_RATE_REPLENISH 200; setenv LOGIN_RATE_BURST 400
    setenv ORDER_RATE_REPLENISH 3000; setenv ORDER_RATE_BURST 6000
    sed -i 's/rate=[0-9]*r\/s/rate=5000r\/s/' /etc/nginx/conf.d/quickbite-limits.conf
    sed -i 's/limit_req zone=qb_api burst=[0-9]*/limit_req zone=qb_api burst=5000/; s/limit_conn qb_conn [0-9]*/limit_conn qb_conn 5000/' /etc/nginx/sites-enabled/quickbite ;;
  off)
    setenv LOGIN_RATE_REPLENISH 5; setenv LOGIN_RATE_BURST 10
    setenv ORDER_RATE_REPLENISH 100; setenv ORDER_RATE_BURST 300
    sed -i 's/rate=[0-9]*r\/s/rate=50r\/s/' /etc/nginx/conf.d/quickbite-limits.conf
    sed -i 's/limit_req zone=qb_api burst=[0-9]*/limit_req zone=qb_api burst=200/; s/limit_conn qb_conn [0-9]*/limit_conn qb_conn 50/' /etc/nginx/sites-enabled/quickbite ;;
  *) echo "dung: $0 on|off"; exit 1 ;;
esac
nginx -t && systemctl reload nginx
$C up -d api-gateway          # recreate gateway voi bien moi
echo "OK: loadtest-mode $1"; grep -E "^(LOGIN|ORDER)_RATE" .env; grep -E "rate=|burst=|qb_conn " /etc/nginx/conf.d/quickbite-limits.conf /etc/nginx/sites-enabled/quickbite
