#!/bin/bash
# =============================================================================
# Database-per-Service THAT SU: moi service co 1 ROLE rieng + 1 DATABASE rieng.
# Role chi la OWNER cua DB cua minh, bi REVOKE quyen CONNECT cua PUBLIC => service A
# khong the ket noi vao DB cua service B. Chay 1 lan khi volume postgres con trong.
# Mat khau doc tu bien DB_PASS_<ROLE> (xem .env): DB_PASS_IDENTITY_USER, ...
# =============================================================================
set -euo pipefail

create_role_and_db() {
  local db="$1"
  local role="${db%_db}"
  local var="DB_PASS_$(echo "$role" | tr 'a-z' 'A-Z')"
  local pass="${!var:?Thieu bien moi truong $var}"
  echo "Creating role '$role' + database '$db'"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE ROLE ${role} LOGIN PASSWORD '${pass}';
    CREATE DATABASE ${db} OWNER ${role};
    REVOKE ALL ON DATABASE ${db} FROM PUBLIC;
    GRANT CONNECT ON DATABASE ${db} TO ${role};
EOSQL
}

for db in $(echo "${POSTGRES_MULTIPLE_DATABASES}" | tr ',' ' '); do
  create_role_and_db "$db"
done
