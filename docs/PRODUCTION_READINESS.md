# Production Readiness

Folio는 지금 개인 포트폴리오/MVP 단계입니다. 이 문서는 "코드를 안 짜고, 지금까지 확인된
갭을 있는 그대로 정리"한 것입니다 — 실제 서비스로 운영한다고 가정했을 때 무엇이 이미
되어 있고, 무엇이 아직 비어 있는지, 그리고 그 순서를 정리합니다.

## 완료된 항목

| 항목 | 구현 | 참고 |
|---|---|---|
| CI | push·PR마다 `./gradlew clean test` + API 키 패턴 검사 자동 실행 | `.github/workflows/ci.yml` |
| LLM 비용 거버넌스 | Financial Evidence Agent와 News 요약이 하루 호출 수·토큰 사용량 카운터를 공유, 초과 시 `429 AI_BUDGET_EXCEEDED`로 실제 호출 전에 차단 | `evidence/agent/LlmUsageBudgetService.java`, `GET /api/ai/usage/today` |
| 인증 세션 관리 | Access Token 30분 + 회전·폐기되는 Refresh Token(SHA-256 해시 저장), 재사용 탐지 시 세션 전체 폐기, 로그아웃·비밀번호 변경·회원 탈퇴가 실제로 세션을 끊음 | `global/security/RefreshTokenService.java` |
| 비밀 관리 | API 키·JWT Secret은 환경변수로만 주입, 코드·설정 파일에 저장하지 않음. `prod` 프로필은 `APP_JWT_SECRET` 없으면 기동 자체가 실패 | `application.yml`, `JwtTokenProvider.java` |
| 데이터 삭제 | 회원 탈퇴 시 자산·거래·Snapshot·근거 문서·Agent Trace·평가 배치·Refresh Token까지 연쇄 삭제, 고아 레코드 없음 | `UserAccountService.deleteAccount()` |
| 인가 | 소유권 기반 404(리소스 존재 여부 비노출), `SecurityContext` 기반 `userId`만 신뢰, 신규 API는 기본적으로 인증 필요(명시적으로 연 경로만 예외) | `SecurityConfig.java` |
| DB 마이그레이션 | Flyway로 스키마 이력 관리(`V1~V9`), `prod`는 `ddl-auto=validate`로 스키마 드리프트 방지 | `db/migration/` |
| 백업 | AWS 스테이징용 백업·복구·검증 스크립트 존재(수동 실행) | `deploy/aws/backup.sh`, `restore.sh`, `verify-backup.sh` |
| LLM 안전장치 | 숫자 조작·가격 인과·Prompt Injection·문장수·비정상 토큰을 규칙 기반으로 차단, 골든셋으로 회귀 검증 | `evidence/news/NewsAnswerGuardrail.java`, `news/NewsSummaryGuardrail.java` |
| 인증 API rate limit | 같은 이메일 로그인 실패 5회 연속 시 15분 잠금(brute force 방어), 같은 IP의 `/api/auth/**` 요청은 60초에 20회로 제한(스캐닝·스팸 방어) — 둘 다 실제 서버 기동 후 curl로 재현 검증 | `global/security/LoginAttemptGuard.java`, `AuthRateLimitFilter.java` |
| 골든셋 Live 커버리지 확장 | fixture만으로 확장 가능한 4건(`stale-price`, `stale-fx`, `transaction-evidence`, `price-direction`)을 추가해 5개 → 9개로, 이어서 `searchSymbolEvidence` Agent Tool을 연결해 사용자 등록 근거 자료 7건을 추가해 9개 → 16개로 확장. 나머지 2개(`news-correlation`, `future-document`)는 멀티 Tool 체이닝·날짜 파싱이 필요해 포함하지 않음(아래 갭 참고) | `evidence/document/SymbolEvidenceService.java`, `SymbolEvidenceAnswerGuardrail.java`, `LiveEvaluationBatchQueueService.SUPPORTED_CASES` |
| 거래 멱등성 키 | 매수·매도·입금·출금 4개 API가 `Idempotency-Key` 헤더를 필수로 받아, 네트워크 재시도·버튼 중복 클릭으로 같은 요청이 두 번 와도 한 번만 체결. DB unique 제약(`user_id`, `idempotency_key`)으로 동시 요청 경합도 막고, 같은 키에 다른 요청 본문이 오면 `409 IDEMPOTENCY_KEY_REUSED`로 거부. 24시간 뒤 자동 만료 | `domain/transaction/service/TransactionIdempotencyService.java`, `V9__idempotency_keys.sql` |
| Refresh Token 정리 작업 | 만료된 지 7일 지난 Refresh Token 행을 매일 자동 삭제. 재사용 탐지(`rotate()`가 폐기 여부를 만료 여부보다 먼저 검사)에 잠시 쓰일 수 있어 만료 즉시가 아니라 유예 기간을 두고 지움 — 그전까지는 `refresh_tokens` 테이블이 무기한 쌓였음 | `global/security/RefreshTokenService.evictExpiredTokens()` |
| Graceful shutdown | SIGTERM 수신 시 새 요청을 받지 않고 진행 중인 요청을 최대 30초까지 기다린 뒤 종료. Docker 종료 유예는 35초로 두어 애플리케이션보다 먼저 SIGKILL하지 않게 함 | `application.yml`, `docker-compose.yml`, `deploy/aws/docker-compose.yml` |

