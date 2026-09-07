# Implementation Notes

> PRD(`Asset_Dashboard_PRD.md`)를 구현하면서 내린 판단과, 구현 과정에서 드러난 PRD의 빈틈을 기록한 문서다.
> 코드를 보지 않은 사람도 읽고 설명할 수 있도록 "무엇을 / 왜 / 검토한 대안 / 실무자가 물어볼 지점" 순서로 정리했다.

---

## 0. 한눈에 보는 결과

| 항목 | 상태 |
|---|---|
| PRD 8-2 구현 순서 | 1~6단계 전부 완료 |
| PRD 8-3 검증 시나리오 | 8/8 통과 (`scripts/verify.sh`, 정상 모드 49/49 assertion) |
| 자동 시세·환율 조회 | 완료 — CRYPTO(Binance), STOCK(Yahoo), USD/KRW(Yahoo), USDT/KRW(Upbit) |
| 화면 | 총 투자자산·Daily PnL·Position·투자 대기자금 중심의 정적 HTML 대시보드 (Mock 없음) |
| 자동화 테스트 | JUnit 11개 클래스·35개 테스트 + 실제 서버 대상 HTTP 검증 49개 |

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
  → 현재 Asset은 **772줄, 영속 상태 필드 21개**로 초기 설명보다 커졌다. 아직 Aggregate Root와 도메인 불변식은
  유지하되, 다음 단계에서는 수량·원가·실현손익을 `PositionState`, 현재가·환율·갱신시각을 `MarketData`로 묶는
  리팩터링을 검토한다. 단순히 클래스 수를 늘리기보다 책임 단위 분리가 실제로 필요해진 시점으로 본다.

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

정산 연동 이후 한 매매가 투자 자산과 통화별 대기자금 두 자산을 함께 갱신한다. 따라서 BTC와 ETH를 동시에
매수해도 같은 사용자의 USDT 대기자금에서 충돌할 수 있다. 다만 대기자금은 서비스 전체 공용이 아니라
**사용자별·통화별 한 개**이고, 개인용 수동 입력 MVP에서는 동일 사용자가 같은 통화 거래를 동시에 보낼 가능성이
낮다. 이 저동시성 가정 아래 충돌을 기다리게 하기보다 감지해 409로 실패시키는 낙관적 락을 유지한다.

### 실무자가 물어볼 만한 지점
- **"8건 중 7건이 실패하는 게 정상인가?"**
  → 갱신 유실 없이 한 요청만 커밋되고 나머지를 명시적으로 거부한 결과다. 현재는 409로 응답한다. 내부 재시도는
  버튼 중복 요청까지 성공시킬 수 있어 낙관적 락만으로는 안전하지 않다 — 그래서 매수·매도·입금·출금에는
  아래 §3-1의 멱등성 키를 별도로 둔다.
- **"버튼 연타 방지는?"**
  → §3-1 참고. 서버가 `Idempotency-Key`로 실제 중복 실행을 막고, 프론트는 폼을 열 때 키 하나를 만들어 재시도마다
  재사용한다.

---

## 3-1. 거래 멱등성 키 — 낙관적 락이 못 막는 "같은" 요청의 중복 실행

### 무엇을 했나
매수·매도·입금·출금 4개 API에 `Idempotency-Key` 헤더를 필수로 받는다. `idempotency_keys` 테이블에
`(user_id, idempotency_key)` unique 제약을 걸고, 요청마다 이렇게 처리한다.

```
1. (userId, key)로 기존 레코드 조회
2. 없으면 → claim 시도(INSERT). 유니크 제약 위반이면 동시 요청 경합 → 409 IDEMPOTENCY_KEY_IN_PROGRESS
3. 있고 요청 fingerprint가 다르면 → 409 IDEMPOTENCY_KEY_REUSED (다른 요청에 같은 키 재사용)
4. 있고 아직 완료 안 됐으면(진행 중 요청이 있음) → 409 IDEMPOTENCY_KEY_IN_PROGRESS
5. 있고 완료됐으면 → 완료 시 저장한 `TransactionResponse` JSON을 역직렬화해 최초 응답 그대로 반환(재실행 없음)
```

fingerprint는 `(operation, assetId, request 직렬화)`의 SHA-256이라, 같은 키라도 요청 내용이 다르면 재사용으로
간주해 거부한다. 키는 공백이 아닌 1~255자만 컨트롤러 입구에서 허용하고, 24시간 지난 키는 `@Scheduled`
작업이 매시간 정리한다.

후속 리뷰에서 최초 구현이 `result_transaction_id`만 보관해, 그 사이 다른 거래가 있으면 재시도 응답의 Asset
snapshot이 현재 값으로 바뀌는 문제를 확인했다. V11에서 nullable `result_response_json`을 추가하고 거래·claim·응답
저장을 같은 트랜잭션으로 커밋하도록 보완했다. V11 이전에 생성된 완료 row는 최대 24시간의 보존 기간 동안 JSON이
null일 수 있으므로 기존 재구성 경로를 임시 호환 경로로 유지한다. 이를 일괄 삭제하면 배포 직후의 정상 재시도가
중복 체결될 수 있어 선택하지 않았다.

### 왜 낙관적 락만으로는 부족한가
`@Version`은 **서로 다른** 요청끼리의 경합만 막는다. 네트워크 재시도나 버튼 중복 클릭으로 **같은** 요청이 두 번
도착하면, 두 번째 요청은 첫 번째가 이미 커밋한 새 버전을 읽고 정상적으로 한 번 더 체결된다 — 락 충돌이 아니라
"정상적인 이중 매수"가 된다. 이건 낙관적 락이 아니라 요청 자체를 식별해서 막아야 하는 문제다.

### 왜 `REQUIRES_NEW`가 아니라 기본 `@Transactional`인가
Refresh Token 재사용 탐지(`RefreshTokenService.rotate()`, §3-2)에서는 실패해도 폐기 기록을 남겨야 해서
`REQUIRES_NEW` + `noRollbackFor`를 썼다. 여기는 반대다 — **거래 자체가 실패하면 claim도 함께 롤백돼야** 사용자가
같은 키로 (또는 고친 요청으로) 다시 시도할 수 있다. "실패해도 흔적을 남긴다"가 아니라 "성공한 것만 기억한다"가
맞는 정책이라, claim과 완료 기록은 호출자(`TransactionService`)의 트랜잭션에 그대로 참여시켰다.

### 실제 확인 결과
같은 키로 `buy()`를 두 번 호출(재시도 시나리오)했을 때, `@Transactional` 경계를 진짜로 통과하는지 보려고
클래스 단위 `@Transactional`을 걸지 않은 통합 테스트로 확인했다 — 걸었다면 두 호출이 테스트 트랜잭션 하나에
참여해 서로의 미커밋 쓰기를 그대로 보게 되어, 커밋까지 끝난 뒤에도 중복 실행이 안 되는지를 검증하지 못한다.

