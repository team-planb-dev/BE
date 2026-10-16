# P2 후속 1~3 설계와 구현 진행

기준: dev `bc6a7f12b2e1aabc94bf47d650056484b35ea33f`, Issue #199, 작업 브랜치 `codex/199-refactor-pre-dag-edit-policies-and-selection-quality`.
상태: 구현·전체 무료 회귀 1,111건 통과. 독립 리뷰 수명/예산 9.3, 영양/재선택/점심 9.2, Critical/Important 0. 실제 생성12·REST4·STOMP1·독립 영양8 검증 완료. dev PR #200으로 반영 진행, 병합 상태는 GitHub PR과 계획서v30 참조. main 자동 동기화 제외.

## 1. 위임된 권장 정책

- 사용자 grilling 권장안 위임에 따른 결정. 세부 질문으로 중단하지 않는 진행.
- SQL travel 행 잠금으로 요청 시작·완료·취소·확정 관리. AI는 DB 트랜잭션 밖 유지.
- 동일 여행의 마지막 시작 요청 우선. 요청 UUID별 Redis payload, 완료 이후 이전 요청의 저장·게시 차단.
- 취소는 결과 무효화이며 이미 시작된 외부 호출의 interrupt 보장과 별개.
- token-aware confirm/cancel 및 미리보기 GET 추가. 기존 token-less 호출은 현재 요청 대상으로 유지.
- 확정은 Redis GETDEL 대신 SQL 저장 성공 뒤 정리. SQL 실패 시 미리보기와 요청 상태 보존.
- 5분 내 동일 확정 토큰은 재저장 없이 결과 재전송. Redis 실패 시 DB 일정 복원. candidateId 미저장으로 바이트 동일 응답 보장은 제외.
- 메뉴 fallback은 기존 D1 계약 유지: 같은 식당의 다른 메뉴 우선, 다른 메뉴 확인 불가 시 기존 승인 범위와 사용자 안내 유지. 메뉴 변경 성공 보장 제외.
- 요청 전체 32 모델 시도·24 Tool 왕복·128 callback 상한. 공급자 quota가 아닌 앱 정책. SDK max-retries=0, 기존 앱 parse/correction 유지. 32는 SDK 논리 모델 시도이며 모든 transport 내부 동작까지 포함하는 절대 HTTP 상한이라는 주장은 제외.
- business budget는 명시적인 Context 값. 새 ThreadLocal·DAG 엔진 의존성 없음.

## 2. 생성·편집 품질

- 재선택 candidateId 누락·미검색 ID의 1회 correction. 새 모델 요청에 이전 Tool 대화가 자동 유지되지 않는 경로이므로 Java 후보 ID·이름·유형·원본 메뉴·메뉴 조회 상태를 JSON으로 correction 입력에 포함. 조회 완료 표시 임의 변경·시도 간 공유 Context 복사 없음.
- correction 무효 두 종류만 기존 2회 슬롯 재선택·검증된 원본 fallback으로 연결. provider/upstream/budget 실패는 전파.
- 생성 점심만 공통 finish에서 복구. 등록 미사용 식당 최대 3곳의 원본 메뉴·알레르기·중복·앞뒤 경로 검증.
- 관련 점심과 다음 구간 이동분만 변경, 관광지 identity/start/end·체류 시간 보존.
- 탐색은 이미 원본 메뉴가 기록된 최대 3개 등록 식당에 한정. 전역 최적·모든 식당의 불가능 판정이 아닌 제한 탐색. 탐색 실패 시 `MEAL_TIME_UNFEASIBLE`, 경로 확인 실패는 `ROUTE_LOOKUP_FAILED` 구분. 0분/가짜 경로/무관한 관광지 삭제 금지.
- 기존 편집 일정의 창 밖 식사까지 자동 변경하지 않는 범위 보존. 기존 승인된 근거리 도보 추정과 실제 공급자 조회는 구분해 보고.

## 3. 영양 사실

