# Asset Dashboard

여러 곳에 흩어진 개인 자산(현금·은행·주식·암호화폐)을 한 화면에서 관리하고 투자 현황을 빠르게 확인하는 Personal Asset Dashboard.

## 빠른 시작

```bash
./gradlew bootRun          # H2 인메모리로 즉시 기동
bash scripts/seed-demo.sh  # 시연 데이터 주입
```

http://localhost:8080 → `demo@example.com` / `1234abcd`

Docker(MySQL)로 실행하려면:

```bash
docker compose up -d --build
```

## 검증

```bash
bash scripts/verify.sh
```

PRD 8-3의 검증 시나리오 8개를 실제 HTTP 요청으로 실행한다.

## 문서

| 문서 | 내용 |
|---|---|
| [docs/Asset_Dashboard_PRD.md](docs/Asset_Dashboard_PRD.md) | 설계 원본 (유일한 진실의 원천) |
| [docs/IMPLEMENTATION_NOTES.md](docs/IMPLEMENTATION_NOTES.md) | 구현 판단 근거, 검토한 대안, PRD의 빈틈 기록 |
| [docs/DEMO.md](docs/DEMO.md) | 시연 순서와 실행 명령어, 외부 API 장애 시 대체 경로 |
| http://localhost:8080/swagger-ui.html | API 문서 (서버 기동 후) |

## 기술 스택

Java 17 · Spring Boot 3.5.9 · Spring Security(JWT) · JPA · Gradle · MySQL 8 / H2 · springdoc-openapi

시세 조회: Yahoo Finance(STOCK) · Binance × Upbit(CRYPTO)

## 구조 요약

```
com.assetdashboard
├── global      설정 · 보안 · 공통 예외
├── domain
│   ├── user            인증 주체
│   ├── asset           Aggregate Root — 상태 변경은 도메인 메서드로만
│   └── transaction     Asset 에 종속된 이벤트 기록
├── dashboard   Entity 없음, 조회 결과를 조립하는 Facade
└── infra.price 외부 시세 조회 (PriceProvider 인터페이스 뒤에 은닉)
```

핵심 설계 결정은 [IMPLEMENTATION_NOTES](docs/IMPLEMENTATION_NOTES.md)에 정리되어 있다.
