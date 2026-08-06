#!/bin/bash
# 발표 시연용 데이터를 실제 API 호출로 만들어 넣는다.
#
#   사용법: 서버를 띄운 뒤   bash scripts/seed-demo.sh
#
# SQL 대신 API 를 쓰는 이유:
#   1. H2 든 MySQL 이든 같은 스크립트가 동작한다 (비밀번호 해시를 직접 만들 필요가 없다).
#   2. 평단가·실현손익이 도메인 메서드를 실제로 통과해 계산된다. SQL 로 값을 직접 꽂아 넣으면
#      화면에는 숫자가 보여도 그 숫자가 우리 로직에서 나온 것인지 알 수 없다.

set -eu
B=${B:-http://localhost:8080}
EMAIL=${EMAIL:-demo@example.com}
PASSWORD=1234abcd

echo "▸ 데모 계정 준비: $EMAIL / $PASSWORD"
curl -s -o /dev/null -X POST "$B/api/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"nickname\":\"재동\"}"

T=$(curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -n "$T" ] || { echo "로그인 실패 — 서버가 떠 있는지 확인하세요"; exit 1; }

mk() { curl -s -X POST "$B/api/assets" -H "Authorization: Bearer $T" \
        -H 'Content-Type: application/json' -d "$1" | sed -n 's/.*"id":\([0-9]*\).*/\1/p'; }
tx() { curl -s -o /dev/null -X POST "$B/api/assets/$1/transactions/$2" -H "Authorization: Bearer $T" \
        -H 'Content-Type: application/json' -d "$3"; }
setprice() { curl -s -o /dev/null -X PATCH "$B/api/assets/$1/price" -H "Authorization: Bearer $T" \
        -H 'Content-Type: application/json' -d "{\"currentPrice\":$2}"; }
setfx() { curl -s -o /dev/null -X PATCH "$B/api/assets/$1/exchange-rate" -H "Authorization: Bearer $T" \
        -H 'Content-Type: application/json' -d "{\"exchangeRate\":$2}"; }

echo "▸ 자산 등록"
BTC=$(mk  '{"type":"CRYPTO","symbol":"BTC","name":"비트코인","currency":"USDT"}')
ETH=$(mk  '{"type":"CRYPTO","symbol":"ETH","name":"이더리움","currency":"USDT"}')
NVDA=$(mk '{"type":"STOCK","symbol":"NVDA","name":"엔비디아","currency":"USD"}')
KB=$(mk   '{"type":"BANK","symbol":"KRW","name":"KB국민은행","currency":"KRW"}')
CASH=$(mk '{"type":"CASH","symbol":"KRW","name":"현금","currency":"KRW"}')
echo "  BTC=$BTC ETH=$ETH NVDA=$NVDA KB=$KB CASH=$CASH"

echo "▸ 거래 입력"
tx "$BTC"  buy      '{"quantity":0.5,"price":40000,"exchangeRate":1380,"memo":"첫 매수","tradedAt":"2026-07-20T10:00:00"}'
tx "$BTC"  buy      '{"quantity":0.3,"price":45000,"exchangeRate":1390,"memo":"추가 매수","tradedAt":"2026-08-01T10:00:00"}'
tx "$BTC"  sell     '{"quantity":0.2,"price":56000,"exchangeRate":1385,"memo":"일부 익절","tradedAt":"2026-08-04T10:00:00"}'
tx "$ETH"  buy      '{"quantity":4,"price":2400,"exchangeRate":1385,"tradedAt":"2026-08-02T10:00:00"}'
tx "$NVDA" buy      '{"quantity":20,"price":120,"exchangeRate":1380,"memo":"실적 발표 전","tradedAt":"2026-07-25T10:00:00"}'
tx "$KB"   deposit  '{"quantity":8500000,"memo":"월급 입금","tradedAt":"2026-08-05T09:00:00"}'
tx "$CASH" deposit  '{"quantity":1200000,"memo":"생활비","tradedAt":"2026-08-06T09:00:00"}'
tx "$KB"   withdraw '{"quantity":450000,"memo":"카드값","tradedAt":"2026-08-06T12:00:00"}'

echo "▸ 폴백용 수동 시세 입력 (해외주식 환율은 MVP 에서 수동 입력이다)"
# Yahoo Finance 가 죽어 있어도 화면이 정상적으로 그려지는지 이 값으로 확인할 수 있다 (PRD 8-1 대응).
setprice "$NVDA" 219.22
setfx    "$NVDA" 1417

echo
echo "✅ 완료. http://localhost:8080 에서 $EMAIL / $PASSWORD 로 로그인하세요."
