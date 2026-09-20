# 식약처 음식명 후보 매칭 정책

조사 기준일: 2026-09-20  
범위: PlanB `FoodNtrCpntHelper`의 후보 정제, 식약처 음식명에서 밑줄(`_`)의 의미, 안전한 매칭 실패 처리  
근거 범위: 공공데이터포털·식품의약품안전처가 공개한 Swagger, 컬럼 정의, 음식 표준데이터 원본과 현재 저장소 소스만 사용했다.

## 결론

현재 공식 명세에는 밑줄 앞·뒤의 의미나 파싱 규칙이 없다. `FOOD_NM_KR`은 `식품명`, `FOOD_REF_NM`은 `대표식품명`으로만 정의된다. 밑줄 앞이 지역명이고 뒤가 기본 음식명이라는 규칙, 또는 뒤가 항상 주재료·변형명이라는 규칙은 공식 자료에서 확인되지 않는다. [공식 OpenAPI Swagger](https://www.data.go.kr/data/15127578/openapi.do#/API%20%EB%AA%A9%EB%A1%9D/getFoodNtrCpntDbInq03), [공식 음식 표준데이터 컬럼 정의](https://www.data.go.kr/download/columList.json?pk=15100070&ext=JSON)

공식 음식 표준데이터의 `달걀탕_순두부` 행은 대표식품명이 `달걀탕`, 식품중분류명이 `순두부`다. 따라서 이 행을 `순두부` 음식으로 축약·동일시할 공식 근거가 없다. 오히려 공식 구조화 필드는 이 행의 대표 음식이 `달걀탕`임을 보여 준다. [공식 음식 표준데이터 원본](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_CD&colNmList=FOOD_NM&colNmList=FOOD_LV4_NM&colNmList=FOOD_LV5_NM&colNmList=FOOD_ORIGIN_NM&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1)

P0에서 안전한 정책은 다음과 같다.

1. `FOOD_NM_KR` 원문을 `trim`한 완전일치를 가장 먼저 채택한다.
2. 원문 일치가 없으면 구조화된 `FOOD_REF_NM`의 완전일치를 후보 확인에 쓸 수 있다. 다만 여러 행이 일치하면 API 순서의 첫 행을 임의로 선택하지 않는다.
3. 마지막 밑줄 뒤만 남기는 현재 정규화와 부분 문자열 일치만으로는 후보를 채택하지 않는다.
4. 유일하고 근거 있는 후보를 확정할 수 없으면 무매칭으로 처리한다. 현재 서비스 계약에서는 `UNAVAILABLE`이 이에 해당한다.

현재 작업 트리의 helper는 이 정책을 구현한다. 이 정책은 모호한 후보를 다른 음식의 영양값으로 확정하는 거짓 양성보다, 영양값을 제공하지 않는 거짓 음성을 택한다. 건강 관련 수치와 추천 태그에는 이 편이 안전하다.

## 1. 공식 명세가 정의하는 것

현재 공개된 `FoodNtrCpntDbInfo03` Swagger는 다음 필드만 의미를 부여한다.

| 필드 | 공식 설명 |
| --- | --- |
| `FOOD_NM_KR` | 식품명 |
| `FOOD_REF_NM` | 대표식품명 |
| `FOOD_CAT2_NM` | 식품중분류명 |
| `FOOD_OR_NM` | 식품기원명 |

출처: [공식 OpenAPI Swagger](https://www.data.go.kr/data/15127578/openapi.do#/API%20%EB%AA%A9%EB%A1%9D/getFoodNtrCpntDbInq03)

공식 음식 표준데이터의 대응 컬럼도 `FOOD_NM=식품명`, `FOOD_LV4_NM=대표식품명`, `FOOD_LV5_NM=식품중분류명`으로만 설명한다. 어느 문서도 `_`를 구분자로 정의하거나 각 토큰에 지역명·주재료·변형명 같은 역할을 부여하지 않는다. [공식 음식 표준데이터 페이지](https://www.data.go.kr/data/15100070/standard.do), [공식 컬럼 정의](https://www.data.go.kr/download/columList.json?pk=15100070&ext=JSON)

따라서 밑줄 토큰의 의미를 이름 문자열만으로 확정하는 것은 공식 스키마 해석이 아니라 PlanB가 별도로 만드는 휴리스틱이다.

## 2. 공식 원본 데이터에서 관찰되는 패턴

2026-09-20 공개 원본 19,495행을 집계했다. `_`가 있는 식품명은 16,883행이었다. 이 16,883행에서는 첫 번째 토큰이 모두 같은 행의 `대표식품명`과 일치했다. 반면 마지막 토큰이 `대표식품명`과 일치한 행은 50개, `식품중분류명`과 일치한 행은 1,343개였다. [공식 음식 표준데이터 원본](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_CD&colNmList=FOOD_NM&colNmList=FOOD_LV4_NM&colNmList=FOOD_LV5_NM&colNmList=FOOD_ORIGIN_NM&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1)

이 집계는 **현재 데이터의 관찰 결과**이지, 앞으로도 지켜지는 공식 명명 계약이 아니다. 공식 문서는 첫 토큰을 대표식품명으로 파싱하라고 규정하지 않는다.

오른쪽 부분은 한 가지 의미가 아니다.

| 공식 식품명 | 대표식품명 | 식품중분류명 | 관찰 가능한 형태 |
| --- | --- | --- | --- |
| `달걀탕_순두부` | `달걀탕` | `순두부` | 중분류명 |
| `김치순두부_돼지고기` | `김치순두부` | `돼지고기` | 재료명 |
| `김치순두부_바지락` | `김치순두부` | `바지락` | 재료명 |
| `순두부찌개_김치` | `순두부찌개` | `김치` | 재료·변형 표현 |
| `순두부찌개_간편조리세트_강릉식 짬뽕 순두부` | `순두부찌개` | `해당없음` | 제품 유형과 지역식 상품명 |
| `피자_점보스테이크불갈비 피자 (L)` | `피자` | `해당없음` | 상품명과 규격 |

출처: [공식 음식 표준데이터 원본](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_CD&colNmList=FOOD_NM&colNmList=FOOD_LV4_NM&colNmList=FOOD_LV5_NM&colNmList=FOOD_ORIGIN_NM&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1)

검증된 사실은 `_` 오른쪽에 재료처럼 보이는 값, 분류명, 제품 유형, 지역식 상품명, 규격이 섞여 있다는 것이다. 각 표현의 언어학적 역할은 데이터에서 추론할 수 있지만 공식 문법으로 일반화할 수 없다.

## 3. `달걀탕_순두부` 판정

공식 원본 행은 다음과 같다.

| 필드 | 값 |
| --- | --- |
| 식품코드 | `D105-211260000-0001` |
| 식품명 | `달걀탕_순두부` |
| 대표식품명 | `달걀탕` |
| 식품중분류명 | `순두부` |
| 식품기원명 | `가정식(분석 함량)` |

출처: [공식 음식 표준데이터 원본](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_CD&colNmList=FOOD_NM&colNmList=FOOD_LV4_NM&colNmList=FOOD_LV5_NM&colNmList=FOOD_ORIGIN_NM&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1)

따라서 다음을 구분해야 한다.

- **검증된 사실:** 전체 식품명은 `달걀탕_순두부`이고 대표식품명은 `달걀탕`이다.
- **근거 있는 부정 결론:** `순두부`가 중분류명이라는 사실만으로 전체 행을 `순두부` 음식의 영양값으로 사용할 수 없다.
- **추론:** 이름만 보면 순두부가 들어간 달걀탕 변형으로 읽을 수 있다. 이는 자연어 해석이며 공식 canonical-name 규칙이 아니다.
- **확인 불가:** 식약처 내부 구축 과정에서 `_`를 생성하는 데이터사전이나 명명 규칙은 공개 Swagger와 표준데이터 설명에서 찾지 못했다.

## 4. 매칭 방식별 안전성

| 방식 | 안전성 | 판정 |
| --- | --- | --- |
| `FOOD_NM_KR` 원문 완전일치 | 가장 높음 | 공백 앞뒤 제거 외에 토큰을 버리지 않으므로 동일한 공식 식품명임을 확인한다. |
| 손실 없는 표기 정규화 후 완전일치 | 제한적으로 가능 | `trim`처럼 의미를 보존하는 변환만 허용한다. 공백 제거, 기호 제거, 유니코드 치환까지 확대하려면 별도 충돌 검증이 필요하다. |
| `FOOD_REF_NM` 완전일치 | 후보 분류에는 유용 | 공식 구조화 대표식품명과 일치한다. 여러 세부 행이 남으면 이것만으로 한 영양 행을 임의 선택할 수 없다. |
| 마지막 `_` 뒤만 남긴 뒤 완전일치 | 안전하지 않음 | `달걀탕_순두부`를 `순두부`로 바꾸어 대표식품명이 다른 행을 채택한다. |
| 부분 문자열 일치 | 안전하지 않음 | 재료·변형·상품명·규격 안에서 우연히 일치할 수 있다. 검색 후보 탐색에는 쓸 수 있어도 최종 채택 근거가 되지 않는다. |

Swagger는 `FOOD_NM_KR` 검색 파라미터를 `식품명`이라고만 설명하며 서버의 완전일치·부분일치 규칙을 공개하지 않는다. 그러므로 서버가 돌려준 행이라는 사실만으로 PlanB 입력 음식과 동일하다고 볼 수 없고, 응답 필드로 다시 검증해야 한다. [공식 OpenAPI Swagger](https://www.data.go.kr/data/15127578/openapi.do#/API%20%EB%AA%A9%EB%A1%9D/getFoodNtrCpntDbInq03)

## 5. 현재 PlanB 동작과 안전 경계

현재 요청은 `FOOD_NM_KR`에 입력 이름을 보내고 `DB_CLASS_NM=품목대표`를 사용한다.

- `src/main/java/com/planb/global/client/foodNtrCpnt/handler/FoodNtrCpntHandler.java:27-55`
- `src/main/java/com/planb/global/client/foodNtrCpnt/dto/request/FoodNtrCpntSearchRequest.java:9-16`

현재 작업 트리의 `FoodNtrCpntHelper`는 `DB_GRP_NM=음식`만 남긴 뒤, `trim`한 원문 식품명의 완전일치를 먼저 찾는다. 원문 일치가 없으면 `FOOD_REF_NM` 완전일치를 최대 2개까지만 확인하고, 정확히 1개일 때만 반환한다. 0개이거나 2개 이상이면 빈 목록을 반환한다. 밑줄 suffix 절단과 부분 문자열 일치는 제거돼 있다.

- 그룹 필터: `src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:23-32`
- 원문 완전일치: `src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:34-47`
- 대표식품명 후보 2개 확인: `src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:49-58`
- 유일 후보만 반환하고 나머지는 무매칭: `src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:60-64`
- `trim`과 대소문자 무시 비교: `src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:67-82`

이 동작에서는 입력 `순두부`와 `FOOD_NM_KR=달걀탕_순두부`, `FOOD_REF_NM=달걀탕`인 후보가 일치하지 않는다. 조사 대상이 된 suffix 오판은 현재 helper 변경으로 차단된다.

DTO는 이미 `FOOD_REF_NM`을 `foodReferenceName`으로 보존한다. 이름을 파싱하지 않고 공식 구조화 필드를 비교할 seam이 존재한다.

- `src/main/java/com/planb/global/client/foodNtrCpnt/dto/response/FoodNtrCpntResponse.java:28-38`

`NutritionService`는 원문 이름의 완전일치를 찾고, 없으면 후보 목록의 첫 행을 선택한다.

- `src/main/java/com/planb/domain/travel/service/NutritionService.java:143-159`

실제 handler 경로에서는 helper가 대표식품명 후보를 한 행으로 확정한 경우에만 넘기므로, 대표식품명 다중 후보가 서비스의 첫 행 선택까지 도달하지 않는다. 원문 완전일치 결과는 모두 요청한 전체 식품명과 일치한다. 따라서 helper가 서비스 앞에서 모호성을 닫는 경계 역할을 한다.

## 6. 무매칭 처리의 타당성

빈 후보 목록은 현재 `NutritionService`에서 `UNAVAILABLE`로 변환되고, 영양 수치와 평가를 비운다.

- 빈 목록 분기: `src/main/java/com/planb/domain/travel/service/NutritionService.java:100-108`
- `UNAVAILABLE` 결과: `src/main/java/com/planb/domain/travel/service/NutritionService.java:128-140`

이 상태는 “공식 데이터에 해당 음식이 절대 없다”가 아니라 “현재 검색과 검증 규칙으로 동일 음식을 확정하지 못했다”는 뜻으로 사용해야 한다. 후보를 잘못 연결하면 다른 음식의 탄수화물·나트륨·지방이 화면에 표시되고 이후 건강 판단의 입력이 된다. 반대로 무매칭은 값을 비워 두므로 오류 범위가 작고 되돌리기 쉽다.

서빙 기준이 없어 평가하지 못하는 `NOT_EVALUABLE`과도 구분된다. 이름을 확정하지 못하면 `UNAVAILABLE`, 이름은 확정했지만 1회분량 평가 근거가 없으면 `NOT_EVALUABLE`이 맞다.

## 7. 최소 정책과 테스트 seam

가장 작은 안전 정책은 다음 순서다. 현재 helper는 1~5번을 모두 구현한다.

1. `DB_GRP_NM=음식` 필터를 유지한다.
2. `trim`한 `FOOD_NM_KR` 원문 완전일치를 우선한다.
3. 원문 일치가 없으면 `FOOD_REF_NM` 완전일치만 남긴다.
4. 대표식품명 일치 후보가 하나면 채택하고, 둘 이상이면 임의의 첫 행을 선택하지 않는다.
5. 밑줄 suffix 정규화와 부분일치만 남은 경우 빈 목록을 반환한다.

단위 테스트의 직접 seam은 `FoodNtrCpntHelper.filterFoodNutrition`이다.

- 원문 완전일치 우선: `src/test/java/com/planb/unit/global/client/foodNtrCpnt/FoodNtrCpntHelperTest.java:44-84`
- 대표식품명 완전일치: `src/test/java/com/planb/unit/global/client/foodNtrCpnt/FoodNtrCpntHelperTest.java:86-117`
- `달걀탕_순두부` 회귀 사례: `src/test/java/com/planb/unit/global/client/foodNtrCpnt/FoodNtrCpntHelperTest.java:119-144`
- 다중 대표식품명 후보 거부: `src/test/java/com/planb/unit/global/client/foodNtrCpnt/FoodNtrCpntHelperTest.java:146-175`
- 원문 완전일치 후보 3개 제한: `src/test/java/com/planb/unit/global/client/foodNtrCpnt/FoodNtrCpntHelperTest.java:177-213`
- DTO fixture가 대표식품명을 받을 수 있는 seam: `src/test/java/com/planb/unit/global/client/foodNtrCpnt/FoodNtrCpntHelperTest.java:248-284`
- 첫 행 fallback을 고정한 서비스 테스트: `src/test/java/com/planb/unit/domain/travel/service/NutritionServiceTest.java:97-156`

현재 helper 테스트는 다음 회귀 사례를 덮는다. 서비스의 첫 행 fallback 테스트는 helper를 거치지 않고 여러 불일치 후보를 직접 주입하는 단위 테스트다. 실제 handler 경로의 대표식품명 후보는 helper에서 유일성이 확인된다.

1. 입력 `순두부`, `FOOD_NM_KR=달걀탕_순두부`, `FOOD_REF_NM=달걀탕`이면 무매칭.
2. 입력 `달걀탕`, 같은 행이면 대표식품명 완전일치 후보.
3. 입력 `달걀탕_순두부`, 같은 행이면 원문 완전일치.
4. 부분 문자열만 일치하면 무매칭.
5. 대표식품명 완전일치가 여러 행이면 API 순서의 첫 행을 선택하지 않음.

외부 API 검색 자체의 부분일치 여부는 `@Tag("external")`인 `FoodNtrCpntHandlerTest`에서만 검증할 수 있다. 이 테스트는 외부 API 키가 필요하므로 사용자가 실행하는 범위다.

- 외부 테스트 클래스: `src/test/java/com/planb/integration/external/foodNtrCpnt/FoodNtrCpntHandlerTest.java:27-28`
- 현재 검색 probe: `src/test/java/com/planb/integration/external/foodNtrCpnt/FoodNtrCpntHandlerTest.java:118-135`
- 실제 `달걀탕_순두부` 제외 probe: `src/test/java/com/planb/integration/external/foodNtrCpnt/FoodNtrCpntHandlerTest.java:138-155`

## 확인 불가 항목

1. `_`를 생성하는 식약처 내부 데이터사전·명명 규약. 공개 Swagger와 표준데이터 설명에는 없다.
2. 첫 토큰과 대표식품명의 현재 16,883행 일치가 앞으로도 유지된다는 계약.
3. `FOOD_NM_KR` 서버 검색의 완전일치·부분일치·정규화 알고리즘. Swagger는 연산 방식을 명시하지 않는다.
4. 여러 `FOOD_REF_NM` 일치 행 중 하나를 영양 대표값으로 선택하는 공식 우선순위. 공개 명세에는 순위 필드나 선택 규칙이 없다.
