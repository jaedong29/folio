# Implementation Notes

> PRD(`Asset_Dashboard_PRD.md`)를 구현하면서 내린 판단과, 구현 과정에서 드러난 PRD의 빈틈을 기록한 문서다.
> 코드를 보지 않은 사람도 읽고 설명할 수 있도록 "무엇을 / 왜 / 검토한 대안 / 실무자가 물어볼 지점" 순서로 정리했다.

---

## 0. 한눈에 보는 결과

| 항목 | 상태 |
|---|---|
| PRD 8-2 구현 순서 | 1~6단계 전부 완료 |
| PRD 8-3 검증 시나리오 | 8/8 통과 (`scripts/verify.sh`, 정상 모드 26/26 assertion) |
| 자동 시세 조회 | 완료 — CRYPTO(Binance×Upbit), STOCK(Yahoo, 해외·코스피·코스닥) 전부 실동작 확인 |
| 화면 | 정적 HTML 대시보드 1페이지 (Mock 없음) |
| 자동화 테스트 | **없음** — 우선순위상 잘라냈다 (§8) |

기술 스택: Java 17 / Spring Boot 3.5.9 / Gradle / JPA / MySQL 8(Docker) · H2(로컬) / springdoc-openapi

---

## 1. 인증·인가 — "검증을 빠뜨리는 것이 구조적으로 불가능하게"

### 무엇을 했나
- 인증: JWT(HS512, 24시간). 토큰의 subject에는 **사용자 id만** 넣는다.
- 인가: `AssetRepository`의 **단건 조회 메서드가 예외 없이 `userId`를 파라미터로 받는다.**
  ```java
  Optional<Asset> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
  ```
  `findById`는 코드 어디에서도 쓰지 않는다.
- 타인 자산과 없는 자산을 **동일하게 404 `ASSET_NOT_FOUND`** 로 응답한다. `FORBIDDEN_ASSET_ACCESS`는 서버 로그에만 남는다.
- `userId`는 요청 본문에서 절대 받지 않는다. `@CurrentUserId` 애노테이션 + `HandlerMethodArgumentResolver`가 `SecurityContext`에서만 꺼내온다.

### 왜 그렇게 했나
인가 검사를 "나중에 한 줄 더 쓰는 일"로 두면 새 API를 만들 때마다 빠뜨릴 수 있다. 조회 조건 자체에 `userId`를 넣으면 **잊는 것이 불가능**해진다. 실수의 여지를 줄이는 게 아니라 없앤 것이다.

`@CurrentUserId`도 같은 발상이다. userId를 요청 본문으로 받을 수 있는 통로를 코드에 아예 만들지 않았다.

### 검토한 대안
| 대안 | 왜 채택하지 않았나 |
|---|---|
| `findById` 후 `if (!asset.getUserId().equals(userId)) throw` | 검사 누락이 가능하다. 코드 리뷰로만 막을 수 있는 종류의 버그를 남긴다 |
| Spring Security `@PreAuthorize("...")` | 자산 소유권은 DB를 봐야 알 수 있어 결국 조회가 한 번 더 필요하다. 표현식 문자열이라 컴파일 타임 검증도 안 된다 |
| Hibernate `@Filter`로 전역 userId 필터 | 세션 단위 상태에 의존해 테스트·배치에서 예기치 못하게 동작한다. 명시성이 떨어진다 |

### 실무자가 물어볼 만한 지점
- **"Transaction에는 user_id가 없는데 거래 내역 인가는 어떻게 하나?"**
  → `TransactionService.getHistory()`는 `assetService.getOwnedAsset(userId, assetId)`를 먼저 호출한다. Asset 소유권 확인이 유일한 관문이고, 그 이후에만 `assetId`로 거래를 조회한다. Dashboard의 최근 거래도 마찬가지로 **이미 userId로 필터링된 자산 목록의 id**만 `IN` 절에 넣는다.
- **"404로 통일하면 정상 사용자도 원인을 모르지 않나?"**
  → 맞다. 자기 자산을 조회하다 404를 받을 일은 없으므로 실사용에서 혼란은 없고, 얻는 것(리소스 열거 차단)이 더 크다고 판단했다.
- **"토큰에 권한을 안 넣으면 매 요청 DB를 보나?"**
  → 현재는 모든 인증 사용자가 동일 권한이라 DB 조회가 필요 없다. 역할이 생기면 그때 캐시 여부를 판단한다.

