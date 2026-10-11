#!/usr/bin/env bash
# Kiem tra nhanh stack tren VPS: container/health/OOMKilled, RAM-swap, port lang nghe, Flyway, Kafka topic/DLT, Outbox.
# Chay: cd /opt/quickbite && bash verify-stack.sh   (hoac bash infra/scripts/verify-stack.sh)
set -uo pipefail
cd /opt/quickbite 2>/dev/null || true
ok(){ echo "  [OK]   $*"; }; bad(){ echo "  [LOI]  $*"; FAIL=1; }; FAIL=0
echo "== 1. Container / health / OOMKilled / restart =="
for c in postgres redis kafka identity-user-service product-catalog-service order-inventory-service payment-billing-service notification-service api-gateway; do
  st=$(docker inspect -f '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}no-hc{{end}}|{{.State.OOMKilled}}|{{.RestartCount}}' "$c" 2>/dev/null || echo "missing|||")
  IFS='|' read -r s h o r <<<"$st"
  if [ "$s" = running ] && { [ "$h" = healthy ] || [ "$h" = no-hc ]; } && [ "$o" = false ]; then ok "$c  $s/$h restart=$r"; else bad "$c  status=$s health=$h OOMKilled=$o restart=$r"; fi
  [ "${r:-0}" -gt 2 ] 2>/dev/null && bad "$c restart nhieu lan ($r) -> xem: docker logs --tail 80 $c"
done
echo "== 2. RAM / swap =="; free -m; swapon --show
echo "== 3. docker stats =="; docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.CPUPerc}}'
dmesg 2>/dev/null | grep -i "out of memory\|oom-kill" | tail -3 | sed 's/^/  dmesg: /'
echo "== 4. Port lang nghe tren host (chi 22/80/443 + 127.0.0.1:8080) =="
ss -tlnH | awk '{print $4}' | sort -u | sed 's/^/  /'
ss -tlnH | awk '{print $4}' | grep -E ':(5432|6379|9092|9093|8082|8083|8084|8085|8086|9090|9091)$' | grep -v '^127\.' && bad "port noi bo dang bi publish ra ngoai!" || ok "khong co port DB/Redis/Kafka/service bi publish"
echo "== 5. Flyway =="
for pair in identity_user:identity_user_db product_catalog:product_catalog_db order_inventory:order_inventory_db payment_billing:payment_billing_db notification:notification_db; do
  role=${pair%%:*}; db=${pair##*:}
  n=$(docker exec postgres psql -U "$role" -d "$db" -Atc "select count(*) from flyway_schema_history where success" 2>/dev/null)
  [ -n "$n" ] && [ "$n" -ge 1 ] && ok "$db: $n migration thanh cong" || bad "$db: khong doc duoc flyway_schema_history"
done
echo "== 6. Database-per-Service: role identity_user KHONG ket noi duoc DB cua service khac =="
docker exec postgres psql -U identity_user -d order_inventory_db -Atc "select 1" >/dev/null 2>&1 && bad "identity_user vao duoc order_inventory_db" || ok "bi chan dung nhu mong doi"
echo "== 7. Kafka topics =="
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null | sed 's/^/  /'
echo "== 8. Outbox ton dong (PENDING/FAILED) =="
for pair in order_inventory:order_inventory_db payment_billing:payment_billing_db; do
  role=${pair%%:*}; db=${pair##*:}
  docker exec postgres psql -U "$role" -d "$db" -Atc "select '  $db '||status||': '||count(*) from outbox_events group by status" 2>/dev/null
done
echo "== 9. Loi gan day trong log =="
for c in identity-user-service product-catalog-service order-inventory-service payment-billing-service notification-service api-gateway; do
  n=$(docker logs --since 15m "$c" 2>&1 | grep -cE " ERROR |Exception" || true); echo "  $c: $n dong ERROR/Exception (15 phut)"
done
echo "== 10. Monitoring / Tracing (chi kiem tra neu da bat) =="
if docker ps --format '{{.Names}}' | grep -qx prometheus; then
  up=$(docker exec prometheus wget -qO- http://localhost:9090/api/v1/targets 2>/dev/null | grep -o '"health":"up"' | wc -l)
  [ "$up" -ge 7 ] && ok "Prometheus: $up target up (mong doi >= 7: 6 service + prometheus)" || bad "Prometheus chi $up target up (mong doi >= 7)"
  docker ps --format '{{.Names}}' | grep -qx grafana && { curl -fsS --max-time 5 http://127.0.0.1:3000/api/health | grep -q '"database": *"ok"' && ok "Grafana health ok" || bad "Grafana health loi"; }
  docker ps --format '{{.Names}}' | grep -qx loki && { docker exec loki wget -qO- http://localhost:3100/ready 2>/dev/null | grep -q ready && ok "Loki ready" || bad "Loki chua ready"; }
  docker ps --format '{{.Names}}' | grep -qx jaeger && { n=$(curl -fsS --max-time 5 http://127.0.0.1:16686/api/services 2>/dev/null | grep -o '"[a-z-]*-service"\|"api-gateway"' | sort -u | wc -l); [ "$n" -ge 1 ] && ok "Jaeger thay $n service co trace (goi API qua Postman truoc)" || echo "  [CHUA] Jaeger chua co service nao — goi vai API roi chay lai (hoac chua bat tracing overlay)"; }
  for c in prometheus grafana loki promtail jaeger; do
    docker ps -a --format '{{.Names}}' | grep -qx $c || continue
    st=$(docker inspect -f '{{.State.Status}}|{{.State.OOMKilled}}|{{.RestartCount}}' $c); IFS='|' read -r s o r <<<"$st"
    [ "$s" = running ] && [ "$o" = false ] && ok "$c running restart=$r" || bad "$c status=$s OOMKilled=$o restart=$r"
  done
else
  echo "  (monitoring chua bat — bo qua)"
fi
[ "$FAIL" = 0 ] && echo "=> TAT CA OK" || echo "=> CO LOI, xem cac dong [LOI] o tren"; exit $FAIL
