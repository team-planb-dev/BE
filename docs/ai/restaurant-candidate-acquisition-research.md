# 음식점 후보 부족과 지역 기반 보충 조회 조사

조사 기준일: 2026-09-21  
범위: 한국관광공사 `KorService2`, 카카오 로컬 장소 검색, 현재 음식점 후보 수집·검증 구현

## 결론

현재 장애의 외부 데이터 측면 핵심은 **음식명 키워드 검색 결과 수**와 **최종적으로 식사 슬롯에 사용할 수 있는 음식점 수**가 같지 않다는 점이다.

- `searchKeyword2` 결과가 있어도 `detailIntro2`의 `firstmenu`와 `treatmenu`는 모두 선택 항목이므로 메뉴를 확정하지 못할 수 있다.
- 현재 Java는 `firstmenu`만 인정하고 `treatmenu`를 사용하지 않는다. Prompt는 `firstmenu`가 없으면 `treatmenu`를 참고하라고 하므로 계약이 충돌한다.
- 현재 검색은 음식명별 `searchKeyword2`, 첫 페이지 100건에 한정된다. 지역 안의 전체 음식점 풀을 확보하는 경로가 없다.
- `areaBasedList2`에 음식점 타입 `contentTypeId=39`를 사용하면 음식명에 종속되지 않고 현재 지역 범위의 음식점 후보를 조회할 수 있다. 응답의 `pageNo`, `numOfRows`, `totalCount`로 필요한 수가 모일 때까지 제한적으로 다음 페이지를 조회할 수 있다.
- 카카오 키워드 검색은 장소 식별자·주소·좌표·`FD6` 분류를 제공하지만 TourAPI의 `contentId`와 대표메뉴를 제공하지 않는다. 대표메뉴가 최종 식사 계약이라면 카카오 결과를 TourAPI 음식점과 동등한 후보로 승격하면 안 된다.

따라서 가장 작은 안전한 방향은 **기존 음식명 검색을 우선 유지하고, 부족한 수만큼 동일 지역의 `areaBasedList2(contentTypeId=39)` 후보를 Java가 추가 확보한 뒤, 상세 메뉴와 중복을 검증하여 채우는 것**이다. 동일 지역 목록까지 소진했는데도 부족하면 누락 일정을 저장하지 말고 부족 원인을 구분한 명시적 실패로 끝내야 한다.

## 공식 계약

### 1. 한국관광공사 국문 관광정보 서비스

공공데이터포털은 국문 관광정보 서비스가 지역 코드, 지역 기반 관광정보, 키워드 검색, 소개정보 등을 제공한다고 설명한다. REST 응답은 JSON 또는 XML이며 개발계정 기본 트래픽은 일 1,000건이다.