---

## 2. Asset을 유일한 Aggregate Root로 — 도메인 메서드 캡슐화

### 무엇을 했나
수량·평단가·실현손익은 **전부 `Asset`의 도메인 메서드 안에서만** 바뀐다. `TransactionService`는 계산식을 한 줄도 갖고 있지 않다.

```java
private TransactionResponse apply(Asset asset, Transaction tx) {
    asset.applyTransaction(tx);        // 검증 + 계산 + 상태 변경
    transactionRepository.save(tx);    // 이벤트 기록
    return TransactionResponse.from(tx, asset);
}
```

`applyTransaction`의 라우팅(`switch`)까지 Asset이 갖는다. 새 거래 종류가 생겨도 변경 지점이 한 곳으로 모인다.

### 왜 그렇게 했나
평단가 계산식은 이 서비스에서 가장 틀리기 쉬운 로직이다. 이 식이 Service 여러 곳에 흩어지면, 나중에 "매수 시 수수료 반영" 같은 요구가 생겼을 때 고쳐야 할 곳을 전부 찾아야 한다. 엔티티 안에 있으면 고칠 곳이 하나다.

### 실무자가 물어볼 만한 지점
- **"왜 CASH/BANK도 `currentPrice = 1`인가?"**
  → 평가금액이 `quantity × currentPrice × exchangeRate` **한 공식으로 모든 타입에 적용**된다. `if (type == CASH)` 분기가 도메인 메서드에서 사라지고, 앞으로 추가될 계산 로직에서도 분기가 번식하지 않는다.
- **"엔티티가 비대해지지 않나?"**
  → 현재 Asset은 필드 15개 + 도메인 메서드 12개다. 계산 로직이 더 늘어나면 `Money`/`Quantity` 같은 값 객체(VO)로 뽑아내는 것이 다음 단계다. 지금 나누면 클래스만 늘고 얻는 게 없다.

---

## 3. 동시성 — 낙관적 락 (`@Version`)

### 무엇을 했나
`Asset`에 `@Version`을 걸고, `OptimisticLockingFailureException`을 `409 CONCURRENT_MODIFICATION`으로 응답한다.

### 실제 측정 결과
같은 Asset에 매수 요청 8건을 동시에 보냈다.

| 응답 | 건수 |
|---|---|
| 201 Created | 1 |
| 409 CONCURRENT_MODIFICATION | 7 |

최종 수량은 **정확히 1** — 갱신 유실(Lost Update) 0건.

### 왜 그렇게 했나 / 검토한 대안
| 방식 | 장점 | 단점 | 채택 |
|---|---|---|---|
| 원자적 UPDATE (`SET qty = qty + ?`) | 락 없이 안전, 가장 빠름 | **평단가 계산식이 SQL에 박혀 도메인 메서드 캡슐화가 깨진다** | ✗ |
| 비관적 락 (`SELECT FOR UPDATE`) | 확실함 | 충돌이 거의 없는데 매번 대기 비용 지불 | ✗ |
| 낙관적 락 (`@Version`) | 도메인 메서드 유지, 저충돌에 적합, 코드 1줄 | 충돌 시 클라이언트 재시도 필요 | **✓** |

개인 자산 관리 서비스라 같은 Asset에 대한 동시 쓰기가 극히 드물다. "충돌이 없다고 가정하고 진행하되, 충돌하면 감지해서 실패시키는" 쪽이 비용 대비 적합하다.

### 실무자가 물어볼 만한 지점
- **"8건 중 7건이 실패하는 게 정상인가?"**
  → 정상이다. 다만 **재시도를 서버가 하지 않는 것**은 트레이드오프다. 자동매매처럼 실제로 동시 요청이 잦아지면 `@Retryable`로 2~3회 재시도를 서버에 넣는 것이 다음 단계다. 현재는 클라이언트에게 409로 알린다.
- **"버튼 연타 방지는?"**
  → 프론트엔드의 책임으로 두었다. 서버는 정합성만 보장한다.

---

## 4. 외부 시세 조회 — 트랜잭션 경계가 핵심

### 무엇을 했나