| 검증 | 결과 |
|---|---|
| 같은 키로 재시도 | 두 응답의 `transactionId` 동일, 거래 row 1건만 생성 |
| 중간에 다른 거래 후 첫 키 재시도 | 최초 `TransactionResponse` 전체와 동일, 현재 Asset 수량과 섞이지 않음 |
| 최초 거래 삭제 후 같은 키 재시도 | 거래를 다시 만들지 않고 삭제 전 최초 응답을 그대로 반환 |
| 같은 키 + 다른 요청 본문 | `409 IDEMPOTENCY_KEY_REUSED`, 거래 row 추가 생성 안 됨 |
| 서로 다른 키 + 같은 요청 본문 | 둘 다 정상 실행, 거래 row 2건, 수량 2배 — 의도된 별개 거래까지 막지는 않음 |
| 공백·256자 키 | 컨트롤러에서 `400 INVALID_INPUT`, DB 예외·경합 오류로 내려가지 않음 |

클래스 단위 `@Transactional`이 없는 통합 테스트로 위 커밋 경계를 검증했고, 실 서버에서도 첫 입금 → 다른 입금
→ 첫 키 재시도를 순서대로 보내 최초 응답 JSON 전체가 같고 실제 잔액은 두 거래만 반영되는지 확인했다. 헤더 없이
보내면 `400 IDEMPOTENCY_KEY_REQUIRED`, 256자 키를 보내면 `400 INVALID_INPUT`이다.

### 실무자가 물어볼 만한 지점
- **"멱등성 키가 없으면 정말 막을 방법이 없나?"**
  → 없다. `@Version`은 두 요청이 같은 row를 다른 버전으로 읽었을 때만 걸린다. 재시도는 첫 요청이 이미 반영한
  최신 버전을 읽고 시작하므로 충돌이 아니다.
- **"클라이언트가 멱등성 키를 안 보내면?"**
  → `MissingRequestHeaderException`을 `GlobalExceptionHandler`가 `400 IDEMPOTENCY_KEY_REQUIRED`로 매핑한다.
  프론트는 폼을 열 때 `crypto.randomUUID()`로 키를 하나 만들어 그 폼 세션 동안(에러 후 재시도 포함) 재사용하고,
  폼을 다시 열면 새 키를 만든다.
- **"재시도 응답도 최초 응답과 완전히 같은가?"**
  → V11 이후 요청은 그렇다. 최초 `TransactionResponse`를 JSON으로 저장하고 그대로 읽으므로 이후 Asset 변경이나
  거래 삭제와 무관하다. V11 이전 완료 row만 최대 24시간 동안 기존 재구성 경로를 사용한다.
- **"응답 JSON 저장이 실패하면 거래만 커밋될 수 있나?"**
  → 없다. 직렬화와 완료 기록은 거래 트랜잭션 안에서 실행되며 실패하면 거래·claim도 함께 롤백된다. claim이
  사라진 비정상 상태도 조용히 무시하지 않고 예외를 내 전체 트랜잭션을 실패시킨다.
- **"다중 인스턴스에서도 안전한가?"**
  → 그렇다. claim은 DB unique 제약(INSERT 경합 시 `DataIntegrityViolationException`)에 의존하지 인메모리 상태를
  쓰지 않는다 — `docs/PRODUCTION_READINESS.md`의 "단일 인스턴스 메모리 상태" 갭(로그인 잠금 등)과 달리 별도
  인프라 없이도 다중 인스턴스로 확장된다.

---

## 3-2. Refresh Token family 수명 분리 — 정리와 재사용 탐지를 함께 지키기

### 무엇을 했나
최초 정리 작업은 개별 token의 `expiresAt + 7일`이 지나면 row를 삭제했다. 후속 리뷰에서 같은 family의 최신
token이 계속 회전해 살아 있는 동안 과거 hash가 먼저 사라질 수 있고, 그 과거 토큰이 다시 오면 `familyId`를
찾지 못해 최신 토큰을 폐기할 수 없다는 문제를 확인했다.

V12에서 `refresh_token_families` 테이블을 추가해 로그인 한 번에서 시작된 family의 `userId`, 발급 시각, 절대
만료, 폐기 시각을 한 행으로 분리했다. 새 family의 절대 만료는 로그인 시점의 `refreshExpirationDays`(기본 14일)로
한 번만 정하며, 회전된 모든 token은 같은 만료 시각을 공유한다. 회전은 값을 바꾸지만 세션의 절대 수명을
연장하지 않는다.

정리 작업도 개별 token 만료가 아니라 **family 절대 만료 + 7일**을 기준으로 바꿨다. 대상 family의 token hash를
먼저 모두 지운 뒤 family row를 지우므로, family가 살아 있는 동안에는 아무리 오래된 회전 토큰도 hash가 남아
재사용 탐지에 쓰인다. 로그아웃은 제시한 token 하나가 아니라 그 로그인 family 전체를 폐기하며, 비밀번호 변경은
기존처럼 사용자의 모든 family를 폐기한다. 회원 탈퇴는 token row를 먼저 지운 뒤 family 메타데이터도 삭제한다.

기존 운영 데이터는 V12가 family별 `MIN(issued_at)`과 `MAX(expires_at)`으로 backfill한다. 가장 늦게 만료되는 현재
token을 그대로 살려 배포 순간 강제 로그아웃을 만들지 않고, 활성 token이 하나도 없는 family만 폐기 상태로
이관한다.

### 왜 family가 살아 있는 동안 hash를 지우지 않나
현재 Refresh Token은 family id를 포함하지 않는 opaque random 값이다. hash row를 삭제하면 나중에 같은 원문이
와도 어느 family였는지 복구할 정보가 없다. family 상태 테이블만 추가한 채 개별 hash를 먼저 지우는 설계는 원래
보안 구멍을 해결하지 못한다. 절대 만료 시각 이후에는 그 family의 최신 token도 더는 유효하지 않으므로, 그때
hash와 family를 함께 지우면 재사용 탐지 기간과 저장량 상한이 일치한다.

### 검토한 대안
| 대안 | 왜 채택하지 않았나 |
|---|---|
| 개별 token 만료 + 7일 정리 유지 | 활성 family의 과거 hash가 먼저 사라져 재사용 시 현재 세션을 폐기할 수 없다 |
| 보존 기간 숫자만 늘리기 | 구멍이 나타나는 시점만 늦출 뿐 sliding 회전이 계속되면 같은 문제가 반복된다 |
| raw token에 family id 포함 | 삭제된 hash도 family에 연결할 수 있지만 토큰 형식 변경과 위조 family id 처리 정책이 추가된다 |
| 회전마다 family 만료 연장 | 사용성은 좋지만 family와 과거 hash가 계속 살아 저장량 상한이 다시 사라진다 |

### 실제 확인 결과
클래스 단위 `@Transactional`이 없는 통합 테스트로 예외 뒤 family 폐기와 정리 커밋을 확인했다.

| 검증 | 결과 |
|---|---|
| 정상 회전 | raw token은 바뀌지만 새 token의 만료 시각은 최초 family 절대 만료와 동일 |
| 폐기된 과거 token 재사용 | `INVALID_REFRESH_TOKEN`, family 폐기 상태가 예외 뒤에도 실제 커밋 |
| family 폐기 뒤 최신 token 사용 | token 자체 상태와 무관하게 `INVALID_REFRESH_TOKEN` |
| 과거 token은 만료됐지만 family는 활성 | 정리 작업 뒤에도 hash와 family 모두 보존 |
| family 절대 만료 + 7일 경과 | 해당 family의 모든 token hash를 먼저 지우고 family row 삭제 |
| V12 기존 데이터 backfill(MySQL 8.0) | 활성 token이 남은 family는 ACTIVE·최대 만료 유지, 전부 폐기된 family는 REVOKED로 이관 |
| JVM 나노초 시각과 DB `DATETIME(6)` | 발급 시각을 마이크로초로 정규화해 최초 응답과 DB 재조회 만료가 정확히 일치 |

