# Folio Financial Evidence Agent

## 1. 문제 정의

Folio의 AI 기능은 투자 결정을 대신하거나 가격 변동 원인을 단정하는 챗봇이 아니다. 사용자가 등록한 자산에 대해 다음 질문을 **계산 근거와 출처를 함께 제시하며** 답하는 읽기 전용 분석 도구다.

- 현재 평가금액과 손익은 어떤 가격·환율·평단으로 계산됐는가?
- 가격이나 환율을 확인할 수 없거나 오래된 자산이 있는가?
- 관련 공식자료·뉴스는 무엇이며 언제 발표됐는가?
- 근거가 부족해 답할 수 없는 부분은 무엇인가?

## 2. 현재 구현 범위

### 계산 Evidence

`GET /api/ai/evidence/assets/{assetId}`

- Asset 소유권을 먼저 검증한다.
- 현재가, 현재 환율, 평균 매입 단가, 평가금액, 미실현·실현손익을 반환한다.
- 계산 공식을 문자열 계약으로 함께 반환한다.
- 최근 거래 최대 20건과 전체 건수를 함께 반환한다. 일부만 반환했으면 `truncated=true`다.
- 결과는 `CONFIRMED`, `PARTIAL`, `UNAVAILABLE` 중 하나다.

| 경고 | 의미 |
|---|---|
| `PRICE_MISSING` | 현재가를 확보하지 못함 |
| `PRICE_STALE` | 현재가가 TTL을 넘김 |
| `FX_MISSING` | 외화 자산의 원화 환율을 확보하지 못함 |
| `FX_STALE` | 환율이 TTL을 넘김 |
| `COST_BASIS_MISSING` | 평단이 없어 평가손익을 확정할 수 없음 |

환율이 없으면 `1`, 현재 환율 추정값, `0`을 넣지 않는다. 평가금액을 계산할 수 없으면 `UNAVAILABLE`과 `null`을 반환한다.

### Symbol Evidence Inbox

`POST /api/assets/{assetId}/evidence-documents`

사용자는 등록된 STOCK/CRYPTO 자산에 공식자료, 뉴스, 개인 메모를 붙여넣을 수 있다.

```json
{
  "sourceType": "OFFICIAL",
  "title": "Example quarterly results",
  "publisher": "Example Corp",
  "sourceUrl": "https://example.com/investors/results",
  "publishedAt": "2026-09-01T00:00:00Z",
  "content": "붙여넣은 원문"
}
```

- `OFFICIAL`, `NEWS`는 발행처·원문 URL·발표 시각이 필수다.
- URL은 저장만 하며 서버가 자동으로 접속하지 않는다.
- 같은 자산에 공백만 다른 동일 본문을 다시 등록하면 `409`로 거부한다.
- DART·OpenDART·KIND·SEC 화이트리스트 URL은 `VERIFIED_OFFICIAL`로 승격한다.
- 그 밖의 URL은 사용자 선택을 외부 검증으로 오인하지 않도록 `USER_ASSERTED_OFFICIAL`, `USER_ASSERTED_NEWS`, `USER_PROVIDED`로 표시한다.
- 계정 탈퇴 시 등록 문서도 함께 영구 삭제한다.

문서 삭제는 애플리케이션 서비스가 거래·자산보다 먼저 명시적으로 수행한다. DB FK는 `CASCADE` 없이 고아 데이터만 차단한다. 삭제 순서와 실패를 서비스 테스트에서 확인하기 위한 선택이다.

### 검색

`GET /api/assets/{assetId}/evidence-documents/search?query=...`

현재 단계는 제목·발행처·본문의 결정적 키워드 검색이다. 제목 일치에 가장 높은 가중치를 두며 최대 10건의 짧은 snippet만 반환한다. 이 결과는 임베딩 검색의 품질을 비교할 기준선이다.

### 최소 평가 하네스와 Trace

