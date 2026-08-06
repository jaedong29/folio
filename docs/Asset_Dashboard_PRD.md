# Asset Dashboard - Project Direction Document

> 이 문서는 Claude Code에게 전달하는 프로젝트 설계 문서입니다.
> 구현 시 아래 원칙과 결정 사항을 반드시 따르며, 문서에 명시되지 않은 부분은 임의로 판단하지 말고 질문할 것.

---

## 코드 작성 규칙 (필독)

- **모든 주석은 Google Java Style Guide의 Javadoc 규칙을 따른다.**
  - 클래스, 메서드, 필드 등 공개(public) API에는 Javadoc(`/** ... */`)을 작성한다.
  - 첫 문장은 요약(summary fragment)으로 작성하고 마침표로 끝낸다.
  - `@param`, `@return`, `@throws` 태그를 필요한 경우 빠짐없이 작성한다.
  - 구현 세부사항이나 왜(why)에 대한 설명이 필요한 경우 라인 주석(`//`)을 보조적으로 사용한다.
  - 참고: https://google.github.io/styleguide/javaguide.html#s7-javadoc

예시:
```java
/**
 * 자산에 매수 거래를 반영하고 평균 매입 단가를 재계산한다.
 *
 * @param quantity 매수 수량
 * @param price 매수 가격 (원래 통화 기준)
 * @param exchangeRate 매수 시점의 환율 (원/통화)
 * @throws IllegalArgumentException quantity 또는 price가 0 이하인 경우
 */
public void buy(BigDecimal quantity, BigDecimal price, BigDecimal exchangeRate) {
    // ...
}
```

---

## 1. 프로젝트 목표

### 프로젝트명
**Asset Dashboard**

### 한 줄 소개
> 여러 곳에 흩어진 개인 자산(현금, 주식, 암호화폐)을 한 화면에서 관리하고 투자 현황을 빠르게 확인할 수 있는 Personal Asset Dashboard

### 핵심 철학
- 이 서비스는 **기록(Recording)이 아니라 조회(Overview)**가 목적이다.
- 사용자가 서비스를 실행했을 때 가장 먼저 알고 싶은 정보는 **"오늘 내가 얼마를 가지고 있는가"**이다.
- 따라서 첫 화면은 거래 입력이나 현금 관리가 아니라 **Investment Portfolio**를 중심으로 구성한다.
- Cash와 Bank는 자산의 일부이지만, 사용 빈도와 사용자 목적을 고려하여 **요약 정보**로 제공한다.
- **Asset**은 유일한 Aggregate Root이며, Dashboard·Portfolio·Allocation은 모두 Asset을 다양한 관점에서 보여주는 조회(View)이다.
- Transaction은 별도 Aggregate가 아니라 Asset에 종속된 이벤트 기록 Entity로 설계한다. Transaction 단독으로는 조회/변경의 시작점이 되지 않는다.

> **Aggregate 경계에 대한 실용적 절충 (명시)**: 엄밀한 DDD에서는 비-루트 엔티티를 루트의 Repository를 통해서만 영속화한다. 이 프로젝트는 그 원칙을 **부분적으로만** 따른다.
> - **따르는 것**: Asset의 상태 변경은 **반드시** Asset의 도메인 메서드를 통해서만 일어난다. Transaction을 조회/변경의 진입점으로 삼는 API는 만들지 않는다(`GET /api/transactions` 없음). Transaction API는 항상 `/assets/{id}/transactions/*` 형태로 Asset에 종속된다.
> - **따르지 않는 것**: 구현 편의를 위해 `TransactionRepository`를 별도로 두고 Transaction을 직접 `save()`한다. Asset의 컬렉션으로 cascade 저장하지 않는다.
> - **이유**: Transaction은 append-only 이벤트 로그이고 건수가 무한히 증가한다. Asset의 컬렉션(`@OneToMany`)으로 묶으면 Asset을 로드할 때마다 거래 내역 전체가 따라올 위험이 있고, 이를 막기 위한 지연 로딩·페이징 처리가 오히려 복잡도를 키운다. "변경 진입점 통제"라는 Aggregate의 본질적 이점은 위 규칙으로 이미 확보되므로, 영속화 경로까지 강제하지 않는다.

### 레퍼런스에 대한 태도
DeBank, Zerion 등을 그대로 따라 만들지 않는다. 대신 배치와 구조의 **이유**를 계속 질문한다.
- 왜 Portfolio가 첫 화면일까? → Wallet List가 첫 화면이 아닌 이유는?
- 왜 거래내역은 맨 아래에 있을까?
- 왜 카드는 이런 형태일까?

레퍼런스는 결론이 아니라 질문의 출발점으로 사용한다.

### 용어 사전 (Glossary)

**Unrealized PNL (평가손익)**
- 아직 보유 중인 수량에 대한 손익.
- 계산식: `평가금액(quantity × currentPrice × exchangeRate) - 매입금액(quantity × avgPrice)`
- Snapshot 없이 현재 Asset 상태만으로 계산되며, Portfolio 화면에 포함된다.

**Realized PNL (실현손익)**
- 이미 매도한 수량에서 확정된 손익. Asset의 `realizedPnl` 필드에 **누적**된다.
- 매도 시 가산식: `realizedPnl += (매도가 × 매도시점환율 - avgPrice) × 매도수량`
- **누적 합계만 유지하며, 기간별로 쪼개서 보지 않는다**(그건 Analytics이므로 Roadmap).
- 이 필드가 없으면 전량 매도한 자산의 수익 기록이 시스템에서 사라진다. 필드 하나로 "지금까지 얼마 벌었나"에 답할 수 있으므로 MVP에 포함한다.

**총 손익 = Unrealized PNL + Realized PNL**
- Portfolio·Dashboard는 두 값을 **분리해서** 노출한다(합쳐서 하나로 보여주지 않는다).

**Historical PNL — 제외**
- 기간별 변화(예: 7일 전 대비 손익)는 포함하지 않는다. Snapshot이 필요하므로 Analytics(Roadmap).

**환차손익(FX PNL) 분리 — 의도적 제외**
- `avgPrice`는 매수 시점 환율로 환산된 KRW 값이고, 평가금액은 **현재** 환율로 계산된다. 따라서 이 프로젝트의 PNL에는 **주가 변동으로 인한 손익과 환율 변동으로 인한 손익이 섞여 있다.**
- 이를 분리하려면 원통화 기준 평단가(`avgPriceInOriginalCurrency`)를 함께 관리해야 하는데, MVP에서는 "총 원화 기준 손익"만 보여주면 충분하다고 판단해 **인지한 상태로 제외**한다.
- 확장 시: Asset에 `avgPriceOriginal` 컬럼을 추가하고 매수 시 함께 갱신하면, `환차손익 = quantity × avgPriceOriginal × (현재환율 - 매수시점가중평균환율)`로 분리 가능.

**Analytics**
- 이 프로젝트에서 Analytics는 **시계열 기반 분석 기능**(자산 성장 그래프, 기간별 수익률, 최고 수익률 기록 등)을 의미한다.
- AssetSnapshot, Scheduler, Batch가 필요한 기능이며, MVP에서 제외한다.

**History**
- Transaction History(거래 내역 목록)만 포함한다.
- Asset의 시간에 따른 상태 변화 이력(스냅샷)은 포함하지 않는다.

### MVP 범위

**포함 (Do)**
- JWT 로그인 / 회원가입
- Asset CRUD
- Transaction CRUD (입금/출금/매수/매도)
- Dashboard (Facade 조립)
- Portfolio (View, Unrealized PNL 포함)
- Asset Allocation (Pie Chart, type 단위)
- **자동 시세 조회**
  - STOCK: Yahoo Finance 비공식 API (국내 `.KS`/`.KQ`, 해외 그대로)
  - CRYPTO: Binance API(USDT 기준 가격) × Upbit KRW-USDT 마켓(환율) — 아래 참고
  - 공통: **symbol 단위 전역 캐시(15분 TTL)**, 타임아웃 2초, 실패 시 마지막 저장값 → 수동 입력 폴백