실 서버에서도 로그인 → 정상 회전 → 첫 token 재사용 순서로 호출해 첫 token과 방금 받은 token이 모두
`401 INVALID_REFRESH_TOKEN`이 되는지 확인했다. 별도 로그인 family는 로그아웃 후 재발급이 같은 401로 거부됐다.

### 실무자가 물어볼 만한 지점
- **"회전할 때마다 14일이 다시 시작되지 않나?"**
  → 이제는 시작되지 않는다. 탈취된 세션이 회전만으로 무기한 연장되지 않게 로그인 시점부터 최대 14일로
  제한한다. 계속 이용하려면 14일마다 비밀번호로 다시 로그인해야 한다.
- **"왜 family 만료 뒤에도 7일 보존하나?"**
  → 이미 모든 token이 사용할 수 없는 시점이라 활성 세션 보호에는 필요 없지만, 운영 중 만료·재사용 징후를
  짧게 확인할 여유를 둔다. 정리 주기는 하루라 최대 약 하루 늦게 삭제될 수 있다.
- **"재사용 탐지 예외가 family 폐기를 롤백하지 않나?"**
  → `rotate()`는 `REQUIRES_NEW + noRollbackFor(BusinessException.class)`를 유지한다. 실제 커밋 경계 테스트에서
  예외 응답 뒤에도 family row의 `revokedAt`과 최신 token 폐기가 남는지 확인했다.

---

## 3-3. Graceful shutdown — 배포 종료 신호가 진행 중 요청을 즉시 끊지 않게

### 무엇을 했나
`server.shutdown: graceful`을 켜고, `spring.lifecycle.timeout-per-shutdown-phase: 30s`로 종료 대기 상한을
명시했다. 운영 환경에서 SIGTERM을 받으면 새 요청 수락을 중단하고 이미 처리 중인 요청은 최대 30초까지 마칠
기회를 얻는다. 로컬·AWS Compose의 `stop_grace_period`는 35초로 설정해 Docker의 강제 종료가 Spring의 대기
상한보다 먼저 실행되지 않게 했다.

### 왜 그렇게 했나
기본 즉시 종료에서는 배포·재시작 순간의 요청이 중간에 끊길 수 있다. 반대로 제한 없이 기다리면 종료되지 않는
요청 하나가 배포 전체를 멈출 수 있다. 개인 MVP의 일반 HTTP 요청과 외부 API timeout(최대 45초)을 고려해,
일상 요청에는 충분하면서 배포 지연이 무한정 이어지지 않는 30초를 상한으로 정했다. 45초짜리 AI 호출까지 항상
완료시키겠다는 보장은 아니며, 종료 안정성과 배포 시간 사이의 절충이다.

### 검토한 대안
| 대안 | 왜 채택하지 않았나 |
|---|---|
| 즉시 종료 유지 | 설정은 없지만 배포 중 진행 요청이 끊기는 위험을 그대로 둔다 |
| 무제한 대기 | 멈춘 요청 때문에 프로세스 종료와 다음 배포가 끝나지 않을 수 있다 |
| 종료 상한 60초 이상 | 긴 AI 호출에는 유리하지만 단일 인스턴스 재배포 중 서비스 공백이 더 길어진다 |

### 실제 확인 결과
`@SpringBootTest(webEnvironment = RANDOM_PORT)`로 실제 웹 서버를 띄워 `ServerProperties`와
`LifecycleProperties`에 각각 `GRACEFUL`, `30초`가 바인딩되는지 확인한다. 로컬 서버의 health가 `UP`인 상태에서
애플리케이션 프로세스에 SIGTERM을 보내 Spring의 graceful shutdown 시작·완료 로그도 확인했다. Gradle
`bootRun` 작업은 자식 JVM의 SIGTERM 종료값 143을 실패로 표시하지만, 애플리케이션 로그에서는 graceful 종료가
완료된 뒤 JPA와 HikariCP가 순서대로 닫혔다.

### 실무자가 물어볼 만한 지점
- **"30초 뒤에도 요청이 끝나지 않으면?"**
  → 상한이 지나면 강제 종료된다. 쓰기 API는 DB 트랜잭션 단위로 커밋되므로 중간 커밋을 남기지 않고, 클라이언트는
  거래 요청에 같은 멱등성 키를 사용해 결과를 안전하게 재확인하거나 재시도한다.
- **"로드밸런서 drain도 설정했나?"**
  → 아니다. 현재는 단일 EC2 인스턴스라 애플리케이션 종료 정책만 적용했다. 다중 인스턴스와 로드밸런서를 도입할
  때 deregistration delay와 readiness 전환을 함께 맞춰야 한다.

---

## 3-4. 민감 액션 감사 로그 — 업무 성공과 같은 커밋 경계에 최소 사실만 보존

### 무엇을 했나
비밀번호 변경과 회원 탈퇴가 성공하면 `audit_logs`에 내부 사용자 id(`subject_user_id`), 액션 종류, 생성 시각만
append-only로 남긴다. 비밀번호·이메일·닉네임·요청 본문은 기록하지 않는다. `AuditLogService.record()`는
`Propagation.MANDATORY`라 반드시 `UserAccountService`의 기존 트랜잭션에 참여한다.

회원 탈퇴 감사 기록에는 `users` 외래키를 두지 않았다. 탈퇴 시 사용자의 업무 데이터와 인증 데이터는 모두
삭제하지만, 별도 보존 목적의 최소 감사 기록은 같은 트랜잭션이 커밋된 뒤에도 남는다. 이 예외는 탈퇴 API와
Production Readiness 문서에 명시한다.

### 왜 그렇게 했나
애플리케이션 로그만으로는 보존·조회 정책이 다른 보안 이벤트를 안정적으로 추적하기 어렵다. 그러나 감사 저장을
`REQUIRES_NEW`로 분리하면 뒤의 비밀번호 변경이나 회원 삭제가 롤백돼도 "성공" 기록만 남을 수 있다. 이번 감사
로그는 실패 시도 수집이 아니라 **성공한 상태 변경의 증거**이므로, 업무 변경과 원자적으로 커밋하거나 롤백하는
정책이 맞다.

### 검토한 대안
| 대안 | 왜 채택하지 않았나 |
|---|---|
| 일반 애플리케이션 로그만 사용 | 배포·로그 로테이션과 보존 수명이 묶이고 액션별 조회 계약이 없다 |
| `REQUIRES_NEW`로 감사 기록 선커밋 | 업무 트랜잭션이 실패해도 성공 기록이 남을 수 있다 |
| `users` 외래키 + cascade | 회원 탈퇴와 함께 감사 트레일까지 지워진다 |
| 이메일·IP·User-Agent까지 저장 | 현재 목적에 불필요한 개인정보를 늘리고 별도 접근·보존 정책이 필요하다 |
| 실패 시도까지 같은 테이블에 기록 | 예외 뒤에도 기록을 남기는 별도 트랜잭션·outcome 모델이 필요해 이번 성공 변경 추적 범위를 넘는다 |

