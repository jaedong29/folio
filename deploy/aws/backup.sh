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

BACKUP_DIR="${BACKUP_DIR:-$PWD/backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="$BACKUP_DIR/${MYSQL_DATABASE}_${timestamp}.sql.gz"
temporary_file="${backup_file}.tmp"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
trap 'rm -f "$temporary_file"' EXIT

if ! docker compose ps --status running --services | grep -qx 'mysql'; then
  echo "mysql 컨테이너가 실행 중이 아닙니다." >&2
  docker compose ps >&2
  exit 1
fi

echo "MySQL 백업 생성 중: $backup_file"
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --hex-blob \
    --no-tablespaces \
    "$MYSQL_DATABASE"' \
  | gzip -9 > "$temporary_file"

test -s "$temporary_file"
gzip -t "$temporary_file"
mv "$temporary_file" "$backup_file"
chmod 600 "$backup_file"
sha256sum "$backup_file" > "${backup_file}.sha256"
chmod 600 "${backup_file}.sha256"

if [[ "$KEEP_DAYS" =~ ^[0-9]+$ ]]; then
  find "$BACKUP_DIR" -maxdepth 1 -type f \
    -name "${MYSQL_DATABASE}_*.sql.gz" \
    -mtime "+$KEEP_DAYS" -delete
  find "$BACKUP_DIR" -maxdepth 1 -type f \
    -name "${MYSQL_DATABASE}_*.sql.gz.sha256" \
    -mtime "+$KEEP_DAYS" -delete
else
  echo "KEEP_DAYS가 숫자가 아니므로 오래된 백업을 삭제하지 않습니다: $KEEP_DAYS" >&2
fi

echo "백업 완료: $backup_file"
echo "무결성 파일: ${backup_file}.sha256"