`AgentRunResult`를 18개 골든셋 계약과 비교하는 `RuleBasedFinancialEvidenceScorer`를 추가했다. 실제 DB 합성 자산을 사용하는 `fresh-valuation`, `missing-price`, `missing-fx`, `missing-cost-basis` 4개 사례, 가격 이력을 사용하는 `price-direction`, 공식자료를 사용하는 `symbol-official-news` 사례로 Tool·결론·필수 근거와 hard failure를 검증한다.

LLM 응답은 `AgentModelResponse.finalAnswer`만 생성한다. `AssetEvidenceFactExtractor`가 `AssetEvidenceService`의 warning·null·계산 필드에서 evidenceFacts를 만들고, `EvidenceConclusionPolicy`가 Tool 결론을 조합한다. `GroundedAgentRunAssembler`는 완료된 Tool Trace와 결정적 Tool 결과의 이름이 다르면 실행 결과 생성을 거부한다.

실행 기록은 `AGENT/MODEL/TOOL/RETRIEVAL/GUARDRAIL/EVALUATION` Span을 `parentSpanId`로 연결한다. `GET /api/ai/traces`와 `GET /api/ai/traces/{traceId}`로 본인 기록만 조회할 수 있다. 질문·답변·문서·거래 원문은 저장하지 않고 해시, 상태, 오류 코드, 참조 id, 지연, 토큰, 모델·프롬프트 버전만 저장한다.

### NVIDIA NIM LLM 연결

`POST /api/ai/agent/assets/{assetId}/ask`

NVIDIA NIM의 OpenAI 호환 Chat Completions를 사용하는 첫 단일 자산 Agent를 연결했다.

```text
질문 + 경로 assetId
→ 결정적 질문 분류기가 자산 계산·가격 방향·심볼 공식자료 근거를 선택
→ 일반 질문은 서버 라우터가 Tool을 선택하고, 평가는 NIM의 강제 Tool 선택까지 검증
→ 모델 Tool 이름과 assetId 검증
→ 인증 사용자 id로 선택된 읽기 전용 Tool Adapter 실행
→ 구조화된 Tool 결과를 NIM에 전달
→ 최종 한국어 답변 생성
→ 가격 방향 답변의 평가손익 오용·방향 모순·뉴스 인과를 출력 Guardrail로 검사
→ 결정적 fact/conclusion 조립
→ 원문 없는 Trace 저장
```

- 모델 기본값은 `nvidia/nemotron-3.5-lightning-30b-a3b`다.
- Agent는 기본적으로 꺼져 있으며 `APP_AI_ENABLED=true`일 때만 외부 호출한다.
- API 키는 `NVIDIA_API_KEY` 환경변수로만 받는다.
- 모델이 경로의 `assetId`와 다른 값을 Tool 인자로 반환하면 호출을 거부한다.
- 첫 모델 호출은 질문 의도에 맞는 단일 Tool을 강제하고, 두 번째 호출은 추가 Tool 사용을 금지한다.
- 일반 화면은 비용과 지연을 줄이기 위해 결정적 라우터 뒤 한 번만 NIM을 호출한다. 평가 API는 Tool Calling 배관 검증을 위해 두 번 호출한다.
- `enable_thinking=false`, `stream=false`로 구조화된 응답만 처리한다.
- 모델은 `finalAnswer`만 만들며 conclusion과 evidenceFacts는 Tool 결과에서 결정한다.
- 방향성 질문의 결론은 포트폴리오 평가손익이 아니라 최근 가격 이력의 상태로 결정한다.
- 가격 이력이 부족하면 두 번째 모델 호출을 생략하고 결정적인 `UNAVAILABLE` 답변을 반환한다.
- 관련 공식자료가 없으면 모델 호출 없이 `UNAVAILABLE`을 반환한다.
- 공식자료 본문은 `untrustedContent=true`인 인용 데이터이며, 가격 변동의 직접 원인으로 단정한 답변은 Guardrail이 교체한다.
- 모델이 평가손익을 추세 근거로 사용하면 안전한 서버 계산 문장으로 교체하고 Trace에 `priceTrendClaimValidation` 차단을 남긴다.
- 질문·답변 원문은 응답 시점에만 사용하고 Trace DB에는 SHA-256 해시만 저장한다.

