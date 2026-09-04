# Agent Trace & Minimum Evaluation Harness

## 목적

한 번의 금융 Agent 요청이 어떤 모델·Tool·검색·Guardrail 단계를 거쳐 끝났는지 트리로 남기고, 같은 실행 결과를 골든셋과 결정적으로 비교한다. 모델의 비공개 추론 과정이나 사용자의 금융 원문을 저장하는 기능이 아니다.

## 현재 구현

### 실행 결과 계약

`AgentRunResult`는 LLM 제공자와 무관하게 다음 값을 받는다.

- `traceId`, `promptVersion`, `model`
- 실행 상태와 `CONFIRMED/PARTIAL/UNAVAILABLE` 결론
- 최종 답변
- `parentSpanId`로 연결된 실행 단계 목록
- 채점할 구조화된 근거와 금융 안전 위반 코드
- 총 지연 시간과 입력·출력 토큰 수

최종 답변은 실행 중 채점에만 사용한다. DB에는 질문과 답변의 SHA-256 해시만 남기며 원문은 저장하지 않는다.

### LLM과 결정적 근거의 경계

모델 제공자가 반환하는 `AgentModelResponse`에는 `finalAnswer`, 모델명, 지연 시간, 토큰 사용량만 있다. 모델이 `conclusion`이나 `evidenceFacts`를 생성할 수 있는 필드는 두지 않았다.

```text
AssetEvidenceService
→ AssetEvidenceToolAdapter
→ AssetEvidenceFactExtractor
→ GroundedToolResult
→ EvidenceConclusionPolicy
→ GroundedAgentRunAssembler
→ AgentRunResult
```

`AssetEvidenceFactExtractor`는 warning 코드와 구조화된 null을 그대로 변환한다. 예를 들어 현재 환율이 없는 응답은 `FX_MISSING`, `exchangeRate=null`, `valuationKrw=null`이 된다. `EvidenceConclusionPolicy`는 실제로 호출한 필수 Tool 결과 중 가장 보수적인 결론을 사용한다.

`AssetEvidenceToolAdapter.execute(userId, assetId)`의 `userId`는 모델 Tool 인자가 아니다. 인증 컨텍스트의 사용자 id를 애플리케이션이 주입하므로 모델이 다른 사용자 id를 선택할 수 없다.

### Trace 트리

Span 유형은 `AGENT`, `ROUTER`, `MODEL`, `TOOL`, `RETRIEVAL`, `GUARDRAIL`, `EVALUATION`이다. 일반 질문의 결정적 Tool 선택은 `ROUTER`, 평가의 NIM 강제 Tool 선택은 `MODEL`로 구분한다. 루트는 정확히 하나의 `AGENT` Span이어야 하며 모든 자식은 존재하는 부모를 가리켜야 한다. 중복 id, 부모 누락, 순환 참조는 저장 전에 거부한다.

각 Span에는 다음 정보만 저장한다.

- 안전한 연산 이름
- 성공·오류·차단 상태
- 지연 시간
- 오류 메시지가 아닌 오류 코드
- 문서 id, 자산 id 같은 불투명 참조 id

참조 id에 공백이나 원문 문장이 들어오면 저장을 거부한다.

### 규칙 기반 채점

`RuleBasedFinancialEvidenceScorer`는 다음을 PASS/FAIL로 비교한다.

1. 필요한 Tool을 모두 호출했는가
2. 기대하지 않은 Tool을 호출하지 않았는가
3. Tool 실행이 성공했는가
4. 기대 결론과 실제 결론이 같은가
5. 필수 근거가 구조화된 결과에 포함됐는가
6. 금지 주장이 최종 답변에 포함됐는가
7. 금융 안전 위반 코드가 발생했는가

거짓 숫자, stale 값을 현재값으로 주장, 인용 누락, 근거 없는 인과 단정, 타인 접근, Prompt Injection 실행, 미래 문서 사용, 미검증 출처의 공식자료 표시는 `hardFailure`다.

현재 `forbiddenClaims` 검사는 1차 문자열 기준이다. 문장을 부정하거나 인용한 경우의 오탐을 줄이는 의미 기반 판정은 LLM 연결 뒤 별도 평가기로 확장한다.

