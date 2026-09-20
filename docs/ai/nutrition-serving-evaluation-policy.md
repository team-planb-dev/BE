# 식약처 영양값 기준량과 PlanB 평가 정책

조사 기준일: 2026-09-20

이 문서는 식약처 영양값을 한 끼 기준으로 평가할 때 필요한 근거를 다시 확인한 기록이다.
외부 근거는 식품의약품안전처·공공데이터포털·국가법령정보센터의 현재 자료만 사용했고,
PlanB 동작은 저장소 소스로 확인했다.

## 결론

현재의 고정 `× 3` 평가는 공식 데이터 의미로 정당화되지 않는다.

- `SERVING_SIZE`는 **영양성분함량기준량**이다. 영문 필드명과 달리 1회분량이 아니다.
- `AMT_NUM*`은 같은 레코드의 `SERVING_SIZE`를 기준으로 한 영양성분 함량으로 읽어야 한다.
- `NUTRI_AMOUNT_SERVING`은 **1회 섭취참고량**, `DISH_ONE_SERVING`은 **1회분량 참고량**이다.
- `Z10500`은 **식품중량**이다. 1인분이라는 공식 근거가 없다.
- PlanB의 현재 `02` DTO는 `SERVING_SIZE`만 받고, 두 1회분량 필드와 `Z10500`은 받지 않는다.
- 따라서 현재 인터페이스에는 한 끼 환산을 신뢰할 근거가 없다.

P0의 가장 작은 안전한 정책은 **기준량 수치는 보존하되 한 끼 등급은 `NOT_EVALUABLE`로 두는 것**이다.
이렇게 하면 화면에 쓰는 원본 수치를 임의로 3배 하지 않고, 근거 없는 영양 추천 태그도 만들지 않는다.
신뢰 가능한 1회분량과 단위가 들어온 경우에만 별도의 후속 작업으로 비례 환산을 허용한다.

## 1. 사실·추론·미확인 구분

### 검증된 사실

#### 현재 공식 `03` 명세