### 실제 확인 결과
클래스 단위 `@Transactional`이 없는 `@SpringBootTest`에서 비밀번호 변경 뒤 비밀번호 해시와 감사 row가 함께
커밋되고, 회원 탈퇴 뒤 `users` row는 없어져도 `ACCOUNT_DELETED` row는 남는지 확인한다. 강제로 rollback-only로
끝낸 트랜잭션에서는 감사 row도 남지 않고, 업무 트랜잭션 없이 직접 기록하려 하면
`IllegalTransactionStateException`으로 거부되는지도 확인한다.

실 서버 HTTP 흐름도 `회원가입 201 → 로그인 200 → 비밀번호 변경 204 → 새 비밀번호 로그인 200 → 회원 탈퇴 204
→ 탈퇴 계정 로그인 401` 순서로 재현했다. 감사 row 자체의 커밋·탈퇴 후 보존 여부는 위 통합 테스트가 별도 DB
조회로 확인한다.

### 실무자가 물어볼 만한 지점
- **"탈퇴했는데 user id를 남겨도 되나?"**
  → 로그인 이메일 같은 직접 식별자는 남기지 않지만 내부 id도 보존 데이터이므로 무기한 보존이 정답은 아니다.
  실제 운영 전 법적 요구와 개인정보 처리방침에 맞춰 접근 권한과 보존 기간을 정해야 하며, 현재는 이 항목을
  Production Readiness의 미해결 갭으로 명시했다.
- **"실패한 비밀번호 변경 시도는 왜 없나?"**
  → 이번 테이블은 성공한 민감 변경의 감사 트레일이다. 인증 공격 탐지는 기존 로그인 잠금·rate limit과
  애플리케이션 로그가 담당한다. 실패 이벤트까지 영속화하려면 성공 감사와 다른 커밋 정책을 별도로 설계해야 한다.
- **"감사 로그 조회 API는?"**
  → 없다. 일반 사용자가 조회·수정할 데이터가 아니며 현재는 DB 운영 조회만 전제한다. 관리자 역할과 접근 통제가
  생길 때 read-only 조회 경로를 추가한다.

---

## 3-5. 감사·Trace·평가 기록 보존 — 목적별 기간과 참조 수명을 함께 관리

### 무엇을 했나
`app.retention`에 감사 로그 365일, 일반 Agent Trace 30일, 완료·실패 Live 평가 배치 90일의 운영 기본값을
정했다. 세 기간과 하루인 정리 주기는 환경변수로 바꿀 수 있다. `DataRetentionService`가 매일 오래된 기록을
정리하며, 한 SQL의 id 목록은 최대 500건으로 제한한다. `DATETIME`인 `created_at`은 Spring Data Auditing과
같은 JVM 기본 시간대로 경계를 만들고, `TIMESTAMP`인 평가 `completed_at`은 `Instant`로 비교한다.

부모를 바로 지우지 않고 다음 순서를 같은 트랜잭션에서 지킨다.

```text
만료된 COMPLETED/FAILED 평가 배치: case → batch
만료된 Agent Trace: evaluation result → span → run
만료된 감사 로그: audit log
```

`PENDING`·`RUNNING` 평가 배치는 생성된 지 오래됐더라도 이 작업에서 삭제하지 않는다. 또한 30일이 지난
Trace라도 아직 보존 중인 평가 batch case의 `traceId`가 가리키면 배치 만료까지 남긴다. 만료 배치를 먼저
삭제한 뒤 Trace를 조회하므로, 만료 배치만 참조하던 Trace는 같은 정리 실행에서 제거된다. 전역 기간 조회가
기존 `(user_id, created_at)` 인덱스의 앞 열을 사용할 수 없는 문제를 피하려고 `created_at`,
`(status, completed_at)`, `trace_id` 정리용 인덱스도 V13에 추가했다.
V3의 Agent 테이블은 `utf8mb4_unicode_ci`를 명시했지만 V6의 평가 테이블은 DB 기본 collation을 사용했으므로,
V13에서 평가 case의 `trace_id`도 `utf8mb4_unicode_ci`로 맞춰 서버 기본값과 무관하게 비교되도록 했다.
자식 Repository 삭제는 엔티티를 하나씩 `remove`하지 않고 명시적 bulk delete를 사용한다. MySQL의 평가 case
FK에는 `ON DELETE CASCADE`가 있어 자식 remove가 flush되기 전에 부모 bulk delete가 실행되면 DB가 자식을 먼저
지우고, 뒤늦은 Hibernate remove가 같은 row를 다시 지우며 낙관적 락 예외를 내기 때문이다.

접근 정책은 바꾸지 않았다. 감사 로그는 일반 사용자 API가 없어 운영 DB에서만 조회하고, Agent Trace와 Live
평가 배치는 기존 소유권 조건으로 본인 기록만 조회한다. 회원 탈퇴 때 Trace와 평가 배치는 즉시 삭제되고,
감사 로그만 탈퇴 후에도 365일 기본 보존된다.

### 왜 그렇게 했나
세 기록은 목적과 민감도가 다르다. 감사 로그는 계정 변경 사실을 사후 확인할 기간이 필요하지만 저장 필드가 내부
사용자 id·액션·시각뿐이다. Trace는 질문·답변 원문을 저장하지 않아도 실행 구조와 해시·토큰·참조 id가 쌓이므로
운영 진단에 필요한 짧은 기간만 둔다. Live 평가 배치는 모델·프롬프트 품질 추세를 비교하기 위해 Trace보다 길게
둔다. 다만 배치가 가리키는 상세 Trace를 먼저 지우면 보존된 평가 결과의 조사 경로가 끊기므로 참조 중인 Trace는
함께 연장한다.

365/30/90일은 법령이 정한 값이라고 주장하지 않는 **개인 MVP 운영 기본값**이다. 실제 사용자 대상 운영 전에는
적용 법령, 개인정보 처리방침, 사고 조사 기간과 저장 비용을 검토해 환경변수 값을 확정해야 한다.

### 검토한 대안
| 대안 | 왜 채택하지 않았나 |
|---|---|
| 세 기록을 무기한 보존 | 장애 조사에는 편하지만 목적이 끝난 내부 id와 실행 메타데이터가 계속 쌓인다 |
| 모든 기록에 같은 기간 적용 | 감사, 단기 운영 Trace, 품질 추세 배치의 목적 차이를 반영하지 못한다 |
| 30일에 Trace를 무조건 삭제 | 90일 보존 평가 배치의 `traceId`가 상세 조회 불가능한 끊어진 참조가 된다 |
| 오래된 `RUNNING` 배치도 자동 삭제 | 실제 실행 중인 작업과 중단된 작업을 보존 정리만으로 구분할 수 없다 |
| 한 번의 무제한 `IN` 삭제 | 적체량이 클 때 SQL 크기와 잠금 시간이 불필요하게 커질 수 있다 |
| 자식 엔티티 `remove` 뒤 부모 bulk delete | MySQL `ON DELETE CASCADE`와 flush 순서가 겹치면 같은 자식을 두 번 삭제한다 |
| 감사 로그를 탈퇴 즉시 삭제 | 탈퇴 성공 사실을 별도로 남긴 감사 로그의 목적과 충돌한다 |

