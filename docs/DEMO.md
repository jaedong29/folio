# DEMO — 시연 순서

> 발표 당일 이 문서만 보고 순서대로 따라가면 된다.
> **외부 시세 API가 죽었을 때의 대체 경로는 §5에 있다. 발표 전에 §5를 한 번 읽어둘 것.**

---

## 0. 사전 준비 (발표 15분 전)

```bash
cd ~/asset
```

**A안 — Docker Compose (기본)**

```bash
docker compose up -d --build
```

MySQL 기동 후 앱이 뜬다. 첫 빌드는 2~3분 걸리므로 **발표 직전에 처음 실행하지 말 것.**

**B안 — Docker 없이 (권장 폴백)**

```bash
./gradlew bootRun
```

H2 인메모리로 뜬다. 별도 설치가 필요 없고 5초 안에 기동한다. Docker 데몬이 안 뜨거나 이미지 빌드가 꼬였을 때 이쪽으로 즉시 전환한다.

**기동 확인**

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/v3/api-docs
```

`200`이 나오면 정상.

---

## 1. 시연 데이터 넣기

```bash
bash scripts/seed-demo.sh
```

출력:
```
▸ 데모 계정 준비: demo@example.com / 1234abcd
▸ 자산 등록
  BTC=1 ETH=2 NVDA=3 KB=4 CASH=5
▸ 거래 입력
▸ 환율 입력 (해외주식의 환율은 MVP 에서 수동 입력이다 — Roadmap 7-2)
  ✓ NVDA 자동 조회 성공: 219.22000000 USD