```
1. [TX] 사용자 Asset 목록 조회                    ← 트랜잭션은 여기서 끝
2.      갱신 대상에서 (type, symbol) 집합 추출     ← 중복 제거
3.      PriceCache 확인 → 미스/만료된 심볼만 외부 조회 (타임아웃 2초)
4. [별도 TX] 성공한 심볼만 assets.current_price 갱신 (REQUIRES_NEW)
5.      응답 조립
```

이를 강제하기 위해 **`AssetService`의 클래스 레벨 `@Transactional`을 제거**하고 메서드마다 명시했다. 그리고 트랜잭션이 필요한 부분은 **별도 빈**으로 분리했다.

| 클래스 | 트랜잭션 | 역할 |
|---|---|---|
| `AssetService.getPortfolio()` | 없음 | 조회 → 외부 호출 → 반영을 조율 |
| `PriceQueryService` | **절대 없음** | 캐시 확인, 외부 호출, 중복 차단 |
| `PriceRefreshService.applyQuotes()` | `REQUIRES_NEW` | 조회된 시세를 Asset에 반영 |
| `AssetRegistrar.persist()` | `REQUIRED` | 심볼 검증을 마친 자산을 저장 |

### 왜 별도 빈으로 나눴나
**같은 클래스 안의 `@Transactional` 메서드를 자기 자신이 호출하면 Spring 프록시를 타지 않아 트랜잭션이 걸리지 않는다.** "외부 호출은 트랜잭션 밖, 반영만 트랜잭션 안"이라는 규칙을 지키려면 빈이 달라야 한다. 이 분리는 편의가 아니라 **필수**다.

### 실제 측정 결과

**캐시 효율**
| 상황 | 외부 호출 횟수 |
|---|---|
| 자산 등록 1건 (심볼 검증) | 1 |
| 대시보드 10회 연속 요청 (TTL 내) | **0** |

**Thundering herd 차단** — TTL 만료 후 동시 20요청, 보유 심볼 3개
| 기대 | 실제 |
|---|---|
| 3회 (심볼당 1회) | **3회** |

`ConcurrentHashMap` + symbol 단위 `synchronized` 블록 + 락 안에서의 캐시 재확인(double-check)으로 구현했다.

### 실무자가 물어볼 만한 지점
- **"조회 API(GET)가 DB를 바꾸는데 괜찮은가?"**
  → 엄밀히 safe하지 않다. 인지한 절충이다. 변경 대상이 사용자 데이터가 아니라 외부에서 가져온 파생 캐시값이고, 응답이 호출 횟수에 따라 달라지지 않는다. 자산 수가 늘면 Scheduler 기반 사전 갱신으로 전환해 이 절충을 제거한다(Roadmap 7-2).
- **"자산 30개면 대시보드가 몇 초 걸리나?"**
  → symbol 단위로 중복 제거되므로 호출 수는 자산 수가 아니라 **서로 다른 심볼 수**에 비례한다. 대부분 캐시 히트이고, 미스 건만 2초 타임아웃으로 조회한다. 다만 **미스가 N건이면 순차 호출이라 최대 2N초**다 — 병렬화(`CompletableFuture`)는 넣지 않았다. §8 참고.
- **"인메모리 캐시면 서버 재시작 시 다 날아가지 않나?"**
  → 날아간다. 하지만 `assets.current_price`에 마지막 성공값이 남아 있어 데이터 손실이 아니라 캐시 미스일 뿐이다. 인스턴스가 2대 이상 되면 `PriceCache` 구현체만 Redis로 교체한다.

---

## 5. 실패 처리 — "조용히 틀린 숫자"를 만들지 않기

### 무엇을 했나
- 시세 조회 실패 시 **예외를 던지지 않는다.** 만료된 캐시 → `assets.current_price` 순으로 폴백한다.
- 폴백을 쓰고 있으면 응답에 `priceStale: true` + `priceUpdatedAt`을 실어 화면에 "N분 전 기준"을 표시한다.
- 현재가를 한 번도 확보하지 못한 자산은 `valuationKRW: null`로 응답하고, 총자산 합계에서는 0으로 취급한다.

### 실제 측정 결과 (외부 API 강제 실패 모드)
| 항목 | 결과 |
|---|---|
| `GET /api/portfolio` | 200 OK |
| `GET /api/dashboard` | 200 OK |
| `priceStale` | `true` |
| 수동 시세 입력 `PATCH /assets/{id}/price` | 200 OK |
| 외부 죽은 상태에서 자산 등록 | 201 Created (등록 허용) |

