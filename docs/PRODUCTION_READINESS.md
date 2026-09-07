# Production Readiness

Folio는 지금 개인 포트폴리오/MVP 단계입니다. 이 문서는 "코드를 안 짜고, 지금까지 확인된
갭을 있는 그대로 정리"한 것입니다 — 실제 서비스로 운영한다고 가정했을 때 무엇이 이미
되어 있고, 무엇이 아직 비어 있는지, 그리고 그 순서를 정리합니다.

## 완료된 항목

| 항목 | 구현 | 참고 |
|---|---|---|
| CI | push·PR마다 `./gradlew clean test` + API 키 패턴 검사 자동 실행 | `.github/workflows/ci.yml` |
| LLM 비용 거버넌스 | Financial Evidence Agent와 News 요약이 하루 호출 수·토큰 사용량 카운터를 공유, 초과 시 `429 AI_BUDGET_EXCEEDED`로 실제 호출 전에 차단 | `evidence/agent/LlmUsageBudgetService.java`, `GET /api/ai/usage/today` |
| 인증 세션 관리 | Access Token 30분 + 회전·폐기되는 Refresh Token(SHA-256 해시 저장). 로그인 family의 절대 수명은 기본 14일로 회전해도 연장되지 않고, 과거 token 재사용 시 family 전체 폐기. 로그아웃·비밀번호 변경·회원 탈퇴가 실제 세션과 family 데이터를 정리 | `global/security/RefreshTokenService.java`, `RefreshTokenFamily.java` |
| 비밀 관리 | API 키·JWT Secret은 환경변수로만 주입, 코드·설정 파일에 저장하지 않음. `prod` 프로필은 `APP_JWT_SECRET` 없으면 기동 자체가 실패 | `application.yml`, `JwtTokenProvider.java` |
| 데이터 삭제 | 회원 탈퇴 시 자산·거래·Snapshot·근거 문서·Agent Trace·평가 배치·Refresh Token까지 연쇄 삭제. 법적·운영 보존 정책이 필요한 내부 사용자 id·액션·시각의 최소 감사 기록만 별도 보존 | `UserAccountService.deleteAccount()` |
| 인가 | 소유권 기반 404(리소스 존재 여부 비노출), `SecurityContext` 기반 `userId`만 신뢰, 신규 API는 기본적으로 인증 필요(명시적으로 연 경로만 예외) | `SecurityConfig.java` |
| DB 마이그레이션 | Flyway로 스키마 이력 관리(`V1~V12`), `prod`는 `ddl-auto=validate`로 스키마 드리프트 방지 | `db/migration/` |
| 백업 | AWS 스테이징용 백업·복구·검증 스크립트 존재(수동 실행) | `deploy/aws/backup.sh`, `restore.sh`, `verify-backup.sh` |
| LLM 안전장치 | 숫자 조작·가격 인과·Prompt Injection·문장수·비정상 토큰을 규칙 기반으로 차단, 골든셋으로 회귀 검증 | `evidence/news/NewsAnswerGuardrail.java`, `news/NewsSummaryGuardrail.java` |
| 인증 API rate limit | 같은 이메일 로그인 실패 5회 연속 시 15분 잠금(brute force 방어), 같은 IP의 `/api/auth/**` 요청은 60초에 20회로 제한(스캐닝·스팸 방어) — 둘 다 실제 서버 기동 후 curl로 재현 검증 | `global/security/LoginAttemptGuard.java`, `AuthRateLimitFilter.java` |
| 골든셋 Live 커버리지 확장 | fixture만으로 확장 가능한 4건(`stale-price`, `stale-fx`, `transaction-evidence`, `price-direction`)을 추가해 5개 → 9개로, 이어서 `searchSymbolEvidence` Agent Tool을 연결해 사용자 등록 근거 자료 7건을 추가해 9개 → 16개로 확장. 나머지 2개(`news-correlation`, `future-document`)는 멀티 Tool 체이닝·날짜 파싱이 필요해 포함하지 않음(아래 갭 참고) | `evidence/document/SymbolEvidenceService.java`, `SymbolEvidenceAnswerGuardrail.java`, `LiveEvaluationBatchQueueService.SUPPORTED_CASES` |
| 거래 멱등성 키 | 매수·매도·입금·출금 4개 API가 공백이 아닌 1~255자의 `Idempotency-Key`를 필수로 받아 같은 요청을 한 번만 체결. DB unique 제약으로 경합을 막고, 완료 시 최초 `TransactionResponse` JSON을 같은 트랜잭션에 저장해 이후 자산 변경·거래 삭제와 무관하게 그대로 재생. 같은 키의 다른 본문은 `409 IDEMPOTENCY_KEY_REUSED`, 24시간 뒤 자동 만료 | `TransactionIdempotencyService.java`, `V9__idempotency_keys.sql`, `V11__idempotency_response_snapshot.sql` |
| Refresh Token family 수명·정리 | family 상태와 절대 만료를 별도 row로 관리하고 회전해도 기본 14일 한도를 연장하지 않음. 활성 family의 과거 token hash는 재사용 탐지를 위해 보존하고, family 절대 만료 7일 뒤 token과 family를 함께 삭제 | `RefreshTokenFamily.java`, `V12__refresh_token_families.sql`, `RefreshTokenService.evictExpiredTokens()` |
| Graceful shutdown | SIGTERM 수신 시 새 요청을 받지 않고 진행 중인 요청을 최대 30초까지 기다린 뒤 종료. Docker 종료 유예는 35초로 두어 애플리케이션보다 먼저 SIGKILL하지 않게 함 | `application.yml`, `docker-compose.yml`, `deploy/aws/docker-compose.yml` |
| 민감 액션 감사 로그 | 비밀번호 변경·회원 탈퇴 성공을 내부 사용자 id·액션·시각만 별도 append-only 테이블에 기록. 업무 변경과 같은 트랜잭션에 참여해 실패한 변경을 성공으로 기록하지 않고, users FK를 두지 않아 탈퇴 후에도 보존 | `global/audit`, `V10__audit_logs.sql`, `UserAccountService.java` |
| 감사·Trace·평가 기록 보존 | 감사 로그 365일, 일반 Agent Trace 30일, 완료·실패 평가 배치 90일의 운영 기본값을 두고 매일 500건 단위 SQL로 자동 정리. 보존 중인 평가 배치가 참조하는 Trace와 진행 중인 배치는 삭제하지 않으며 기간은 환경변수로 조정. 격리 MySQL에서 V1~V13, 운영 schema validation, 실제 Scheduler 삭제까지 확인 | `global/retention`, `V13__retention_cleanup_indexes.sql` |