**제외 (Don't) — Roadmap으로 이동**
- Analytics (Historical PNL, 월별 변화, History Snapshot, Scheduler, Batch)
- **유료/공식 시세 API 전환** (TwelveData 등 — Yahoo Finance 프로바이더를 인터페이스로 교체하는 수준의 확장)
- 해외주식(STOCK)의 환율 자동 조회 (계속 수동 입력 — CRYPTO의 환율 자동화와는 별개 문제, 아래 참고)
- WalletConnect / Web3 Wallet 연동 (계좌 자체를 자동 동기화하는 것 — 시세 조회와는 다른 문제)
- MyData / 증권사 API 연동
- 전체 거래 내역 화면
- 종목 단위 Allocation
- Asset 검색 자동완성

> **자동 시세 조회를 MVP에 포함한 이유**: 이 프로젝트의 출발점은 "여러 거래소/증권사 앱을 매일 아침 돌아다니며 확인하는 번거로움"을 없애는 것이었다. 자산 수량은 한 번만 입력하면 되지만(Transaction), 가격은 매일 바뀌므로 수동 입력으로는 이 문제를 해결하지 못한다. 따라서 시세 자동 조회는 부가 기능이 아니라 핵심 가치와 직결된 기능으로 판단해 MVP에 포함한다. 다만 계좌 자체의 자동 동기화(WalletConnect, MyData)는 별도 인증/보안 인프라가 필요한 무거운 작업이라 계속 Roadmap에 남긴다.
>
> **CRYPTO 가격 계산 방식**: 거래소별 개별 시세(Upbit 등)를 직접 쓰지 않는다. 코인마다 거래소 간 입출금 제한 등으로 가격이 비정상적으로 벌어지는 경우가 있어, 특정 거래소 가격을 그대로 신뢰하는 것은 위험하다고 판단했다. 대신 `Binance의 USDT 기준 가격 × Upbit의 KRW-USDT 환율`로 계산한다. 이렇게 하면 모든 코인에 동일한 계산 방식이 적용되어 일관성이 유지되고, 특정 코인 자체의 가격 왜곡에 흔들리지 않는다.

### 성공 기준

**사용자 관점**
- 로그인 후 5초 안에 자신의 총 자산, Investment 비중, 최근 거래를 확인할 수 있어야 한다.

**설계 관점**
- Asset을 유일한 Aggregate Root로 설계하고, Dashboard·Portfolio·Allocation은 모두 Asset을 기반으로 한 조회(View)로 구현한다.
- Asset의 상태 변경은 Asset의 도메인 메서드를 통해서만 이루어지며, Service 계층은 상태를 직접 변경하지 않는다.

**보안 관점 (필수)**
- **다른 사용자의 Asset에 어떤 방식으로도 접근할 수 없어야 한다.** 인증(로그인 여부)과 인가(소유권)는 별개 문제이며, 둘 다 충족해야 한다.
- 자산 데이터를 다루는 서비스이므로, 이 항목이 깨지면 나머지 설계의 완성도와 무관하게 실패로 간주한다.

**구현 관점**
- Asset과 Transaction의 실제 데이터를 기반으로 Dashboard가 동작하며, Mock 데이터 없이 핵심 기능이 연결되어야 한다.
- 사용자 요청 처리 경로(트랜잭션 내부)에서 외부 API를 동기 호출하지 않는다. 외부 시세 조회는 DB 트랜잭션 밖에서 수행한다.

### 발표에서 받고 싶은 피드백
- 도메인 모델링이 적절한가? (Asset을 Aggregate Root로 가져가는 것이 적절한가)
- Asset 상태 변경 로직을 엔티티 내부 도메인 메서드로 캡슐화하는 것과 Service 계층에서 처리하는 것 중 유지보수 관점에서 어느 쪽이 나은가?
  - 관련: 캡슐화를 지키기 위해 원자적 UPDATE 대신 낙관적 락을 택했는데(6장 참고), 실무에서도 이 트레이드오프가 타당한가?
- 조회 API가 외부 시세를 갱신하는 구조(GET의 부수효과)를 어디까지 허용하는가? 처음부터 Scheduler로 가는 것이 맞았을까?
- Dashboard 중심 구조가 실제 서비스에서도 적절한가?
- 향후 MyData/Web3를 붙인다면 어떤 구조로 확장하는 것이 좋은가?

---

## 2. 도메인 모델 (User, Asset, Transaction)

### User
**책임**: 인증의 주체. Asset을 소유한다.

| 필드 | 타입 | 제약 |
|---|---|---|
| id | Long | PK |
| email | String | unique, not null |
| password | String | not null (암호화 저장) |
| nickname | String | not null |
| createdAt | LocalDateTime | not null |

도메인 메서드: 없음 (인증 로직은 Service/Security 계층에서 처리)

---

### Asset (Aggregate Root)
**책임**: 사용자가 보유한 자산 하나. 수량·평단가·현재가 등 상태를 스스로 관리한다.

| 필드 | 타입 | 제약 |
|---|---|---|
| id | Long | PK |
| userId | Long | FK 값 (객체 참조 아님), not null |
| type | Enum | CASH, BANK, STOCK, CRYPTO |
| symbol | String | not null — **시세 조회에 사용하는 불변 키.** `BTC`, `NVDA`, `000660.KS`. CASH/BANK는 통화 코드(`KRW`, `USD`)를 넣는다. **등록 후 수정 불가** |
| name | String | not null — **화면 표시용 이름.** 사용자가 자유롭게 수정 가능(`내 비트코인`, `KB국민은행`). 시세 조회에 절대 사용하지 않음 |
| quantity | BigDecimal | not null, default 0 — **보유 수량.** CASH/BANK는 "보유 금액"이 곧 수량이며, `currentPrice = 1`로 두어 평가금액 공식이 모든 타입에 동일하게 적용된다 |
| avgPrice | BigDecimal | nullable — **항상 KRW 기준** (매수 시점 환율로 환산된 값). CASH/BANK는 1 |
| currentPrice | BigDecimal | nullable — **원래 통화 기준** (USD, USDT 등 그대로). **CASH/BANK는 항상 1로 고정** |
| currency | String | not null — currentPrice가 어떤 통화인지 표시용 |
| exchangeRate | BigDecimal | not null, default 1 — **현재** 환율. **CRYPTO는 Upbit KRW-USDT 마켓에서 자동 조회**, **해외주식은 수동 입력**, **국내주식·원화현금은 1로 고정**(이미 KRW) |
| realizedPnl | BigDecimal | not null, default 0 — **누적 실현손익(KRW).** 매도 시에만 증감 |
| source | Enum | MANUAL, CSV, API, MYDATA, WEB3 — STOCK/CRYPTO는 API(자동 조회 성공 시), 나머지는 MANUAL |
| priceUpdatedAt | LocalDateTime | nullable — currentPrice가 마지막으로 갱신된 시각 |
| version | Long | not null — **낙관적 락(`@Version`).** 동시 거래로 인한 갱신 유실 방지 |
| createdAt | LocalDateTime | not null |
| updatedAt | LocalDateTime | not null |

> **symbol / name 분리 이유**: 이전 설계에서는 `name` 하나가 표시명과 시세 조회 키를 겸했다. 이 경우 사용자가 이름을 `내 하이닉스`로 바꾸는 순간 시세 조회가 조용히 깨지고, 화면에는 `000660.KS`라는 기계적 문자열이 그대로 노출된다. 두 값은 **변경 주기와 사용 주체가 완전히 다르므로** 분리한다.
>
> **CASH/BANK에 `currentPrice = 1`을 넣는 이유**: 이렇게 하면 평가금액이 `quantity × currentPrice × exchangeRate` **한 공식으로 모든 타입에 적용**된다. 타입별 분기(`if (type == CASH) ...`)가 도메인 메서드에서 사라지고, 앞으로 추가되는 모든 계산 로직에서도 분기가 번식하지 않는다. "타입에 따라 의미가 다르니 주석에 명시하자"는 접근은 결함을 문서로 덮는 것에 불과하다.
>
> **`version`(낙관적 락) 이유**: 도메인 메서드 + JPA 더티 체킹은 "읽고-계산하고-쓰기" 구조라, 같은 Asset에 매수 요청이 거의 동시에 두 번 들어오면 하나가 유실된다(Lost Update). `@Version` 한 줄로 두 번째 커밋이 `OptimisticLockException`으로 실패하게 만들고, 클라이언트에 409로 응답한다. 도메인 메서드 캡슐화를 포기하지 않으면서 정합성을 지키는 가장 저렴한 방법이다.
>
> **`realizedPnl`을 Asset에 두는 이유**: 매도 시 손익을 어디에도 남기지 않으면, 전량 매도한 자산의 수익 기록이 시스템에서 사라진다(`quantity = 0`이 되고 평가손익도 0). Transaction 로그에 매도가는 남지만 이를 집계하는 기능(Analytics)은 Roadmap이므로, "지금까지 얼마 벌었나"에 답할 수 없게 된다. 컬럼 1개 + 매도 시 덧셈 1줄로 해결되는 문제다.

> **자동 시세 조회 정책 — 캐시는 Asset이 아니라 symbol에 붙는다**
>
> 시세는 **사용자별 데이터가 아니라 전역 사실**이다. 따라서 `assets.current_price`를 TTL 판단 기준으로 삼지 않고, `(type, symbol)`을 키로 하는 **전역 캐시(`PriceCache`)**를 별도로 둔다.
>
> - 사용자 100명이 BTC를 보유해도 BTC 시세는 15분에 **1번만** 외부에서 조회된다. (Asset row별로 TTL을 관리하면 100번 조회하게 된다)
> - `assets.current_price`는 캐시 갱신의 **결과가 반영되는 스냅샷** 겸, 외부 조회가 계속 실패할 때 쓰는 **마지막 폴백값**으로만 유지한다.
>
> **조회 흐름 (Portfolio / Dashboard)**
> ```
> 1. [TX 시작] 사용자 Asset 목록 조회 → [TX 종료]      ← DB 트랜잭션은 여기서 끝
> 2. 필요한 symbol 집합 추출 (중복 제거)
> 3. PriceCache 조회 → 미스/만료된 symbol만 외부 API 호출 (타임아웃 2초)
>      ├─ 성공 → 캐시 갱신, [별도 TX] assets.current_price·price_updated_at 갱신 (source=API)
>      └─ 실패 → 예외를 던지지 않고 만료된 캐시값 → 없으면 assets.current_price 사용
> 4. 응답 조립 (평가금액 = quantity × price × exchangeRate)
> ```
>
> **핵심 제약 3가지 (구현 시 반드시 지킬 것)**
> 1. **외부 HTTP 호출을 DB 트랜잭션 안에서 하지 않는다.** 트랜잭션이 열린 채로 수 초짜리 네트워크 I/O를 기다리면 커넥션 풀이 고갈된다.
> 2. **타임아웃을 반드시 설정한다** (connect/read 각 2초). 외부 API가 느려질 때 대시보드 전체가 멈추면 안 된다.
> 3. **동일 symbol에 대한 동시 중복 호출을 막는다.** 같은 사용자가 대시보드를 연타하거나 여러 사용자가 동시 진입하면 같은 symbol을 중복 호출한다(thundering herd). `ConcurrentHashMap` + symbol 단위 락 정도로 충분하다.
>
> **GET이 상태를 변경하는 것에 대한 입장 (명시)**: 위 흐름에서 조회 API가 `current_price`를 갱신하므로, 엄밀히 GET은 safe하지 않다. 이는 **인지한 상태의 절충**이다. 변경 대상이 사용자 데이터가 아니라 외부에서 가져온 파생 캐시값이고, 응답 결과가 호출 횟수에 따라 달라지지 않기 때문에 허용 가능하다고 판단했다. 사용자·자산 수가 늘어나면 Scheduler 기반 사전 갱신으로 전환해 이 절충을 제거한다(Roadmap 7-2).
>
> **PriceProvider 추상화**: 시세 조회는 `PriceProvider` 인터페이스 뒤에 감춘다.
> - STOCK: `YahooFinancePriceProvider` (Yahoo Finance 비공식 API)
> - CRYPTO: `CryptoPriceProvider` (내부적으로 Binance 클라이언트로 USDT 가격, Upbit 클라이언트로 KRW-USDT 환율을 각각 조회해 조합)
>
> 구현체 선택은 `boolean supports(AssetType type)` 메서드를 인터페이스에 두고, `List<PriceProvider>`를 주입받아 `AssetType → PriceProvider`의 `Map`을 시작 시점에 구성하는 방식으로 해결한다. ("Spring이 타입에 따라 알아서 주입"되지 않는다 — 한 인터페이스에 구현체가 둘이면 명시적 리졸버가 필요하다.)
>
> 향후 공식/유료 API(TwelveData 등)로 전환할 때 해당 타입의 구현체만 교체하면 되도록 설계한다.

> **userId 설계 이유**: Asset은 User를 객체 참조가 아닌 `userId`(FK 값)로만 참조한다. Aggregate 경계를 명확히 유지하기 위함이며, User 저장을 위해 Asset을 함께 로드할 필요가 없도록 한다.

**도메인 메서드**
```
buy(quantity, price, exchangeRate)
  └ 새 avgPrice(KRW) =
       (기존quantity×기존avgPrice(KRW) + 신규quantity×(price×exchangeRate))
       / (기존quantity+신규quantity)
  └ quantity 증가

sell(quantity, price, exchangeRate)
  └ 보유수량 - sell수량 < 0 이면 InsufficientAssetQuantityException
  └ realizedPnl += (price × exchangeRate - avgPrice) × quantity   ← 실현손익 확정
  └ quantity 감소
  └ avgPrice(KRW)는 유지 (남은 수량의 원가는 변하지 않음)
  └ quantity가 0이 되어도 avgPrice·realizedPnl은 초기화하지 않는다
       (전량매도 후 재매수 시, 위 buy 공식에 기존quantity=0을 대입하면
        새 avgPrice = 신규매수단가가 되어 자동으로 올바르게 처리됨)

deposit(amount) / withdraw(amount)
  └ CASH/BANK 전용, quantity 증감
  └ withdraw 시 잔액 부족이면 InsufficientAssetQuantityException

applyTransaction(Transaction tx)
  └ tx.type에 따라 위 메서드로 라우팅

updateCurrentPrice(price)     // 원래 통화 기준 시세 갱신, Transaction 생성 안 함
                               //   └ 호출 시 priceUpdatedAt도 현재 시각으로 함께 갱신
                               //   └ 자동 조회 성공 시 / 사용자 수동 입력 시 모두 이 메서드를 통해서만 갱신
updateExchangeRate(rate)      // 현재 환율 갱신, Transaction 생성 안 함

getValuation()                 // 평가금액(KRW) = quantity × currentPrice × exchangeRate
                                //   CASH/BANK는 currentPrice가 1로 고정되어 있으므로
                                //   타입 분기 없이 동일한 공식이 적용된다
getUnrealizedPnl()             // 평가금액(KRW) - (quantity × avgPrice)
getRealizedPnl()               // 누적 실현손익 (필드 반환)
getPnlRate()                   // 매입금액(quantity × avgPrice)이 0이면 null 반환
                                //   (수량 0인 자산 / 미매수 자산에서 0으로 나누기 방지)
```

---

### Transaction
**책임**: Asset에 발생한 변경 이벤트의 기록. Asset 상태 변경의 트리거.

| 필드 | 타입 | 제약 |
|---|---|---|
| id | Long | PK |
| assetId | Long | FK, not null |
| type | Enum | DEPOSIT, WITHDRAW, BUY, SELL |
| quantity | BigDecimal | not null |
| price | BigDecimal | nullable (DEPOSIT/WITHDRAW는 의미 없음, 거래 시점 원래 통화 기준) |
| exchangeRate | BigDecimal | nullable — **BUY와 SELL 모두 필수** (거래 시점의 환율) |
| memo | String | nullable |
| tradedAt | LocalDateTime | not null (사용자가 지정한 거래 시점) |
| createdAt | LocalDateTime | not null (레코드 생성 시점) |

도메인 메서드: 없음 (단순 기록. 상태 변경 책임은 Asset에 있음)

> **SELL에도 `exchangeRate`를 받는 이유**: 실현손익을 KRW 기준으로 확정하려면 매도 시점의 환율이 필요하다(`(매도가 × 매도환율 - avgPrice) × 수량`). 이전 설계는 SELL에서 `price`만 받고 환율은 받지 않았는데, 그러면 `price`가 기록만 되고 어디에도 쓰이지 않는 값이 된다. 국내주식·원화현금은 항상 1을 넘긴다.
>
> **환율 스냅샷을 Transaction에 남기는 이유**: `assets.exchange_rate`는 **현재** 환율이라 계속 덮어써진다. 거래 시점의 환율은 그 순간에만 존재하는 사실이므로 이벤트 레코드에 함께 박아둬야 나중에 재계산·검증이 가능하다.

---

### 관계
```
User (1) ─── (N) Asset
Asset (1) ─── (N) Transaction
```

### View (DTO) 목록 — Entity 아님

| View | 나오는 곳 | 내용 |
|---|---|---|
| PortfolioView | AssetService.getInvestmentAssets() | STOCK+CRYPTO 필터링, 평가금액·Unrealized PNL·Realized PNL·`priceStale` 포함 |
| AllocationView | AssetService.getAllocation() | type별 KRW 환산 합산 비율 (Pie Chart용) |
| DashboardResponse | DashboardFacade | 총자산(KRW 환산) + AllocationView + 최근 Transaction N건 |

> **View는 모두 Entity를 그대로 노출하지 않는다.** Asset 엔티티에는 `userId`, `version`, `deletedAt`처럼 클라이언트가 알 필요 없는 필드가 있고, 엔티티를 직접 직렬화하면 필드 추가 시 의도치 않게 외부로 새어나간다. 조회 결과는 반드시 별도 DTO로 변환해 응답한다.

---

## 3. ERD

### 관계도
```
users (1) ────< assets (N) ────< transactions (N)
```

### 참조 방식 원칙
- **DB 레벨**: FK 제약조건 유지 (`assets.user_id` → `users.id`, `transactions.asset_id` → `assets.id`)
- **코드(JPA) 레벨**: `@ManyToOne` 객체 참조 사용하지 않음. `Long userId`, `Long assetId`처럼 ID 값으로만 참조
- **이유**: Asset을 Aggregate Root로 설계했으므로, 객체 그래프 탐색이 Aggregate 경계를 넘어가지 않도록 한다. 정합성은 DB가 보장하고, 도메인 로직은 ID 기반으로 명시적으로 처리한다.

### users
| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| password | VARCHAR(255) | NOT NULL |
| nickname | VARCHAR(50) | NOT NULL |
| created_at | DATETIME | NOT NULL |

### assets
| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| user_id | BIGINT | NOT NULL, FK → users.id, INDEX |
| type | VARCHAR(20) | NOT NULL — CASH, BANK, STOCK, CRYPTO |
| symbol | VARCHAR(30) | NOT NULL — 시세 조회 키 (등록 후 불변) |
| name | VARCHAR(100) | NOT NULL — 표시용 이름 (수정 가능) |
| quantity | DECIMAL(20,8) | NOT NULL, DEFAULT 0 |
| avg_price | DECIMAL(20,8) | NULL (KRW 기준) |
| current_price | DECIMAL(20,8) | NULL (원래 통화 기준, CASH/BANK는 1) |
| price_updated_at | DATETIME | NULL — 마지막 시세 갱신 시각 |
| currency | VARCHAR(10) | NOT NULL |
| exchange_rate | DECIMAL(10,4) | NOT NULL, DEFAULT 1 |
| realized_pnl | DECIMAL(20,8) | NOT NULL, DEFAULT 0 — 누적 실현손익(KRW) |
| source | VARCHAR(20) | NOT NULL, DEFAULT 'MANUAL' |
| version | BIGINT | NOT NULL, DEFAULT 0 — 낙관적 락 |
| deleted_at | DATETIME | NULL — Soft Delete 표시 (NULL이면 활성) |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

인덱스
- `(user_id, type)` 복합 인덱스 — Dashboard/Portfolio 조회 시 "user_id 필터링 + type별 그룹핑"이 빈번하므로 최적화.
- **`(user_id, type, symbol)` UNIQUE 제약** — 같은 사용자가 동일 자산을 중복 등록하는 것을 DB 레벨에서 차단한다. "CRYPTO는 symbol 기준 통합"이라는 규칙은 문서에만 써두면 지켜지지 않으므로 제약조건으로 강제한다.

> DECIMAL(20,8) 선택 이유: 암호화폐는 소수점 8자리(사토시 단위)까지 필요. 주식/현금도 이 정밀도로 통일해서 type별 컬럼 분기 없이 하나의 컬럼으로 처리.

### transactions
| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| asset_id | BIGINT | NOT NULL, FK → assets.id, INDEX |
| type | VARCHAR(20) | NOT NULL — DEPOSIT, WITHDRAW, BUY, SELL |
| quantity | DECIMAL(20,8) | NOT NULL |
| price | DECIMAL(20,8) | NULL |
| exchange_rate | DECIMAL(10,4) | NULL |
| memo | VARCHAR(255) | NULL |
| traded_at | DATETIME | NOT NULL |
| created_at | DATETIME | NOT NULL |

인덱스: `(asset_id, traded_at DESC)` — 최근 거래 조회 시 정렬 비용 절감.

### 의도적 생략
- `asset_snapshots` 테이블 — Analytics/History 기능이 Roadmap이므로 없음
- `exchange_rates` 별도 테이블 — `assets.exchange_rate`에 값으로만 저장 (환율 이력 관리 안 함)
- **`price_cache` 테이블 — 만들지 않는다.** 전역 시세 캐시는 애플리케이션 메모리(`ConcurrentHashMap<CacheKey, CachedPrice>`)에 둔다.
  - **이유**: MVP는 단일 인스턴스로 운영되며, 캐시는 15분이면 만료되는 휘발성 데이터다. 서버 재시작 시 캐시가 비어도 `assets.current_price`(마지막 성공값)가 폴백으로 남아 있으므로 데이터 손실이 아니다. 테이블/Redis를 추가하는 것은 이 규모에서 과설계다.
  - **확장 시**: 인스턴스가 2대 이상이 되는 순간 인스턴스별로 캐시가 따로 돌아 외부 호출이 배수로 늘어난다. 그 시점에 Redis로 교체한다. 캐시 접근을 `PriceCache` 인터페이스 뒤에 두어 구현체 교체만으로 전환 가능하게 설계한다.

---

## 4. API 명세

### 4-0. 공통 인증·인가 규칙 (모든 API에 적용, 예외 없음)

**인증(Authentication)과 인가(Authorization)는 별개 문제다. 둘 다 통과해야 한다.**

| 구분 | 질문 | 실패 시 |
|---|---|---|
| 인증 | 유효한 토큰이 있는가? | `401 UNAUTHORIZED` |
| 인가 | 이 리소스가 **내 것**인가? | `403 FORBIDDEN_ASSET_ACCESS` |

**규칙 1 — 경로 변수의 리소스 소유권은 항상 검증한다.**
`/api/assets/{id}`, `/api/assets/{id}/transactions/*` 등 `{id}`를 받는 모든 API는 해당 Asset이 **현재 인증된 사용자의 소유인지** 반드시 확인한다. 토큰이 유효하다는 것은 "로그인했다"는 뜻일 뿐, "이 자산의 주인이다"라는 뜻이 아니다.

**규칙 2 — 검증은 Repository 조회 단계에서 강제한다.**
```java
// 권장: 소유권이 조회 조건에 포함되어 있어 검증을 빠뜨릴 수 없다
assetRepository.findByIdAndUserId(assetId, currentUserId)
    .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));

// 지양: findById로 가져온 뒤 별도로 검사하는 방식은 검사를 잊을 여지가 남는다
```
> **왜 `findById` 후 `if`로 검사하지 않는가**: 인가 검사를 "선택적으로 추가하는 한 줄"로 두면 새 API를 만들 때마다 빠뜨릴 수 있다. 조회 조건 자체에 `userId`를 넣으면 **검증을 빠뜨리는 것이 구조적으로 불가능**해진다.

**규칙 3 — 존재하지 않는 자산과 남의 자산은 동일하게 응답한다.**
남의 Asset id로 요청했을 때 `403`(존재하지만 권한 없음)과 `404`(존재하지 않음)를 구분해서 응답하면, 공격자가 id를 순회하며 **"어떤 id가 실재하는지" 목록을 알아낼 수 있다**(리소스 열거). 따라서 두 경우 모두 `ASSET_NOT_FOUND`로 응답한다. `FORBIDDEN_ASSET_ACCESS`는 로그·감사 용도로만 내부에 남긴다.

**규칙 4 — 사용자 식별자는 요청 본문에서 받지 않는다.**
`userId`는 항상 `SecurityContext`(= 검증된 JWT)에서 꺼낸다. 요청 body나 query parameter로 받은 `userId`를 신뢰하면 인증 자체가 무의미해진다.

---

### 4-1. 인증 (Auth)

**회원가입**
```
POST /api/auth/signup
인증 필요: X
```
Request:
```json
{ "email": "test@example.com", "password": "1234abcd", "nickname": "재동" }
```
비밀번호 규칙: 8자 이상 (특수문자 강제 없음)

**로그인**
```
POST /api/auth/login
인증 필요: X
```
Response:
```json
{ "accessToken": "eyJ...", "tokenType": "Bearer" }
```
이후 모든 요청 헤더: `Authorization: Bearer {token}`

**로그아웃**: 별도 API 없음. JWT는 서버가 상태를 저장하지 않는 구조(stateless)이므로, 로그아웃은 프론트엔드가 저장해둔 토큰을 삭제하는 것으로 처리한다. 토큰은 만료 시각(24시간)까지는 유효하다.

---

### 4-2. Asset

**Asset 등록** (이름 직접 입력, 최초 1회 — 자동완성/자동생성 없음)
```
POST /api/assets
인증 필요: O
```
Request:
```json
{ "type": "CRYPTO", "symbol": "BTC", "name": "비트코인", "currency": "USDT" }
```
Response:
```json
{ "id": 5, "type": "CRYPTO", "symbol": "BTC", "name": "비트코인", "quantity": 0, "avgPrice": null, "currentPrice": null, "currency": "USDT", "exchangeRate": 1, "realizedPnl": 0 }
```

**등록 시점 symbol 검증 (필수)**
STOCK/CRYPTO 등록 시, 해당 `symbol`이 외부 API에서 실제로 조회되는지 **1회 확인**한 뒤 저장한다. 조회에 성공하면 그 가격을 `currentPrice` 초기값으로 함께 저장한다.
- 실패 시: `400 INVALID_SYMBOL` — "해당 심볼의 시세를 찾을 수 없습니다. 국내주식은 `000660.KS` 형식으로 입력해주세요."
- 외부 API 자체가 응답하지 않는 경우(타임아웃): 검증을 통과시키고 등록을 허용한다. 외부 장애로 자산 등록 자체가 막히면 안 된다.

> **왜 등록 시점에 검증하는가**: 검증이 없으면 사용자가 `BITCOIN`처럼 잘못된 심볼을 입력해도 그대로 저장되고, 이후 시세 조회는 "실패해도 예외를 던지지 않는" 정책 때문에 **조용히 계속 실패**한다. 사용자는 대시보드에 자산이 0원으로 표시되는 이유를 영원히 알 수 없다. API 호출 1번으로 막을 수 있는 침묵형 버그다.

**Asset 목록 조회**
```
GET /api/assets
인증 필요: O
```

**Asset 단건 조회 / 수정 / 삭제**
```
GET /api/assets/{id}
PATCH /api/assets/{id}     -- name, currency만 수정 가능 (symbol/type은 불변)
DELETE /api/assets/{id}    -- Soft Delete (deleted_at 기록, 거래 내역 보존)
인증 필요: O
```

> **Soft Delete를 쓰는 이유**: 자산 관리 서비스에서 거래 기록을 물리 삭제하는 것은 방어하기 어렵다. 사용자가 실수로 삭제했을 때 복구 수단이 없고, "이 자산에서 얼마 벌었나"라는 확정된 사실(`realizedPnl`)까지 함께 사라진다. `assets.deleted_at`(nullable) 컬럼을 두고 모든 조회에서 `deleted_at IS NULL` 조건을 적용한다. Transaction은 손대지 않는다.
>
> **`symbol`을 수정 불가로 두는 이유**: symbol이 바뀌면 그 Asset에 쌓인 거래 내역·평단가가 전혀 다른 종목의 것과 섞인다. 종목을 바꾸고 싶으면 새 Asset을 등록하는 것이 맞다. 표시 이름만 바꾸고 싶은 경우는 `name` 수정으로 해결된다.

**현재가 조회/갱신** (Transaction 생성 안 함)

STOCK/CRYPTO Asset은 조회 시(Portfolio, Dashboard) 서버가 내부적으로 아래 흐름을 수행한다. 별도의 "조회용 API"가 프론트에 노출되는 것이 아니라, 기존 조회 API 내부 로직으로 동작한다.
```
Asset 조회 요청 (Portfolio/Dashboard)
  ↓
[TX] 사용자 Asset 목록 조회 → [TX 종료]
  ↓
필요한 (type, symbol) 집합 추출 — 중복 제거
  ↓  ※ BTC를 3개 Asset에서 쓰더라도 조회는 1회
전역 PriceCache 확인 (TTL 15분)
  ├─ HIT  → 캐시값 사용
  └─ MISS → PriceProvider로 외부 조회 (타임아웃 2초, symbol별 단일 비행)
             ├─ 성공 → 캐시 저장 + [별도 TX] assets.current_price/price_updated_at 갱신 (source=API)
             └─ 실패 → 만료된 캐시값 → 없으면 assets.current_price → 그것도 null이면
                        해당 자산은 평가금액 null로 응답 (에러를 던지지 않음)
  ↓
응답 조립 (평가금액 = quantity × price × exchangeRate)
```

> **응답에 `priceStale` 플래그를 포함한다**: 시세 조회가 실패해 오래된 값을 쓰고 있다면, 사용자가 그 사실을 알아야 한다. Portfolio/Dashboard 응답의 각 자산에 `"priceStale": true`와 `priceUpdatedAt`을 함께 내려보내 화면에서 "N분 전 기준" 같은 표시를 할 수 있게 한다. 조용히 틀린 숫자를 보여주는 것이 가장 나쁜 실패 방식이다.

사용자가 자동 조회 결과를 신뢰하지 못하거나 CASH/BANK처럼 자동 조회 대상이 아닌 경우를 위해, 수동 갱신 API는 그대로 유지한다 (폴백):
```
PATCH /api/assets/{id}/price
Request: { "currentPrice": 56000000 }
```
> 수동 갱신 시에도 `priceUpdatedAt`이 현재 시각으로 갱신되어, 이후 15분간은 자동 조회가 이 값을 덮어쓰지 않는다.

**환율 갱신** (Transaction 생성 안 함)
```
PATCH /api/assets/{id}/exchange-rate
Request: { "exchangeRate": 1390.0 }
```

---

### 4-3. Transaction (항상 특정 Asset을 대상으로만 생성됨)

**매수**
```
POST /api/assets/{id}/transactions/buy
인증 필요: O
```
Request:
```json
{ "quantity": 0.1, "price": 40000, "exchangeRate": 1380, "tradedAt": "2026-08-06T10:00:00" }
```
Response:
```json
{ "transactionId": 12, "asset": { "id": 5, "quantity": 0.6, "avgPrice": 68500000, "realizedPnl": 0 } }
```

**매도**
```
POST /api/assets/{id}/transactions/sell
Request: { "quantity": 0.2, "price": 56000000, "exchangeRate": 1380, "tradedAt": "..." }
```
Response:
```json
{ "transactionId": 13, "asset": { "id": 5, "quantity": 0.4, "avgPrice": 68500000, "realizedPnl": 1420000 } }
```
- `exchangeRate`는 실현손익을 KRW로 확정하기 위해 필요하다. 국내주식·원화현금은 `1`.
- 매도 후에도 `avgPrice`는 변하지 않는다 (남은 수량의 원가는 그대로).
- 보유 수량 초과 시 `INSUFFICIENT_ASSET_QUANTITY` 에러.

**입금 / 출금**
```
POST /api/assets/{id}/transactions/deposit
POST /api/assets/{id}/transactions/withdraw
Request: { "quantity": 1000000, "tradedAt": "...", "memo": "월급 입금" }
```

**특정 Asset의 거래 내역 조회**
```
GET /api/assets/{id}/transactions
인증 필요: O
```
> 전체 Asset을 아우르는 `GET /api/transactions`는 만들지 않는다 (Roadmap 참고).

---

### 4-4. Dashboard
```
GET /api/dashboard
인증 필요: O
```
Response:
```json
{
  "totalAssetKRW": 15000000,
  "investmentSummary": {
    "valuationKRW": 12000000,
    "unrealizedPnl": 300000,
    "unrealizedPnlRate": 2.56,
    "realizedPnl": 1420000
  },
  "cashSummary": { "valuationKRW": 3000000 },
  "allocation": [
    { "type": "STOCK", "ratio": 50.0 },
    { "type": "CRYPTO", "ratio": 30.0 },
    { "type": "CASH", "ratio": 20.0 }
  ],
  "recentTransactions": [
    { "assetName": "BTC", "type": "BUY", "quantity": 0.1, "tradedAt": "2026-08-06T10:00:00" }
  ]
}
```

### 4-5. Portfolio
```
GET /api/portfolio?type={type}&sort=unrealizedPnl,desc
인증 필요: O
```
Response:
```json
[
  {
    "assetId": 5, "symbol": "BTC", "name": "비트코인", "type": "CRYPTO",
    "quantity": 0.6, "avgPrice": 68500000, "currentPrice": 56000000,
    "valuationKRW": 33600000,
    "unrealizedPnl": 3100000, "unrealizedPnlRate": 10.16,
    "realizedPnl": 1420000,
    "priceStale": false, "priceUpdatedAt": "2026-08-06T10:03:00"
  }
]
```
- `type` 생략 시 전체 조회
- `sort=unrealizedPnl,desc` 기본값 (평가손익 높은 순)
- **`unrealizedPnlRate`는 매입금액이 0이면 `null`** (수량 0 자산, 미매수 자산). 프론트는 `null`일 때 `-`로 표시한다. 0으로 나누기를 방지하기 위한 명시적 규칙이다.
- `priceStale: true`면 시세 조회에 실패해 오래된 값을 쓰고 있다는 뜻. 화면에 "N분 전 기준" 표시.

### 4-6. Allocation
- 별도 API 없음. Dashboard 응답의 `allocation` 필드로 대체.
- 종목 단위 Allocation이 필요해지면 `GET /api/assets/allocation?groupBy=asset` 신설 (Roadmap).

### 4-7. 공통 에러 응답 형식
```json
{ "status": 400, "code": "INSUFFICIENT_ASSET_QUANTITY", "message": "보유 수량이 부족합니다." }
```

| 코드 | HTTP | 상황 |
|---|---|---|
| INSUFFICIENT_ASSET_QUANTITY | 400 | 매도/출금 수량이 보유 수량보다 많을 때 |
| INVALID_INPUT | 400 | 필수 필드 누락, 값 범위 위반 등 |
| INVALID_SYMBOL | 400 | Asset 등록 시 외부 API에서 조회되지 않는 심볼 |
| UNAUTHORIZED | 401 | 토큰 없음 / 만료 / 위조 |
| ASSET_NOT_FOUND | 404 | 존재하지 않는 Asset id **또는 타인 소유 Asset** (4-0 규칙 3) |
| DUPLICATE_ASSET | 409 | 동일 `(type, symbol)` 자산을 중복 등록 |
| CONCURRENT_MODIFICATION | 409 | 동시 거래로 낙관적 락 충돌 (`OptimisticLockException`) — 재시도 안내 |

> `FORBIDDEN_ASSET_ACCESS`는 클라이언트에 노출하지 않는다. 타인 소유 자산 접근 시도는 `ASSET_NOT_FOUND`로 응답하되, 서버 로그에는 별도 식별자로 기록해 감사 추적이 가능하게 한다.

### 4-8. 공통 Validation 규칙

| 대상 | 규칙 |
|---|---|
| `quantity` (모든 Transaction) | 0보다 커야 함 (0 이하이면 `INVALID_INPUT`) |
| `price` (BUY/SELL) | 0보다 커야 함 |
| `exchangeRate` (**BUY/SELL 모두 필수**) | 0보다 커야 함 |
| `symbol` (Asset 등록) | 빈 문자열 불가, 영문·숫자·`.`만 허용, 최대 30자, **대문자로 정규화 후 저장** |
| `name` (Asset 등록) | 빈 문자열 불가, 최대 100자 |
| `tradedAt` (모든 Transaction) | 미래 시각 불가 (`@PastOrPresent`) |
| `email` (회원가입) | 이메일 형식 검증 |
| `password` (회원가입) | 8자 이상 |

> **symbol 대문자 정규화 이유**: 사용자가 `btc`와 `BTC`를 각각 입력하면 `(user_id, type, symbol)` 유니크 제약이 이를 서로 다른 값으로 보아 중복 등록을 허용해버린다. 저장 전에 `toUpperCase()`로 정규화해 제약조건이 실제로 작동하게 한다.
>
> **`tradedAt` 미래 불가 이유**: 미래 날짜 거래가 들어오면 "최근 거래 5건"이 영구히 그 항목으로 채워진다.

> `@Valid` + Bean Validation 어노테이션(`@Positive`, `@NotBlank`, `@Email` 등)으로 DTO 단에서 처리하고, 위반 시 `INVALID_INPUT`으로 응답한다.

---

## 5. 화면 와이어프레임

### Dashboard (첫 화면)
```
┌─────────────────────────────────┐
│  총 자산  ₩15,000,000            │
├─────────────────────────────────┤
│  Investment Summary               │  → 탭 시 Investment 화면 이동
├─────────────────────────────────┤
│  Asset Allocation (Pie Chart)     │  → 탭 시 Investment 화면 이동 (필터 없음)
├─────────────────────────────────┤
│  Cash/Bank 요약                   │  → 탭 시 Assets 화면 이동
├─────────────────────────────────┤
│  최근 거래 (최대 5건)                │  → 항목 탭 시 해당 Asset 거래내역 이동
│                                    │
│                            (FAB +)│  → 자산등록/매수매도/입출금 선택
└─────────────────────────────────┘
```
- "전체 거래 보기" 링크 없음 (전체 Transactions 화면 자체를 MVP에서 제외)

**빈 상태 (Empty State)**: 신규 가입 직후 Asset이 하나도 없으면 위 카드들 대신 안내 화면을 보여준다.
```
┌─────────────────────────────────┐
│                                    │
│      아직 등록된 자산이 없어요        │
│   + 버튼을 눌러 첫 자산을 등록해보세요  │
│                                    │
│                            (FAB +)│
└─────────────────────────────────┘
```

### Investment (Portfolio 화면)
```
┌─────────────────────────────────┐
│  [전체] [CRYPTO] [STOCK]  탭       │
│  정렬: 손익 높은순 (기본값)          │
├─────────────────────────────────┤
│  BTC  0.6개 · 평단 ₩68,500,000   │
│  평가 ₩33,600,000 (+10.16%)      │
└─────────────────────────────────┘
```
호출 API: `GET /api/portfolio?type={type}&sort=unrealizedPnl,desc`

### Assets (Cash/Bank 화면)
```
┌─────────────────────────────────┐
│  Cash & Bank                      │
│  KB국민은행  ₩2,000,000            │
│  현금        ₩1,000,000            │
└─────────────────────────────────┘
```

### Transactions (특정 Asset 거래 내역만 존재)
```
┌─────────────────────────────────┐
│  BTC 거래 내역                     │
│  BUY  0.1개  40,000 USDT×1,380   │
│  08/06 10:00                      │
└─────────────────────────────────┘
```
호출 API: `GET /api/assets/{id}/transactions`

### FAB 입력 흐름 (2단계 — 자동완성/자동생성 없음)
```
[FAB 클릭] → "무엇을 하시겠어요?" → [자산 등록] / [매수·매도] / [입금·출금]

[자산 등록]                    [매수·매도·입출금]
심볼 입력 (BTC, NVDA…)          Dropdown에서 기존 Asset 선택
표시 이름 입력 (선택)             가격/수량/환율 입력
타입/통화 선택                   → POST /api/assets/{id}/transactions/*
→ POST /api/assets
   └ 저장 전 심볼 유효성 확인
     (실패 시 INVALID_SYMBOL)
```
> Transaction이 Asset을 자동 생성하지 않는다 — Asset 등록과 거래 입력을 명확히 분리해 Aggregate 경계를 지키고, 자유 텍스트 입력으로 인한 이름 표기 불일치(BTC/bitcoin/비트코인)를 방지한다.
>
> **STOCK 등록 시 심볼 입력 형식 안내**: 국내주식은 `000660.KS`(코스피) / `.KQ`(코스닥)처럼 시장 접미사를 포함해서 입력해야 Yahoo Finance 조회가 된다. 해외주식은 `NVDA`처럼 티커만 입력한다. 자산 등록 화면의 **심볼** 입력 필드 아래에 이 형식을 안내 문구로 표시한다.
>
> **표시 이름(name)은 선택 입력**: 비워두면 심볼을 그대로 표시 이름으로 사용한다. 화면에는 항상 `name`을 노출하고 `symbol`은 보조 정보로 작게 표시한다(`비트코인 · BTC`). 사용자가 `000660.KS`라는 기계적 문자열을 계속 보게 되는 것을 막기 위함이다.
>
> **심볼 검증은 저장 버튼을 누른 시점에 수행한다**: 입력 중 실시간 검증(자동완성)은 MVP에서 제외했으므로(Roadmap 7-6), 저장 시 1회 확인하고 실패하면 폼에 에러를 표시한다.

### 화면 흐름 요약도
```
[로그인] → [Dashboard]
              ├─ Investment Summary/Allocation 탭 → [Investment]
              ├─ Cash 요약 탭 → [Assets]
              ├─ 최근 거래 항목 탭 → [Transactions - 특정 Asset]
              └─ FAB(+) → [자산등록/매수매도/입출금 모달]

[Investment] → 카드 탭 → [Transactions - 특정 Asset]
[Assets] → 카드 탭 → [Transactions - 특정 Asset]
```

---

## 6. 패키지 구조

```
com.assetdashboard
│
├── global
│   ├── config
│   │   ├── SecurityConfig.java
│   │   └── JwtConfig.java
│   ├── security
│   │   ├── JwtTokenProvider.java
│   │   └── JwtAuthenticationFilter.java
│   ├── exception
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ErrorCode.java
│   │   └── BusinessException.java
│   └── common
│       └── BaseTimeEntity.java
│
├── domain
│   │
│   ├── user
│   │   ├── entity/User.java
│   │   ├── repository/UserRepository.java
│   │   ├── service/UserService.java
│   │   ├── controller/AuthController.java
│   │   └── dto/ (SignupRequest, LoginRequest, LoginResponse)
│   │
│   ├── asset                               # Aggregate Root
│   │   ├── entity/ (Asset, AssetType, AssetSource)
│   │   ├── repository/AssetRepository.java
│   │   ├── service/AssetService.java       # CRUD + 조회(Allocation, Portfolio) 전부 포함
│   │   ├── controller/AssetController.java
│   │   ├── dto/ (AssetCreateRequest, AssetResponse, AssetPriceUpdateRequest, PortfolioResponse, AllocationResponse)
│   │   └── exception/InsufficientAssetQuantityException.java
│   │
│   └── transaction
│       ├── entity/ (Transaction, TransactionType)
│       ├── repository/TransactionRepository.java
│       ├── service/TransactionService.java
│       ├── controller/TransactionController.java   # /assets/{id}/transactions/*
│       └── dto/ (BuyRequest, SellRequest, DepositRequest, WithdrawRequest, TransactionResponse)
│
├── dashboard                               # Entity 없음, Facade만 존재
│   ├── facade/DashboardFacade.java
│   ├── controller/DashboardController.java
│   └── dto/DashboardResponse.java
│
└── infra
    └── price                               # 외부 시세 조회 (도메인 로직과 분리)
        ├── PriceProvider.java              # 인터페이스 — fetchPrice(symbol), supports(AssetType)
        ├── PriceProviderResolver.java      # AssetType → PriceProvider 매핑 (List 주입 후 Map 구성)
        ├── PriceProviderException.java     # 조회 실패 시 던짐 (상위에서 잡아 폴백 처리)
        ├── PriceQueryService.java          # 캐시 확인 → 미스만 외부 조회 → 결과 반환
        │                                   #   (트랜잭션 밖에서 호출됨, 중복 호출 차단 담당)
        │
        ├── cache
        │   ├── PriceCache.java             # 인터페이스 (추후 Redis 교체 지점)
        │   └── InMemoryPriceCache.java     # ConcurrentHashMap 구현, TTL 15분
        │
        ├── stock
        │   └── YahooFinancePriceProvider.java   # STOCK 구현체 (Yahoo Finance 비공식 API)
        │
        └── crypto
            ├── CryptoPriceProvider.java         # CRYPTO 구현체 (아래 두 클라이언트를 조합)
            ├── BinanceClient.java                # USDT 기준 가격 조회
            └── UpbitExchangeRateClient.java       # KRW-USDT 마켓 환율 조회
```

> **`PriceQueryService`를 둔 이유**: "캐시 확인 → 미스만 외부 조회 → 동시 중복 호출 차단"은 도메인 로직이 아니라 조회 인프라의 책임이다. 이것을 `AssetService`에 두면 도메인 서비스가 캐시·타임아웃·동시성 같은 관심사를 떠안게 된다. `AssetService`는 `PriceQueryService.getPrices(Set<SymbolKey>)`를 호출해 `Map<SymbolKey, Price>`를 받아 쓰기만 한다.

> **`CryptoPriceProvider` 동작 방식**: `BinanceClient`로 USDT 기준 가격을, `UpbitExchangeRateClient`로 KRW-USDT 환율을 각각 조회한 뒤, `평가금액 = USDT가격 × 환율`로 조합해서 반환한다. 두 클라이언트 중 하나라도 실패하면 `PriceProviderException`을 던지고, `AssetService`가 이를 잡아 기존 저장값으로 폴백한다.

> **`infra.price`를 `domain.asset`이 아니라 별도 패키지로 분리한 이유**: 외부 API 호출은 도메인 로직이 아니라 인프라 관심사이므로, Asset 엔티티나 AssetService가 Yahoo Finance/Binance/Upbit이라는 구체적인 구현을 직접 알지 못하게 한다. `AssetService`는 `PriceProvider` 인터페이스에만 의존하고, 실제 구현체는 Spring이 타입(STOCK/CRYPTO)에 따라 주입해준다. 이렇게 하면 나중에 TwelveData 등으로 교체할 때 `domain` 패키지의 코드는 전혀 건드릴 필요가 없다.

### 설계 결정 기록

**AssetQueryService를 분리하지 않은 이유**
> 현재 MVP 규모에서는 조회 로직이 많지 않아 AssetService 하나로도 충분하다고 판단했다. CQRS나 조회 전용 Service 분리는 조회 복잡도가 증가하거나 QueryDSL/Projection 등을 도입하는 시점에 고려한다.

**Transaction 생성 흐름 (도메인 메서드 호출 순서)**
```java
@Transactional
public TransactionResponse buy(Long assetId, Long currentUserId, BuyRequest request) {
    // 소유권을 조회 조건에 포함 — 인가 검증을 빠뜨릴 수 없는 구조 (4-0 규칙 2)
    Asset asset = assetRepository.findByIdAndUserIdAndDeletedAtIsNull(assetId, currentUserId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));

    Transaction transaction = Transaction.createBuy(
        assetId, request.getQuantity(), request.getPrice(),
        request.getExchangeRate(), request.getTradedAt()
    );

    asset.applyTransaction(transaction);   // 도메인 메서드로 상태 변경

    transactionRepository.save(transaction);
    // asset은 @Transactional 안에서 조회된 영속 상태이므로,
    // 트랜잭션 종료 시 JPA 더티 체킹으로 자동 UPDATE된다. 명시적 save() 불필요.
    // 이때 @Version이 함께 검증되어, 동시에 들어온 다른 거래가 먼저 커밋했다면
    // OptimisticLockException이 발생하고 이 트랜잭션 전체가 롤백된다.

    return TransactionResponse.from(transaction, asset);
}
```

**동시성 처리 방식과 그 선택 이유**

같은 Asset에 매수 요청이 거의 동시에 두 번 들어오는 상황(버튼 연타, 자동매매)에서 "읽고-계산하고-쓰기" 구조는 갱신 유실(Lost Update)이 발생한다. 세 가지 선택지를 검토했다.

| 방식 | 장점 | 단점 | 채택 |
|---|---|---|---|
| 원자적 UPDATE (`UPDATE ... SET qty = qty + ?`) | 락 없이 안전, 가장 빠름 | 평단가 계산식이 SQL에 박혀 **도메인 메서드 캡슐화가 깨짐** | X |
| 비관적 락 (`SELECT FOR UPDATE`) | 확실함 | 충돌이 거의 없는데 매번 대기 비용 지불 | X |
| **낙관적 락 (`@Version`)** | 도메인 메서드 유지, 저충돌 상황에 적합, 코드 1줄 | 충돌 시 재시도 필요 | **O** |

이 서비스는 **개인 자산 관리**라 동일 Asset에 대한 동시 쓰기가 극히 드물다. 따라서 "충돌이 없다고 가정하고 진행하되, 충돌하면 감지해서 실패시키는" 낙관적 락이 비용 대비 가장 적합하다. 충돌 시 `409 CONCURRENT_MODIFICATION`으로 응답하고 클라이언트가 재시도한다.

> 이 프로젝트의 핵심 설계 원칙("Asset의 상태 변경은 도메인 메서드를 통해서만")을 지키면서 정합성을 확보하려면 낙관적 락이 유일한 선택지다. 원자적 UPDATE는 더 빠르지만 평단가 계산 로직이 SQL 문자열로 새어나가 원칙과 충돌한다.

---

## 7. Roadmap

### 7-1. Analytics (시계열 분석)
- 자산 성장 그래프, Historical PNL, 월별 수익률, 최고 수익률 기록
- **미룬 이유**: AssetSnapshot 테이블과 Scheduler/Batch 인프라가 필요하며, 6일 MVP에서는 "조회 경험 완성도"라는 핵심 가치 대비 비용이 크다.
- **확장 시 구조**: `Asset → (매일 00시 Scheduler) → AssetSnapshot(asset_id, total_value, profit_loss, snapshot_date)`

### 7-2. 시세 API 고도화 (시세 자동 조회 자체는 MVP에 포함됨)
- **유료/공식 API 전환** (TwelveData 등) — Yahoo Finance 비공식 API는 SLA가 없고 페이지 구조 변경 시 예고 없이 깨질 수 있음
- **해외주식 환율 자동 조회** — 현재 해외주식(USD 등)의 exchangeRate는 수동 입력. (CRYPTO의 환율은 Upbit로 이미 자동화되어 MVP에 포함됨 — 별개 사안)
- **Scheduler 기반 사전 갱신** — 현재는 화면 진입 시점에 캐시(15분) 만료 여부를 확인해 그때 조회하는 방식. 사용자·자산 수가 늘어나면 배치로 미리 갱신해두는 방식으로 전환. 이때 조회 API가 상태를 변경하는 현재의 절충(GET이 safe하지 않음)도 함께 해소된다.
- **Redis 캐시 전환** — 현재 전역 시세 캐시는 애플리케이션 메모리. 인스턴스가 2대 이상이 되면 인스턴스별로 캐시가 분리되어 외부 호출이 배수로 늘어난다. `PriceCache` 인터페이스 구현체만 교체.
- **MVP에서 이미 반영한 것**: `PriceProvider` 인터페이스 추상화 + `PriceCache` 인터페이스 분리 + symbol 단위 전역 캐싱. STOCK은 `YahooFinancePriceProvider`, CRYPTO는 `CryptoPriceProvider`(Binance 가격 × Upbit 환율)를 구현체로 사용.
- **전환 시 구조**: `PriceProvider`를 구현하는 새 클래스(`TwelveDataPriceProvider` 등)만 추가하고 `PriceProviderResolver`의 매핑을 교체. `domain` 패키지는 변경 불필요.

### 7-3. Web3 / MyData 연동
- WalletConnect, MyData
- **미룬 이유**: 인증/보안 요구사항이 크고, 프로젝트 목표(백엔드 아키텍처 경험)와 직접 관련이 적다.
- **확장 시 구조**: `AssetSource = WEB3 / MYDATA`가 이미 Enum에 마련되어 있어 수집 로직만 추가하면 기존 Asset 구조 재사용 가능.

### 7-4. 전체 거래 내역 화면
- **미룬 이유**: "기록이 아니라 조회"라는 서비스 철학과 충돌. Dashboard 최근 5건 + 개별 Asset 거래 내역으로 충분.

### 7-5. 종목 단위 Allocation
- **미룬 이유**: Dashboard 응답이 무거워짐. type 단위로 MVP 충분.
- **확장 시 구조**: `GET /api/assets/allocation?groupBy=asset` 신설.

### 7-6. Asset 검색 자동완성
- **미룬 이유**: 외부 심볼 API(7-2)에 의존. 한글 회사명("SK하이닉스") 검색이 Yahoo 비공식 검색 API에서 정확히 매칭되는지도 검증 안 됨. 현재는 직접 등록(종목코드/티커) + Dropdown 선택으로 표기 불일치 방지.

### 7-7. 환차손익(FX PNL) 분리
- 현재 PNL에는 주가 변동 손익과 환율 변동 손익이 섞여 있다(Glossary 참고).
- **미룬 이유**: 원통화 기준 평단가를 별도로 관리해야 하고, 표시할 지표가 하나 더 늘어 MVP 화면이 복잡해진다.
- **확장 시 구조**: `assets.avg_price_original` 컬럼 추가 → 매수 시 원통화 평단가도 함께 갱신 → `환차손익 = quantity × avgPriceOriginal × (현재환율 - 매수시점 가중평균환율)`

### 로드맵 우선순위
1. 유료/공식 시세 API 전환 (TwelveData 등 — Yahoo Finance의 SLA 부재 리스크 해소)
2. AssetSnapshot + Analytics (데이터 누적 후 의미 생김)
3. 해외주식 환율 자동 조회
4. Scheduler 기반 사전 갱신 + Redis 캐시 (트래픽 증가 시)
5. 환차손익 분리
6. Asset 검색 자동완성 (한글 매칭 검증 후)
7. WalletConnect
8. MyData (가장 무거운 인증/보안 요구사항)

---

## 8. 구현 시 리스크와 대응

### 8-1. 데모 실패 리스크 — Yahoo Finance 비공식 API

MVP에서 가장 불안정한 의존성이 발표 데모의 핵심 경로에 있다. 비공식 API는 예고 없이 응답 형식이 바뀌거나 IP 단위로 차단될 수 있다.

**대응**
- 시세 조회 실패가 **화면을 깨뜨리지 않는지** 반드시 테스트한다 (외부 호출을 강제로 실패시켜보는 시나리오 1개 필수).
- 발표 직전 시연용 seed 데이터(`current_price`가 채워진 Asset 몇 개)를 준비해둔다. 외부 API가 죽어도 폴백값으로 화면이 정상 동작해야 한다.
- 수동 갱신 API(`PATCH /assets/{id}/price`)가 실제로 동작하는지 확인한다. 이것이 최후의 폴백이다.

### 8-2. 구현 순서 권장

의존 관계상 아래 순서를 권장한다. 앞의 것이 깨지면 뒤의 것을 검증할 수 없다.

```
1. User + JWT 인증          ← 이게 없으면 나머지 API를 테스트할 수 없음
2. Asset CRUD + 인가 검증    ← 4-0 규칙을 여기서 확립하고 이후 전부 재사용
3. Transaction (buy/sell/deposit/withdraw) + 도메인 메서드
     └ 이 시점에 평단가·실현손익이 정확한지 실제 요청으로 검증할 것
4. Portfolio 조회 (시세 없이, avgPrice 기준으로만)
5. PriceProvider + 전역 캐시 연동
6. Dashboard (위를 조립하는 Facade)
```

> **4번과 5번을 분리한 이유**: 시세 연동은 외부 의존성이라 불안정하다. 시세 없이도 Portfolio가 동작하는 상태를 먼저 만들어두면, 시세 연동이 실패해도 프로젝트 전체가 멈추지 않는다.

### 8-3. 최소 검증 시나리오

시간이 부족하더라도 아래는 반드시 직접 요청을 보내 확인한다.

| # | 시나리오 | 기대 결과 |
|---|---|---|
| 1 | A 계정 토큰으로 B의 Asset 조회 | `404 ASSET_NOT_FOUND` (성공하면 안 됨) |
| 2 | 10개 @1,500 매수 → 5개 @1,600 매수 | `avgPrice = 1533.33…` |
| 3 | 보유 수량 초과 매도 | `400 INSUFFICIENT_ASSET_QUANTITY`, 수량 변화 없음 |
| 4 | 정상 매도 | `avgPrice` 불변, `realizedPnl` 증가, `quantity` 감소 |
| 5 | 전량 매도 후 재매수 | `avgPrice` = 재매수 단가, `realizedPnl` 유지 |
| 6 | 같은 symbol 중복 등록 | `409 DUPLICATE_ASSET` |
| 7 | 존재하지 않는 심볼 등록 | `400 INVALID_SYMBOL` |
| 8 | 외부 시세 API 강제 실패 | 화면 정상, `priceStale: true` |

### 8-4. 발표 대비 — 예상 질문과 답변 포인트

| 질문 | 답변의 핵심 |
|---|---|
| 다른 사람 자산 ID로 요청하면? | 4-0 규칙. Repository 조회 조건에 `userId` 포함 → 검증 누락이 구조적으로 불가능. 404로 응답해 리소스 열거도 차단 |
| 자산 30개면 대시보드 응답이 몇 초? | symbol 단위 중복 제거 후 캐시 히트. 미스 건만 2초 타임아웃으로 조회. 트랜잭션 밖에서 수행하므로 커넥션 점유 없음 |
| 동시에 매수 버튼 두 번 누르면? | `@Version` 낙관적 락 → 두 번째는 `409`. 원자적 UPDATE·비관적 락과 비교해 선택한 이유 설명 가능 |
| Yahoo가 죽으면? | 예외를 던지지 않고 캐시 → 마지막 저장값 순으로 폴백, `priceStale`로 사용자에게 고지, 수동 갱신 API 제공 |
| Transaction이 Aggregate가 아니라면서 왜 Repository가 따로 있나? | 1장의 "실용적 절충" 항목. 변경 진입점은 통제하되 영속화 경로는 강제하지 않는 이유(append-only 로그의 무한 증가) |
| 환율로 번 돈과 주가로 번 돈을 구분할 수 있나? | 현재는 불가. **의도적으로 제외**했고 확장 구조(7-7)까지 준비되어 있음 |
| 왜 User가 아니라 Asset이 Aggregate Root인가? | 모든 조회·변경의 시작점이 Asset이고, User는 인증 주체일 뿐 자산 상태를 책임지지 않음. User를 루트로 삼으면 자산 1건 변경에 User 전체를 로드해야 함 |

> **답변 원칙**: "몰랐다"보다 "알지만 이런 이유로 이 범위에서는 제외했다"가 훨씬 강하다. 이 문서에 명시된 절충(FX 분리, GET 부수효과, 인메모리 캐시, Aggregate 영속화 경로)은 모두 **인지한 뒤 선택한 것**임을 분명히 말할 것.