### 실제 확인 결과
클래스 단위 `@Transactional`이 없는 `@SpringBootTest`에서 366일/364일 감사 로그, 31일/29일 Trace,
91일/89일 평가 배치와 200일 전에 시작한 `RUNNING` 배치를 실제 H2에 저장했다. 정리 호출이 오래된 완료 기록과
자식 row만 커밋 삭제하고 최근 기록과 진행 중 배치를 남기는지 확인했다. 별도로 31일 된 Trace를 89일 된 평가
배치 case가 참조하게 해, 일반 Trace 기간은 넘었어도 배치와 함께 보존되는지 검증했다.

단위 테스트에서는 주입된 시각과 DB 로컬 시간대 cutoff, 500건 Page, 부모보다 자식을 먼저 삭제하는 호출 순서,
삭제 개수를 고정했다. 전체 `./gradlew clean test` 218개도 통과했다.

격리한 MySQL 8.0에서 V1~V13을 빈 스키마에 적용하고 `mysql,prod` 프로필의 `ddl-auto=validate`로 서버를
기동했다. 이 과정에서 V8/V12가 `family_id`와 `token_hash`를 `CHAR`로 만들었지만 엔티티는 기본 `VARCHAR`로
해석돼 운영 기동이 막히는 기존 드리프트를 발견해 두 엔티티의 `columnDefinition`을 실제 스키마와 맞췄다.
또한 V3/V6의 서로 다른 기본 collation 때문에 `trace_id` 비교가 실패하는 문제와, MySQL의
`ON DELETE CASCADE`가 자식 엔티티 remove와 겹쳐 같은 row를 두 번 삭제하는 문제도 실 서버에서 재현한 뒤
V13 collation 정규화와 bulk delete로 수정했다.

MySQL에 오래된/최근 감사 로그 2건, Trace 2건, 완료·실패·진행 중 배치 3건을 직접 저장하고 정리 주기를 2초로
줄여 실제 Scheduler를 실행했다. 로그의 삭제 결과는 `auditLogs=1, agentTraces=1, evaluationBatches=1`이었고,
DB 재조회도 각각 1/1/2건만 남아 최근 기록과 오래된 `RUNNING` 배치가 보존됐음을 확인했다. 같은 서버의
`/actuator/health`도 `UP`이었다.

### 실무자가 물어볼 만한 지점
- **"왜 365/30/90일인가?"**
  → 감사는 사후 보안 확인, Trace는 단기 장애 진단, 평가는 모델 품질 추세라는 목적 차이를 둔 초기값이다. 법적
  확정값이 아니며 실제 운영 정책이 정해지면 코드 변경 없이 환경변수로 조정한다.
- **"오래 멈춘 RUNNING 배치는 영원히 남나?"**
  → 보존 작업은 실행 중 여부를 추측해 삭제하지 않는다. Worker 재시작 시 중단 작업을 `PENDING`으로 되돌리는
  기존 복구 정책과, 별도의 stuck-job 탐지·실패 전환 정책이 책임져야 한다.
- **"평가 배치 때문에 Trace가 30일보다 오래 남을 수 있나?"**
  → 그렇다. 보존된 배치의 조사 가능성을 지키기 위한 명시적 예외다. 배치가 만료되면 다음 Trace 정리 조회에서
  함께 삭제된다.
- **"삭제 도중 하나가 실패하면?"**
  → 한 번의 정리 실행이 같은 트랜잭션이므로 모두 롤백된다. 다음 스케줄에서 다시 시도하며 반쪽짜리 부모·자식
  상태를 남기지 않는다.

---

## 3-6. 외부 API Circuit Breaker와 출처별 재시도 — 장애 전파와 중복 과금을 함께 제한

### 무엇을 했나
Yahoo Finance, Binance, Upbit, GitHub Releases, NVIDIA NIM에 Resilience4j Circuit Breaker를 적용했다. 출처마다
별도 인스턴스와 상태를 사용하므로 GitHub가 열려도 Yahoo나 Binance 호출은 계속된다. 최근 10개 논리 요청 중
최소 5개가 쌓인 뒤 실패율이 50% 이상이면 30초 동안 호출을 차단하고, half-open에서 2개 요청으로 회복을
확인한다. 상태 전환과 재시도는 출처·횟수·예외 클래스만 로그에 남기고 URL, 요청 본문, API 키는 기록하지 않는다.

멱등인 GET만 네트워크 연결·timeout과 HTTP 5xx에 재시도한다. 사용자 화면의 가격 요청은 지연 상한을 줄이려고
Yahoo/Binance/Upbit 모두 최초 포함 2회, 백그라운드 GitHub 수집은 최초 포함 3회다. 최초 대기는 200ms이고 다음
재시도마다 두 배로 늘어난다. 각 횟수는 출처별 환경변수로 독립 조정할 수 있다. 400/401/403/404 같은 4xx는
재시도하지 않고 차단기 장애율에서도 제외하되, 일시적 제한인 429는 재시도 폭주를 피하려 즉시 실패시키면서
출처 실패로는 집계한다.

NVIDIA NIM Chat Completions POST에는 Circuit Breaker만 적용하고 자동 재시도는 하지 않는다. 제공자가 요청을
처리했지만 응답만 유실된 상황에서 같은 프롬프트를 다시 보내면 사용자에게는 한 번인 작업이 두 번 과금될 수 있기
때문이다. 기존 fallback 계약은 유지해 가격·환율·차트는 last-good 또는 unavailable로 내려가고, GitHub와 NIM은
기존의 안전한 오류 코드로 변환한다. Resilience4j의 Circuit Breaker·Retry 지표는 Micrometer에 등록하며
`/actuator/metrics`는 health와 달리 기존 Security 기본 정책에 따라 인증된 요청만 접근할 수 있다.

### 왜 그렇게 했나
timeout은 느린 호출 한 건의 상한만 정할 뿐, 이미 죽은 제공자를 사용자 요청마다 계속 기다리는 문제는 막지
못한다. 반대로 모든 실패를 같은 방식으로 재시도하면 존재하지 않는 심볼 같은 확정 오류까지 반복하고, NIM은
비용까지 중복될 수 있다. 그래서 **호출 출처**, **멱등성**, **실패 종류**를 함께 분리했다.

Circuit Breaker를 Retry 바깥에 배치했다. 한 사용자의 논리 요청이 내부에서 2~3회 시도돼도 차단기에는 최종 성공
또는 실패 한 건으로 기록된다. Retry를 바깥에 두면 한 번의 요청이 실패율 표본 여러 개를 차지해 차단기가 실제
요청 수보다 빨리 열릴 수 있다.

Java 17을 유지하므로 Resilience4j 2.4.0을 선택했다. 현재 3.x는 Java 21을 요구하므로 라이브러리를 올리기 위해
프로젝트 런타임 전체를 함께 바꾸지 않았다.

