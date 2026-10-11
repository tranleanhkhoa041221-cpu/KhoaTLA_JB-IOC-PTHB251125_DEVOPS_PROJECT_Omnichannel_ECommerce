#!/usr/bin/env bash
# Smoke test end-to-end qua HTTPS: dang ky -> dang nhap -> tao SP + ton kho -> dat hang
#  -> Outbox -> Kafka -> Payment tao giao dich -> Notification ghi log -> tat Notification van dat duoc hang.
# Chay (tu VPS hoac may ban):  DOMAIN=ten.duckdns.org ADMIN_PASSWORD='...' bash smoke-test.sh
set -uo pipefail
: "${DOMAIN:?}"; : "${ADMIN_PASSWORD:?}"; B="https://$DOMAIN"; R=$RANDOM$RANDOM
j(){ curl -sS -m 30 -H 'Content-Type: application/json' "$@"; }
step(){ echo; echo "== $* =="; }; chk(){ [ -n "$2" ] && [ "$2" != null ] && echo "  [OK] $1: $2" || { echo "  [LOI] $1"; exit 1; }; }
step "Health (qua Nginx)";            curl -fsS -m 10 "$B/health"; echo
step "Nginx chan actuator/docs";      echo "  /actuator/health -> $(curl -s -o /dev/null -w '%{http_code}' $B/actuator/health) (mong doi 404)"
AT=$(j -X POST $B/api/users/login -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r .data.accessToken); chk "login admin" "$AT"
U="cust$R"; step "Tao khach hang $U"
j -X POST $B/api/users/register -d "{\"username\":\"$U\",\"password\":\"Password123!\",\"email\":\"$U@example.com\",\"fullName\":\"Smoke\",\"phone\":\"09$(printf %08d $((RANDOM*RANDOM%100000000)))\",\"role\":\"CUSTOMER\"}" >/dev/null
L=$(j -X POST $B/api/users/login -d "{\"username\":\"$U\",\"password\":\"Password123!\"}"); CT=$(echo "$L"|jq -r .data.accessToken); CID=$(echo "$L"|jq -r .data.id); chk "login customer" "$CT"
j -X POST $B/api/users/addresses -H "Authorization: Bearer $CT" -d '{"recipientName":"Smoke","phone":"0912345678","province":"HCM","district":"Q1","ward":"P1","addressDetail":"1 Test","isDefault":true}' >/dev/null
step "Tao Brand/Category/Product/Inventory"
BR=$(j -X POST $B/api/catalog/brands -H "Authorization: Bearer $AT" -d "{\"name\":\"Smoke $R\"}" | jq -r .data.id); chk brandId "$BR"
CA=$(j -X POST $B/api/catalog/categories -H "Authorization: Bearer $AT" -d "{\"name\":\"Smoke Cat $R\"}" | jq -r .data.id); chk categoryId "$CA"
SKU="SMK-$R"
j -X POST $B/api/catalog/products -H "Authorization: Bearer $AT" -d "{\"name\":\"Smoke $R\",\"description\":\"smoke\",\"basePrice\":100000,\"categoryId\":$CA,\"brandId\":$BR,\"status\":\"ACTIVE\",\"variants\":[{\"sku\":\"$SKU\",\"price\":100000,\"stockQuantity\":5,\"attributes\":\"{}\"}]}" | jq -r .data.id | { read x; chk productId "$x"; }
j -X POST $B/api/inventory -H "Authorization: Bearer $AT" -d "{\"sku\":\"$SKU\",\"productName\":\"Smoke $R\",\"quantity\":5}" | jq -r .data.availableQty | { read x; chk "ton kho" "$x"; }
order(){ j -X POST $B/api/orders -H "Authorization: Bearer $CT" -d "{\"idempotencyKey\":\"$(cat /proc/sys/kernel/random/uuid)\",\"customerId\":$CID,\"items\":[{\"sku\":\"$SKU\",\"quantity\":1}]}"; }
step "Dat hang #1"; O=$(order); echo "  response: $O"; OID=$(echo "$O"|jq -r .data.id); chk "orderId (khong null)" "$OID"; echo "  status=$(echo "$O"|jq -r .data.status)"
step "Cho Outbox -> Kafka -> Payment (toi da 60s)"
for i in $(seq 1 20); do PR=$(j $B/api/payments/order/$OID -H "Authorization: Bearer $CT"); P=$(echo "$PR" | jq -r .data.status); [ "$P" != null ] && [ -n "$P" ] && break; sleep 3; done; echo "  response cuoi: $PR"; chk "payment status (Kafka order.created OK)" "$P"
step "Notification da ghi log cho don hang"
for i in $(seq 1 20); do N=$(j "$B/api/notifications/logs?orderId=$OID&page=0&size=5" -H "Authorization: Bearer $AT" | jq -r '.data.totalElements // .data.content|length'); [ "${N:-0}" != 0 ] && [ "$N" != null ] && break; sleep 3; done; chk "so notification log" "$N"
step "Kich ban de bai: TAT notification-service -> van dat duoc hang"
( cd /opt/quickbite 2>/dev/null && docker compose stop notification-service ) || echo "  (chay tren VPS de tu tat; neu khong hay tu tat roi chay tiep)"
O2=$(order); echo "  response: $O2"; chk "dat hang khi Notification tat" "$(echo "$O2"|jq -r .data.id)"
( cd /opt/quickbite 2>/dev/null && docker compose start notification-service )
step "Ton kho: available + reserved phai = 5 - don da dat"; j $B/api/inventory/$SKU -H "Authorization: Bearer $AT" | jq -c '.data|{availableQty,reservedQty}'
echo; echo "=> SMOKE TEST XONG"
