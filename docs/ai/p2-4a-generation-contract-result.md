# P2-4A 생성 출력 계약 정리 결과

작성일: 2026-10-08

기준: Issue #169, `origin/dev` `ee029847`에서 분기

상태: 코드·무료 테스트 검증 완료, 실제 API 품질 게이트 미실행

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
- `./gradlew build --console=plain`: 860건 통과, 실패·오류 0건. 실제 API 호출 12회 품질 게이트: 사용자 승인 대기.

## 후속 판정

- 실제 API 4케이스 × 3회에서 R0 대비 통과 수·지정 장소 포함·하드 위반을 비교. 지연·토큰은 직전 단계 R1과 비교해 참고 기록.
- `decidedLocation`이 비어 있는 입력을 제품에서 허용한다면 첫 장소의 이동시간 기준점 부재를 별도 처리해야 함. 현재 요청 DTO에 비어 있지 않음 검증은 없지만 기존 테스트·실측 사례는 출발 장소가 채워진 입력을 사용.
- P2-4B 전에 생성 전용 Tool 경계와 `standardFoodName`을 Java 영양 조회에 전달할 경로 확정 필요.