### 검토한 대안
| 대안 | 왜 채택하지 않았나 |
|---|---|
| 모든 호출을 같은 Circuit Breaker로 묶기 | 한 제공자의 장애가 정상인 다른 제공자까지 차단한다 |
| `@Retry`·`@CircuitBreaker` annotation 중첩 | proxy 순서가 설정에 숨어 논리 요청 실패가 여러 건으로 집계될 수 있고, private/self 호출에는 적용되지 않는다 |
| 429도 고정 간격으로 즉시 재시도 | `Retry-After`를 해석하지 않은 재시도는 제한을 더 악화시킬 수 있다 |
| NIM POST도 3회 재시도 | 서버 처리 뒤 응답 유실을 구분할 수 없어 중복 토큰 사용·과금 위험이 있다 |
| Resilience4j 3.x | Java 21이 필요해 현재 Java 17 프로젝트 범위를 넘는다 |

### 실제 확인 결과
| 검증 | 결과 |
|---|---|
| GET network/5xx 뒤 성공 | 설정된 출처별 최대 횟수 안에서 재시도한 뒤 정상 응답 반환 |
| GET 404 | 1회만 호출, `SYMBOL_NOT_FOUND` 유지, 차단기 표본에도 넣지 않음 |
| 논리 요청 2건이 각각 내부 3회 실패 | supplier는 6회 실행되지만 차단기 실패는 2건만 기록되고 open |
| GitHub 차단기 open | 추가 supplier 실행 없이 즉시 거부, Yahoo/Binance 차단기는 closed 유지 |
| NIM 5xx | POST 1회만 실행하고 `AI_PROVIDER_UNAVAILABLE` |
| Spring 실제 구성 | 환경변수 binding, 출처별 registry, Micrometer `resilience4j.circuitbreaker.calls`와 `resilience4j.retry.calls` 등록 확인 |

클래스 단위 `@Transactional`이 없는 `@SpringBootTest`로 실제 Spring 설정과 registry/metrics 연결을 확인했다.
외부 Adapter 테스트에서는 Mock HTTP가 5xx 후 200을 반환하도록 해 실제 `RestClient` 경로가 재시도를 통과하는지,
NIM은 5xx여도 두 번째 POST가 발생하지 않는지 검증했다. 실 서버에서는 인증 후 metrics endpoint와 기존
stale/unavailable fallback을 HTTP로 다시 확인했다.

### 실무자가 물어볼 만한 지점
- **"왜 5회 실패가 아니라 50%인가?"**
  → 간헐 장애와 지속 장애를 구분하면서도 최소 5건 전에는 열리지 않게 했다. 현재 개인 MVP 트래픽의 운영
  기본값이며 실제 지표를 보고 조정할 값이다.
- **"half-open 전환은 누가 만드는가?"**
  → 30초 뒤 들어온 다음 호출이 상태를 half-open으로 바꾸고 제한된 2개 호출로 회복을 판단한다. 별도 Scheduler는
  두지 않았다.
- **"재시도하면 화면이 더 느려지지 않나?"**
  → 그렇다. 그래서 2초 timeout인 가격 GET은 최대 2회로 제한했다. Circuit Breaker가 열린 뒤에는 외부 대기 없이
  기존 last-good fallback으로 바로 내려간다.
- **"왜 429를 재시도하지 않는데 실패로 세나?"**
  → 제한 중인 출처에 즉시 반복 요청하지 않으면서, 연속 제한 시에는 차단기를 열어 추가 호출 자체를 줄이기
  위해서다. 향후 `Retry-After`를 신뢰할 수 있는 공식 API로 전환하면 source adapter 정책을 따로 확장한다.

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

### 불확실성 보존 원칙 (Unknown Preservation)

> 시스템이 모르는 외부 사실은 `null` 또는 `stale`로 보존한다. 필수 계산을 확정할 수 없다면 그럴듯한 숫자를
> 만들지 않고 입력을 중단한다.

판정 기준은 다음과 같다.

1. 모델이 `UNKNOWN`을 `null` 또는 명시적 상태로 표현할 수 있는가?
2. 그 상태에서도 해당 작업의 도메인 불변식이 유지되는가?
3. 파생 계산이 `0`, `1`, 현재값 같은 대체값을 임의로 만들지 않는가?
4. API와 화면도 불확실성을 그대로 노출하는가?
5. 이후 값을 확보해 복구할 경로가 있는가?

`currentPrice`는 null로 등록하고 이후 조회로 복구할 수 있으므로 외부 장애 중에도 자산 등록을 허용한다. 반면
BUY/SELL의 거래 환율은 원화 평단과 실현손익을 확정하는 필수값이므로 모르면 중단한다. 과거 외화 거래는
`exchangeRateMode=MANUAL`과 당시 환율을 모두 요구하고, AUTO에 현재 환율 숫자를 함께 보내도 거부한다.

1단계에서 오늘 AUTO 거래는 Asset에 저장된 현재 환율만 사용한다. 값이 없으면 수동 입력을 안내하는 400으로
안전하게 실패한다. 대시보드 선행 의존을 없애는 전역 FX 통합은 거래 트랜잭션 경계 분리와 함께 후속 작업으로 둔다.

현재 거래 경로는 `FxRateQueryService`를 호출하지 않으므로 트랜잭션 안에서 외부 HTTP가 실행되는 위반은 없다.
후속 전역 FX 통합 때는 외부 조회를 먼저 끝낸 뒤 짧은 쓰기 트랜잭션에 확정값만 전달하고, 실제 HTTP 진입점에서
활성 트랜잭션이 없음을 단언해 이 규칙을 코드로 강제한다.

### 무엇을 했나
- 시세 조회 실패 시 **예외를 던지지 않는다.** 만료된 캐시 → `assets.current_price` 순으로 폴백한다.
- 폴백을 쓰고 있으면 응답에 `priceStale: true` + `priceUpdatedAt`을 실어 화면에 "N분 전 기준"을 표시한다.
- 현재가를 한 번도 확보하지 못한 자산은 `valuationKRW: null`로 응답하고, 총 투자자산도 잘못된 부분합 대신
  `null`로 응답해 화면에 `-`로 표시한다.

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
- **"한 자산의 평가가 불가능하면 나머지 자산의 부분합이라도 보여주는 편이 낫지 않나?"**
  → 부분합을 총 투자자산처럼 표시하면 숫자는 정밀해 보여도 의미가 틀린다. 행별 확보 값은 그대로 보여주되 총액과
  Daily PnL은 `-`로 표시하고, 시세/환율 경고를 함께 노출하는 쪽을 택했다.

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

### 6-4. 오입력 정정 — append-only를 깨야 했던 이유 ★★

**문제**: 실수는 **거래 단위**로 일어나는데("수량을 0.5 대신 5로 입력"), PRD가 제공하는 정정 수단은 **자산 단위**밖에 없다. 층위가 맞지 않는다.

없는 채로 쓸 수 있었던 두 방법 모두 성립하지 않는다.

| 시도 | 왜 안 되나 |
|---|---|
| 자산을 삭제하고 다시 등록 | **초기화가 안 된다.** §6-2의 복구 정책 때문에 옛 수량·평단가·실현손익이 그대로 돌아온다. 실측: 삭제 전후 모두 `quantity 0.6 / avgPrice 57,956,250 / realizedPnl 3,920,750`. 게다가 거래 1건 고치려고 자산을 지우면 정상 거래까지 함께 잃는다 |
| 반대 매매를 입력해 0으로 만들기 | **데이터를 오염시킨다.** 매도는 실현손익을 확정하는 행위라 `realizedPnl`에 가짜 손익이 영구히 박힌다. `avgPrice`도 초기화되지 않고(수량 0이어도 유지), 하지 않은 거래가 이력에 남는다. 없던 일로 만드는 게 아니라 거짓말을 하나 더 추가하는 셈 |