## 남은 갭

우선순위 순서가 아니라 카테고리별로 나열합니다. 각 항목 끝의 굵은 글씨가 "왜 아직 안 했는지"입니다.

### 보안

- **로그인 잠금·IP 제한이 메모리 상태다.** `LoginAttemptGuard`/`AuthRateLimitFilter` 둘 다 `ConcurrentHashMap`에 상태를 둔다. 단일 인스턴스에서는 문제없지만 인스턴스를 늘리면 인스턴스마다 카운터가 따로 놀아 우회가 쉬워진다 — Redis 같은 공유 저장소로 옮겨야 한다. **지금은 단일 인스턴스 MVP라 별도 인프라 없이 바로 동작하는 쪽을 택했다.**
- **회원가입·이메일 중복확인에는 이메일 단위 잠금이 없다.** IP 단위 전역 제한(`AuthRateLimitFilter`)은 걸리지만, 특정 이메일을 겨냥한 시도를 막는 장치는 로그인에만 있다.
- **Secrets가 환경변수뿐이다.** AWS Secrets Manager, Vault 같은 별도 비밀 관리 시스템 연동이 없다. **단일 인스턴스 개인 배포 규모에서는 환경변수로 충분해서다.**
- **CORS 정책이 명시돼 있지 않다.** 지금은 정적 리소스와 API가 같은 origin에서 서빙되어 필요 없지만, 프런트엔드를 분리 배포하면 그때 반드시 설정해야 한다.

### 신뢰성 · 장애 대응

