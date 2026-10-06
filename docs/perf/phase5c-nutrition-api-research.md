# Phase 5C 식품영양성분 API 공식 자료 확인

확인일: 2026-10-04. 실제 API 호출 없음. 인증키 확인·출력 없음.

## 대상 서비스와 저장소 호출

- 공식 서비스명: **식품의약품안전처_식품영양성분DB정보**. 공공데이터포털 데이터 번호 `15127578`, 제공기관 식품의약품안전처, REST/JSON·XML. [공식 서비스 페이지](https://www.data.go.kr/data/15127578/openapi.do)
- 저장소의 `FoodNtrCpntHandler.searchFoodNutrition` 호출 경로: `${FOOD_NTR_CPNT_URL}/getFoodNtrCpntDbInq02`. `FOOD_NTR_CPNT_URL`은 환경변수이므로 이 작업 트리에서 실제 호스트·서비스 경로 확인 불가. 요청에는 `pageNo=1`, `numOfRows=100`, `type=json`, `FOOD_NM_KR=<메뉴명>`, `DB_CLASS_NM=품목대표` 사용. 근거: `src/main/resources/application-common-prod.yml`, `src/main/java/com/planb/global/client/foodNtrCpnt/handler/FoodNtrCpntHandler.java`, `.../dto/request/FoodNtrCpntSearchRequest.java`. 공식 페이지가 서비스명은 확인하지만, 공개 HTML 본문에서 저장소 호출 경로와 전체 요청 URL을 독립적으로 확인하지 못함.

## 공식 제한·오류

| 항목 | 확인 결과 |
|---|---|
| 개발계정 신청 가능 트래픽 | **10,000**. 공식 페이지의 표기값. 실제 발급 계정의 승인량은 계정 화면에서 재확인 필요. |
| 운영계정 | 활용사례 등록 후 트래픽 증설 신청 가능. 운영 기본 허용량 숫자는 페이지에 없음. 운영단계는 심의승인. |
| 일일 허용량 초과 | 응답 오류 `LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR`, 코드 `22`. 공식 설명은 **일일** 호출 허용량 초과. |
| 초당 허용량 초과 | 응답 오류 `LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR`, 코드 `23`. **초당 허용량의 숫자**는 공개 페이지에 없음. |
| 동시 호출 수 | 공식 페이지에 허용 병렬 요청 수 또는 동시 접속 수 명시 없음. `10,000`을 동시 호출 한도로 해석 불가. |
| 지연·SLA | 공식 페이지에 응답시간 분포, 보장 상한, 가용성 SLA 명시 없음. 코드 주석의 정상 응답 `4~6초`는 공식 수치나 실측 아님. |
| 연결·대기 오류 | `SERVICETIMEOUT_ERROR` 코드 `05`: 기관 API/GW 연결 실패 또는 응답 대기시간 초과. 이것만으로 실제 응답시간 상한 추론 불가. |

위 표의 포털 표기·오류 설명 출처: [공공데이터포털 해당 서비스의 이용 조건과 에러코드](https://www.data.go.kr/data/15127578/openapi.do). 식약처의 [K-FIND 안내](https://various.foodsafetykorea.go.kr/nutrient/industry/openApi/info.do)도 이 공공데이터포털 서비스로 연결.

## 실제 지연 확인 절차와 병렬화 판단 조건

1. 현재 사용 중인 **개발/운영 계정의 승인된 일일 트래픽 및 초당 한도**를 공공데이터포털 계정 화면 또는 운영기관 답변으로 확인. 공개 페이지에는 개별 계정 승인량과 초당 숫자가 없음.
2. 기존 앱의 동일 GET 요청을 낮은 빈도로 소수 반복 측정. 메뉴명, 시간대, HTTP 상태, 본문 `header.resultCode`, 외부 HTTP 구간 경과시간, timeout을 기록. 인증키와 인증키가 포함된 전체 URL은 기록하지 않음. 코드의 `NutritionService`는 조회마다 15초 timeout 사용.
3. 단일 호출의 p50·p95와 오류율을 먼저 확인. 계획된 요청량이 승인량을 넘지 않도록 유지하고 코드 `22`·`23`을 일부러 유발하지 않음. 실제 병렬 요청 수는 제공기관의 허용 기준 확인 후에만 작은 상한으로 실험.
4. 로컬 fixture의 `nutrition_lookup`·`nutrition_enrichment`·전체 요청 시간 기여도와 실제 호출 지연을 함께 검토. 동시 호출 허용 기준이 확인되지 않으면 병렬화 상한을 임의로 정하지 않고 적용 보류.

**현재 판정:** 공식 자료만으로 동시 호출 허용 수나 실제 응답시간을 확정할 수 없음. 병렬화 결정 보류.
