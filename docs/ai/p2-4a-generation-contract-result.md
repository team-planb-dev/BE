# P2-4A 생성 출력 계약 정리 결과

작성일: 2026-10-08

기준: Issue #169, `origin/dev` `ee029847`에서 분기

상태: P2-4A 채택 — 코드·무료 테스트 및 실제 API 생성 12회 주 품질 gate 통과

## 변경 목적

기존 생성 응답은 AI가 `candidateId`와 함께 장소명·주소·좌표·이미지·이동시간·영양 수치까지 다시 출력하는 구조였음. 검색 원본과 다른 사실값이 섞일 수 있고, 양수 이동시간은 Java 재조회 대상에서 빠지는 경로가 있었음. P2-4A는 AI 출력을 후보와 일정 선택으로 줄이고, 사실값은 검색 원본과 기존 Java 후처리로 확정하는 단계임.

## 적용 구조

1. `CreatePlanSelection`을 생성 전용 구조화 응답으로 사용. 날짜·시간·슬롯 유형·`candidateId`·식사 메뉴·`standardFoodName`·태그만 모델 출력에 포함.
2. `PlanGenerationSelectionMapper`가 `candidateId`로 `PlaceCandidateContext`의 원본 장소명·주소·좌표·이미지를 복원. 매핑은 `OpenAiClient.call`의 검증 callback 안에서도 수행해 기존 correction 한도와 날짜·관광지 개수 검증을 유지.
3. 모델 이동시간은 출력 계약에서 제거. 생성 응답의 이동시간을 `null`로 두고 기존 `TravelMinutesResolver`가 확정. 영양 수치는 기존 `NutritionEvaluationCollector` 결과 및 부족분 조회를 통해 `PlanService`가 확정.
4. `TravelPlanPrompt`는 생성 전용 선택 필드와 현재 Tool 호출 순서를 설명. 관광지 개수는 `TouristPlaceCountPolicy.expectedCount` 결과를 삽입. 복약 슬롯은 생성 스키마 enum에서도 제외.
5. 음식점 상세 Tool의 응답에는 영업시간 필드가 없음. 이전 모델 출력 `openTime`은 검증된 사실이 아니므로 생성 선택 계약에서 제거하고 최종 응답에 미확인 값(`null`)을 남김.

## 유지한 경계

- 생성 경로의 Tool 집합과 `OpenAiClient`의 파싱·검증·1회 correction 계약 유지.
- 편집·날짜 재구성·장소 재선택의 공통 `CreatePlanAiResponse`와 기존 프롬프트 유지.
- `standardFoodName`은 이번 선택 DTO에 포함. Java 영양 조회 입력으로 넘기는 전환은 P2-4B 범위.
- 생성 전용 Tool 클래스는 Tool 집합이 달라지는 P2-4B 착수 시 분리. P2-4A에서는 공통 `PlanTourismTool`의 동작 변경 없음.

## 검증

- RED → GREEN: 생성 프롬프트 출력 계약, strict 구조화 스키마, 후보 원본 매핑.
- 단위 계약: 선택적 필드의 null 허용, 모든 객체의 `required`·`additionalProperties`, 실제 JSON 파싱, 미검색 `candidateId`의 최종 검증 거부, 기존 편집 경로.
- `./gradlew build --console=plain`: 860건 통과, 실패·오류 0건.
- 생성 전용 실행 옵션 추가 후 `compileTestJava` 통과. `P2_GENERATION_ONLY=true`로 실제 API 생성 12회만 실행, 편집 추가 호출 0회.
- `./gradlew externalTest --rerun-tasks --tests "*TravelLlmQualityBaselineTest" --console=plain`: 성공(6분 1초).

## 실제 API 생성 12회 결과

실행일: 2026-10-08. 분석일: 2026-10-09. 실행 commit: `84444b5`. runId: `2026-10-08T05-45-48.083415Z`.

서울·부산·강릉·경주 4케이스 × 3회. 동일 케이스 반복 시 첫 회차 캐시 미적중, 이후 회차 적중 조건. 생성만 측정하며 기존 편집 계약은 무료 테스트로 검증.

