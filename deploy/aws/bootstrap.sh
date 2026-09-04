#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "deploy/aws/.env 파일이 없습니다. .env.example을 복사해 실제 값을 입력하세요." >&2
  exit 1
fi

docker compose up -d --build

APP_PORT="$(docker compose port app 8080 | sed -n 's/.*:\([0-9][0-9]*\)$/\1/p')"
APP_PORT="${APP_PORT:-8080}"

for attempt in {1..30}; do
  if curl --fail --silent "http://localhost:${APP_PORT}/actuator/health" >/dev/null; then
    echo "Folio is healthy: http://localhost:${APP_PORT}"
    exit 0
  fi
  sleep 2
done

echo "Folio health check failed. 최근 로그:" >&2
docker compose logs --tail=100 app >&2
exit 1
