#!/bin/bash
# PRD 8-3 최소 검증 시나리오 8개를 실제 HTTP 요청으로 실행한다.
#
#   사용법: 서버를 띄운 뒤   bash scripts/verify.sh
#   외부 시세 강제 실패(시나리오 8)까지 자동 확인하려면:
#     APP_PRICE_EXTERNAL_ENABLED=false 로 띄운 서버에 대해 실행하거나,
#     아래 8번 항목의 안내대로 별도 기동한다.

set -u
# 일부 모바일 네트워크는 IPv4 주소도 NAT64 IPv6 경로로 먼저 변환한다.
# EC2의 퍼블릭 IPv4 smoke test는 명시적으로 IPv4를 사용한다.
curl() { command curl -4 "$@"; }
B=${B:-http://localhost:8080}
PASS=0; FAIL=0
NOW="2026-08-06T09:00:00"
TA=""
TB=""

cleanup_test_accounts() {
  local token
  for token in "$TA" "$TB"; do
    if [ -n "$token" ]; then
      curl -sS -o /dev/null -X DELETE "$B/api/auth/account" \
        -H 'Content-Type: application/json' \
        -H "Authorization: Bearer $token" \
        -d '{"password":"1234abcd","confirmation":"DELETE"}' || true
    fi
  done
}
trap cleanup_test_accounts EXIT

ok()   { PASS=$((PASS+1)); printf "  \033[32mPASS\033[0m %s\n" "$1"; }
ng()   { FAIL=$((FAIL+1)); printf "  \033[31mFAIL\033[0m %s\n" "$1"; }
skip() { printf "  \033[33mSKIP\033[0m %s\n" "$1"; }
check(){ if [ "$2" = "$3" ]; then ok "$1 (기대=$2, 실제=$3)"; else ng "$1 (기대=$2, 실제=$3)"; fi; }
checkcontains(){
  case "$2" in
    *"$3"*) ok "$1 (포함=$3)" ;;
    *) ng "$1 (포함 기대=$3, 실제=$2)" ;;
  esac
}
jsoncount(){
  echo "$1" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))'
}
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
  local idem_header=""
  if [[ "$p" == */transactions/buy || "$p" == */transactions/sell \
        || "$p" == */transactions/deposit || "$p" == */transactions/withdraw ]]; then
    idem_header="Idempotency-Key: $(python3 -c 'import uuid; print(uuid.uuid4())')"
  fi
  if [ -n "$b" ]; then
    if [ -n "$idem_header" ]; then
      curl -s -w "\n%{http_code}" -X "$m" "$B$p" -H "Authorization: Bearer $t" \
        -H 'Content-Type: application/json' -H "$idem_header" -d "$b"
    else
      curl -s -w "\n%{http_code}" -X "$m" "$B$p" -H "Authorization: Bearer $t" \
        -H 'Content-Type: application/json' -d "$b"
    fi
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
AVAILABLE=$(curl -s -w "\n%{http_code}" "$B/api/auth/email-availability?email=new$SUFFIX%40example.com")
check "회원가입 전 이메일 중복확인" "200" "$(code "$AVAILABLE")"
check "  미가입 이메일 사용 가능" "True" "$(field "$(body "$AVAILABLE")" available)"
STATIC_CATALOG=$(curl -s -o /dev/null -w "%{http_code}" "$B/data/krx-listed-securities.json")
check "국내 종목 카탈로그 공개 제공" "200" "$STATIC_CATALOG"
curl -s -o /dev/null -X POST $B/api/auth/signup -H 'Content-Type: application/json' \
  -d "{\"email\":\"a$SUFFIX@example.com\",\"password\":\"1234abcd\",\"nickname\":\"A\"}"
curl -s -o /dev/null -X POST $B/api/auth/signup -H 'Content-Type: application/json' \
  -d "{\"email\":\"b$SUFFIX@example.com\",\"password\":\"1234abcd\",\"nickname\":\"B\"}"
