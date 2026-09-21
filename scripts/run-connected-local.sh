#!/usr/bin/env bash
# Personal account connection testing with persistent local data and server keys.
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p .folio-local
chmod 700 .folio-local

for secret_file in connection-key jwt-key db-password; do
  if [ ! -s ".folio-local/$secret_file" ]; then
    if [ -e .folio-local/portfolio.mv.db ]; then
      echo "저장된 DB의 비밀 설정이 없습니다. .folio-local 백업을 복구해주세요: $secret_file" >&2
      exit 1
    fi
    openssl rand -base64 32 > ".folio-local/$secret_file"
  fi
  chmod 600 ".folio-local/$secret_file"
done
export APP_CONNECTIONS_ENCRYPTION_KEY="$(cat .folio-local/connection-key)"
export APP_JWT_SECRET="$(cat .folio-local/jwt-key)"
export FOLIO_LOCAL_DB_PASSWORD="$(cat .folio-local/db-password)"
export SPRING_PROFILES_ACTIVE=connected-local
# Never inherit a paid model enablement or alternate profile from another session.
export APP_AI_ENABLED=false
export APP_NEWS_EXTERNAL_ENABLED=false
export APP_NEWS_SUMMARY_ENABLED=false
export APP_CONNECTIONS_ENABLED=true
export APP_PRICE_EXTERNAL_ENABLED=true

./gradlew bootJar --console=plain
echo "계좌 연결 테스트: http://127.0.0.1:8081 (종료: Ctrl+C)"
echo "계정과 연결 정보는 .folio-local에 보관됩니다. 이 폴더를 삭제하지 마세요."
exec java -jar build/libs/asset-0.0.1-SNAPSHOT.jar