**결정**: `DELETE /api/assets/{id}/transactions/{txId}` — 거래를 지우고 남은 이력을 재생(replay)해 상태를 다시 계산한다.

PRD 1장은 Transaction을 append-only 이벤트 로그로 정의했고 이 기능은 거기서 벗어난다. **append-only는 시스템이 생성하는 이벤트에는 맞지만, 사람이 손으로 입력하는 이벤트에는 오타 정정 경로가 반드시 필요하다.**

**왜 "빼기"가 아니라 "다시 접기"인가**: 매도의 실현손익은 *그 시점의 평단가*에 의존한다. 중간 거래 하나가 사라지면 그 이후 모든 계산의 전제가 바뀌므로, 삭제된 거래의 영향만 역산해 빼는 것은 성립하지 않는다.

이 메서드의 존재가 곧 설계상의 답이다 — **Asset의 거래 상태는 Transaction 이력의 파생값이고, 필드는 매번 재계산하지 않기 위한 스냅샷이다.** 평상시엔 증분 갱신으로 비용을 아끼고, 이력이 바뀐 순간에만 전체를 다시 접는다.

**검토한 대안 (회계식 역분개)**: 원본을 남기고 반대 거래를 자동 생성. 감사 추적은 완벽하지만 `realizedPnl`이 오염되고 사용자가 하지 않은 거래가 화면에 보인다. 은행·회계 시스템이면 이쪽이지만 개인 자산 앱에는 과하다.

**삭제하면 이력이 깨지는 경우**: 매수 10 → 매도 5 상태에서 매수를 지우면 매도할 수량이 없어진다. 데이터를 음수로 망가뜨리는 대신 `400 TRANSACTION_DELETE_BREAKS_HISTORY`로 거부하고, 트랜잭션 전체를 롤백한다.

**최초 수량 오입력은 거래 삭제로 고칠 수 없다**: 자산 등록의 `quantity`는 Transaction이 아니라 replay의
시작 상태이므로 지울 행이 없다. 그렇다고 1개를 매도하면 실현손익과 대기자금에 가짜 변화가 생긴다. 자산 상세의
`보유 수량 정정`은 사용자가 확인한 최종 수량을 받아 그 차이만큼 `initialQuantity`를 보정하고 기존 거래를 다시
재생한다. 신규 거래·정산은 만들지 않으며, 과거 매도 시점의 수량이 음수가 되는 정정은 거부한다. 잘못 입력한
BUY/SELL은 기존 거래 삭제를 사용한다는 경계도 화면에서 함께 안내한다.

수량 정정이 이미 생성된 오늘 Snapshot 뒤에 일어나면, 총액 Snapshot만으로는 정정된 1 ZEC의 기준 시점 가치를
복원할 수 없다. 이를 오늘 시장 손실로 꾸미지 않고 해당 기준일의 Daily PnL을 `unavailable`로 표시한다. 다음
09:00 KST Snapshot부터는 정정된 수량이 기준값에 포함되어 자동으로 정상 계산된다.

### 6-5. 재생 순서를 정하다가 발견한 기존 버그 ★★

재생을 `tradedAt` 순으로 하기로 정하고 나니, **거래 생성 경로가 입력 순서로 계산하고 있다는 것**이 드러났다. 같은 이력인데 삭제를 했느냐 안 했느냐에 따라 숫자가 달라지는 상황이었다.

실측:

```
08-01 매수 10@1,500 → 08-03 매도 5@2,000 → (뒤늦게) 08-02 매수 5@1,600 입력

  입력순 증분 계산 → avgPrice 1,550.00000000   realizedPnl 2,500.00
  tradedAt 순 재생 → avgPrice 1,533.33333333   realizedPnl 2,333.33333335  ← 이쪽이 맞다
```

`tradedAt`을 "기록만 하는 필드"로 두면 과거 날짜 거래를 뒤늦게 입력할 때 평단가가 조용히 틀린다. PRD는 이 경우를 다루지 않았다.

**수정**: 거래 생성 시 이력의 **맨 뒤에 붙는지 중간에 끼는지**를 판별해 분기한다.

- 맨 뒤(일반적인 경우) → 증분 계산 (기존과 동일, 추가 비용 없음)
- 중간(과거 시점 거래) → 저장 후 전체를 `tradedAt` 순으로 재생

판별은 `existsByAssetIdAndTradedAtGreaterThan` 한 번의 존재 확인이면 되므로, 정상 경로의 비용은 거의 그대로다.

### 6-6. `name`을 선택 입력으로

PRD 4-8 표는 `name`을 "빈 문자열 불가"라 하고, PRD 5장은 "표시 이름은 **선택 입력**, 비워두면 심볼을 사용"이라 한다. 서로 어긋난다.

→ 5장(화면 흐름)을 따랐다. 사용자 입력 부담을 줄이는 쪽이 의도에 가깝다고 판단했다. 값을 주면 100자 제한은 그대로 검증한다.

### 6-7. Portfolio "전체"의 범위

PRD 4-5는 "`type` 생략 시 전체 조회"라 하고, PortfolioView 정의는 "STOCK+CRYPTO 필터링"이라 한다.

→ 와이어프레임 탭이 `[전체][CRYPTO][STOCK]`이므로 **"전체" = 투자 자산 전체**로 해석했다. `?type=CASH`는 `400 INVALID_INPUT`으로 거부한다.

### 6-8. 자산 타입과 거래 종류의 불일치

PRD는 "deposit/withdraw는 CASH/BANK 전용"이라고만 하고 위반 시 에러 코드를 정하지 않았다.

→ `400 INVALID_INPUT`으로 처리한다. 검증은 Service가 아니라 **Asset 도메인 메서드 안**에 두었다 — 어느 경로로 들어와도 막힌다.

### 6-9. PRD가 정하지 않은 에러 코드 추가

PRD 4-7 표에 없는 상황을 위해 추가했다.
- `DUPLICATE_EMAIL` (409)
- `INVALID_CREDENTIALS` (401) — **미가입 이메일과 비밀번호 불일치를 같은 코드로 응답**한다. 구분하면 계정 존재 여부가 새어나간다(4-0 규칙 3과 같은 논리).
- `TRANSACTION_NOT_FOUND` (404), `TRANSACTION_DELETE_BREAKS_HISTORY` (400) — §6-4의 거래 삭제용
- `METHOD_NOT_ALLOWED` (405), `NOT_FOUND` (404) — 지원하지 않는 메서드/경로. 없으면 전역 핸들러가 500으로 응답해 클라이언트 실수를 서버 오류로 보고하게 된다

### 6-10. Spring Boot 버전

초기 `build.gradle`이 Spring Boot 4.1.0이었으나 지시된 스택(3.x)에 맞춰 **3.5.9**로 내렸다. springdoc-openapi가 Boot 4를 아직 안정 지원하지 않는 것도 이유다.

---

## 7. 도메인 판단이 필요해 사용자에게 물어본 것

PRD와 사용자 피드백을 바탕으로 확정한 주요 결정.