USED=$(curl -s -w "\n%{http_code}" "$B/api/auth/email-availability?email=a$SUFFIX%40example.com")
check "가입 후 이메일 중복확인" "False" "$(field "$(body "$USED")" available)"
TA=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d "{\"email\":\"a$SUFFIX@example.com\",\"password\":\"1234abcd\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
TB=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' -d "{\"email\":\"b$SUFFIX@example.com\",\"password\":\"1234abcd\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# 첫 조회에서 매매 정산용 KRW/USD/USDT 대기자금이 0으로 자동 준비된다.
ASSETS_A=$(body "$(req GET /api/assets "$TA")")
ASSETS_B=$(body "$(req GET /api/assets "$TB")")
cash_id(){
  echo "$1" | python3 -c '
import json, sys
symbol = sys.argv[1]
assets = json.load(sys.stdin)
print(next(a["id"] for a in assets if a["type"] == "CASH" and a["symbol"] == symbol))
' "$2"
}
USDT_A=$(cash_id "$ASSETS_A" USDT)
USDT_B=$(cash_id "$ASSETS_B" USDT)
USD_A=$(cash_id "$ASSETS_A" USD)
DEFAULT_SYMBOLS=$(echo "$ASSETS_A" | python3 -c 'import json,sys; print(",".join(sorted(a["symbol"] for a in json.load(sys.stdin) if a["type"] == "CASH")))')
DEFAULT_ZERO=$(echo "$ASSETS_A" | python3 -c 'import json,sys; print(str(all(float(a["quantity"]) == 0 for a in json.load(sys.stdin) if a["type"] == "CASH")).lower())')

echo
echo "[0] 첫 진입 → KRW/USD/USDT 기본 대기자금이 0으로 자동 생성"
check "기본 대기자금 통화" "KRW,USD,USDT" "$DEFAULT_SYMBOLS"
check "기본 대기자금 초기 잔액" "true" "$DEFAULT_ZERO"

# 이후 매수 시 정산할 수 있도록 검증용 USDT를 충전한다.
req POST "/api/assets/$USDT_A/transactions/deposit" "$TA" \
  "{\"quantity\":1000000,\"exchangeRate\":1,\"exchangeRateMode\":\"MANUAL\",\"tradedAt\":\"$NOW\"}" >/dev/null

# A 의 검증용 자산.
# 실존 심볼(BTC)을 쓴다. 존재하지 않는 심볼을 픽스처로 쓰면, 외부 API 가 살아 있을 때
# 등록 자체가 INVALID_SYMBOL 로 거부되어 이후 시나리오가 전부 무너진다.
# 외부 API 가 죽어 있을 때는 등록이 허용되므로(PRD 4-2), 어느 쪽이든 이 픽스처는 만들어진다.
R=$(req POST /api/assets "$TA" '{"type":"CRYPTO","symbol":"BTC","name":"검증용","currency":"USDT"}')
AID=$(field "$(body "$R")" id)

echo
echo "[0-A] 환율 출처 계약·JSON 오타·정산 오류를 각각 식별"
R=$(req POST /api/assets "$TA" '{"type":"BANK","symbol":"KRW","name":"오타 검증","currency":"KRW","initialQuantity":0.5}')
check "알 수 없는 JSON 필드 거부" "400" "$(code "$R")"
check "  에러 코드" "INVALID_INPUT" "$(field "$(body "$R")" code)"
checkcontains "  오타 필드명 안내" "$(field "$(body "$R")" message)" "initialQuantity"

R=$(req POST "/api/assets/$AID/transactions/buy" "$TA" "{\"quantity\":1,\"price\":1,\"exchangeRate\":1,\"exchangeRateMode\":\"AUTO\",\"settlementAssetId\":$USDT_A,\"tradedAt\":\"$NOW\"}")
check "과거 AUTO 거래 거부" "400" "$(code "$R")"
checkcontains "  당시 환율 수동 입력 안내" "$(field "$(body "$R")" message)" "과거 외화 거래"

R=$(req POST "/api/assets/$AID/transactions/buy" "$TA" "{\"quantity\":1,\"price\":1,\"exchangeRate\":1,\"exchangeRateMode\":\"MANUAL\",\"settlementAssetId\":$USD_A,\"tradedAt\":\"$NOW\"}")
check "정산 통화 불일치" "400" "$(code "$R")"
check "  전용 에러 코드" "SETTLEMENT_CURRENCY_MISMATCH" "$(field "$(body "$R")" code)"