공공데이터포털의 현재 Swagger는
`https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo03/getFoodNtrCpntDbInq03`을
식품 영양성분 조회 operation으로 공개한다.
현재 공식 페이지에는 `02` Swagger가 없다.
([공식 OpenAPI 명세](https://www.data.go.kr/data/15127578/openapi.do#/API%20%EB%AA%A9%EB%A1%9D/getFoodNtrCpntDbInq03))

공식 `03` Swagger의 필드 설명은 다음과 같다.

| 필드 | 공식 설명 | 확인되는 의미 |
| --- | --- | --- |
| `SERVING_SIZE` | 영양성분함량기준량 | 영양성분 값의 기준량 |
| `NUTRI_AMOUNT_SERVING` | 1회 섭취참고량 | 법령상 섭취 참고량 |
| `Z10500` | 식품중량 | 식품 또는 제품의 중량 |
| `DISH_ONE_SERVING` | 1회분량 참고량 | 1회분량에 관한 별도 참고값 |
| `AMT_NUM1` | 에너지(kcal) | 번호 1의 영양소와 단위 |
| `AMT_NUM3` | 단백질(g) | 번호 3의 영양소와 단위 |
| `AMT_NUM4` | 지방(g) | 번호 4의 영양소와 단위 |
| `AMT_NUM6` | 탄수화물(g) | 번호 6의 영양소와 단위 |
| `AMT_NUM7` | 당류(g) | 번호 7의 영양소와 단위 |
| `AMT_NUM8` | 식이섬유(g) | 번호 8의 영양소와 단위 |
| `AMT_NUM13` | 나트륨(mg) | 번호 13의 영양소와 단위 |
| `AMT_NUM23` | 콜레스테롤(mg) | 번호 23의 영양소와 단위 |
| `AMT_NUM24` | 포화지방산(g) | 번호 24의 영양소와 단위 |
| `AMT_NUM25` | 트랜스지방산(g) | 번호 25의 영양소와 단위 |

출처: [공식 OpenAPI Swagger](https://www.data.go.kr/data/15127578/openapi.do#/API%20%EB%AA%A9%EB%A1%9D/getFoodNtrCpntDbInq03)

`AMT_NUM`이라는 이름 자체에는 영양소나 단위 정보가 없다.
번호별 의미와 단위는 Swagger의 각 필드 설명이 정한다.
([공식 OpenAPI Swagger](https://www.data.go.kr/data/15127578/openapi.do#/API%20%EB%AA%A9%EB%A1%9D/getFoodNtrCpntDbInq03))

#### 공식 표시기준이 구분하는 양

식약처 고시는 영양성분 함량의 표시 기준으로 총 내용량, `100g(ml)`, 단위 내용량,
1회 섭취참고량을 구분한다. 총 내용량이 크면 `100g(ml)`당 표시를 허용하고,
1회 섭취참고량당 표시도 별도로 허용한다. 즉 `100g(ml)`, 총 식품중량,
1회 섭취참고량은 서로 바꿔 쓸 수 있는 용어가 아니다.
([식품등의 표시기준 별지 1, 14~15쪽](https://www.law.go.kr/LSW/flDownload.do?bylClsCd=200203&flNm=%5B%EB%B3%84%EC%A7%80+1%5D+%ED%91%9C%EC%8B%9C%EC%82%AC%ED%95%AD%EB%B3%84+%EC%84%B8%EB%B6%80%ED%91%9C%EC%8B%9C%EA%B8%B0%EC%A4%80&flSeq=156388521))

공식 정의상 `1회 섭취참고량`은 만 3세 이상 소비계층이 통상 소비하는 식품별 1회 섭취량과
시장조사 결과 등을 바탕으로 설정한 값이다. 제품 총중량이나 특정 사용자의 실제 섭취량과
동일하다는 뜻이 아니다.
([식품의약품안전처 법령해석](https://www.law.go.kr/LSW/cgmExpcInfoP.do?cgmExpcDatSeq=422340&mode=2&ofiClsCd=350123))

#### 공식 음식 데이터의 실제 형태

공식 음식 표준데이터는 `영양성분함량기준량`, 각 영양소 함량,
`1인(회)분량 참고량`, `식품중량`을 서로 다른 열로 제공한다.
([전국통합식품영양성분정보(음식) 표준데이터](https://www.data.go.kr/data/15100070/standard.do))

2026-09-20 공개된 음식 데이터 19,495행을 직접 집계하면:

- 영양성분함량기준량은 `100g` 13,755행, `100ml` 5,740행이다.
- `1인(회)분량 참고량`은 19,495행 모두 비어 있다.
- `식품중량`은 12행이 비어 있고 나머지는 단위가 있는 값과 없는 값이 섞인다.
- `칼국수_간편조리세트_장칼국수`는 기준량 `100g`, 식품중량 `1330g`이다.
- 공개 표의 피자 예시는 기준량 `100g`, 1인분 참고량 공란, 식품중량 `1640g` 또는 `930g`이다.

출처: [공식 데이터 컬럼·행수](https://www.data.go.kr/download/columList.json?pk=15100070&ext=JSON),
[공식 음식 데이터 조회](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_NM&colNmList=NUT_CON_SRTR_QUA&colNmList=SERV_SIZE&colNmList=FOOD_SIZE&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1),
[공식 데이터 미리보기](https://www.data.go.kr/data/15100070/standard.do)

이 데이터는 `식품중량`이 1인분이라는 해석을 반증한다. 다인용 조리세트나 제품 전체 중량도
같은 열에 들어간다.

### 근거가 있는 추론

`AMT_NUM*` 값은 같은 행의 `SERVING_SIZE`를 기준으로 해석해야 한다.
공식 Swagger가 `SERVING_SIZE`를 영양성분함량기준량으로 정의하고,
공식 표준데이터도 기준량 바로 뒤에 영양소별 함량을 배치한다.
예를 들어 `SERVING_SIZE=100g`이고 `AMT_NUM13=250`이면 나트륨은 `100g당 250mg`으로 읽는다.
([공식 OpenAPI Swagger](https://www.data.go.kr/data/15127578/openapi.do#/API%20%EB%AA%A9%EB%A1%9D/getFoodNtrCpntDbInq03),
[공식 음식 표준데이터](https://www.data.go.kr/data/15100070/standard.do))

공식 문서는 환산 수식을 규정하지 않는다. 다만 기준량과 신뢰 가능한 목표 1회분량이
모두 숫자로 파싱되고 동일 단위이면 다음 비례 계산은 수학적으로 가능하다.

```text
1회분량 영양값 = AMT_NUM × (1회분량 / SERVING_SIZE)
```

이 식은 API가 보장하는 정책이 아니라 소비자가 수행하는 계산이다.
`g↔ml`, `1식`, `개`, 단위 없는 값, 비균질 복합 음식은 밀도·개당중량·배분 기준이 없으면
안전하게 환산할 수 없다.

### 확인하지 못한 것

- 현재 공식 페이지는 `03`만 문서화한다. PlanB가 호출하는 `02`의 현재 공식 Swagger와
  `03`과의 필드별 차이는 확인할 수 없다.
- `02`가 `NUTRI_AMOUNT_SERVING`, `Z10500`, `DISH_ONE_SERVING`을 실제로 반환하는지는
  키를 제거한 원문 응답이 없어 확인하지 못했다.
- `NUTRI_AMOUNT_SERVING`과 `DISH_ONE_SERVING`의 적용 대상, 우선순위, 둘이 충돌할 때의
  선택 규칙은 현재 Swagger에 없다.
- 빈 1회분량 값이 “해당 없음”, “미수집”, “알 수 없음” 중 무엇인지는 명세에 없다.
- PlanB의 `NutritionThreshold` 값이 어떤 임상·공식 기준에서 왔는지는 저장소에 기록돼 있지 않다.

## 2. PlanB가 현재 받고 사용하는 값

### 호출 interface

`FoodNtrCpntHandler`는 설정된 base URL에 `/getFoodNtrCpntDbInq02`를 붙여 호출한다.
검색 조건은 `FOOD_NM_KR`과 `DB_CLASS_NM=품목대표`다.
(`src/main/java/com/planb/global/client/foodNtrCpnt/handler/FoodNtrCpntHandler.java:30`,
`src/main/java/com/planb/global/client/foodNtrCpnt/handler/FoodNtrCpntHandler.java:53`,
`src/main/java/com/planb/global/client/foodNtrCpnt/dto/request/FoodNtrCpntSearchRequest.java:9`)

응답은 `DB_GRP_NM=음식`인 행만 남긴다.
(`src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:23`)

### DTO가 보존하는 필드

현재 `FoodNtrCpntResponse.Item`은 다음 16개 필드만 매핑한다.

- 식별·분류 5개: `FOOD_CD`, `FOOD_NM_KR`, `DB_GRP_NM`, `FOOD_CAT1_NM`, `FOOD_REF_NM`
- 기준량 1개: `SERVING_SIZE`
- 영양값 10개: `AMT_NUM1`, `3`, `4`, `6`, `7`, `8`, `13`, `23`, `24`, `25`

`NUTRI_AMOUNT_SERVING`, `Z10500`, `DISH_ONE_SERVING`은 DTO에 없다.
(`src/main/java/com/planb/global/client/foodNtrCpnt/dto/response/FoodNtrCpntResponse.java:24`)

`NutritionService`는 DTO의 `SERVING_SIZE`도 읽지 않는다. 탄수화물, 당류, 식이섬유,
나트륨, 포화지방, 트랜스지방, 콜레스테롤, 지방만 `NutritionInfo`로 옮긴다.
에너지와 단백질은 DTO에 있지만 평가에 쓰지 않는다.
(`src/main/java/com/planb/domain/travel/service/NutritionService.java:179`)

### 현재 평가와 표시의 서로 다른 기준

평가 입력은 모든 영양값을 고정 `3.0`배 한 값이다.
`REFERENCE_SERVING_GRAMS=300.0`을 하드코딩하고 분모를 `100.0`으로 고정한다.
(`src/main/java/com/planb/domain/travel/service/NutritionService.java:24`,
`src/main/java/com/planb/domain/travel/service/NutritionService.java:196`)

반면 응답의 탄수화물·나트륨·지방은 3배 값을 다시 쓰지 않고 원본 `AMT_NUM` 값으로 되돌린다.
(`src/main/java/com/planb/domain/travel/service/NutritionService.java:220`)

따라서 현재 한 메뉴에는 두 기준이 공존한다.

| 소비처 | 현재 값 |
| --- | --- |
| 질환별 LOW/CHECK/HIGH 평가 | 원본 `AMT_NUM × 3` |
| 화면/저장 탄수화물·나트륨·지방 | 원본 `AMT_NUM` |
| 영양 추천 태그 | `× 3` 평가의 CHECK/HIGH 결과 |

화면·저장 수치는 `PlanService`가 `UNAVAILABLE`이 아닌 결과에서 그대로 가져간다.
영양 추천 태그는 상태가 `AVAILABLE`인 결과 중 LOW가 아닌 항목만 만들며,
탄수화물·나트륨·포화지방만 태그에 연결한다.
(`src/main/java/com/planb/domain/travel/service/PlanService.java:1227`,
`src/main/java/com/planb/domain/travel/service/PlanService.java:1350`,
`src/main/java/com/planb/domain/travel/service/PlanService.java:61`)

## 3. 고정 300g 평가의 문제

### 공식 의미와 맞지 않는 부분

`× 3`은 `SERVING_SIZE=100g`일 때 “이 음식 한 끼가 300g”이라는 가정이다.
공식 데이터가 1회분량 300g을 제공한 것이 아니다.

`SERVING_SIZE=100ml`인 행에서는 실제 계산이 `300ml` 추정인데도 상수 이름과 주석은 grams다.
현재 공식 음식 데이터는 `100ml` 행이 5,740개이므로 예외적인 경우가 아니다.
([공식 음식 데이터 조회](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_NM&colNmList=NUT_CON_SRTR_QUA&colNmList=SERV_SIZE&colNmList=FOOD_SIZE&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1))

`SERVING_SIZE`가 100이 아닌 행이 들어오면 분모도 틀린다. 현재 코드는 필드를 읽지 않으므로
이를 탐지하지 못한다.

### 사용자 결과에 미치는 영향

고정 배수는 낮을수록 좋은 성분과 높을수록 좋은 성분 모두를 바꾼다.

- 탄수화물·당류·나트륨·포화지방·트랜스지방·콜레스테롤은 실제 한 끼가 300 기준보다 작으면
  위험을 과대평가하고, 크면 과소평가한다.
- 식이섬유는 방향이 반대라서 임의의 3배가 부족한 음식을 충분하다고 평가할 수 있다.
- 추천 태그는 이 추정 등급에서 만들어지므로 근거 없는 태그가 붙거나 필요한 태그가 빠질 수 있다.
- 화면 숫자는 원본 기준량 값인데 태그는 3배 값에서 만들어져, 같은 화면의 숫자와 태그가
  서로 다른 양을 설명한다.

예를 들어 나트륨 `250mg`인 행의 기준량이 `100g`이면 화면에는 `250`이 남지만,
평가는 `750mg`으로 수행되어 고혈압 나트륨 `CHECK`가 된다.
실제 한 끼가 100g인지 600g인지는 현재 interface로 알 수 없다.
임계값은 `600mg`과 `1000mg`이다.
(`src/main/java/com/planb/domain/travel/entity/constant/NutritionThreshold.java:30`)

## 4. 권고하는 최소 P0 정책

### 즉시 적용할 정책

`NutritionService`의 public interface와 기존 상태 enum을 유지한다.
새 추상화나 새 상태는 필요하지 않다.

1. `REFERENCE_SERVING_GRAMS`, `SERVING_RATIO`, 고정 `toReferenceServing`을 평가 근거에서 제거한다.
2. 신뢰 가능한 1회분량이 없는 현재 응답은 `NOT_EVALUABLE`로 만든다.
3. `evaluations`는 빈 목록으로 둔다.
4. 탄수화물·나트륨·지방은 원본 `AMT_NUM` 값을 그대로 보존한다.
5. 조회 결과가 없거나 외부 호출이 실패한 경우는 지금처럼 `UNAVAILABLE`로 구분한다.

이 정책은 기존 seam이 이미 지원한다.

- `NOT_EVALUABLE`은 “영양정보 평가 불가”라는 기존 상태다.
  (`src/main/java/com/planb/domain/travel/entity/constant/NutritionEvaluationStatus.java:21`)
- `PlanService`는 `NOT_EVALUABLE` 결과의 원본 수치는 화면·저장에 반영한다.
  (`src/main/java/com/planb/domain/travel/service/PlanService.java:1227`)
- `PlanService`는 `AVAILABLE`만 영양 추천 태그로 변환하므로 `NOT_EVALUABLE`에는 태그가 붙지 않는다.
  (`src/main/java/com/planb/domain/travel/service/PlanService.java:1357`)

따라서 P0는 “근거 없는 평가와 태그를 중단”하면서 “식약처가 실제 제공한 기준량 수치”는 잃지 않는다.

### 표시 수치의 의미

보존되는 탄수화물·나트륨·지방은 **한 끼 값이 아니라 `SERVING_SIZE` 기준 값**이다.
현재 PlanB 응답 모델은 `SERVING_SIZE`를 화면까지 전달하지 않으므로 사용자가 기준량을 알 수 없다.

P0 범위를 평가 안전성으로 제한하면 숫자는 현재처럼 보존하되 1인분·한 끼 수치라고 표현하면 안 된다.
프론트가 현재 그렇게 표시한다면 해당 라벨을 제거하거나 수치를 숨기는 것까지 P0에 포함해야 한다.
후속 작업에서는 `servingBasis`를 응답에 함께 내려 `100g당` 또는 `100ml당`으로 표시해야 한다.
임의로 300g 환산한 값을 화면에 내리는 것은 허용하지 않는다.

### 나중에 1회분량을 사용할 조건

후속 작업에서 `03`으로 전환하고 1회분량 필드를 DTO에 추가하더라도 다음 조건을 모두 만족할 때만
등급 평가용 비례 환산을 한다.

1. `SERVING_SIZE`와 1회분량이 모두 숫자와 단위를 갖는다.
2. 두 단위가 동일하다. `g↔ml` 변환은 하지 않는다.
3. 값이 양수이고 비정상적으로 크거나 작은 값이 아니다.
4. `NUTRI_AMOUNT_SERVING`과 `DISH_ONE_SERVING`이 함께 있으면 공식 우선순위가 확인되거나
   두 값이 일치해야 한다. 충돌하면 평가하지 않는다.
5. `Z10500`은 이 판단에 사용하지 않는다.

조건을 충족하지 못하면 `NOT_EVALUABLE`과 원본 기준량 수치로 돌아간다.
`300g` 같은 fallback은 다시 두지 않는다. fallback이 곧 근거 없는 1회분량 발명이기 때문이다.

## 5. 필요한 검증

사용자가 외부 키로 실행할 프로브는 원문 응답의 키만 제거하고 다음 필드를 남겨야 한다.

```text
FOOD_NM_KR
DB_GRP_NM
DB_CLASS_NM
SERVING_SIZE
NUTRI_AMOUNT_SERVING
Z10500
DISH_ONE_SERVING
AMT_NUM4
AMT_NUM6
AMT_NUM13
AMT_NUM24
```

같은 검색어를 `02`와 `03`에 보내 다음을 확인한다.

1. `02`의 현재 가용성.
2. 세 분량 필드의 실제 반환 여부와 빈 값 분포.
3. `SERVING_SIZE`의 실제 단위.
4. `02`와 `03`의 같은 식품에서 영양값과 기준량이 같은지.

외부 프로브가 끝나기 전에도 P0 정책은 결정할 수 있다. 현재 DTO에 신뢰 가능한 1회분량이 없다는 사실은
저장소에서 이미 확정됐고, 공식 자료는 `Z10500`을 대체 1인분으로 쓰는 방안을 배제한다.

## 6. 최종 판정

| 항목 | 판정 |
| --- | --- |
| `SERVING_SIZE`를 1인분으로 해석 | 기각 |
| `Z10500`을 1인분으로 해석 | 기각 |
| 모든 음식에 300g 고정 환산 | 기각 |
| 단위 없는 값 또는 `g↔ml` 환산 | 기각 |
| 신뢰 가능한 동일 단위 1회분량으로 비례 환산 | 조건부 허용 |
| 1회분량이 없을 때 등급·추천 태그 생성 | 중단 |
| 원본 기준량 수치 보존 | 허용, 기준량 표시 필요 |

P0 구현은 `NutritionService` 한 곳에서 평가 상태를 `NOT_EVALUABLE`로 결정하는 것으로 끝낼 수 있다.
`PlanService`의 기존 소비 규칙이 수치 보존과 태그 억제를 이미 분리하고 있으므로,
새 module이나 새 interface는 필요하지 않다.
