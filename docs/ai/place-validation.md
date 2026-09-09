# AI 일정 장소 검증

PlanService가 생성·편집의 의미 검증 경계다. Handler 직접 호출은 JSON 생성 단계이며 장소 검증을 완료한 응답이 아니다.

- AI 호출마다 PlanTourismTool과 PlaceCandidateContext를 생성한다. Tool 입력에는 컨텍스트가 없다. JSON 파싱 재시도에서는 후보를 비우고, 슬롯 재선택에서는 새 컨텍스트를 사용한다.
- 검색 Tool이 반환한 candidateId만 선택할 수 있다. PlanPlaceHelper가 저장한 검색 원본으로 장소명·주소·좌표·이미지 및 음식점 위치 필드를 확정한다.
- 관광지/MUST_HAVE는 TourAPI 12 또는 Kakao AT4, 카페는 Kakao CE7, 음식점은 TourAPI 39 또는 Kakao FD6를 허용한다. PARK_WALK는 Kakao AT4이면서 원본 카테고리에 공원이 있는 경우만 허용한다. 유형 근거가 없는 장소는 거부한다.
- scheduleType/courseType 허용 조합은 장소 출처와 별도로 검사한다. MUST_HAVE도 관광지 유형 검사 대상이므로 음식점 등은 해당 음식점 슬롯으로 배치해야 한다.
- 정상 슬롯을 먼저 예약해 이후 날짜의 정상 장소까지 재선택에서 제외한다. 중복·미확정·유형 오류는 실패 슬롯만 최대 두 번 재선택한다. 슬롯 삭제나 음식점으로 자동 유형 변경은 없다.
- 편집 복구는 날짜·일차·시각·유형이 일치하는 기존 슬롯만 대상으로 한다. 기존 데이터에는 영구 출처 ID가 없으므로 카카오에서 이름 일치, 좌표 차이 0.0001도 미만, 원본 카테고리를 다시 검사한다. 통과한 기존 슬롯의 필드는 보존한다. 재검증하지 못하면 성공 응답으로 복원하지 않는다.
- candidateId는 AI 응답과 후처리에서 보존하며 DB 스키마는 변경하지 않는다. 이후 편집에서는 원본 검색을 다시 수행한다.
- 장소 교체 또는 편집 전 장소 변경이 있는 구간부터 이동시간과 뒤따르는 일정 시간을 다시 계산한다. 경로 시간을 확인할 수 없거나 자정을 넘으면 명시적 실패다. 확정 식사시각을 기준으로 기존 복약 보정을 적용한다.
- 재선택 및 기존 슬롯 복구까지 실패하면 기존 BaseException 형식의 PLAN.EXCEPTION.INVALID_AI_PLACE를 반환한다.

## 로컬 검증

아래 세 테스트는 외부 OpenAI·TourAPI·Kakao 호출 없이 동작하도록 작성했다. 이번 작업에서는 사용자 요청에 따라 실행하지 않았다. 컴파일도 미확인 상태다.

```sh
./gradlew test \
  --tests 'com.planb.unit.domain.travel.service.PlanPlaceValidationTest' \
  --tests 'com.planb.unit.domain.travel.service.PlanServiceTest' \
  --tests 'com.planb.unit.global.ai.client.OpenAiClientTest'
```

PlanPlaceValidationTest: 원본 유형/필드, 카카오 분류, 후보 격리, 재선택 제한, 이후 정상 슬롯 예약, 편집 복원 및 2일차 보존, 이동시간 재계산.

PlanServiceTest: 기존 복약·영양 태그 후처리 회귀. 기존의 슬롯 삭제 기대 테스트는 새 오류 정책을 검증하는 PlanPlaceValidationTest로 대체했다. 기존 미커밋 복약 시각 테스트는 보존했다.

OpenAiClientTest: JSON 파싱 재시도와 실패한 호출의 후보 제거.

유료 외부 호출용 externalTest 및 TravelRecommendHandlerTest는 실행 대상이 아니다.

## 전체 날짜 재구성의 변경 이행 검증

편집 전에 별도 AI 호출로 `PlanEditScope`를 해석한다. 날짜 전체 재구성이 명시된 일차만 검증 대상으로 삼으며, 단순 시간·식사 조정은 기존 경로를 사용한다. 범위가 불명확하거나 없는 일차이면 `EDIT_NOT_APPLIED` 오류다. 다른 날짜에 별도 변경 요청이 함께 있는 경우에는 그 날짜를 무조건 원본으로 덮어쓰지 않는다.

전체 날짜 재구성만 요청한 경우, 다른 날짜를 먼저 원본 출처 검증 후 예약한다. AI가 작성한 유지 날짜는 사용하지 않으며, 해당 날짜에는 복약·태그·시간 후처리를 다시 적용하지 않는다.

대상 날짜는 원본 검증 이후 기존 장소와 이름 및 좌표를 대조한다. 최소한 하나의 새로운 실제 장소가 있어야 변경 이행으로 간주한다. 순서 변경·이름 공백 변경·동일 좌표의 다른 표기·슬롯 삭제만으로는 성공하지 않는다. 이는 요청 전체의 품질을 점수화하는 검증이 아니라 전체 재구성의 최소 변경 기준이다.

변경이 없으면 해당 날짜만 새 후보 컨텍스트로 최대 두 번 재구성한다. 날짜·일차, 출처·유형, 다른 날짜와의 중복, 이동시간을 재검증한다. 그래도 변경되지 않으면 `PLAN.EXCEPTION.EDIT_NOT_APPLIED`로 실패하며, 기존 AI의 '변경하지 않았다' 설명을 성공 설명으로 재사용하지 않는다.

범위 해석에 AI 호출 1회가 추가되고, 날짜 재구성은 실패한 일차당 최대 2회 추가된다. 기존 슬롯별 장소 복구와 JSON 파싱 재시도 한도는 별도다.

실제 호출 테스트는 고정된 두 관광지(해운대해수욕장·송정해수욕장)의 카카오 원본 좌표를 읽어 기존 일정을 구성한 뒤 `PlanService`로 편집한다. AI 최초 생성은 실행하지 않는다. 외부 호출은 사용자가 명시적으로 아래 명령을 실행할 때만 수행한다.

```sh
./gradlew externalTest --tests 'com.planb.integration.ai.service.PlanEditRebuildTest'
```

`TravelRecommendHandlerTest`에도 external 태그를 적용했다. 일반 `test`에서 실행되지 않으며, 새 변경 이행 기능은 위 Service 테스트로 확인한다. 추가 단위 회귀는 기존 `PlanPlaceValidationTest`에 포함했다. 이번 추가 변경도 컴파일·테스트는 실행하지 않았다.