R=$(req POST "/api/assets/$AID/transactions/buy" "$TA" "{\"quantity\":2000000,\"price\":1,\"exchangeRate\":1,\"exchangeRateMode\":\"MANUAL\",\"settlementAssetId\":$USDT_A,\"tradedAt\":\"$NOW\"}")
check "정산 대기자금 부족" "400" "$(code "$R")"
check "  전용 에러 코드" "INSUFFICIENT_SETTLEMENT_FUNDS" "$(field "$(body "$R")" code)"
checkcontains "  부족한 자산명 안내" "$(field "$(body "$R")" message)" "USDT"

echo
echo "[0-B] 최초 보유 수량 오입력 정정 → 매도·정산 없이 15.7 ZEC를 14.7로 변경"
R=$(req POST /api/assets "$TA" '{"type":"CRYPTO","symbol":"ZEC","name":"수량정정 검증","currency":"USDT","quantity":15.7,"averagePrice":370.4,"averageExchangeRate":1380}')
ZEC_ID=$(field "$(body "$R")" id)
USDT_BEFORE=$(field "$(body "$(req GET "/api/assets/$USDT_A" "$TA")")" quantity)
R=$(req PATCH "/api/assets/$ZEC_ID/quantity" "$TA" '{"quantity":14.7}')
check "보유 수량 정정 API" "200" "$(code "$R")"
checknum "  정정 후 수량" "14.7" "$(field "$(body "$R")" quantity)"
checknum "  기존 평단 유지" "370.4" "$(field "$(body "$R")" avgPriceOriginal)"
checknum "  실현손익 미발생" "0" "$(field "$(body "$R")" realizedPnl)"
HISTORY_AFTER=$(body "$(req GET "/api/assets/$ZEC_ID/transactions" "$TA")")
check "  거래 미생성" "0" "$(jsoncount "$HISTORY_AFTER")"
USDT_AFTER=$(field "$(body "$(req GET "/api/assets/$USDT_A" "$TA")")" quantity)
checknum "  정산 대기자금 불변" "$USDT_BEFORE" "$USDT_AFTER"

echo
echo "[1] A 계정 토큰으로 B 의 Asset 조회 → 404 ASSET_NOT_FOUND"
R=$(req GET "/api/assets/$AID" "$TB")
check "타인 자산 조회" "404" "$(code "$R")"
check "  에러 코드" "ASSET_NOT_FOUND" "$(field "$(body "$R")" code)"
R=$(req PATCH "/api/assets/$AID" "$TB" '{"name":"탈취"}');    check "  타인 자산 수정" "404" "$(code "$R")"
R=$(req DELETE "/api/assets/$AID" "$TB");                      check "  타인 자산 삭제" "404" "$(code "$R")"
R=$(req GET "/api/assets/$AID/transactions" "$TB");            check "  타인 거래내역 조회" "404" "$(code "$R")"
R=$(req POST "/api/assets/$AID/transactions/buy" "$TB" "{\"quantity\":1,\"price\":1,\"exchangeRate\":1,\"exchangeRateMode\":\"MANUAL\",\"settlementAssetId\":$USDT_B,\"tradedAt\":\"$NOW\"}")
check "  타인 자산에 거래 생성" "404" "$(code "$R")"
R=$(curl -s -o /dev/null -w "%{http_code}" $B/api/assets/$AID); check "  토큰 없이 접근" "401" "$R"

echo
echo "[2] 10개 @1,500 매수 → 5개 @1,600 매수 → avgPrice = 1533.33…"
req POST "/api/assets/$AID/transactions/buy" "$TA" "{\"quantity\":10,\"price\":1500,\"exchangeRate\":1,\"exchangeRateMode\":\"MANUAL\",\"settlementAssetId\":$USDT_A,\"tradedAt\":\"$NOW\"}" >/dev/null
R=$(req POST "/api/assets/$AID/transactions/buy" "$TA" "{\"quantity\":5,\"price\":1600,\"exchangeRate\":1,\"exchangeRateMode\":\"MANUAL\",\"settlementAssetId\":$USDT_A,\"tradedAt\":\"$NOW\"}")
checknum "평균 매입 단가" "1533.33333333" "$(field "$(body "$R")" asset avgPrice)"
checknum "보유 수량" "15.00000000" "$(field "$(body "$R")" asset quantity)"