| 품질 지표 | R0 재측정 | P2-4A | 판정 |
|---|---:|---:|---|
| 생성 통과 | 12/12 | 12/12 | 주 품질 gate 통과 |
| 지정 장소 포함 | 12/12 | 12/12 | 주 품질 gate 통과 |
| 알레르기 키워드 추정 | 0 | 0 | 주 품질 gate 통과 |
| 날짜 간 장소 중복 | 0 | 0 | 주 품질 gate 통과 |
| 메뉴 출처 일치 | 73/73 | 72/72 | 감소 없음 |
| 영양 채움 | 33/73 (45.2%) | 36/72 (50.0%) | 감소 없음 |
| CAFE_REST 슬롯 | 4 | 11 | 감소 없음 |
| 관광지 유형 태그 합계 | 78 | 72 | 동일 72개 관광 슬롯 기준 약 7.7% 감소, 허용 폭 30% 이내 |
| 실행당 총 이동시간 중앙값 | 271분 | 300분 | 약 10.7% 증가, 허용 폭 20% 이내 |

R0 지정 장소 포함은 당시 결과 문서의 제목 정규화 재계수(12/12)를 사용. R0 JSONL에 남은 이전 계수(6/12)와 구분. 태그·이동시간은 R0 재측정 원본 JSONL에서 집계하며, 이전 R0 1차 실행의 291.5분을 섞지 않음.

| 참고 성능 지표 | 직전 단계 R1 | P2-4A |
|---|---:|---:|
| 성공 생성 중앙값 | 40.039초 | 19.986초 (약 50.1% 감소) |
| 성공 생성 범위 | 20.4~67.9초 | 13.083~57.475초 |
| 실행당 input + output token 중앙값 | 108,451 | 47,916.5 (약 55.8% 감소) |
| 성공 실행 LLM 호출 합계 | 84 / 성공 11회 | 61 / 성공 12회 |

P2-4A는 주 품질 gate 통과로 채택. 지연·비용 gate는 P2-4B·P2-4C 대상이므로 위 수치는 참고 결과. 다른 날의 실제 API 호출이며 모델·공급자 지연·캐시의 영향을 포함하므로 변화 전부를 schema 축소 효과로 확정하지 않음. R1 실패 1회는 성공 지연 집계에서 제외. stub 부하 결과와 실제 API 결과는 상호 비교하지 않음.

- C1·C2·C3·C4 생성 시간 중앙값: 21.739·15.170·20.022·19.950초.
- correction 0회. TourAPI 실제 HTTP 107건: `areaBasedList2` 4, `areaCode2` 42, `detailIntro2` 33, `searchKeyword2` 28. 응답 전부 `ok`.
- HIGH 메뉴 비율은 실행기·공개 응답에 등급이 없어 미측정. 영업시간 불일치는 상세 API에 영업시간이 없어 미측정. 이 두 지표의 검증 완료 주장은 제외.
- 12회 소표본이며 운영 지연·부하 처리량·실제 알레르기 성분 안전성의 증거로 사용하지 않음.

## 증거 보존

- 집계 데이터: `p2-4a-generation-measurements-20261008.json`(결과 문서와 같은 디렉터리).
- 원본 JSONL과 응답 12개: 작업 트리 `build/llm-quality-baseline/` 및 지정 보관 디렉터리의 `p2-4a-external-evidence-20261008/`.
- 원본 응답과 실행 로그는 Git 커밋 대상에서 제외. 문서·집계 데이터와 보관 사본은 바이트 동일성 확인.

## 후속 판정

- P2-4B를 다음 후보로 검토. 자동 구현 착수는 이번 범위 밖.
- `decidedLocation`이 비어 있는 입력을 제품에서 허용한다면 첫 장소의 이동시간 기준점 부재를 별도 처리해야 함. 현재 요청 DTO에 비어 있지 않음 검증은 없지만 기존 테스트·실측 사례는 출발 장소가 채워진 입력을 사용.
- P2-4B 전에 생성 전용 Tool 경계와 `standardFoodName`을 Java 영양 조회에 전달할 경로 확정 필요.
