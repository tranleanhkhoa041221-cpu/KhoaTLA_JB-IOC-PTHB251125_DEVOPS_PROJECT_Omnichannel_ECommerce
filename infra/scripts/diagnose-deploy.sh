#!/usr/bin/env bash
# CHAY TREN VPS khi deploy bao loi:  bash /opt/quickbite/infra/scripts/diagnose-deploy.sh
# In ra trang thai that cua tung container de biet service nao khong healthy / bi OOMKilled / restart.
cd /opt/quickbite || exit 1
echo "== .env (chi hien khoa quan trong, KHONG hien secret) =="; grep -E '^(DOMAIN|IMAGE_PREFIX|IMAGE_TAG|COMPOSE_FILE)=' .env || echo "THIEU cac bien tren"
echo; echo "== docker compose ps =="; docker compose ps -a
echo; echo "== health / OOM / restart tung container =="
for c in $(docker ps -a --format '{{.Names}}'); do
  docker inspect -f '{{.Name}}  health={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}  status={{.State.Status}}  OOM={{.State.OOMKilled}}  restarts={{.RestartCount}}  exit={{.State.ExitCode}}' "$c"
done
echo; echo "== Gateway noi bo =="; curl -sS --max-time 10 http://127.0.0.1:8080/actuator/health; echo
D=$(grep -E '^DOMAIN=' .env | cut -d= -f2- || true); [ -n "$D" ] && { echo "== HTTPS public =="; curl -sS --max-time 15 "https://$D/health"; echo; }
echo; echo "== RAM / swap / disk =="; free -m; df -h / | tail -1
echo; echo "== 25 dong log cuoi cua container KHONG healthy =="
for c in $(docker ps -a --format '{{.Names}}'); do
  h=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$c")
  case "$h" in healthy|running) ;; *) echo "---- $c ($h) ----"; docker logs --tail=25 "$c" 2>&1;; esac
done
