#!/bin/bash
# PRD 8-3 최소 검증 시나리오 8개를 실제 HTTP 요청으로 실행한다.
#
#   사용법: 서버를 띄운 뒤   bash scripts/verify.sh
#   외부 시세 강제 실패(시나리오 8)까지 자동 확인하려면:
#     APP_PRICE_EXTERNAL_ENABLED=false 로 띄운 서버에 대해 실행하거나,
#     아래 8번 항목의 안내대로 별도 기동한다.

set -u
B=${B:-http://localhost:8080}
PASS=0; FAIL=0
NOW="2026-08-06T09:00:00"

ok()   { PASS=$((PASS+1)); printf "  \033[32mPASS\033[0m %s\n" "$1"; }
ng()   { FAIL=$((FAIL+1)); printf "  \033[31mFAIL\033[0m %s\n" "$1"; }
check(){ if [ "$2" = "$3" ]; then ok "$1 (기대=$2, 실제=$3)"; else ng "$1 (기대=$2, 실제=$3)"; fi; }
# 수치 비교 — 15.0 과 15.00000000 을 같은 값으로 본다 (DECIMAL 자릿수 표기 차이 무시)
checknum(){
  if python3 -c "
from decimal import Decimal
import sys
try:
    sys.exit(0 if Decimal(sys.argv[1]) == Decimal(sys.argv[2]) else 1)
except Exception:
    sys.exit(1)
" "$2" "$3"; then ok "$1 (기대=$2, 실제=$3)"; else ng "$1 (기대=$2, 실제=$3)"; fi
}

req() { # method path token body → "HTTPCODE|BODY"
  local m=$1 p=$2 t=$3 b=${4:-}
  if [ -n "$b" ]; then
    curl -s -w "\n%{http_code}" -X "$m" "$B$p" -H "Authorization: Bearer $t" \
      -H 'Content-Type: application/json' -d "$b"
  else
    curl -s -w "\n%{http_code}" -X "$m" "$B$p" -H "Authorization: Bearer $t"
  fi
}
code() { echo "$1" | tail -1; }
body() { echo "$1" | sed '$d'; }
# field <JSON본문> <키1> [키2 ...] — 중첩 키를 순서대로 따라가 값을 출력한다.
field(){
  local json=$1; shift
  echo "$json" | python3 -c '
import json, sys
try:
    node = json.load(sys.stdin)
    for key in sys.argv[1:]:
        node = node[int(key)] if key.lstrip("-").isdigit() else node[key]
    print(node)
except Exception:
    print("")
' "$@"
}

echo "════════════════════════════════════════════════"
echo " PRD 8-3 검증 시나리오"
echo "════════════════════════════════════════════════"

# 준비: 두 명의 사용자
SUFFIX=$(date +%s)
curl -s -o /dev/null -X POST $B/api/auth/signup -H 'Content-Type: application/json' \
  -d "{\"email\":\"a$SUFFIX@example.com\",\"password\":\"1234abcd\",\"nickname\":\"A\"}"
curl -s -o /dev/null -X POST $B/api/auth/signup -H 'Content-Type: application/json' \
  -d "{\"email\":\"b$SUFFIX@example.com\",\"password\":\"1234abcd\",\"nickname\":\"B\"}"
TA=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d "{\"email\":\"a$SUFFIX@example.com\",\"password\":\"1234abcd\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
TB=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d "{\"email\":\"b$SUFFIX@example.com\",\"password\":\"1234abcd\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# A 의 자산 (STOCK — 외부 시세가 죽어 있어도 등록되도록 국내 심볼 사용)
R=$(req POST /api/assets "$TA" "{\"type\":\"STOCK\",\"symbol\":\"TEST$SUFFIX.KS\",\"name\":\"검증종목\",\"currency\":\"KRW\"}")
AID=$(field "$(body "$R")" id)

echo
echo "[1] A 계정 토큰으로 B 의 Asset 조회 → 404 ASSET_NOT_FOUND"
R=$(req GET "/api/assets/$AID" "$TB")
check "타인 자산 조회" "404" "$(code "$R")"
check "  에러 코드" "ASSET_NOT_FOUND" "$(field "$(body "$R")" code)"
R=$(req PATCH "/api/assets/$AID" "$TB" '{"name":"탈취"}');    check "  타인 자산 수정" "404" "$(code "$R")"
R=$(req DELETE "/api/assets/$AID" "$TB");                      check "  타인 자산 삭제" "404" "$(code "$R")"
R=$(req GET "/api/assets/$AID/transactions" "$TB");            check "  타인 거래내역 조회" "404" "$(code "$R")"
R=$(req POST "/api/assets/$AID/transactions/buy" "$TB" "{\"quantity\":1,\"price\":1,\"exchangeRate\":1,\"tradedAt\":\"$NOW\"}")
check "  타인 자산에 거래 생성" "404" "$(code "$R")"
R=$(curl -s -o /dev/null -w "%{http_code}" $B/api/assets/$AID); check "  토큰 없이 접근" "401" "$R"

echo
echo "[2] 10개 @1,500 매수 → 5개 @1,600 매수 → avgPrice = 1533.33…"
req POST "/api/assets/$AID/transactions/buy" "$TA" "{\"quantity\":10,\"price\":1500,\"exchangeRate\":1,\"tradedAt\":\"$NOW\"}" >/dev/null
R=$(req POST "/api/assets/$AID/transactions/buy" "$TA" "{\"quantity\":5,\"price\":1600,\"exchangeRate\":1,\"tradedAt\":\"$NOW\"}")
checknum "평균 매입 단가" "1533.33333333" "$(field "$(body "$R")" asset avgPrice)"
checknum "보유 수량" "15.00000000" "$(field "$(body "$R")" asset quantity)"

echo
echo "[3] 보유(15) 초과 매도(20) → 400 INSUFFICIENT_ASSET_QUANTITY, 수량 변화 없음"
R=$(req POST "/api/assets/$AID/transactions/sell" "$TA" "{\"quantity\":20,\"price\":2000,\"exchangeRate\":1,\"tradedAt\":\"$NOW\"}")
check "초과 매도 거부" "400" "$(code "$R")"
check "  에러 코드" "INSUFFICIENT_ASSET_QUANTITY" "$(field "$(body "$R")" code)"
R=$(req GET "/api/assets/$AID" "$TA")
checknum "  수량 불변" "15.00000000" "$(field "$(body "$R")" quantity)"

echo
echo "[4] 정상 매도 5개 @2,000 → avgPrice 불변 / realizedPnl 증가 / quantity 감소"
R=$(req POST "/api/assets/$AID/transactions/sell" "$TA" "{\"quantity\":5,\"price\":2000,\"exchangeRate\":1,\"tradedAt\":\"$NOW\"}")
checknum "avgPrice 불변" "1533.33333333" "$(field "$(body "$R")" asset avgPrice)"
checknum "realizedPnl (2000-1533.33333333)x5" "2333.33333335" "$(field "$(body "$R")" asset realizedPnl)"
checknum "quantity 감소" "10.00000000" "$(field "$(body "$R")" asset quantity)"

echo
echo "[5] 전량 매도 후 재매수 → avgPrice = 재매수 단가, realizedPnl 유지"
R=$(req POST "/api/assets/$AID/transactions/sell" "$TA" "{\"quantity\":10,\"price\":3000,\"exchangeRate\":1,\"tradedAt\":\"$NOW\"}")
checknum "전량 매도 후 수량" "0.00000000" "$(field "$(body "$R")" asset quantity)"
checknum "  avgPrice 초기화 안 됨" "1533.33333333" "$(field "$(body "$R")" asset avgPrice)"
checknum "  realizedPnl 누적" "17000.00000005" "$(field "$(body "$R")" asset realizedPnl)"
R=$(req POST "/api/assets/$AID/transactions/buy" "$TA" "{\"quantity\":3,\"price\":5000,\"exchangeRate\":1,\"tradedAt\":\"$NOW\"}")
checknum "재매수 후 avgPrice = 재매수 단가" "5000.00000000" "$(field "$(body "$R")" asset avgPrice)"
checknum "  realizedPnl 유지" "17000.00000005" "$(field "$(body "$R")" asset realizedPnl)"

echo
echo "[6] 같은 symbol 중복 등록 → 409 DUPLICATE_ASSET"
R=$(req POST /api/assets "$TA" "{\"type\":\"STOCK\",\"symbol\":\"test$SUFFIX.ks\",\"name\":\"소문자 중복\",\"currency\":\"KRW\"}")
check "중복 등록 (소문자 입력도 정규화되어 차단)" "409" "$(code "$R")"
check "  에러 코드" "DUPLICATE_ASSET" "$(field "$(body "$R")" code)"

echo
echo "[7] 존재하지 않는 심볼 등록 → 400 INVALID_SYMBOL"
R=$(req POST /api/assets "$TA" '{"type":"CRYPTO","symbol":"NOTACOIN","name":"없는코인","currency":"USDT"}')
if [ "$(code "$R")" = "400" ]; then
  check "없는 심볼 거부" "INVALID_SYMBOL" "$(field "$(body "$R")" code)"
elif [ "$(code "$R")" = "201" ]; then
  ng "없는 심볼 거부 — 외부 API 자체가 응답하지 않아 등록이 허용됨(PRD 4-2의 의도된 동작). 네트워크 확인 필요"
else
  ng "없는 심볼 거부 (예상 밖 응답: $(code "$R"))"
fi

echo
echo "[8] 외부 시세 API 강제 실패 → 화면 정상, priceStale: true"
R=$(req PATCH "/api/assets/$AID/price" "$TA" '{"currentPrice":6000}')
check "수동 시세 입력(최후 폴백) 동작" "200" "$(code "$R")"
R=$(req GET "/api/portfolio" "$TA");  check "Portfolio 응답" "200" "$(code "$R")"
R=$(req GET "/api/dashboard" "$TA");  check "Dashboard 응답" "200" "$(code "$R")"
STALE=$(field "$(body "$R")" priceStale)
if [ "${EXPECT_STALE:-0}" = "1" ]; then
  # 외부 시세를 강제로 끈 서버(APP_PRICE_EXTERNAL_ENABLED=false)에 대해 실행한 경우
  check "priceStale 플래그" "True" "$STALE"
else
  echo "     현재 priceStale=$STALE (외부 정상 모드). 강제 실패 검증은 아래 명령으로:"
  echo "       APP_PRICE_EXTERNAL_ENABLED=false ./gradlew bootRun   그리고   EXPECT_STALE=1 bash scripts/verify.sh"
fi

echo
echo "════════════════════════════════════════════════"
printf " PASS %d / FAIL %d\n" "$PASS" "$FAIL"
echo "════════════════════════════════════════════════"
[ "$FAIL" -eq 0 ]