echo
echo "[3] 보유(15) 초과 매도(20) → 400 INSUFFICIENT_ASSET_QUANTITY, 수량 변화 없음"
R=$(req POST "/api/assets/$AID/transactions/sell" "$TA" "{\"quantity\":20,\"price\":2000,\"exchangeRate\":1,\"exchangeRateMode\":\"MANUAL\",\"settlementAssetId\":$USDT_A,\"tradedAt\":\"$NOW\"}")
check "초과 매도 거부" "400" "$(code "$R")"
check "  에러 코드" "INSUFFICIENT_ASSET_QUANTITY" "$(field "$(body "$R")" code)"
checkcontains "  부족한 종목명 안내" "$(field "$(body "$R")" message)" "검증용(BTC)"
R=$(req GET "/api/assets/$AID" "$TA")
checknum "  수량 불변" "15.00000000" "$(field "$(body "$R")" quantity)"

echo
echo "[4] 정상 매도 5개 @2,000 → avgPrice 불변 / realizedPnl 증가 / quantity 감소"
R=$(req POST "/api/assets/$AID/transactions/sell" "$TA" "{\"quantity\":5,\"price\":2000,\"exchangeRate\":1,\"exchangeRateMode\":\"MANUAL\",\"settlementAssetId\":$USDT_A,\"tradedAt\":\"$NOW\"}")
checknum "avgPrice 불변" "1533.33333333" "$(field "$(body "$R")" asset avgPrice)"
checknum "realizedPnl (2000-1533.33333333)x5" "2333.33333335" "$(field "$(body "$R")" asset realizedPnl)"
checknum "quantity 감소" "10.00000000" "$(field "$(body "$R")" asset quantity)"

echo
echo "[5] 전량 매도 후 재매수 → avgPrice = 재매수 단가, realizedPnl 유지"
R=$(req POST "/api/assets/$AID/transactions/sell" "$TA" "{\"quantity\":10,\"price\":3000,\"exchangeRate\":1,\"exchangeRateMode\":\"MANUAL\",\"settlementAssetId\":$USDT_A,\"tradedAt\":\"$NOW\"}")
checknum "전량 매도 후 수량" "0.00000000" "$(field "$(body "$R")" asset quantity)"
checknum "  avgPrice 초기화 안 됨" "1533.33333333" "$(field "$(body "$R")" asset avgPrice)"
checknum "  realizedPnl 누적" "17000.00000005" "$(field "$(body "$R")" asset realizedPnl)"
R=$(req POST "/api/assets/$AID/transactions/buy" "$TA" "{\"quantity\":3,\"price\":5000,\"exchangeRate\":1,\"exchangeRateMode\":\"MANUAL\",\"settlementAssetId\":$USDT_A,\"tradedAt\":\"$NOW\"}")
checknum "재매수 후 avgPrice = 재매수 단가" "5000.00000000" "$(field "$(body "$R")" asset avgPrice)"
checknum "  realizedPnl 유지" "17000.00000005" "$(field "$(body "$R")" asset realizedPnl)"

echo
echo "[6] 같은 symbol 중복 등록 → 409 DUPLICATE_ASSET"
R=$(req POST /api/assets "$TA" '{"type":"CRYPTO","symbol":"btc","name":"소문자 중복","currency":"USDT"}')
check "중복 등록 (소문자 입력도 정규화되어 차단)" "409" "$(code "$R")"
check "  에러 코드" "DUPLICATE_ASSET" "$(field "$(body "$R")" code)"

echo
echo "[7] 존재하지 않는 심볼 등록 → 400 INVALID_SYMBOL"
R=$(req POST /api/assets "$TA" '{"type":"CRYPTO","symbol":"NOTACOIN","name":"없는코인","currency":"USDT"}')
if [ "$(code "$R")" = "400" ]; then
  check "없는 심볼 거부" "INVALID_SYMBOL" "$(field "$(body "$R")" code)"
elif [ "$(code "$R")" = "201" ] && [ "${EXPECT_STALE:-0}" = "1" ]; then
  # 외부 강제 실패 모드에서는 심볼 검증 자체를 건너뛰고 등록을 허용하는 것이 PRD 4-2의 의도된 동작이다.
  # 7번과 8번은 서로 배타적이므로, 이 모드에서는 7번을 SKIP 으로 처리한다.
  skip "없는 심볼 거부 — 외부 강제 실패 모드에서는 검증을 건너뛰고 등록을 허용하는 것이 정상 (PRD 4-2)"
elif [ "$(code "$R")" = "201" ]; then
  ng "없는 심볼 거부 — 외부 API 가 응답하지 않아 등록이 허용됨. 네트워크 상태를 확인하세요"
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