✅ 완료. http://localhost:8080 에서 demo@example.com / 1234abcd 로 로그인하세요.
```

만들어지는 데이터:

| 자산 | 타입 | 거래 |
|---|---|---|
| 비트코인 (BTC) | CRYPTO | 0.5개 @40,000×1,380 매수 → 0.3개 @45,000×1,390 매수 → 0.2개 @56,000×1,385 **매도** |
| 이더리움 (ETH) | CRYPTO | 4개 @2,400×1,385 매수 |
| 엔비디아 (NVDA) | STOCK | 20개 @120×1,380 매수 + 환율 1,417 (시세는 Yahoo 자동 조회, 실패 시에만 219.22 수동 폴백) |
| KB국민은행 | BANK | 8,500,000 입금 → 450,000 출금 |
| 현금 | CASH | 1,200,000 입금 |

> **왜 SQL이 아니라 API 호출인가**: 평단가·실현손익이 실제 도메인 메서드를 통과해 계산되게 하기 위해서다. SQL로 값을 직접 꽂으면 화면에 숫자는 보이지만 그게 우리 로직에서 나온 값인지 알 수 없다. H2든 MySQL이든 같은 스크립트가 동작하는 것도 장점이다.
>
> SQL 방식이 꼭 필요하면 `src/main/resources/db/seed.sql`에 넣으면 되지만(H2 프로필에서 자동 로드), BCrypt 해시를 직접 만들어야 하므로 권장하지 않는다.

---

## 2. 화면 시연 (5분)

### 2-1. 로그인
브라우저에서 **http://localhost:8080**
- 이메일·비밀번호가 미리 채워져 있다 → **로그인** 클릭

> 설명 포인트: "JWT는 stateless라 로그아웃 API가 없다. 프론트가 토큰을 지우는 게 로그아웃이다."

### 2-2. Dashboard — 첫 화면
보여줄 것:
- **총 자산** (₩8천만 대)
- **Investment Summary** — 평가손익과 실현손익이 **분리**되어 있다
- **Asset Allocation** 도넛 차트 (CRYPTO / STOCK / BANK / CASH)
- **보유 자산** — 평가손익 높은 순 정렬 (기본값)
- **Cash & Bank**, **최근 거래 5건**

> 설명 포인트:
> - "실현손익 옆에 *(삭제 자산 포함)*이라 써둔 이유 → PRD의 빈틈을 찾은 이야기 (IMPLEMENTATION_NOTES §6-3)"
> - "이 숫자는 전부 `/api/dashboard` 응답 하나다. Mock 없음."

### 2-3. 자산 행 클릭 → 거래 내역
비트코인 행을 누르면 **거래 내역 드로어**가 열린다.

```
SELL  0.2   56,000 × 1,385   08/04 10:00 · 일부 익절
BUY   0.3   45,000 × 1,390   08/01 10:00 · 추가 매수
BUY   0.5   40,000 × 1,380   07/20 10:00 · 첫 매수
```

> 설명 포인트: "거래 시점의 환율이 이벤트에 박혀 있다. `assets.exchange_rate`는 현재 환율이라 계속 덮어써지므로, 실현손익을 나중에 재계산·검증하려면 그 순간의 환율이 남아 있어야 한다."

### 2-4. 오입력 정정 ★ (설계 이야기를 꺼내기 좋은 지점)

비트코인 드로어에서 **08/01 추가 매수(0.3)** 행의 🗑 버튼을 누른다.

```
삭제 전:  수량 0.6  평단 ₩57,956,250  실현손익 +₩3,920,750
삭제 후:  수량 0.3  평단 ₩55,200,000  실현손익 +₩4,472,000
```

수량뿐 아니라 **평단가와 실현손익까지 다시 계산**된다. 매도 손익이 그 시점의 평단가에 의존하므로, 지운 거래의 영향만 빼는 것으로는 안 되고 남은 이력을 처음부터 다시 접어야 하기 때문이다.

이어서 **07/20 첫 매수** 행의 🗑을 눌러본다.

```
이 거래를 삭제하면 이후 거래의 보유 수량이 음수가 됩니다. 최근 거래부터 순서대로 삭제해주세요.
```

> 설명 포인트:
> - "PRD는 Transaction을 append-only 이벤트 로그로 정의했는데, 이 기능은 거기서 벗어납니다. **append-only는 시스템이 만드는 이벤트에는 맞지만, 사람이 손으로 입력하는 이벤트에는 오타 정정 경로가 반드시 필요합니다.**"
> - "이 기능의 존재 자체가 설계상의 답입니다 — **Asset의 상태는 Transaction 이력의 파생값이고, 필드는 매번 재계산하지 않기 위한 스냅샷**입니다."
> - 시연 후 다시 `bash scripts/seed-demo.sh`를 돌리거나 서버를 재시작하면 데이터가 복구된다.

### 2-5. 자산 수정 / 삭제

같은 드로어의 **[이름 수정] [시세 입력] [환율 입력] [자산 삭제]** 칩을 보여준다.

**[자산 삭제]**를 누르면 이런 확인창이 뜬다.

```
거래 내역과 실현손익은 보존됩니다. 같은 심볼을 다시 등록하면 이 자산이 그대로 복구됩니다.
숫자를 되돌리려면 자산 삭제가 아니라 거래 삭제를 사용하세요.
```

> 설명 포인트: "Soft Delete + 재등록 시 복구 정책 때문에 **자산 삭제는 초기화가 아닙니다.** 사용자가 오해하면 데이터가 되살아나는 걸 보고 버그로 받아들이므로, 지우기 전에 명시했습니다."

### 2-6. 탭 필터
보유 자산 카드의 **[CRYPTO] / [STOCK]** 탭을 눌러 필터링을 보여준다.

### 2-7. 새로고침 버튼 (↻)
우상단 ↻ 클릭 → "최신 시세로 갱신했습니다"

> 설명 포인트: "여러 번 눌러도 15분 안에는 외부 API를 다시 부르지 않는다. 캐시는 자산이 아니라 **심볼**에 붙어 있다."

### 2-8. FAB(+) — 도메인 규칙 보여주기
우하단 **+** → **[매수 · 매도]**
- 자산: 비트코인 / 거래: **매도** / 수량: **99**
- **기록** 클릭

```
보유 수량이 부족합니다. (보유: 0.6, 요청: 99)
```

> 설명 포인트: "이 검증은 Service가 아니라 **Asset 엔티티 안**에 있다. 어느 경로로 들어와도 막힌다."

---

## 3. API 시연 (Swagger) — 3분

**http://localhost:8080/swagger-ui.html**

1. `POST /api/auth/login` → `demo@example.com` / `1234abcd` → `accessToken` 복사
2. 우상단 **Authorize** → 토큰 붙여넣기
3. `GET /api/dashboard` 실행 → 응답 구조 설명
4. `GET /api/portfolio?sort=unrealizedPnl,desc` 실행

---

## 4. 인가 검증 시연 ★ (가장 중요)

**터미널에서 직접 보여줄 것.** PRD가 "이 항목이 깨지면 나머지와 무관하게 실패"라고 규정한 부분이다.

```bash
bash scripts/verify.sh
```

8개 시나리오가 전부 실행되고 결과가 나온다.

```
[1] A 계정 토큰으로 B 의 Asset 조회 → 404 ASSET_NOT_FOUND
  PASS 타인 자산 조회 (기대=404, 실제=404)
  PASS   타인 자산 수정 (기대=404, 실제=404)
  PASS   타인 자산 삭제 (기대=404, 실제=404)
  PASS   타인 거래내역 조회 (기대=404, 실제=404)
  PASS   타인 자산에 거래 생성 (기대=404, 실제=404)
  PASS   토큰 없이 접근 (기대=401, 실제=401)
...
 PASS 26 / FAIL 0
```

> 설명 포인트: "403이 아니라 **404**다. 403으로 응답하면 공격자가 id를 순회하며 어떤 자산이 실재하는지 목록을 만들 수 있다."

**동시성도 보여주려면:**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"1234abcd"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

for i in $(seq 1 8); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/assets/1/transactions/buy \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d '{"quantity":0.01,"price":50000,"exchangeRate":1400,"tradedAt":"2026-08-06T10:00:00"}' &
done | sort | uniq -c
```

기대 결과: `201` 1건, `409` 7건.

---

## 5. 외부 시세 API가 죽었을 때 — 대체 시연 경로 ★★

> **발표 전 반드시 확인**: Yahoo Finance는 비공식 API라 **IP 단위로 `429 Too Many Requests`를 준다.** 개발 중에 실제로 겪었다. Binance/Upbit(CRYPTO)은 안정적이었다.

