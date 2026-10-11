#!/usr/bin/env bash
# Backup tung DB bang pg_dump qua `docker exec`; giu 7 ban gan nhat (20GB o dia -> gioi han dung luong).
set -euo pipefail
DIR=/opt/quickbite/backups; mkdir -p "$DIR"; TS=$(date +%F_%H%M)
for db in identity_user_db product_catalog_db order_inventory_db payment_billing_db notification_db; do
  docker exec postgres pg_dump -U postgres -Fc "$db" > "$DIR/${db}_${TS}.dump"
done
find "$DIR" -name '*.dump' -mtime +7 -delete
df -h / | tail -1
