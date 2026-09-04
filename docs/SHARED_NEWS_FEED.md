# Shared News Feed

## 목적과 경계

사용자마다 등록 symbol의 뉴스를 다시 수집하지 않고, 출처별 자료를 애플리케이션 공용으로 한 번 저장한 뒤 전체 시장 또는 내 자산 관련 자료로 조회한다. 이는 매매 신호 생성기가 아니라 원문 링크가 있는 읽기 전용 Evidence Feed다.

현재 실제 수집 범위는 **Zcash Foundation의 Zebra GitHub Releases**뿐이다. 화면의 Company, Macro, FX, Geopolitics 분류와 Source 인터페이스는 다음 Adapter를 위한 계약이며 해당 분야의 자동 수집이 완료됐다는 뜻은 아니다.

## 처리 흐름

```text
POST /api/news/refresh
→ 활성 작업 또는 6시간 이내 성공 작업 재사용
→ durable news_refresh_jobs에 PENDING 저장
→ 백그라운드 Worker가 화이트리스트 Source 호출
→ (source_key, external_id) 기준 upsert와 content hash 비교
→ 새 content hash만 별도 Worker가 NIM 한국어 요약
→ GET /api/news?scope=ALL|PORTFOLIO 로 조회
```

- 외부 HTTP 요청은 DB 트랜잭션 밖에서 실행한다.
- 공식 출처 Adapter가 지정한 symbol과 topic만 저장한다.
- API 응답에는 전문 대신 짧은 excerpt와 원문 URL을 노출한다.
- 외부 내용은 검증된 공식 출처여도 LLM 명령이 아닌 `untrustedContent`로 취급한다.
- 요약은 `PENDING/RUNNING/COMPLETED/FAILED` 상태와 모델·프롬프트 버전·지연·토큰을 함께 저장한다.
- 요약에 가격 전망·투자 권유·원문에 없는 숫자가 있으면 저장하지 않고 원문 excerpt로 돌아간다.
- 완료된 요약도 공식 원문 일부와 원문 링크를 함께 보여준다.
- 사용자나 자산을 삭제해도 공용 뉴스는 유지한다. `PORTFOLIO` 조회에서 활성 자산 symbol이 사라질 뿐이다.

## 비용과 운영 제어

- 사용자별 복제 수집 없음
- 진행 중 작업 합치기
- 성공 작업 6시간 재사용
- 외부 id unique constraint와 hash 기반 변경 감지
- 한 번에 최대 10개 Release 수집
- 수집 실패는 안전한 오류 코드만 작업에 기록
- Agent 검색 결과가 없으면 NIM을 호출하지 않고 `UNAVAILABLE` 반환
- 일반 Agent 질문은 결정적 Tool 라우팅으로 모델 호출을 한 번으로 제한
- News 요약은 사용자별 요청이 아니라 새 공용 자료당 한 번만 실행
- 같은 `contentHash`는 완료 요약을 재사용하고 원문 변경 시에만 재요약

`APP_AI_ENABLED=true`이면 기본적으로 요약 Worker도 활성화된다. Agent는 사용하되 자동 요약 비용을 막으려면 `APP_NEWS_SUMMARY_ENABLED=false`를 설정한다. AI가 꺼졌거나 요약에 실패해도 뉴스 조회와 공식 원문 링크는 정상 동작한다.

단일 애플리케이션 인스턴스 MVP에서는 프로세스 내부 동기화로 중복 enqueue를 줄인다. 다중 인스턴스 운영 전에는 DB 기반 claim 락 또는 별도 작업 큐가 필요하다.

## API

| Method | Endpoint | 역할 |
|---|---|---|
| GET | `/api/news` | 전체/내 자산, category, 검색어, limit 기준 피드 조회 |
| GET | `/api/news/sources` | 화이트리스트 출처와 마지막 성공 시각 조회 |
| POST | `/api/news/refresh` | 비동기 공식자료 갱신 요청 |
| GET | `/api/news/refresh-jobs/{jobId}` | 작업 상태와 수집/삽입/변경 건수 조회 |

## 다음 확장 기준

1. DART, SEC, 기업 IR처럼 공식 API 또는 재배포 조건이 명확한 출처부터 추가한다.
2. 일반 언론은 robots, 약관, 저작권을 확인하고 제목·짧은 요약·원문 링크 중심으로 제한한다.
3. Source별 요청 제한, 재시도, 지수 백오프, 마지막 성공/실패 지표를 분리한다.
4. 다중 인스턴스에서는 작업 claim을 DB 락 또는 메시지 큐로 단일화한다.
5. 한국어 요약 골든셋을 추가해 사실 보존, 숫자 지지 여부, Prompt Injection, 가격 인과 표현을 평가한다.
6. 수집 품질과 Agent 답변은 출처 정확도, 시점 정확도, 인용 누락, 근거 없는 인과로 평가한다.
