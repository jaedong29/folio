#!/usr/bin/env bash
# Folio v2 계정 생명주기 검증.
# 사용법: 서버를 띄운 뒤 bash scripts/verify-account.sh

set -u
# 일부 모바일 네트워크는 IPv4 주소도 NAT64 IPv6 경로로 먼저 변환한다.
# EC2의 퍼블릭 IPv4 smoke test는 명시적으로 IPv4를 사용한다.
curl() { command curl -4 "$@"; }
B=${B:-http://localhost:8080}
SUFFIX=$(date +%s)
EMAIL="account-v2-${SUFFIX}@example.com"
OLD_PASSWORD='old-password'
NEW_PASSWORD='new-password'
PASS=0
FAIL=0

check() {
  if [ "$2" = "$3" ]; then
    PASS=$((PASS + 1))
    printf '  PASS %s (기대=%s, 실제=%s)\n' "$1" "$2" "$3"
  else
    FAIL=$((FAIL + 1))
    printf '  FAIL %s (기대=%s, 실제=%s)\n' "$1" "$2" "$3"
  fi
}

code() { printf '%s\n' "$1" | tail -1; }

HEALTH=$(curl -sS -w '\n%{http_code}' "$B/actuator/health")
check 'health endpoint' '200' "$(code "$HEALTH")"

SIGNUP=$(curl -sS -w '\n%{http_code}' -X POST "$B/api/auth/signup" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$OLD_PASSWORD\",\"nickname\":\"Account QA\"}")
check '회원가입' '201' "$(code "$SIGNUP")"

LOGIN=$(curl -sS -X POST "$B/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$OLD_PASSWORD\"}")
TOKEN=$(printf '%s' "$LOGIN" | sed -n 's/.*"accessToken":"\([^\"]*\)".*/\1/p')
if [ -n "$TOKEN" ]; then
  PASS=$((PASS + 1)); printf '  PASS 로그인 토큰 발급\n'
else
  FAIL=$((FAIL + 1)); printf '  FAIL 로그인 토큰 발급\n'
fi

PASSWORD_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' -X PATCH "$B/api/auth/password" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d "{\"currentPassword\":\"$OLD_PASSWORD\",\"newPassword\":\"$NEW_PASSWORD\"}")
check '비밀번호 변경' '204' "$PASSWORD_STATUS"

NEW_LOGIN_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$B/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$NEW_PASSWORD\"}")
check '변경한 비밀번호 로그인' '200' "$NEW_LOGIN_STATUS"

BAD_DELETE_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE "$B/api/auth/account" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d "{\"password\":\"$NEW_PASSWORD\",\"confirmation\":\"delete\"}")
check '잘못된 탈퇴 확인 문구 거부' '400' "$BAD_DELETE_STATUS"

DELETE_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE "$B/api/auth/account" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d "{\"password\":\"$NEW_PASSWORD\",\"confirmation\":\"DELETE\"}")
check '회원 탈퇴' '204' "$DELETE_STATUS"

AFTER_DELETE_LOGIN=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$B/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$NEW_PASSWORD\"}")
check '탈퇴 후 로그인 차단' '401' "$AFTER_DELETE_LOGIN"

printf 'PASS %d / FAIL %d\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