### 5-1. 지금 살아 있는지 확인

```bash
curl -s -o /dev/null -w "yahoo:  %{http_code}\n" -A "Mozilla/5.0" \
  "https://query1.finance.yahoo.com/v8/finance/chart/NVDA?interval=1d&range=1d"
curl -s -o /dev/null -w "binance:%{http_code}\n" "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT"
curl -s -o /dev/null -w "upbit:  %{http_code}\n" "https://api.upbit.com/v1/ticker?markets=KRW-USDT"
```

`200`이면 정상. Yahoo가 `429`여도 **화면은 정상 동작한다** — 아래 폴백이 작동하기 때문이다.

### 5-2. 폴백 1 — 수동 시세 입력 (최후의 수단, 항상 동작)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"1234abcd"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

curl -s -X PATCH http://localhost:8080/api/assets/3/price \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"currentPrice":219.22}'
```

`seed-demo.sh`가 이미 NVDA에 수동 시세를 넣어두므로, **외부가 전부 죽어도 화면에는 숫자가 채워져 있다.**

### 5-3. 폴백 2 — 실패를 의도적으로 재현해서 보여주기 ★

외부 API가 멀쩡할 때도 **실패 상황을 시연**할 수 있다. 이게 더 설득력 있다.

```bash
# 서버 중지 후
APP_PRICE_EXTERNAL_ENABLED=false ./gradlew bootRun
```

다른 터미널에서:
```bash
bash scripts/seed-demo.sh
EXPECT_STALE=1 bash scripts/verify.sh
```

기대 결과:
```
[8] 외부 시세 API 강제 실패 → 화면 정상, priceStale: true
  PASS 수동 시세 입력(최후 폴백) 동작 (기대=200, 실제=200)
  PASS Portfolio 응답 (기대=200, 실제=200)
  PASS Dashboard 응답 (기대=200, 실제=200)
  PASS priceStale 플래그 (기대=True, 실제=True)
```

브라우저를 새로고침하면 총자산 아래에 **⚠ 일부 시세가 오래됨** 배지가 뜨고, 각 자산에 **"N분 전 기준"** 배지가 붙는다.

> 설명 포인트: "시세 조회 실패가 화면을 깨지 않는다. 그리고 **조용히 틀린 숫자를 보여주지도 않는다** — 오래된 값을 쓰고 있다는 사실을 사용자에게 알린다. 조용히 틀리는 게 가장 나쁜 실패 방식이다."
>
> 이 모드에서 시나리오 7(존재하지 않는 심볼 → 400)은 `SKIP`으로 표시된다. 외부가 죽으면 심볼 검증을 건너뛰고 등록을 허용하는 것이 PRD 4-2의 의도된 동작이라, 7번과 8번은 서로 배타적이기 때문이다. 두 모드 모두 `PASS 26 / FAIL 0`이다.

### 5-4. 최후의 최후 — 네트워크가 아예 없을 때

1. `APP_PRICE_EXTERNAL_ENABLED=false ./gradlew bootRun` (H2, 외부 호출 0회)
2. `bash scripts/seed-demo.sh` — 모든 자산이 등록되고, NVDA는 수동 시세가 들어간다
3. CRYPTO 자산에도 수동 시세를 넣어 화면을 완성한다:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"1234abcd"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

for id_price in "1:64500" "2:1900"; do
  id=${id_price%%:*}; price=${id_price##*:}
  curl -s -o /dev/null -X PATCH "http://localhost:8080/api/assets/$id/price" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"currentPrice\":$price}"
  curl -s -o /dev/null -X PATCH "http://localhost:8080/api/assets/$id/exchange-rate" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"exchangeRate":1417}'
done
```

이 상태에서도 대시보드·차트·손익 계산이 전부 정상 동작한다. 인터넷 없이 시연 가능하다.

---

## 6. 정리 / 초기화

```bash
# H2 (bootRun): 서버를 껐다 켜면 데이터가 초기화된다
# MySQL (docker compose):
docker compose down -v     # 볼륨까지 삭제
docker compose up -d
```

---

## 부록 — 자주 쓰는 명령

| 목적 | 명령 |
|---|---|
| 로컬 실행 (H2) | `./gradlew bootRun` |
| Docker 실행 (MySQL) | `docker compose up -d --build` |
| 외부 시세 끄고 실행 | `APP_PRICE_EXTERNAL_ENABLED=false ./gradlew bootRun` |
| 캐시 TTL 짧게 (시연용) | `APP_PRICE_CACHE_TTL_MINUTES=1 ./gradlew bootRun` |
| 시연 데이터 주입 | `bash scripts/seed-demo.sh` |
| 검증 시나리오 실행 | `bash scripts/verify.sh` |
| Swagger | http://localhost:8080/swagger-ui.html |
| H2 콘솔 | http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:assetdashboard`, user `sa`, 암호 없음) |
| 대시보드 | http://localhost:8080 |

**데모 계정**: `demo@example.com` / `1234abcd`
