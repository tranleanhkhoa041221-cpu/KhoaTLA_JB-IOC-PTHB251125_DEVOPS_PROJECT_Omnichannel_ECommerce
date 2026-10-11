#!/usr/bin/env bash
# Cap nhat IP cho DuckDNS. File /etc/quickbite/duckdns.env (chmod 600):  DUCKDNS_SUBDOMAIN=ten  DUCKDNS_TOKEN=xxxx
set -euo pipefail
source /etc/quickbite/duckdns.env
curl -fsS "https://www.duckdns.org/update?domains=${DUCKDNS_SUBDOMAIN}&token=${DUCKDNS_TOKEN}&ip=" -o /tmp/duckdns.log
logger -t duckdns "update: $(cat /tmp/duckdns.log)"