### 저장과 조회

| 테이블 | 역할 |
|---|---|
| `ai_agent_runs` | 모델·프롬프트 버전·결론·지연·토큰·원문 해시 |
| `ai_agent_spans` | parent span으로 연결한 실행 트리 |
| `ai_evaluation_results` | case별 통과 여부와 실패 코드 |

읽기 전용 API:

- `GET /api/ai/traces?limit=20`
- `GET /api/ai/traces/{traceId}`

모든 조회는 현재 사용자 id를 포함한다. 다른 사용자의 traceId는 존재하지 않는 기록처럼 처리한다. 계정 탈퇴 시 평가 결과 → Span → Run 순서로 명시적으로 삭제한다.

## 현재 검증 범위

- 실제 DB에 정상 계산·현재가 누락·현재 환율 누락·원가 누락 합성 자산을 만들고 `AssetEvidenceService → Tool Adapter → FactExtractor → Assembler → RuleBasedScorer → Trace 저장` 경로가 통과한다.
- 모델 응답 타입에는 conclusion과 evidenceFacts가 없고, 완료된 Tool Trace와 결정적 Tool 결과가 다르면 조립을 거부한다.
- 거짓 금융값 위반은 `hardFailure`다.
- Trace가 루트 → 모델 → Tool → Guardrail → 모델 및 평가 단계로 조회된다.
- 질문·답변 원문은 API와 DB에 저장되지 않는다.
- 원문 형태의 reference id는 거부한다.
- 타인 Trace는 조회할 수 없다.
- 방향성 질문은 `getPriceTrendEvidence`로 라우팅되고 평가손익률은 추세 근거에서 제외된다.
- 출력 Guardrail이 모델 답변을 교체하면 안전한 응답은 반환하되 평가는 `GUARDRAIL_INTERVENED`로 실패해 모델 품질 문제를 숨기지 않는다.

NVIDIA NIM Agent 실행기를 연결했다. 일반 화면은 결정적 라우터로 읽기 전용 Tool을 고른 뒤 답변 생성에만 NIM을 호출하고, 평가 API는 질문 의도에 맞는 단일 Tool 강제 호출까지 NIM으로 검증한다. 자동 테스트는 API 비용과 외부 상태에 영향을 받지 않도록 모의 NIM 서버로 Chat Completions 요청을 검증한다. 계산, 가격 방향, 심볼 공식자료 Tool과 출력 Guardrail, Trace 저장까지 연결되어 있다. 실제 모델 일괄 Runner와 나머지 fixture의 Evidence 상태 구성은 다음 단계다.

## 사용자 판단이 필요한 시점

| 선택 | 현재 기본값 | 결정이 필요한 시점 |
|---|---|---|
| LLM 제공자·모델 | NVIDIA NIM / Nemotron 3.5 Lightning | 두 번째 제공자 비교 시 |
| 질문·답변 원문 저장 | 저장하지 않음 | 원문 기반 장애 재현이 꼭 필요할 때 |
| Trace 보존 기간 | 자동 삭제 없음 | 실제 사용자 운영 전 |
| 외부 Observability | 연동하지 않음 | Trace UI가 필요할 때 |
| Tool 추가 호출 허용 | 골든셋과 정확히 일치 | Agent가 불필요한 호출 없이 안정화된 뒤 |

실사용 금융 데이터가 외부 Observability로 전송될 수 있으므로 원문 수집은 기본적으로 켜지 않는다. 평가 fixture처럼 개인정보가 없는 합성 데이터만 별도 개발 프로필에서 원문을 허용하는 방향이 안전하다.

## 다음 단계

```text
나머지 fixture별 결정적 Tool 결과 builder
→ Prompt Injection Tool 경계
→ Symbol 문서 읽기 전용 Tool Adapter
→ FinancialAgentModelClient 제공자 포트와 NIM 구현 완료
→ 결정적 질문 라우터와 자동 Tool 선택 평가 분리
→ 동일 하네스로 실제 모델 결과 채점
→ OpenTelemetry/Langfuse 선택 연동
```