출처: [한국관광공사_국문 관광정보 서비스_GW](https://www.data.go.kr/data/15101578/openapi.do)

공식 페이지에서 제공하는 2026-02-26자 국문 활용 매뉴얼 v4.4의 `areaBasedList2` 계약은 다음과 같다.

- 지역과 시군구를 기반으로 관광정보 목록을 조회한다.
- `contentTypeId=39`는 음식점이다.
- `pageNo`는 현재 페이지, `numOfRows`는 한 페이지 결과 수다.
- 응답은 `pageNo`, `numOfRows`, `totalCount`를 제공한다.
- `contentid`, `contenttypeid`, 제목, 주소, 경도 `mapx`, 위도 `mapy`를 장소 identity로 제공한다.
- 현행 v4.4의 지역 필터는 법정동 시도 코드 `lDongRegnCd`와 법정동 시군구 코드 `lDongSignguCd`로 문서화돼 있다. `lDongSignguCd`를 사용할 때는 `lDongRegnCd`가 필요하다.

출처: [공식 국문 활용 매뉴얼 v4.4 다운로드](https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000003603931&fileDetailSn=1)

매뉴얼 변경 이력에는 기존 지역코드와 시군구코드 요청·응답이 삭제됐다고 적혀 있다. 현재 구현은 `areaCode2`로 코드를 얻고 `areaCode`와 `sigunguCode`를 전달한다. 외부 통합 테스트에서 동작하더라도 이는 현행 v4.4 문서의 기본 지역 인터페이스와 다르다. 이 이슈에서 즉시 지역 코드 체계 전체를 바꾸기보다, 현재 운영 호환성을 별도 외부 테스트로 고정하고 법정동 코드 전환은 독립 작업으로 다루는 편이 안전하다.

### 2. 음식점 소개정보

`detailIntro2`는 `contentId`와 `contentTypeId`가 필수다. `contentTypeId=39` 음식점 응답에는 다음 필드가 있지만 모두 선택 항목이다.

- `firstmenu`: 대표메뉴
- `treatmenu`: 취급메뉴
- `opentimefood`: 영업시간
- 그 밖의 휴무일, 주차, 예약, 포장 정보

따라서 `areaBasedList2`의 음식점 원시 후보 수만으로 식사 슬롯 충족 가능성을 판단하면 안 된다. 각 후보의 소개정보를 조회한 후 메뉴가 실제로 확정되는 후보만 세어야 한다.

출처: [공식 국문 활용 매뉴얼 v4.4 다운로드](https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000003603931&fileDetailSn=1)

### 3. 카카오 로컬 키워드 장소 검색

카카오 공식 문서의 키워드 장소 검색은 다음을 지원한다.

- `category_group_code=FD6`으로 음식점 결과 필터링
- `page` 1~45, `size` 1~15
- `x`, `y`, `radius` 또는 `rect`를 사용한 공간 제한
- `is_end=false`일 때 다음 페이지 요청
- 장소 ID, 상호명, 지번·도로명 주소, 경도·위도, 카테고리 반환
- `same_name.selected_region`을 통한 검색어의 지역 해석 결과 확인

좌표 없이 지역명을 질의어에 포함하는 방식은 카카오의 검색어 지역 해석에 의존한다. 좌표와 반경을 사용하면 검색 공간은 명시적이지만 반경 최대치는 20km다. 어느 방식도 TourAPI의 `contentId`, `firstmenu`, `treatmenu`를 제공하지 않는다.

출처: [카카오 로컬 REST API 공식 문서](https://developers.kakao.com/docs/ko/local/dev-guide#search-by-keyword)

## 현재 저장소 구현과 계약 차이

### `Kor2ServiceHandler`

- `searchRestaurants(keyword, locationDo, locationSigungu)`는 `searchKeyword2`를 호출한다.
- `contentTypeId=39`, `numOfRows=100`, `pageNo=1`로 고정돼 있다.
- 광역 지역은 `areaCode`만, 도 지역은 `areaCode + sigunguCode`를 보낸다.
- 응답의 `totalCount`가 100을 넘더라도 다음 페이지를 조회하지 않는다.
- 작업 트리에는 `areaBasedList2(contentTypeId=39)` 음식점 후보 조회를 추가하는 변경이 진행 중이지만, 아직 페이지 순회와 유효 후보 확보까지는 구현돼 있지 않다.

### `TravelPlanPrompt`

- 음식명마다 `searchRestaurantsByLocation`을 호출한다.
- 결과가 없으면 키워드를 한 번 바꾸고, 다시 실패하면 해당 식사 슬롯을 생성하지 않도록 한다.
- `firstmenu`가 없으면 `treatmenu`에서 실제 메뉴를 참고하도록 지시한다.

Prompt 자체가 식사 누락을 허용하고 있어, 최종 식사 슬롯을 Java invariant로 보장하려는 방향과 충돌한다.

### `PlanTourismTool`과 `PlaceCandidateContext`

- 음식점 검색 결과의 `contentid`를 `tour:<contentid>` 형태의 `candidateId`로 기록한다.
- 주소, 좌표, 이미지가 같은 후보 하나에서 함께 보존된다.
- 음식점 후보는 `contentTypeId=39`인 기록만 반환한다.

이 identity 흐름은 그대로 재사용할 수 있다. 후보 보충을 위해 새로운 identity 체계를 만들 필요가 없다.

### `MissingSlotCompleter`

- 사용하지 않은 장소명과 메뉴만 후보로 인정한다.
- `detailIntro2` 조회 실패 후보를 제외한다.
- `firstmenu`가 비어 있으면 후보를 제외한다.
- `treatmenu`는 DTO에 존재하지만 fallback으로 사용하지 않는다.

따라서 Prompt가 말하는 메뉴 fallback과 Java가 인정하는 유효 후보 기준이 다르다. `treatmenu`는 여러 메뉴가 들어갈 수 있으므로 사용하려면 "첫 번째 비어 있지 않은 실제 메뉴"처럼 결정적인 파싱 규칙이 먼저 필요하다. 이 규칙 없이 전체 문자열을 메뉴명으로 저장하면 중복 메뉴 검증과 영양정보 조회 품질이 달라진다.

### `PlanService`

- 누락 식사가 있어도 `fillableMealCount()==0`이면 검증을 종료하고 저장을 허용한다.
- 결과가 0인 이유가 원시 후보 없음, 상세 조회 실패, 메뉴 누락, 메뉴 중복, 장소 중복 중 무엇인지 구분하지 않는다.

즉 현재의 0은 "이 지역에 음식점이 없다"를 뜻하지 않는다. **이번 AI 호출 동안 기록된 후보 중 현재 Java 규칙으로 쓸 수 있는 후보가 없다**는 뜻일 뿐이다.

## 권장 후보 확보 순서

기존 모듈의 interface를 크게 늘리지 않고 다음 순서를 적용할 수 있다.

1. `MealSlotPolicy`로 최종적으로 필요한 식사 슬롯과 이미 채워진 슬롯의 차이를 계산한다.
2. AI가 수행한 음식명 `searchKeyword2` 결과에서 기존 Java 규칙을 통과한 후보를 우선 사용한다.
3. 부족한 경우 동일한 지역 범위에서 `areaBasedList2(contentTypeId=39)`를 조회한다.
4. `contentid`로 중복을 제거하고, `detailIntro2`에서 메뉴가 확인된 후보만 사용 가능 수에 더한다.
5. 사용 가능 수가 부족한 슬롯 수에 도달하면 즉시 페이지 조회를 중단한다.
6. 아직 부족하면 `pageNo * numOfRows >= totalCount`인 실제 목록 소진 또는 별도로 정한 요청 상한에서 중단한다.
7. 최종적으로 부족하면 일정을 저장하지 않고 명시적으로 실패시킨다.

페이지 요청 상한 숫자는 공식 계약이 아니라 비용·지연 정책이다. 개발계정 일 1,000건 제한과 후보별 `detailIntro2` 추가 호출을 고려해 grilling 단계에서 정해야 한다. 임의의 큰 상한을 구현에 숨겨서는 안 된다.

## 지역 확대 기준

지역 확대는 검색 편의가 아니라 일정의 지역 계약을 바꾸는 결정이므로 범위를 명확히 제한해야 한다.

- 현재 모델이 도 + 시/군을 받은 경우: 우선 해당 시/군 전체의 `areaBasedList2`까지 조회한다. 도 전체로 자동 확대하면 사용자가 선택한 도시 밖 음식점이 들어갈 수 있으므로 기본 fallback으로 권장하지 않는다.
- 현재 모델이 광역시 + 구를 받은 경우: 현행 Java 정책은 이미 구를 무시하고 광역시 전체를 조회한다. 따라서 이 흐름에서 추가로 넓힐 지역은 없다.
- 읍·면·동처럼 시보다 좁은 입력을 나중에 지원한다면: 해당 시/군까지 넓히고 그 이상은 실패시키는 것이 "시 내부 범위" 상한과 일치한다.
- 같은 지역의 `areaBasedList2`를 모두 확인하기 전에는 카카오 전국 키워드 검색으로 범위를 넓히지 않는다.

즉 이번 문제에서 필요한 fallback은 **다른 지역으로 확대**가 아니라 **같은 지역 안에서 음식명 키워드 검색을 음식점 목록 조회로 확대**하는 것이다.

## 카카오 fallback 판단

카카오는 다음 경우에만 보조 근거가 될 수 있다.

- TourAPI 후보의 상호·주소·좌표를 추가 확인
- TourAPI에 없지만 실제 존재하는 음식점을 사용자에게 별도 표시
- 좌표 중심의 인근 음식점 검색이 제품 요구로 확정된 경우

현재 식사 일정에는 검증된 대표메뉴가 필요하다. 카카오 단독 후보에는 이를 제공할 공식 필드가 없으므로, 이번 누락 보정의 자동 fallback으로 사용하면 기존 메뉴·영양 검증 계약을 우회한다. 카카오 후보를 사용하려면 메뉴를 얻을 별도 신뢰 가능한 출처와 identity 연결 규칙이 먼저 필요하다.

## 실패 원인 분류

누락 식사를 하나의 `INVALID_AI_PLACE`로만 처리하면 재현 없이 원인을 알기 어렵다. 최소한 내부 판정과 로그는 다음을 구분해야 한다.

| 분류 | 판정 | 권장 처리 |
|---|---|---|
| 지역 코드 매핑 실패 | 요청 지역명이 코드 응답에 없음 | 요청/지역 계약 오류로 실패 |
| 외부 호출 실패 | timeout, HTTP 오류, TourAPI 비정상 `resultCode` | 외부 의존성 오류로 실패; 후보 부족으로 간주하지 않음 |
| 지역 원시 후보 없음 | 정상 응답이고 `totalCount=0` | 지역 후보 부족으로 실패 |
| 페이지 조회 상한 도달 | `totalCount`가 남았지만 요청 예산 소진 | 검색 예산 소진으로 실패 |
| 상세정보 없음 | 후보는 있으나 `detailIntro2` item 없음 | 해당 후보 제외 후 계속 검색 |
| 메뉴 없음 | `firstmenu`·정책상 허용한 `treatmenu` 모두 없음 | 해당 후보 제외 후 계속 검색 |
| 중복 장소 | 동일 `contentid` 또는 정규화 장소명 사용됨 | 제외 후 계속 검색 |
| 중복 메뉴 | 정규화 메뉴명 사용됨 | 제외 후 계속 검색 |
| 유효 후보 최종 부족 | 정상 조회를 끝냈으나 필요한 수 미달 | 누락 저장 금지, 필요한 수와 유효 수를 포함해 실패 |

사용자 응답에 외부 서비스의 상세 원문을 그대로 노출할 필요는 없다. 다만 운영 로그에는 `requiredMealCount`, `rawCandidateCount`, `detailFailureCount`, `missingMenuCount`, `duplicatePlaceCount`, `duplicateMenuCount`, `validCandidateCount`, `pagesFetched`, `scope`를 남기면 DB 결과를 다시 열지 않고도 원인을 구분할 수 있다.

## TDD에서 고정할 외부 어댑터 계약

- 동일 지역 `areaBasedList2`에 `contentTypeId=39`가 전달된다.
- 도 지역은 시/군 범위를 유지하고, 광역 지역은 기존 지역 정책을 유지한다.
- 첫 페이지에서 유효 후보가 충분하면 다음 페이지를 호출하지 않는다.
- 원시 후보는 충분하지만 메뉴 없는 후보가 섞여 있으면 다음 후보 또는 다음 페이지를 확인한다.
- `totalCount`를 소진하면 종료한다.
- 요청 상한에 도달하면 무한 호출하지 않는다.
- 동일 `contentid`, 장소명, 메뉴명은 여러 슬롯 분량으로 세지 않는다.
- 외부 오류를 빈 정상 결과로 바꾸지 않는다.
- 후보 부족 상태에서는 누락 일정을 저장하지 않는다.
- Kakao 단독 결과를 TourAPI 대표메뉴가 확인된 후보로 가장하지 않는다.

## grilling에서 확정할 결정

1. `treatmenu`를 fallback으로 인정할지, 인정한다면 여러 메뉴 문자열에서 하나를 고르는 규칙은 무엇인지
2. 한 요청에서 허용할 `areaBasedList2` 최대 페이지 수와 `detailIntro2` 최대 호출 수
3. TourAPI timeout/5xx에 대해 같은 요청 안에서 재시도할지, 즉시 외부 오류로 끝낼지
4. 후보 부족 시 기존 `INVALID_AI_PLACE`를 유지할지, 후보 부족과 외부 장애를 별도 에러 코드로 분리할지
5. 현행 `areaCode/sigunguCode` 호환 경로를 유지할 기간과 `lDongRegnCd/lDongSignguCd` 전환 작업의 범위

