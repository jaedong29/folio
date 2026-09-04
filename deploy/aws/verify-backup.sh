#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "deploy/aws/.env 파일이 없습니다." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source ./.env
set +a

: "${MYSQL_DATABASE:?.env에 MYSQL_DATABASE가 필요합니다}"
if [[ ! "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "MYSQL_DATABASE는 영문, 숫자, 밑줄만 사용할 수 있습니다." >&2
  exit 1
fi

backup_file="${1:-}"
if [[ -z "$backup_file" ]]; then
  backup_file="$(find "${BACKUP_DIR:-$PWD/backups}" -maxdepth 1 -type f \
    -name "${MYSQL_DATABASE}_*.sql.gz" -print | sort | tail -n 1)"
fi

if [[ -z "$backup_file" || ! -f "$backup_file" ]]; then
  echo "검증할 .sql.gz 백업 파일을 찾지 못했습니다." >&2
  exit 1
fi

if [[ -f "${backup_file}.sha256" ]]; then
  (cd "$(dirname "$backup_file")" && sha256sum -c "$(basename "${backup_file}.sha256")")
fi
gzip -t "$backup_file"

restore_db="${MYSQL_DATABASE}_restore_check_$(date -u +%Y%m%d%H%M%S)"
cleanup() {
  docker compose exec -T -e RESTORE_DB="$restore_db" mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "DROP DATABASE IF EXISTS $RESTORE_DB"' \
    >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "임시 복구 DB 생성: $restore_db"
docker compose exec -T -e RESTORE_DB="$restore_db" mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e \
    "CREATE DATABASE $RESTORE_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"'

echo "백업본을 임시 DB에 복구하는 중..."
gzip -dc "$backup_file" | docker compose exec -T -e RESTORE_DB="$restore_db" mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$RESTORE_DB"'

count_query='SELECT CONCAT(
  "users=", (SELECT COUNT(*) FROM users), ",",
  "assets=", (SELECT COUNT(*) FROM assets), ",",
  "transactions=", (SELECT COUNT(*) FROM transactions), ",",
  "portfolio_snapshots=", (SELECT COUNT(*) FROM portfolio_snapshots)
)'

db_counts() {
  local database="$1"
  docker compose exec -T \
    -e TARGET_DB="$database" \
    -e COUNT_QUERY="$count_query" \
    mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -Nse "$COUNT_QUERY" "$TARGET_DB"' \
    2>/dev/null
}

source_counts="$(db_counts "$MYSQL_DATABASE")"
restored_counts="$(db_counts "$restore_db")"

echo "원본 DB:    $source_counts"
echo "복구 검증 DB: $restored_counts"

if [[ "$source_counts" != "$restored_counts" ]]; then
  echo "백업 복구 검증 실패: 테이블별 건수가 일치하지 않습니다." >&2
  exit 1
fi

echo "백업 무결성 및 임시 DB 복구 검증 PASS"