`app.price.external-enabled=false` 스위치로 언제든 재현할 수 있다. 발표 데모에서도 이 스위치로 폴백 동작을 보여줄 수 있다.

### 실무자가 물어볼 만한 지점
- **"평가금액이 null인 자산을 총자산에서 0으로 세면 총액이 조용히 틀리지 않나?"**
  → **맞다. 이건 남아 있는 약점이다.** 다만 각 자산 행에 `valuationKRW: null` + `priceStale: true`가 내려가므로 화면에서는 드러난다. 총자산 옆에 "시세 미확보 N건" 같은 카운터를 추가하는 것이 개선안이다. 시간상 넣지 못했다.

---

## 6. PRD와 다르게 구현한 것 — 전부 기록

### 6-1. Yahoo Finance 4xx를 전부 "잘못된 심볼"로 보면 안 된다 ★

PRD 4-2는 심볼 검증 실패를 두 가지로만 나눈다.
- 조회 실패 → `400 INVALID_SYMBOL`
- **타임아웃** → 등록 허용

처음에는 이대로 구현했다. 즉 HTTP 4xx = 잘못된 심볼로 처리했다.

**그런데 구현 도중 실제로 Yahoo Finance가 이 IP를 `429 Too Many Requests`로 차단했다.** 그 결과 `NVDA`, `000660.KS` 같은 **멀쩡한 심볼조차 `INVALID_SYMBOL`로 거부**됐다. 우리 쪽 사정(호출 빈도) 때문에 사용자가 자산을 등록조차 못 하게 된 것이다.

**PRD가 놓친 세 번째 상태가 있다: "외부가 살아서 응답은 하는데, 그 응답이 거부인 경우."**

수정한 분류:
| 응답 | 분류 | 결과 |
|---|---|---|
| Yahoo `404` / Binance `400`(-1121) | `SYMBOL_NOT_FOUND` | `400 INVALID_SYMBOL` |
| `429`, `401`, `403`, 5xx, 타임아웃, 연결 실패 | `PROVIDER_UNAVAILABLE` | 등록 허용 + 경고 로그 |

`PriceProviderException`에 `Kind` enum을 두어 원인을 구분한다.

> **발표 포인트**: PRD 8-1이 "가장 불안정한 의존성이 데모 핵심 경로에 있다"고 경고했는데, **개발 중에 실제로 그 일이 일어났다.** 그리고 그 사건이 PRD 설계의 빈틈(실패의 세 번째 상태)을 드러냈다.

**후속 조사 — 429의 진짜 원인은 호출 빈도가 아니라 User-Agent였다** ★

같은 IP·같은 시각에 헤더만 바꿔 측정한 결과:

| User-Agent | 응답 |
|---|---|
| (없음) | `429 Too Many Requests` |
| `Mozilla/5.0 (Macintosh; …) Chrome/124.0 Safari/537.36` (전체 Chrome UA) | `429 Too Many Requests` |
| `Mozilla/5.0` | **`200 OK`** |

**브라우저를 정교하게 흉내 낼수록 오히려 차단됐다.** 진짜 Chrome이라면 함께 왔을 쿠키·`sec-ch-ua`·`Referer` 등이 없어 봇으로 판별된 것으로 보인다. `Mozilla/5.0`으로 바꾼 뒤 STOCK 자동 조회가 전부 정상 동작한다.

| 심볼 | 결과 |
|---|---|
| `NVDA` (해외) | 219.22 USD |
| `000660.KS` (코스피) | 1,495,000 KRW |
| `247540.KQ` (코스닥) | 102,500 KRW |
| `BITCOIN` (오타) | `400 INVALID_SYMBOL` |

이 조사 자체가 "비공식 API에 SLA가 없다"는 말의 구체적 의미다. 문서화된 규칙이 없어 **실측으로만 알아낼 수 있고, 내일 다시 바뀔 수 있다.** Roadmap 7-2(공식/유료 API 전환)를 1순위에 둔 이유가 여기 있다.

### 6-2. Soft Delete와 유니크 제약의 충돌 ★