## 남은 갭

우선순위 순서가 아니라 카테고리별로 나열합니다. 각 항목 끝의 굵은 글씨가 "왜 아직 안 했는지"입니다.

### 보안

- **로그인 잠금·IP 제한이 메모리 상태다.** `LoginAttemptGuard`/`AuthRateLimitFilter` 둘 다 `ConcurrentHashMap`에 상태를 둔다. 단일 인스턴스에서는 문제없지만 인스턴스를 늘리면 인스턴스마다 카운터가 따로 놀아 우회가 쉬워진다 — Redis 같은 공유 저장소로 옮겨야 한다. **지금은 단일 인스턴스 MVP라 별도 인프라 없이 바로 동작하는 쪽을 택했다.**
- **회원가입·이메일 중복확인에는 이메일 단위 잠금이 없다.** IP 단위 전역 제한(`AuthRateLimitFilter`)은 걸리지만, 특정 이메일을 겨냥한 시도를 막는 장치는 로그인에만 있다.
- **Secrets가 환경변수뿐이다.** AWS Secrets Manager, Vault 같은 별도 비밀 관리 시스템 연동이 없다. **단일 인스턴스 개인 배포 규모에서는 환경변수로 충분해서다.**
- **감사 로그(audit log)가 없다.** 계정 삭제, 비밀번호 변경 같은 민감 액션이 애플리케이션 로그에만 남고 별도 감사 트레일로 분리돼 있지 않다.
- **CORS 정책이 명시돼 있지 않다.** 지금은 정적 리소스와 API가 같은 origin에서 서빙되어 필요 없지만, 프런트엔드를 분리 배포하면 그때 반드시 설정해야 한다.

### 신뢰성 · 장애 대응

- **다중 인스턴스를 전제하지 않는다.** News 요약·수집 Worker는 프로세스 내부 동기화로 중복 작업을 줄이는데, 이는 단일 인스턴스에서만 유효하다. 인스턴스를 늘리려면 DB 기반 claim 락이나 메시지 큐가 필요하다.
- **외부 API 호출에 Circuit Breaker가 없다.** Yahoo Finance·Binance·Upbit·NVIDIA NIM 호출이 각자의 timeout에만 의존하고, 연속 실패를 감지해 자동으로 호출을 줄이는 회로 차단기가 없다(Resilience4j 등 미도입).
- **외부 API 실패 재시도 정책이 source마다 분리돼 있지 않다.** 뉴스 수집 실패는 안전한 오류 코드만 기록할 뿐, 지수 백오프나 source별 재시도 한도는 없다.

### 관측가능성

- **분산 트레이싱이 없다.** Agent Trace는 자체 DB 테이블에만 남고, OpenTelemetry 같은 표준 트레이싱으로 내보내지 않는다. 외부 APM(Grafana, Datadog 등) 연동도 없다.
- **구조화된 로깅이 아니다.** 기본 Spring Boot 로그 포맷을 그대로 쓴다. 로그 집계 시스템(예: ELK)에 붙이려면 JSON 로깅으로 바꿔야 한다.
- **Trace·로그 보존 기간이 정해져 있지 않다.** Agent Trace, 평가 배치 기록이 무기한 쌓인다. 실제 운영 전 보존·삭제 정책이 필요하다.

### 데이터 · 컴플라이언스

- **골든셋 커버리지가 거의 끝났지만 2개가 남았다.** 금융 Evidence 골든셋 18개 중 16개는 실제 NIM으로 실행 가능하다. 남은 `news-correlation`(한 질문에 `getAssetEvidence`+`searchSymbolEvidence` 두 Tool 결과를 합쳐야 함)과 `future-document`(질문 속 날짜로 `publishedAt`을 필터링해야 함)는 질문당 Tool을 하나만 호출하는 현재 Agent 구조로는 풀리지 않는다. 멀티 Tool 체이닝은 비용·지연이 늘어나는 설계 변경이라 의도적으로 미뤘다.
- **뉴스 출처가 하나다.** 실제로 수집하는 출처는 Zcash Foundation의 Zebra GitHub Releases뿐이라, 화면의 Companies/Macro/FX/Geopolitics 분류는 아직 계약(향후 Adapter를 위한 인터페이스)일 뿐 실제 커버리지가 아니다.

## 지금부터 순서대로 하나만 고른다면

1. **다중 인스턴스 대비 분산 락.** 실제로 인스턴스를 늘릴 계획이 생기기 전까지는 우선순위가 낮다 — 지금 단일 인스턴스 MVP에는 과설계다. 늘리기로 하면 `LoginAttemptGuard`/`AuthRateLimitFilter`의 메모리 상태도 이때 같이 옮겨야 한다.
2. **`news-correlation`/`future-document` 멀티 Tool 체이닝.** golden set 18개 중 마지막 2개. 비용·지연이 늘어나는 설계 변경이라 실제로 필요해지기 전까지는 미룬다.

MyData 연동, 지갑 자동 연동, 주문 실행처럼 이 프로젝트의 문제 정의 자체를 벗어나는 확장은 이
목록에 넣지 않았습니다. 각 기능의 세부 한계는 [README](../README.md)의 "의도적으로 남긴
한계"와 [Shared News Feed](SHARED_NEWS_FEED.md), [Financial Evidence Agent](FINANCIAL_EVIDENCE_AGENT.md)
문서에 있습니다.
