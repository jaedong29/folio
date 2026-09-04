#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")"

if [[ "${CONFIRM_RESTORE:-}" != "YES" ]]; then
  echo "실제 DB를 백업 시점으로 덮어씁니다." >&2
  echo "정말 진행하려면 CONFIRM_RESTORE=YES를 명시하세요." >&2
  exit 1
fi

if [[ ! -f .env ]]; then
  echo "deploy/aws/.env 파일이 없습니다." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source ./.env
set +a

: "${MYSQL_DATABASE:?.env에 MYSQL_DATABASE가 필요합니다}"

backup_file="${1:-}"
if [[ -z "$backup_file" ]]; then
  backup_file="$(find "${BACKUP_DIR:-$PWD/backups}" -maxdepth 1 -type f \
    -name "${MYSQL_DATABASE}_*.sql.gz" -print | sort | tail -n 1)"
fi

if [[ -z "$backup_file" || ! -f "$backup_file" ]]; then
  echo "복구할 .sql.gz 백업 파일을 찾지 못했습니다." >&2
  exit 1
fi

if [[ -f "${backup_file}.sha256" ]]; then
  (cd "$(dirname "$backup_file")" && sha256sum -c "$(basename "${backup_file}.sha256")")
fi
gzip -t "$backup_file"

app_was_running=false
if docker compose ps --status running --services | grep -qx 'app'; then
  app_was_running=true
  docker compose stop app
fi

restart_app() {
  if [[ "$app_was_running" == true ]]; then
    docker compose start app >/dev/null || true
  fi
}
trap restart_app EXIT

echo "현재 DB '$MYSQL_DATABASE'에 백업을 복구합니다: $backup_file"
gzip -dc "$backup_file" | docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"'

echo "복구 완료. 앱 컨테이너를 다시 시작하고 health를 확인합니다."
docker compose start app >/dev/null
trap - EXIT
if ! curl --fail --silent --show-error http://localhost:8080/actuator/health; then
  echo "복구 후 앱 health 확인에 실패했습니다." >&2
  exit 1
fi
echo
echo "실제 DB 복구 PASS"
