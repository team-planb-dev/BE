# PlanB #56 식약처 영양 API 공식 자료 재검증

검증일: 2026-09-20  
범위: `getFoodNtrCpntDbInq02`/`03`, `FOOD_NM_KR`, `SERVING_SIZE`, `Z10500`, `칼국수` 데이터 존재 여부  
원칙: 공공데이터포털·식품의약품안전처가 제공하는 현재 명세와 공개 데이터만 사실 근거로 사용했다. 저장소 문서는 조사 질문을 정하는 데만 사용했다.

## 결론

1. **현재 공식 명세는 `03`이다.** 공공데이터포털의 현재 Swagger는 host를 `apis.data.go.kr/1471000/FoodNtrCpntDbInfo03`, operation을 `getFoodNtrCpntDbInq03`으로 명시한다. 해당 데이터셋의 수정일은 2026-09-16이다. 반면 현재 공식 페이지에는 `02` 명세나 종료 공지가 없다. 따라서 신규 구현 기준은 `03`으로 보는 것이 타당하지만, `02`의 종료 여부와 정확한 전환 기한은 공식 자료만으로 확정할 수 없다. [공식 OpenAPI 페이지](https://www.data.go.kr/data/15127578/openapi.do), [공식 메타데이터](https://www.data.go.kr/catalog/15127578/openapi.json)
2. **`FOOD_NM_KR`의 완전일치/부분일치 규칙은 공식 명세에 적혀 있지 않다.** 설명은 `식품명`뿐이며 연산자, LIKE 여부, 토큰화, 정규화 규칙이 없다. 따라서 부분일치라고 문서 근거로 단정할 수 없다. [공식 Swagger가 포함된 OpenAPI 페이지](https://www.data.go.kr/data/15127578/openapi.do)
3. **`SERVING_SIZE`는 `영양성분함량기준량`, `Z10500`은 `식품중량`이다.** `Z10500 / SERVING_SIZE`는 같은 단위일 때 “기준량 수치를 식품중량 전체로 환산”하는 산술에는 쓸 수 있다. 그러나 공식 명세는 `Z10500`을 1인분이라고 정의하지 않으며, `DISH_ONE_SERVING`을 별도의 `1회분량 참고량`으로 둔다. 그러므로 이 비율을 일반적인 1인분 환산비로 쓰는 것은 공식 의미로 정당화되지 않는다. [공식 Swagger](https://www.data.go.kr/data/15127578/openapi.do), [공식 음식 표준데이터](https://www.data.go.kr/data/15100070/standard.do)
4. **공식 음식 표준데이터에 `칼국수` 계열은 존재한다.** 2026-09-20 공개 데이터 19,495행에서 이름에 `칼국수`가 든 행은 25개, 식품명이 정확히 `칼국수`인 행은 5개였다. `장칼국수`가 든 행은 `칼국수_간편조리세트_원주식장칼국수`, `칼국수_간편조리세트_장칼국수` 두 개다. 다만 이것만으로 `02`/`03` API에서 `FOOD_NM_KR=칼국수&DB_CLASS_NM=품목대표` 요청이 동일한 25행을 돌려준다고 증명되지는 않는다. [공식 음식 표준데이터 페이지](https://www.data.go.kr/data/15100070/standard.do), [공식 공개 데이터 조회](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_CD&colNmList=FOOD_NM&colNmList=FOOD_LV4_NM&colNmList=NUT_CON_SRTR_QUA&colNmList=SERV_SIZE&colNmList=FOOD_SIZE&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1)


## PlanB `02` 라이브 프로브 결과

2026-09-20 사용자 환경의 인증키로 현재 `FoodNtrCpntHandler` 경로를 실행했다.

| 검색어 | 정제 후 결과 |
| --- | --- |
| `칼국수` | 3건, 반환 이름 모두 `칼국수` |
| `장칼국수` | 0건 |
| `검은콩 장칼국수` | 0건 |

테스트는 종료 코드 0으로 완료됐다. 이 결과는 현재 PlanB의 `02`, `DB_CLASS_NM=품목대표`,
`FoodNtrCpntHelper` 정제를 모두 거친 최종 계약을 증명한다. 공식 규격 전체의 검색 알고리즘을
완전일치로 일반화하지는 않는다. #56에서는 `검은콩 장칼국수`의 보조 이름을 `칼국수`로 사용한다.

## 1. `02`와 `03`의 현재 상태 및 차이

### 공식 확인

- 현재 공개된 Swagger의 host/path는 `FoodNtrCpntDbInfo03/getFoodNtrCpntDbInq03`이다. 페이지는 이 operation을 `식품 영양성분 조회`로 설명한다. [공식 OpenAPI 페이지](https://www.data.go.kr/data/15127578/openapi.do)
- 현재 데이터셋의 등록일은 2024-04-11, 수정일은 2026-09-16이다. [공식 메타데이터](https://www.data.go.kr/catalog/15127578/openapi.json)
- `03`의 요청 파라미터는 `serviceKey`, `pageNo`, `numOfRows`, `type`, `FOOD_NM_KR`, `RESEARCH_YMD`, `MAKER_NM`, `FOOD_CAT1_NM`, `ITEM_REPORT_NO`, `UPDATE_DATE`, `DB_CLASS_NM`, `FOOD_OR_NM`이다. 인증키만 필수이고 나머지는 옵션으로 명세돼 있다. [공식 Swagger](https://www.data.go.kr/data/15127578/openapi.do)
- `03` 응답 명세에는 `SERVING_SIZE`, `NUTRI_AMOUNT_SERVING`, `Z10500`, `DISH_ONE_SERVING`, `FOOD_OR_CD`, `FOOD_OR_NM`과 다수의 영양성분 필드가 포함된다. [공식 Swagger](https://www.data.go.kr/data/15127578/openapi.do)
- 인증키 없이 `02`와 `03`을 각각 호출하면 둘 다 HTTP 401과 `SERVICE_KEY_IS_NULL`/reason code `20`을 반환했다. 이는 두 URL 모두 현재 공공데이터포털 게이트웨이에 도달한다는 뜻이지만, 인증 후 실제 operation이 정상 실행된다는 증거는 아니다. [02 무인증 호출](https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02?pageNo=1&numOfRows=1&type=json), [03 무인증 호출](https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo03/getFoodNtrCpntDbInq03?pageNo=1&numOfRows=1&type=json)
- 공공데이터포털은 2024년에 과거의 별도 영양성분 API들을 현재 `식품영양성분DB정보`로 통합·대체한다고 공지했다. 다만 이 공지는 endpoint suffix `02`를 지목하지 않으므로, 이것을 `02` 폐기 공지로 읽으면 안 된다. [공식 폐기·대체 공지](https://www.data.go.kr/bbs/ntc/selectNotice.do?originId=NOTICE_0000000003737)

### 공식 자료로 확인하지 못한 차이

- 현재 공식 페이지에 `02` Swagger나 변경 이력이 남아 있지 않아 **`02`와 `03`의 필드 단위 전체 diff는 확정할 수 없다.**
- `FOOD_OR_NM` 또는 `DISH_ONE_SERVING`이 정말 `03`에서 새로 추가됐는지도 현재 `03` 명세만으로는 증명할 수 없다. `03`에 존재한다는 사실만 확인됐다. [공식 Swagger](https://www.data.go.kr/data/15127578/openapi.do)
- `02`가 인증된 요청에서도 정상 응답하는지, 종료 예정인지, 종료 기한이 있는지는 확인하지 못했다. 사용자 환경의 키로 `02`/`03` 동일 질의를 각각 실행해야 닫히는 항목이다.

## 2. `FOOD_NM_KR` 검색 규칙

현재 Swagger는 `FOOD_NM_KR`을 선택 파라미터로 노출하며 설명을 `식품명`이라고만 쓴다. 완전일치, 부분일치, LIKE, 전방일치, 형태소 검색, 공백·밑줄 정규화에 관한 문구는 없다. 별도 부분검색 파라미터도 없다. [공식 Swagger](https://www.data.go.kr/data/15127578/openapi.do)

따라서 공식 문서로 확정할 수 있는 결론은 다음뿐이다.

- `FOOD_NM_KR`은 식품명 필터다. [공식 Swagger](https://www.data.go.kr/data/15127578/openapi.do)
- 매칭 알고리즘은 공개 규격에 없다. [공식 Swagger](https://www.data.go.kr/data/15127578/openapi.do)
- `검은콩 장칼국수`가 0건이었다는 기존 라이브 프로브는 그 입력의 결과만 증명하며, 전체 매칭 규칙을 완전일치라고 증명하지는 않는다.

확정하려면 인증된 동일 버전에서 최소 다음 대조가 필요하다.

1. `FOOD_NM_KR=칼국수`
2. `FOOD_NM_KR=장칼국수`
3. `FOOD_NM_KR=검은콩 장칼국수`

응답의 `totalCount`와 `FOOD_NM_KR` 목록을 함께 보존해야 한다. `칼국수` 질의가 `칼국수_소고기`, `칼국수_해물` 등을 반환하면 그 버전의 현재 동작은 적어도 부분/확장 매칭이다. 정확히 `칼국수`만 반환한다고 해서 내부 구현이 완전일치라고 단정할 수는 없지만, PlanB의 검색어 축약 효과는 판정할 수 있다. 공식 데이터상 비교 대상 이름은 실제로 존재한다. [공식 음식 표준데이터](https://www.data.go.kr/data/15100070/standard.do)

## 3. `SERVING_SIZE`, `Z10500`, 환산 가능성

### 공식 의미

| 필드 | 공식 설명 | 해석 범위 |
| --- | --- | --- |
| `SERVING_SIZE` | 영양성분함량기준량 | `AMT_NUM*` 영양값이 표현되는 기준량 |
| `NUTRI_AMOUNT_SERVING` | 1회 섭취참고량 | 섭취 참고량 |
| `Z10500` | 식품중량 | 해당 레코드의 식품중량 |
| `DISH_ONE_SERVING` | 1회분량 참고량 | 1회분량에 관한 별도 필드 |

출처: [공식 Swagger](https://www.data.go.kr/data/15127578/openapi.do)

### 공식 공개 데이터 실측

공식 음식 표준데이터 19,495행을 조회한 결과:

- 영양성분함량기준량은 `100g` 13,755행, `100ml` 5,740행이었다. [공식 공개 데이터 조회](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_NM&colNmList=NUT_CON_SRTR_QUA&colNmList=SERV_SIZE&colNmList=FOOD_SIZE&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1)
- 이 공개 음식 데이터에서 `1인(회)분량 참고량`은 19,495행 모두 빈 값이었다. [공식 공개 데이터 조회](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_NM&colNmList=NUT_CON_SRTR_QUA&colNmList=SERV_SIZE&colNmList=FOOD_SIZE&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1)
- `식품중량`은 12행이 비어 있었다. 칼국수 행에는 `700g`, `514.8`, `296.7`, `483ml`, `900g`, `1330g`, `1367g`처럼 단위가 있거나 없는 값이 섞여 있었다. [공식 공개 데이터 조회](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_CD&colNmList=FOOD_NM&colNmList=FOOD_LV4_NM&colNmList=NUT_CON_SRTR_QUA&colNmList=SERV_SIZE&colNmList=FOOD_SIZE&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1)
- `칼국수_간편조리세트_장칼국수`의 식품중량은 `1330g`, `칼국수_간편조리세트_원주식장칼국수`는 `1367g`이다. 이 값은 1인이 먹는 양이라고 보기보다 상품/조리세트 전체 중량일 가능성이 크며, 공식 명세도 이를 단순히 `식품중량`이라고만 정의한다. [공식 공개 데이터 조회](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_NM&colNmList=NUT_CON_SRTR_QUA&colNmList=SERV_SIZE&colNmList=FOOD_SIZE&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1), [공식 Swagger](https://www.data.go.kr/data/15127578/openapi.do)

### 판정

`AMT_NUM × (Z10500 / SERVING_SIZE)`는 아래 조건에서만 **식품중량 전체에 해당하는 값**을 계산하는 산술로 사용할 수 있다.

- 두 값에 숫자가 존재한다.
- 단위가 둘 다 `g`이거나 둘 다 `ml`이다.
- `Z10500`이 무엇의 전체 중량인지 제품 정책상 확인됐다.

그러나 이를 곧바로 **1인분 환산**에 쓰면 안 된다. 공식 인터페이스가 1회분량을 별도 필드로 구분하고, `Z10500`을 1인분으로 정의하지 않기 때문이다. 단위 없는 식품중량, 빈 값, `100ml` 기준과 `g` 중량의 혼합, 다인용 상품 전체 중량도 처리해야 한다. [공식 Swagger](https://www.data.go.kr/data/15127578/openapi.do), [공식 음식 표준데이터](https://www.data.go.kr/data/15100070/standard.do)

PlanB에서 안전한 순서는 다음이다.

1. `DISH_ONE_SERVING`이 숫자와 단위를 갖고 기준량과 단위가 같으면 그 비율을 사용한다.
2. `DISH_ONE_SERVING`이 없고 도메인상 `Z10500`이 한 그릇 전체라는 근거가 있는 음식 레코드에만 `Z10500 / SERVING_SIZE`를 사용한다.
3. 그 외에는 현재의 명시적 fallback을 유지하거나 `환산 불가`로 둔다.

2번의 “한 그릇 전체” 판정 규칙은 공식 스키마가 제공하지 않으므로 제품 정책이다.

## 4. `칼국수` 품목 존재

공식 음식 표준데이터의 현재 공개 19,495행에서 확인한 결과는 다음과 같다. [공식 컬럼·행수 응답](https://www.data.go.kr/download/columList.json?pk=15100070&ext=JSON), [공식 공개 데이터 조회](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_CD&colNmList=FOOD_NM&colNmList=FOOD_LV4_NM&colNmList=NUT_CON_SRTR_QUA&colNmList=SERV_SIZE&colNmList=FOOD_SIZE&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1)

| 조건 | 건수/값 |
| --- | --- |
| 식품명에 `칼국수` 포함 | 25행 |
| 식품명 정확히 `칼국수` | 5행 |
| 식품명에 `장칼국수` 포함 | 2행 |
| 장칼국수 관련 이름 | `칼국수_간편조리세트_원주식장칼국수`, `칼국수_간편조리세트_장칼국수` |

그 밖에 `칼국수_소고기`, `칼국수_해물`, `칼국수_닭고기`, `칼국수_들깨`, `칼국수_바지락`, `해물칼국수` 등이 존재한다. [공식 공개 데이터 조회](https://www.data.go.kr/download/standard.json?publicDataPk=15100070&colNmList=FOOD_CD&colNmList=FOOD_NM&colNmList=FOOD_LV4_NM&colNmList=NUT_CON_SRTR_QUA&colNmList=SERV_SIZE&colNmList=FOOD_SIZE&totalCount=19495&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1)

따라서 “식약처 공식 데이터에 칼국수가 없다”는 결론은 틀리다. 다만 PlanB가 쓰는 `DB_CLASS_NM=품목대표` 조건과 `FOOD_NM_KR` 검색을 통과하는지는 별개의 문제이며, 인증된 OpenAPI 응답으로 확인해야 한다.

## 확인 불가 항목

1. `02`의 인증 후 현재 가용성, 종료 일정, `03`과의 전체 스키마 차이.
2. `02`와 `03` 각각의 `FOOD_NM_KR` 실제 매칭 알고리즘.
3. `DB_CLASS_NM=품목대표`를 함께 보냈을 때 `칼국수` 25행 중 어떤 행이 반환되는지.
4. `Z10500`이 각 음식 레코드에서 한 그릇, 한 상품, 한 조리세트, 다인분 중 무엇을 뜻하는지 판정하는 공식 규칙.
5. `DISH_ONE_SERVING`의 실제 채움률과 단위 분포. 현재 Swagger에는 필드가 있지만, 이번에 사용한 음식 표준데이터의 대응 칼럼 `1인(회)분량 참고량`은 전부 비어 있었다. [공식 Swagger](https://www.data.go.kr/data/15127578/openapi.do), [공식 음식 표준데이터](https://www.data.go.kr/data/15100070/standard.do)

이 다섯 항목은 사용자 환경의 API 키로 `02`와 `03`을 같은 파라미터로 호출하고, 응답 원문에서 키만 제거해 비교해야 확정된다.