현재 NIM Trial은 외부 서비스이므로 실제 실행 시 Tool payload가 NVIDIA로 전송된다. 개인 금융정보 대신 합성 데모 계정으로 먼저 검증해야 한다. 자동 테스트는 실제 NIM을 호출하지 않고 모의 HTTP 서버를 사용한다.

나머지 fixture의 실제 상태 구성과 일괄 Runner는 아직 연결하지 않았다.

### 공용 News와 Agent Tool

`GET /api/news`는 모든 사용자가 함께 쓰는 공식자료 저장소를 조회한다. `scope=PORTFOLIO`를 사용하면 현재 사용자의 활성 투자자산 symbol과 연결된 결과만 보여준다. 자산을 삭제해도 공용 자료 자체는 지우지 않고 해당 사용자의 필터에서만 빠진다.

현재 실제 수집 Adapter는 `ZCASH_ZEBRA_GITHUB` 하나이며 Zcash Foundation의 Zebra GitHub Releases만 다룬다. `POST /api/news/refresh`는 작업을 큐에 넣고, 백그라운드 Worker가 수집한다. 동일 출처·외부 id는 upsert하고, 진행 중 작업과 6시간 안의 성공 작업을 재사용한다. 일반 언론기사, DART, SEC, X 수집은 아직 구현하지 않았다.

새로운 `contentHash`는 별도 Summary Worker가 NIM으로 한국어 요약을 한 번 생성한다. 요약 상태, 모델, `news-summary-v1` 프롬프트 버전, 지연 시간과 토큰을 저장하며 같은 원문은 사용자 수와 무관하게 재사용한다. 모델이 길이 지시를 넘기면 마지막 완성 문장 경계에서 축약한 뒤 Guardrail을 적용한다. 가격·시세·매매 표현이나 원문에 없는 숫자를 포함한 출력은 저장하지 않고 원문 excerpt로 fallback하며, 이때 거부된 텍스트 대신 실패 코드와 호출 메타데이터만 보존한다. 완료 요약에도 공식 원문 일부와 링크를 함께 표시한다.

Agent의 `searchSymbolNews` Tool은 등록 자산의 소유권을 확인한 뒤 이 공용 저장소에서 최신 5건을 조회한다. 제목·발행처·발표시각·원문 URL·검증 상태만 근거로 사용하며, 뉴스 존재와 가격 움직임 사이의 인과관계는 만들지 않는다.

### 실제 NIM 단일 자산 평가

local 프로필에서는 실제 개인정보 대신 합성 자산으로 NIM 실행과 골든셋 채점을 관통할 수 있다.

1. `POST /api/ai/evaluations/fixtures/{caseId}`가 해당 케이스의 합성 자산을 만든다.
2. 반환된 `assetId`로 `POST /api/ai/evaluations/cases/{caseId}/assets/{assetId}/run`을 호출한다.
3. 일반 Agent와 동일한 NIM·Tool Adapter·답변 조립 경로를 실행한다.
4. 골든케이스의 Tool, 기대 결론, 필수 Evidence fact, 금지 주장을 채점한다.
5. 평가 결과를 `evaluation` 노드와 함께 Trace에 저장한다.

지원하는 caseId와 핵심 기대값은 다음과 같다.

| caseId | 기대 결론 | 핵심 Evidence |
|---|---|---|
| `fresh-valuation` | `CONFIRMED` | 수량·현재가·현재 환율·계산식 |
| `missing-price` | `UNAVAILABLE` | `PRICE_MISSING`, `valuationKrw=null` |
| `missing-fx` | `UNAVAILABLE` | `FX_MISSING`, `exchangeRate=null` |
| `missing-cost-basis` | `PARTIAL` | `COST_BASIS_MISSING`, `unrealizedPnlKrw=null` |