| 질문 | 결정 | 근거 |
|---|---|---|
| DB | MySQL 8(Docker) + H2 로컬 프로필 | PRD ERD 타입과 일치. Docker가 죽어도 데모 경로 확보 |
| 매수·매도 시 대기자금 이동? | **동일 통화 CASH/BANK와 정산** | BUY/SELL은 Portfolio 내부 이동이므로 총 투자자산이 사라지거나 늘어나면 안 됨 |
| 정산 대기자금을 매번 등록? | **KRW/USD/USDT CASH를 0으로 자동 준비** | 첫 매도를 기록하기 전에 같은 통화 자산을 또 만드는 UX 마찰 제거 |
| Soft Delete 후 같은 심볼 재등록 | 기존 row 복구 | §6-2 |
| 삭제 자산의 realizedPnl | 손익 합계에만 포함 | §6-3 |

기본 대기자금만 있고 잔액이 모두 0이어도 Dashboard에 KRW/USD/USDT 0 잔액을 보여준다. 다만 실제 Portfolio
내용이 생기기 전에는 Daily Snapshot을 만들지 않는다.
정산 연결은 `Transaction.settlementAssetId/settlementAmount`에 함께 남긴다. 별도의 DEPOSIT/WITHDRAW로 기록하지
않아 Daily PnL의 외부 자금 흐름에 섞이지 않으며, 거래 삭제 시 대응 대기자금 이동도 함께 되돌린다.

---

## 8. 하지 않은 것과 그 이유

우선순위상 잘라낸 것들. **모르고 빠뜨린 게 아니라 알고 자른 것**이다.

| 항목 | 상태 | 이유 / 대안 |
|---|---|---|
| **자동화 테스트** | 추가 | 초기 Position, 평단 미입력, 수량 정정, 기본 대기자금, 거래 replay, Daily PnL 수식을 JUnit으로 검증하고 `scripts/verify.sh` 49개 assertion도 유지한다 |
| **시세 병렬 조회** | 순차 | 미스가 N건이면 최대 2N초. 심볼 수가 적은 개인 사용에서는 문제되지 않지만, `CompletableFuture`로 병렬화하는 것이 다음 단계다 |
| **낙관적 락 자동 재시도** | 없음 | 409로 정합성을 지킨다. 중복 요청을 성공시키지 않도록 멱등성 키와 함께 검토 |
| **거래의 전역 FX 자동 폴백** | 후속 | 현재는 저장된 환율이 없으면 수동 입력. TransactionExecutor로 외부 조회와 쓰기 TX를 분리한 뒤 통합 |
| **평가금액 null 자산의 총자산 표기** | 전체 합계를 `null`로 표시 | 부분합을 총 투자자산으로 오인하지 않게 한다 (§5) |
| **Javadoc** | 전면 적용 | 공개 API 전부에 작성했다 (PRD 코드 작성 규칙) |
| **자산 상세 7일 가격 차트** | 추가 | Binance/Yahoo 일봉을 상세에서만 조회하며 15분 캐시와 마지막 성공값 폴백 적용 |
| **Asset Analysis 순자산 차트** | 추가 | Daily PnL Snapshot + 현재값으로 1D/7D/30D/90D 실제 총액 추이를 표시. 입출금이 섞이므로 수익률로 부르지 않음 |
| **전체 거래내역 / 거래 결과 미리보기** | 추가 | Asset별 이력을 화면에서 합쳐 검색·필터하고, 저장 전 보유량·정산 잔액·예상 손익을 설명한다 |
| **국내주식 검색 자동완성** | 추가 | KRX KIND KOSPI/KOSDAQ 2,595개 정적 스냅샷을 로컬 검색. 실패 시 직접 입력 유지 |
| **TWR/MWR / 종목 Allocation / 해외주식·코인 자동완성** | 미구현 | 정교한 장기 성과와 확장 기능은 Roadmap으로 분류 |

---

## 9. 패키지 구조 — PRD 6장과의 차이

PRD의 Asset Aggregate 구조는 유지하고, 외부 데이터와 Snapshot 책임을 작은 서비스로 추가했다.

| 추가 클래스 | 위치 | 왜 필요했나 |
|---|---|---|
| `AssetRegistrar` | `domain.asset.service` | 자산 등록은 외부 HTTP(심볼 검증) 후 저장이다. 저장만 트랜잭션에 넣으려면 별도 빈이어야 프록시가 적용된다 |
| `PriceRefreshService` | `domain.asset.service` | 시세 반영을 `REQUIRES_NEW`로 격리 |
| `PortfolioSort` | `domain.asset.service` | 정렬 대상(평가손익)이 **DB 컬럼이 아니라 조회 시점 계산값**이라 SQL 정렬이 불가능하다. `Pageable` 대신 허용 필드만 여는 파서를 두어, 잘못된 필드가 500이 아니라 400이 되게 했다 |
| `FxRateQueryService` | `infra.price.fx` | USD/USDT 환율 캐시·동시 호출 차단·마지막 성공값 폴백을 가격 조회와 분리 |
| `DefaultSettlementAssetProvisioner` | `domain.asset.service` | 신규·기존 사용자에게 KRW/USD/USDT 기본 대기자금을 빠진 통화만 자동 생성 |
| `PriceHistoryQueryService` | `infra.price.history` | 자산 상세의 7일 일봉을 조회하며 실패 시 마지막 성공 차트를 유지 |
| `PortfolioHistoryService` | `dashboard.snapshot` | Asset Analysis가 최대 365일의 Portfolio Snapshot을 사용자 범위 안에서 조회하도록 제공 |
| `DailyPnlService` | `dashboard.snapshot` | Portfolio Snapshot과 외부 입출금으로 오늘 손익을 계산 |
| `PortfolioSnapshotWriter` | `dashboard.snapshot` | 첫 기준점 생성을 짧은 독립 트랜잭션으로 격리 |

그 외 `SymbolKey`, `PriceQuote`, `PriceProperties`는 값 타입(record)이라 구조 변경으로 보지 않는다.

---

## 10. 발표에서 먼저 꺼낼 이야기

1. **"PRD 8-1이 경고한 리스크가 개발 중에 실제로 터졌고, 그게 설계의 빈틈을 찾아줬다"** (§6-1)
   — 실패에는 두 종류가 아니라 세 종류가 있었다.
2. **"Soft Delete를 도입한 목적을 Soft Delete 조회 규칙이 무력화하고 있었다"** (§6-3)
3. **"캡슐화를 지키려고 낙관적 락을 골랐고, 8건 동시 요청으로 실제로 측정했다"** (§3)
4. **"BUY/SELL 정산을 동일 통화 대기자금과 연결해 Portfolio 내부 이동으로 만들었다"** (§7)
   — DEPOSIT/WITHDRAW만 Daily PnL의 외부 자금 흐름으로 센다.
5. **"오입력 정정을 넣으려다 append-only 원칙을 깨야 했고, 그 과정에서 기존 버그를 하나 찾았다"** (§6-4, §6-5)
   — `tradedAt`을 기록용으로만 두면 과거 날짜 거래 입력 시 평단가가 조용히 틀린다.
6. **"단위 테스트와 실행 가능한 HTTP 검증 스크립트를 함께 남겼다"** (§8)