PRD ERD는 `(user_id, type, symbol)` UNIQUE와 `deleted_at` 컬럼을 **둘 다** 정의한다. 이 둘은 충돌한다 — 유니크 제약이 `deleted_at`을 포함하지 않으므로, **자산을 삭제하면 같은 심볼을 영원히 재등록할 수 없다.**

**결정(사용자 확인)**: 재등록 시 기존 row를 **복구(부활)** 시킨다.
- 신규 등록 → `201 Created`
- 삭제된 자산 복구 → `200 OK`

PRD 4-2가 정의한 응답 본문 형태를 바꾸지 않으려고 HTTP 상태 코드로 두 경우를 구분했고, 프론트는 "삭제되었던 자산을 복구했습니다" 토스트를 띄운다.

이유: 유니크 제약을 우회하는 것보다, 과거 거래 내역·평단가·실현손익을 이어받는 쪽이 Soft Delete를 쓴 취지(기록 보존)에 맞다.

### 6-3. 삭제된 자산의 실현손익이 사라지는 문제 ★

PRD는 Soft Delete를 쓰는 이유로 **"확정된 사실(`realizedPnl`)까지 함께 사라진다"**를 든다. 그런데 모든 조회에 `deleted_at IS NULL`을 걸면, 정작 그 `realizedPnl`이 대시보드 합계에서 사라진다. **PRD가 자기 목적을 스스로 무력화하는 지점이다.**

**결정(사용자 확인)**: 보유 목록·총자산·Allocation에서는 삭제 자산을 제외하되, **`investmentSummary.realizedPnl`에만 삭제 자산까지 합산**한다.