골든셋은 테스트뿐 아니라 local 실행에서도 같은 파일을 읽도록 `src/main/resources/evaluation`에 둔다. 평가용 fixture 생성 API는 `@Profile("local")`이라 prod 프로필에는 노출되지 않는다.

### 일반 화면

투자자산 상세의 `AI 근거 분석`에서 질문하면 `POST /api/ai/agent/assets/{assetId}/ask`를 호출한다. 화면은 답변과 함께 결론, Evidence reference id, 지연 시간, 토큰 사용량을 보여주며 `실행 Trace 보기`로 `GET /api/ai/traces/{traceId}`의 단계 트리를 펼친다. 화면 표시를 위해서도 질문·답변 원문을 Trace DB에 저장하지 않는다.

### 가격 방향 근거

`GET /api/ai/evidence/assets/{assetId}/price-trend`는 LLM 없이 최근 7개 일별 가격을 조회해 시작가·종료가·변화율과 방향을 반환한다. 변화율이 `+2%`를 초과하면 `UP`, `-2%` 미만이면 `DOWN`, 사이는 `FLAT`으로 판정한다. 가격 이력이 두 점 미만이면 `UNAVAILABLE`, 마지막 성공 이력만 사용하면 `PARTIAL`이다. 이 임계값은 투자 조언의 정답이 아니라 `financial-agent-v2`의 명시적인 MVP 서비스 규칙이다.

## 3. Guardrail

1. 계산 결과와 외부 문서 맥락을 분리한다.
2. 같은 기간에 뉴스가 존재해도 가격 변동의 직접 원인으로 단정하지 않는다.
3. 문서 원문은 항상 `untrustedContent=true`다. 원문 안의 “이전 지시를 무시하라” 같은 문장은 실행 명령이 아니라 인용 데이터다. 실제 악성 지시문 fixture를 DB에 저장하는 회귀 테스트도 유지한다.
4. 존재하지 않는 문서·타인 소유 문서는 모두 찾을 수 없는 것으로 처리한다.
5. 공식 도메인 화이트리스트와 일치한 URL만 `VERIFIED_OFFICIAL`이다. `sec.gov.attacker.example` 같은 접미사 위장 도메인은 사용자 주장 수준으로 남긴다.
6. 기사 전문의 자동 수집·재배포는 라이선스와 robots 정책을 확인하기 전에는 추가하지 않는다.
7. 평가손익률과 평균 매수가는 가격 방향 근거로 사용하지 않는다.
8. 가격 방향은 `getPriceTrendEvidence`의 서버 계산값과 공개된 임계값만 사용한다.

## 4. 다음 구현 순서

1. `symbol-official-news`의 실제 NIM 평가 실행과 전체 골든셋 일괄 Runner를 추가한다.
2. DART·SEC·기업 IR처럼 재배포 조건이 명확한 공식 출처 Adapter를 하나씩 추가한다.
3. 일반 언론은 전문 복제보다 제목·요약·원문 링크 중심의 라이선스 정책부터 확정한다.
4. Prompt Injection fixture를 공용 News Tool 입력까지 통과시키는 회귀 테스트를 추가한다.
5. NIM과 같은 OpenAI 호환 경계를 유지한 두 번째 제공자 프로필로 교체 가능성을 검증한다.
6. Spring AI Observability와 OpenTelemetry를 연결하고, 외부 Trace UI는 민감정보 정책을 확정한 뒤 선택한다.

## 5. 완료 기준

- 환율이 없는 외화 자산 질문은 거짓 숫자 없이 `UNAVAILABLE`이다.
- 평단이 없으면 평가금액과 손익 가능 여부를 구분한다.
- 다른 사용자의 자산·문서를 조회할 수 없다.
- 답변의 모든 외부 주장에 실제 document id와 URL이 연결된다.
- 질문 시점 이후 자료를 과거 분석 근거로 사용하지 않는다.
- 악성 지시문이 포함된 문서를 검색해도 Agent 동작 규칙이 바뀌지 않는다.
- 공식자료와 뉴스가 가격 변동의 직접 원인이라고 근거 없이 단정되지 않는다.