- generation selection의 standardFoodName 소실 복구.
- Tool UNAVAILABLE 뒤 새로운 표준명 존재 시 보충. 동일 표준명으로 이미 실패하면 반복 제외.
- 식약처 DB 음식명·원본 기준량·MENU_NAME_LOOKUP/STANDARD_FOOD_FALLBACK을 결과·미리보기·SQL·조회 DTO에 보존.
- 필드별 파싱으로 실제 0 보존, null·빈 값·음수·비유한값은 미확인. 한 필드 오류가 확인된 다른 값을 제거하지 않는 경계.
- 모든 수치는 식품 DB 참고값. 식당 실제 제공량·조리법·원재료·질환별 HIGH 판단 미확인. `NOT_EVALUABLE` 유지, 1회분 환산·0 채우기 금지.
- migration V19 요청 상태, V20 영양 참고 출처. 기존 데이터의 출처는 null/UNKNOWN, 소급 추정 금지. 기존 여행 SQL 일정은 보존, 이전 travelId 단일 key 기반의 일회성 preview·confirmed cache는 새 UUID 메타데이터가 없어 재생성 필요.
- 실제 프론트엔드 참고값 라벨 UI 변경은 범위 밖. 공개 DTO 출처 필드와 API/결과 문서 제공만 반영. 미평가 영양·재료·영업시간을 안전 통과로 표현 금지.

## 검증 계획

- 취소·새 요청·동시 확정·SQL 실패·SQL 이후 Redis 실패·reconnect 소유권 무료 경로.
- 실제 Spring AI2.0.0 Tool loop/parse/correction budget fixture. 모델 HTTP 수와 callback 실행 전에 차단 확인.
- 기존 회귀 전체와 독립 리뷰 최소 2회·최대 4회, gate 9/10 및 Critical/Important 0.
- 무료/review 통과 이후 최대 생성12·편집4·STOMP1·독립 영양8 조회. 실패 포함 분모, 맹목적 배치 재실행 금지.
- main 동기화·DAG 도입은 이번 자동 실행 범위 밖. 4 공급자/운영, 5 기존 Timer 간헐 실패는 Claude 인계.

## 근거

- 설치 Spring AI2.0.0 ToolCallingAdvisor 하류 CallAdvisor, shallow request context, SDK maxRetries 설정. 최신 문서의2.0.1 전용 ToolCallLimit API 미사용.
- 상세 공식 출처: `p2-followup-123-official-research.md`.
- 식품 DB 기준량과1회섭취 참고량 구분: https://www.data.go.kr/data/15127578/openapi.do
- 과거 실제 raw: Stage7 성공17건 중 식사102슬롯, 영양3필드 충족56/전부null46. 과거 누락 원인 전체 귀속은 UNKNOWN.

## 2026-10-15 무료 검증·리뷰

- 전체 무료 회귀 1,111건, 실패·오류·skip 0. 최초 1,093건 중16실패의 원인(중복 user 연결, 변경된 mock 오버로드, fixture 헤더 누락)을 확인·수정한 뒤 전체 재검증. 성공까지 원인 없이 재실행한 배치와 구분.
- MySQL REPEATABLE READ에서 REST 소유권 조회가 이전 snapshot을 만드는 동시 확정 결함을 추가 재현. RED에서 이전 카페 반환, GREEN에서 확정 카페 반환. REST confirm의 바깥 @Transactional 제거, 소유권 검사 후 Service의 명시적 write 트랜잭션 시작.
- 조회/확정 후 SQL fallback은 저장된 일정 의미 복원. candidateId는 DB 미저장으로 byte-identical preview 응답 보장 제외.
- 실제 네트워크에 이미 전달된 메시지 회수·외부 호출 interrupt·운영 프록시 검증은 범위 밖. UUID 기반 클라이언트의 오래된 응답 무시 필요.
- 무료 gate는 코드/계약 확인이며 실제 AI 품질·성공률·성능 개선 판정과 분리. 실제 측정 결과는 `p2-followup-123-result.md`에 후속 기록.