```java
public BigDecimal getTotalRealizedPnl(Long userId) {
    return assetRepository.findAllByUserId(userId).stream()   // deleted 포함
        .map(Asset::getRealizedPnl)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

### 6-4. `name`을 선택 입력으로

PRD 4-8 표는 `name`을 "빈 문자열 불가"라 하고, PRD 5장은 "표시 이름은 **선택 입력**, 비워두면 심볼을 사용"이라 한다. 서로 어긋난다.

→ 5장(화면 흐름)을 따랐다. 사용자 입력 부담을 줄이는 쪽이 의도에 가깝다고 판단했다. 값을 주면 100자 제한은 그대로 검증한다.

### 6-5. Portfolio "전체"의 범위

PRD 4-5는 "`type` 생략 시 전체 조회"라 하고, PortfolioView 정의는 "STOCK+CRYPTO 필터링"이라 한다.

→ 와이어프레임 탭이 `[전체][CRYPTO][STOCK]`이므로 **"전체" = 투자 자산 전체**로 해석했다. `?type=CASH`는 `400 INVALID_INPUT`으로 거부한다.

### 6-6. 자산 타입과 거래 종류의 불일치

PRD는 "deposit/withdraw는 CASH/BANK 전용"이라고만 하고 위반 시 에러 코드를 정하지 않았다.

→ `400 INVALID_INPUT`으로 처리한다. 검증은 Service가 아니라 **Asset 도메인 메서드 안**에 두었다 — 어느 경로로 들어와도 막힌다.

### 6-7. PRD가 정하지 않은 에러 코드 2개 추가

회원가입/로그인 실패 코드가 PRD 4-7 표에 없어 추가했다.
- `DUPLICATE_EMAIL` (409)
- `INVALID_CREDENTIALS` (401) — **미가입 이메일과 비밀번호 불일치를 같은 코드로 응답**한다. 구분하면 계정 존재 여부가 새어나간다(4-0 규칙 3과 같은 논리).

### 6-8. Spring Boot 버전

초기 `build.gradle`이 Spring Boot 4.1.0이었으나 지시된 스택(3.x)에 맞춰 **3.5.9**로 내렸다. springdoc-openapi가 Boot 4를 아직 안정 지원하지 않는 것도 이유다.

---

## 7. 도메인 판단이 필요해 사용자에게 물어본 것

PRD에 답이 없어 임의 판단하지 않고 확인받은 4가지.

| 질문 | 결정 | 근거 |
|---|---|---|
| DB | MySQL 8(Docker) + H2 로컬 프로필 | PRD ERD 타입과 일치. Docker가 죽어도 데모 경로 확보 |
| 주식 매수 시 현금 자동 차감? | **연동 없음 — 자산은 독립** | 한 트랜잭션이 두 Aggregate를 수정하는 문제를 피한다. "기록이 아니라 조회"라는 철학과도 일치 |
| Soft Delete 후 같은 심볼 재등록 | 기존 row 복구 | §6-2 |
| 삭제 자산의 realizedPnl | 손익 합계에만 포함 | §6-3 |

**현금 연동을 하지 않은 것의 한계(발표에서 먼저 말할 것)**: 주식을 사도 현금이 줄지 않으므로 **총자산이 실제보다 커 보일 수 있다.** 이건 "이 서비스는 계좌 잔고를 자동 동기화하지 않고 사용자가 입력한 사실만 보여준다"는 전제 위에서 성립한다. 계좌 자동 동기화(MyData/Web3, Roadmap 7-3)가 붙으면 자연스럽게 해소되는 문제이기도 하다.

---

## 8. 하지 않은 것과 그 이유

우선순위상 잘라낸 것들. **모르고 빠뜨린 게 아니라 알고 자른 것**이다.

| 항목 | 상태 | 이유 / 대안 |
|---|---|---|
| **자동화 테스트** | 없음 | 시간 우선순위상 잘랐다. 대신 `scripts/verify.sh`로 8개 시나리오를 **실제 HTTP 요청**으로 검증한다. 도메인 계산(`Asset.buy/sell`)은 순수 함수에 가까워 단위 테스트를 붙이기 가장 쉬운 지점이고, 첫 번째로 추가할 대상이다 |
| **시세 병렬 조회** | 순차 | 미스가 N건이면 최대 2N초. 심볼 수가 적은 개인 사용에서는 문제되지 않지만, `CompletableFuture`로 병렬화하는 것이 다음 단계다 |
| **낙관적 락 자동 재시도** | 없음 | 409를 클라이언트에 넘긴다. 동시 쓰기가 잦아지면 `@Retryable` 도입 |
| **평가금액 null 자산의 총자산 표기** | 0으로 합산 | §5 참고. 총자산이 조용히 작아질 수 있다 |
| **Javadoc** | 전면 적용 | 공개 API 전부에 작성했다 (PRD 코드 작성 규칙) |
| **Analytics / 전체 거래내역 / 종목 Allocation / 검색 자동완성** | 미구현 | PRD가 명시적으로 Roadmap으로 분류 |

---

## 9. 패키지 구조 — PRD 6장과의 차이

PRD 6장 구조를 그대로 따르되 **3개 클래스를 추가**했다. 전부 트랜잭션 경계 때문이다.

| 추가 클래스 | 위치 | 왜 필요했나 |
|---|---|---|
| `AssetRegistrar` | `domain.asset.service` | 자산 등록은 외부 HTTP(심볼 검증) 후 저장이다. 저장만 트랜잭션에 넣으려면 별도 빈이어야 프록시가 적용된다 |
| `PriceRefreshService` | `domain.asset.service` | 시세 반영을 `REQUIRES_NEW`로 격리 |
| `PortfolioSort` | `domain.asset.service` | 정렬 대상(평가손익)이 **DB 컬럼이 아니라 조회 시점 계산값**이라 SQL 정렬이 불가능하다. `Pageable` 대신 허용 필드만 여는 파서를 두어, 잘못된 필드가 500이 아니라 400이 되게 했다 |

그 외 `SymbolKey`, `PriceQuote`, `PriceProperties`는 값 타입(record)이라 구조 변경으로 보지 않는다.

---

## 10. 발표에서 먼저 꺼낼 이야기

1. **"PRD 8-1이 경고한 리스크가 개발 중에 실제로 터졌고, 그게 설계의 빈틈을 찾아줬다"** (§6-1)
   — 실패에는 두 종류가 아니라 세 종류가 있었다.
2. **"Soft Delete를 도입한 목적을 Soft Delete 조회 규칙이 무력화하고 있었다"** (§6-3)
3. **"캡슐화를 지키려고 낙관적 락을 골랐고, 8건 동시 요청으로 실제로 측정했다"** (§3)
4. **"현금 연동을 하지 않아 총자산이 실제와 다를 수 있다"** (§7) — 약점을 먼저 말한다.
5. **"자동화 테스트 대신 실행 가능한 검증 스크립트를 남겼다"** (§8) — 타협의 이유를 설명한다.
