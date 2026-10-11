#!/usr/bin/env bash
# PHUONG AN B (khong can GitHub/GHCR): build image tren MAY BAN roi day thang len VPS qua SSH.
# Chay tu thu muc goc project (Git Bash / WSL / Linux / macOS, can Docker Desktop):
#   VPS=root@103.74.101.153 bash infra/scripts/push-images-local.sh
# Tren VPS dat trong /opt/quickbite/.env:  IMAGE_PREFIX=quickbite   IMAGE_TAG=local
set -euo pipefail
: "${VPS:?vd VPS=root@103.74.101.153}"
for s in api-gateway identity-user-service product-catalog-service order-inventory-service payment-billing-service notification-service; do
  echo "== build $s =="; docker build --platform linux/amd64 -t quickbite/$s:local "./$s"
  echo "== gui $s len VPS =="; docker save quickbite/$s:local | gzip | ssh "$VPS" 'gunzip | docker load'
done
echo "Xong. Tren VPS: cd /opt/quickbite && docker compose up -d"