- **다중 인스턴스를 전제하지 않는다.** News 요약·수집 Worker는 프로세스 내부 동기화로 중복 작업을 줄이는데, 이는 단일 인스턴스에서만 유효하다. 인스턴스를 늘리려면 DB 기반 claim 락이나 메시지 큐가 필요하다.
- **외부 API 호출에 Circuit Breaker가 없다.** Yahoo Finance·Binance·Upbit·NVIDIA NIM 호출이 각자의 timeout에만 의존하고, 연속 실패를 감지해 자동으로 호출을 줄이는 회로 차단기가 없다(Resilience4j 등 미도입).
- **외부 API 실패 재시도 정책이 source마다 분리돼 있지 않다.** 뉴스 수집 실패는 안전한 오류 코드만 기록할 뿐, 지수 백오프나 source별 재시도 한도는 없다.

### 관측가능성

- **분산 트레이싱이 없다.** Agent Trace는 자체 DB 테이블에만 남고, OpenTelemetry 같은 표준 트레이싱으로 내보내지 않는다. 외부 APM(Grafana, Datadog 등) 연동도 없다.
- **구조화된 로깅이 아니다.** 기본 Spring Boot 로그 포맷을 그대로 쓴다. 로그 집계 시스템(예: ELK)에 붙이려면 JSON 로깅으로 바꿔야 한다.
- **애플리케이션 로그의 외부 보존 정책은 배포 환경에 맡겨져 있다.** DB의 감사·Agent Trace·평가 배치는 기간과 삭제 작업을 정했지만, stdout 로그는 별도 집계·보존 시스템이 없다. **현재 단일 EC2/Docker 스테이징에는 중앙 로그 저장소가 없어서다.**

### 데이터 · 컴플라이언스

- **골든셋 커버리지가 거의 끝났지만 2개가 남았다.** 금융 Evidence 골든셋 18개 중 16개는 실제 NIM으로 실행 가능하다. 남은 `news-correlation`(한 질문에 `getAssetEvidence`+`searchSymbolEvidence` 두 Tool 결과를 합쳐야 함)과 `future-document`(질문 속 날짜로 `publishedAt`을 필터링해야 함)는 질문당 Tool을 하나만 호출하는 현재 Agent 구조로는 풀리지 않는다. 멀티 Tool 체이닝은 비용·지연이 늘어나는 설계 변경이라 의도적으로 미뤘다.
- **뉴스 출처가 하나다.** 실제로 수집하는 출처는 Zcash Foundation의 Zebra GitHub Releases뿐이라, 화면의 Companies/Macro/FX/Geopolitics 분류는 아직 계약(향후 Adapter를 위한 인터페이스)일 뿐 실제 커버리지가 아니다.

## 지금부터 순서대로 하나만 고른다면

1. **외부 API Circuit Breaker와 source별 재시도.** 단일 인스턴스에서도 Yahoo/Binance/Upbit/NIM 장애 전파를 줄이는 실효가 있다. retry 가능한 오류와 즉시 실패할 오류를 먼저 분리한 뒤 도입한다.
2. **GitHub Actions major 업데이트.** 정합성·보안 수정이 끝난 뒤 `setup-java`, `checkout`, `setup-gradle`을 공식 최신 안정 major로 함께 올리고 실제 GitHub 실행을 확인한다.

CI의 `actions/setup-java@v5` 전환은 실제 GitHub 실행에 성공해 현재 기능 문제는 없다. 다만 2026-09-05 기준
공식 최신 안정판은 v6이고 `actions/checkout`, `gradle/actions/setup-gradle`도 새 major가 있으므로, 위 정합성·보안
수정 뒤 별도 유지보수 커밋으로 함께 갱신한다.

다중 인스턴스 대비 분산 락과 `news-correlation`/`future-document` 멀티 Tool 체이닝은 실제 확장 계획이 생기기
전까지 계속 미룬다. 지금 단일 인스턴스 개인 MVP에는 각각 인프라·비용·지연 대비 우선순위가 낮다.

MyData 연동, 지갑 자동 연동, 주문 실행처럼 이 프로젝트의 문제 정의 자체를 벗어나는 확장은 이
목록에 넣지 않았습니다. 각 기능의 세부 한계는 [README](../README.md)의 "의도적으로 남긴
한계"와 [Shared News Feed](SHARED_NEWS_FEED.md), [Financial Evidence Agent](FINANCIAL_EVIDENCE_AGENT.md)
문서에 있습니다.
