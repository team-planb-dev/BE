# P2 DAG 전 후속 1~3 결과 — #199

작성 기준: 2026-10-15. 기준 dev `bc6a7f1`, main `a6271bf`. 작업 브랜치 `codex/199-refactor-pre-dag-edit-policies-and-selection-quality`. 구현·무료/실제 API 검증 완료. 반영 PR [#200](https://github.com/team-planb-dev/BE/pull/200), 병합 상태는 PR과 계획서v30 참조. 코드 commit `fd2f69dca8af`. main·DAG 변경 없음.

## 1. 범위와 권장 정책

사용자 지시의 후속1 편집 수명/호출 예산, 2 선택·식사 시간 품질, 3 영양 사실 보존. grilling 질문은 사용자 위임에 따라 권장안으로 결정. 공급자/운영 진단4와 과거 Timer 간헐 실패5는 Claude 인계 범위.

- 마지막 시작 편집 우선. SQL Travel 행 잠금과 UUID 현재성, Redis UUID별 미리보기. 취소/이전 요청의 늦은 결과 저장·게시 차단, 소유권 인증 GET 재조회, 선택적 confirm/cancel 토큰. legacy token-less는 현재 요청 대상.
- SQL 저장 성공 후 payload 정리. rollback 시 재확정 가능, 중복 확정은5분 내 같은 토큰을 재저장 없이 응답. 캐시 실패 시 SQL 의미 복원(candidateId 미저장으로 바이트 동일성 제외).
- REST 소유권 조회가 REPEATABLE READ의 이전 snapshot을 만드는 동시 확정 문제를 RED로 추가 확인. Facade 외부 트랜잭션 제거 후 Service write 경계 시작으로 GREEN.
- 요청 예산32 모델 시도/24 Tool 왕복/128 callback. SDK max-retries0, 앱 parse/correction 유지. SDK 논리 호출 상한이며 모든 transport HTTP의 절대 상한으로 확대 금지. 취소의 외부 호출 interrupt 보장 없음.
- 미검색/null ID correction에 Java 후보 ID·이름·유형·메뉴·메뉴 조회 상태 연결. 응답 검증 실패만 기존 슬롯2회 복구/검증된 원본 fallback으로 연결. upstream/budget 실패 전파.
- 생성 점심만 최대3개 등록 식당·원본 메뉴·알레르기·중복·앞뒤 이동 확인으로 복구. 관광지 identity/start/end/stay 보존, 복약 최종 재산정. 제한 후보 실패와 공급자 실패 분리. 기존 승인 근거리 도보 추정 유지, 전역 최적 탐색/불가능 판정 아님.
- standardFoodName 전달, Tool UNAVAILABLE 뒤 미시도 표준명 보강, 동일 표준명 실패 반복 차단. 영양 원본 음식명/기준량/조회 출처를 응답·캐시·SQL·조회·기존 일정 복원에 보존. 필드별 실제0/null/비유한값 구분. HIGH·식당 제공량·원재료 안전 판정 미추가.

## 2. 무료 계약 검증과 리뷰

- 최종 무료 회귀 **1,111건, 실패/오류/skip0**. 최초1,093건 중16실패는 중복 user 연결·mock오버로드·fixture 헤더 누락으로 귀속하고 수정 후 전체 재검증. 원인 없는 성공까지 재실행과 구분.
- 수명/예산 독립 리뷰8.4→8.8→**9.3**, 영양/재선택/점심5→8→**9.2**. 최종 Critical/Important0.
- RED/GREEN: 취소 뒤 늦은 성공/실패 억제, 새 요청 보호, 동시 확정1회 반영, SQL 실패 후 재시도, commit 후 Redis 장애, REST 오래된 snapshot, 실제 Spring AI 모델/Tool/parse/correction 공유 예산, 표준명과 출처, 실제0/부분 파싱.
- 점심9개 경계와 실제 생성 파이프라인 fixture로 후보 상한·앞뒤 충돌·다른 날짜 예약·알레르기·메뉴 원본 부재·복구 뒤 복약 간격 재산정 확인.
- 현재 회귀 통과는 과거 `PlanServiceTest` Timer 간헐 실패의 원인 해결 증거가 아님.

## 3. 실제 측정 조건

- 모델 `gpt-5.6-terra`, reasoning `none`, 실제 OpenAI·TourAPI·Kakao·식약처 사용. 외부 응답 stub 미사용. DB/Redis는 Testcontainers 격리 환경.
- 생성은 C1~C4 각2회8건과 C5 식전 점심/C6 세 끼 식전·식후 각2회4건, 합12건. 새 테스트 선택 옵션은 기존 기본 C1~C4/3회 유지. 기존 Stage7와 같은 사례·반복·후보 seed. 날짜는 실행일+7일로 과거와 다름.
- 공통8건과 복약4건은 별도 컨텍스트/캐시로 실행. 캐시 온도·모델 응답·공급자 상태·요일 차이 통제 불완전. 전후 동시 대조군 없음.
- REST 메뉴 편집4건, native WebSocket/STOMP 편집1건, 별도 영양 조회8건. 생성/편집 내부 Tool·공급자 호출은 요청 수와 별도. 자동 유료 재실행0.
- 각 실측 mode별 source hash 불변. 전체 mode의 `src/main` digest 일치. JUnit은 하네스/저장 계약 gate이며 생성 사업 결과 PASS와 별도 계수.

## 4. 생성 전후 관측

| 항목 | 과거 Stage7 동일12 case/repeat | 이번12건 |
|---|---:|---:|
| 생성 통과 | 11/12 | **12/12** |
| 필수 식사 정시(±30분) | 42/44 | **48/48** |
| 점심 정시 | 20/22 | **24/24** |
| 등록 복약 고유 규칙 일치 | 42/42 | **44/44** |
| 식사3개 영양 수치 충족 | 36/66 (54.5%) | **42/72 (58.3%)** |
| 영양 수치 전부 null | 30/66 | **30/72** |
| 확인 수치의 음식명·기준량·출처 보존 | 공개 필드 없음(0/36) | **42/42** |
| 요청 시간 중앙값(참고) | 42.235초 | 35.3565초 |

- 성공 실행의 첫날 아침·마지막 날 저녁은 필수 식사 정시 분모에서 제외. 실패 실행의 숨겨진 식사 품질을0으로 만들지 않고 생성 시도 분모12에 포함.
- 복약은 동일 약명/동일 규칙의 고유 규칙으로 계수. 동행인별 복약 순응도 평가와 별개. C5/C6의 기대 시각은 원래 설정에서 산출, 응답 interval 숫자만 보고 통과 처리하지 않은 비교.
- 장소 시간 겹침·동일 이름/이름+주소 중복 미관측. 장소 비교의 원본/동의어 판정 한계 유지.
- 이번 생성의 모델 observation **84회**, 요청당 최대16회. budget 도달 거부 미관측. 정상 재선택 검증6회, invalid ID0. 새 점심 복구 분기0회. 따라서 ID correction/점심 복구의 실제 개선 기여는 분해 미입증이며 고장 입력 효과는 결정적 fixture 근거.
- 숫자 없는30식사는 남은 한계. 확인된42식사의 출처는 메뉴명 직접26, 표준명 fallback16. fallback16을 전부 이번 mapper 수정의 효과로 귀속하지 않는 범위.
- 과거 전체18시도17통과·영양56/102·식사 정시66/68은 색인용 원자료. 같은12시도 비교 표와 혼합하지 않는 분모.
- 실제0은 이번 raw에서 미관측. 0 보존은 무료 테스트의 검증 결과. 영양 수치 확보를 질환 적합성·성분 안전·HIGH 판정 완료로 확대 금지.

## 5. REST·STOMP

| 항목 | 결과 |
|---|---|
| REST 메뉴만 변경 | **4/4**, 같은 식당 고등어구이→갈치구이 |
| 대상 밖 identity·순서·메뉴 | **60/60** (복약 포함15슬롯×4) |
| 대상 밖 start/end/stay | **60/60** |
| 미리보기 단계 SQL 원본 보존 | **4/4** |
| 취소·캐시 정리 | **3/3** |
| 확정·같은 토큰 중복 확정 | **1/1**, 재조회 계약 통과 |
| REST 지연/모델 observation | 29.301/17.673/17.196/17.535초, 2/3/3/3회 |
| native STOMP 실제 AI 편집 | **1/1**, 27.555초·모델2회 |
| STOMP 대상 밖 내용·시각 | **15/15**, 같은 식당 메뉴만 변경 |
| STOMP 연결/GET | 연결 유지, 인증 GET의 현재 token 일치 |

- 실제 socket disconnect/reconnect 미실행. 연결 유지 중 GET 재조회와 운영 브라우저·프록시·heartbeat 검증을 구분. Grafana URL·새 Prometheus 실측 시계열은 생성하지 않았고, 이 실험은 실제 결과 JSON과 모델/단계 meter snapshot으로 판정.
- 과거 M3는3/3, 비복약 대상밖39/39. 이번60/60은 복약 포함 분모이므로 직접 증가량으로 비교 금지.

## 6. 독립 영양 조회8건

- 숫자/기준 음식 확인5건(`NOT_EVALUABLE`), 조회 불가3건(`UNAVAILABLE`). `NOT_EVALUABLE`은 숫자가 있어도 임상 평가 근거가 충분하지 않은 상태, 요청 실패와 구분.
- 확인: 비빔밥·갈치구이·고등어구이·김치찌개·설렁탕. 미확인: 불고기·돼지국밥·공깃밥. UNAVAILABLE의 empty/filter/timeout/provider 원인은 분리하지 못한 상태이며 DB 미등재의 증명은 제외. source 모두 직접 메뉴명 조회, 요청별 약3.946~4.960초.
- **갈치구이·고등어구이의 SERVING_SIZE 매핑값 `100mL` 관측.** 100g/1인분으로 임의 수정·환산하지 않은 보존. 이 실행은 service 결과 DTO를 보존했으며 원본 provider body는 미보존, 공급자 데이터/해석의 근본 원인 UNKNOWN. 식당 제공량·임상 해석에 사용하지 않는 추가 확인 항목.
- 02 endpoint 사용. 03 Swagger의 필드/기준량 설명을02와 완전히 같다고 확정하지 않는 근거 범위. 버전/원본 선택 상세는 공식 조사 문서 참조.

## 7. 채택 범위와 잔여

- 코드 계약/무료 gate와 실제 소표본 회귀의 범위에서 후속1~3 반영 가능. 전체 AI 품질·통계적 성공률 개선·운영 적용 완료 선언은 제외.
- 남은 영양30/72, 원본 기준량 해석, 성분·HIGH·영업시간 사실 판정은 미평가/자료 부족. provider 정상화·DAG 도입으로 자동 해결되는 항목 아님.
- 후속4: Kakao400 근본 원인/원본 오류, TourAPI 운영 승인, 실제 브라우저·프록시·heartbeat·재연결. 후속5: 과거 Timer 테스트 실패 원본 특정/재현/최소 수정 또는 UNKNOWN 처리 방침.
- 새 V19/V20은 기존 SQL 일정 보존. 이전 travelId cache-only 미리보기/confirmed cache는 UUID 메타데이터가 없어 재생성 필요. 기존 영양 출처 null은 UNKNOWN. 프론트 참고값 표시 UI 미변경.
- main sync·배포·LangGraph4j/DAG 구현 없음. 후속4/5 뒤 합의된 동일 계약 Java/graph 비교로 이동.

## 8. 증거·재현

- 공개 집계: `evidence/p2-followup-199-20261015/summary.json`.
- private 실측: `/private/tmp/planb-199-evidence/live-20261015/`의 generation/medication/rest/stomp/nutrition 각 manifest·raw·XML·log, 비교JSON과 분석 script.
- 장기 사본: `/Users/wooju-kang/Desktop/개발 포트폴리오/Yeoro 프로젝트 모음/리팩토링 문서/텍스트 문서/p2-followup-199-20261015/`.
- 실제 호출 runner/seed/원본/JSONL/로그/XML은 Git 제외. 장기 사본에 테스트 소스·실행기·분석기·source digest 보존. 키·JWT·원본 건강 seed 출력/커밋 금지.
- 보호 계획서3개·DAG evidence 스테이징/커밋 금지. 본 문서와 공식 조사/설계 사본 동기화.

최종 실측 증거 review-loop: 9.3→9.6/10, Critical/Important/Minor0.
