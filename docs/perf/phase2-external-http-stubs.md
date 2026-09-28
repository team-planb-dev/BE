# Phase 2 — 외부 HTTP 스텁

- Issue: #97
- 기준 commit: `bb8180e` (`origin/dev`)
- 범위: Kor2Service, Kakao Map, Kakao Mobility, 식품영양성분 API. OpenAI는 별도 스텁을 같은 실행 명령으로 기동한다

## 1. 목적

Phase 3 부하 측정은 계획 생성 경로를 API 키, 과금, 쿼터, 네트워크 없이 반복 실행해야 한다.
네 client는 모두 base URL을 `external.<이름>.base-url` 하나에서 읽는다. 이 설정만 로컬 스텁으로
바꾸면 운영 코드 수정 없이 실제 외부 호출이 사라진다.

## 2. 방식

### 2-1. 선택

JDK에 포함된 `com.sun.net.httpserver.HttpServer`로 스텁을 만들었다. **새 의존성이 없다.**

| 후보 | 판단 |
|---|---|
| JDK `HttpServer` | 채택. 의존성 없음, 경로·상태 코드·지연을 직접 제어, 단독 프로세스로도 실행 가능 |
| WireMock | 기각. 새 의존성이 필요하다 |
| OkHttp `MockWebServer` | 기각. 새 의존성이 필요하고, 응답을 순서대로 쌓는 방식이라 경로별 고정 응답에 맞지 않는다 |
| Spring `MockRestServiceServer` | 기각. `RestTemplate`·`RestClient`용이며 이 프로젝트의 `WebClient`를 지원하지 않는다 |
| 운영 코드에 부하 테스트 분기 추가 | 기각. Phase 2 조건 위반 |

### 2-2. 구조

서버 하나가 API별 경로 접두어로 네 API를 나눠 받는다.

| API | base URL | 처리하는 경로 |
|---|---|---|
| Kor2Service | `<stub>/kor2` | `/areaCode2`, `/areaBasedList2`, `/searchKeyword2`, `/detailIntro2` |
| Kakao Map | `<stub>/kakao-map` | `/v2/local/search/keyword.json`, `/v2/routing/publictraffic` |
| Kakao Mobility | `<stub>/kakao-mobility` | `/v1/directions` |
| 식품영양성분 | `<stub>/food-nutrition` | `/getFoodNtrCpntDbInq02` |

fixture가 없는 경로는 `404`로 응답해 새 엔드포인트가 생기면 바로 드러난다.

### 2-3. fixture 선택

fixture는 `src/test/resources/loadtest/` 아래에 있다. 모든 값은 가짜이며 이름에 "스텁"을 붙였다.

| 요청 | fixture |
|---|---|
| `/areaCode2` (`areaCode` 없음) | `kor2/area-codes.json` — 시·도 17개 |
| `/areaCode2` (`areaCode` 있음) | `kor2/sigungu-codes.json` — 요청 지역과 무관하게 같은 시군구 목록 |
| `/areaBasedList2`, `/searchKeyword2` (`contentTypeId=39`) | `kor2/restaurants.json` |
| `/areaBasedList2`, `/searchKeyword2` (그 외) | `kor2/attractions.json` |
| `/detailIntro2` | `kor2/detail-intro/<contentId>.json`, 없으면 `default.json` |
| Kakao 장소 검색 | `kakao/place-search.json` |
| Kakao 대중교통 경로 | `kakao/public-traffic-route.json` |
| Kakao 자동차 경로 | `kakao/car-route.json` |
| 식품영양성분 | `food-nutrition/food-nutrition.json` |

### 2-4. 요청을 따라가는 값

두 곳은 고정 응답으로는 기존 검증을 통과할 수 없어서 요청 파라미터로 값을 채운다.

| 대상 | 이유 | 방식 |
|---|---|---|
| 식품영양성분 이름 | `FoodNtrCpntHelper`가 요청 음식명과 **정확히 같은** 이름만 인정한다 | `FOOD_NM_KR`을 `FOOD_NM_KR`·`FOOD_REF_NM`에 넣는다 |
| Kakao 장소명·ID | 장소는 이름과 candidate ID 양쪽으로 중복 검사된다. 고정 응답이면 두 번째 카페부터 모두 중복이 된다 | `place_name`에 검색어를, `id`에 카테고리 번호 + 검색어의 고정 해시를 넣는다 |

fixture 안의 `{{param:이름}}`은 요청 값으로, `{{hash:이름}}`은 `String.hashCode()`의 부호 없는 값으로
바뀐다. 둘 다 입력만으로 결정되므로 **같은 요청은 항상 같은 응답**을 받는다.

## 3. 시나리오

| 값 | 동작 |
|---|---|
| `normal` | fixture로 `200` |
| `delay` | 설정 시간만큼 늦게 `200` |
| `client-error` | `401` |
| `server-error` | `503` |
| `timeout` | 응답 헤더 없이 설정 시간 동안 연결을 붙잡았다가 끊는다 |

환경변수:

| 변수 | 기본값 | 뜻 |
|---|---|---|
| `STUB_PORT` | `18080` | 단독 실행 포트 |
| `STUB_SCENARIO` | `normal` | 모든 API의 시나리오 |
| `STUB_SCENARIO_<API>` | — | API별 덮어쓰기. `<API>`는 `KOR2`, `KAKAO_MAP`, `KAKAO_MOBILITY`, `FOOD_NUTRITION` |
| `STUB_DELAY_MS` | `800` | `delay` 지연 시간 |
| `STUB_TIMEOUT_MS` | `30000` | `timeout` 유지 시간 |
| `OPENAI_STUB_PORT` | `18081` | OpenAI 호환 스텁 포트 |
| `STUB_PLAN_START_DATE` | `2030-01-01` | 고정 1박 2일 일정의 시작일 |

현재 애플리케이션에는 외부 HTTP timeout이 없다(`refactoring-plan.md` P8). `timeout` 시나리오는 그
상태를 재현할 뿐이며 timeout 값은 여기서 정하지 않는다.

## 4. 사용법

### 4-1. 스텁 실행

```bash
./gradlew travelLoadTestStubs
# 식품영양성분만 5xx로
STUB_SCENARIO_FOOD_NUTRITION=server-error ./gradlew travelLoadTestStubs
```

한 명령이 OpenAI 스텁과 외부 API 스텁을 함께 시작한다. `localhost`에만 바인딩하며
지연·timeout 요청은 가상 스레드로 처리한다. 외부 API 스텁만 필요하면 기존
`./gradlew externalHttpStub`도 사용할 수 있다.

### 4-2. 애플리케이션 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=loadtest'
```

`loadtest` 프로파일(`application-common-loadtest.yml`):

- 네 base URL을 `${LOADTEST_STUB_URL:http://localhost:18080}/<접두어>`로 둔다
- OpenAI base URL을 `${LOADTEST_OPENAI_STUB_URL:http://localhost:18081}/v1`로 둔다
- 외부 API 키와 OpenAI 키는 **환경변수에서 읽지 않고 고정된 가짜 값**을 쓴다. 실행 셸에 실제
  키가 있어도 부하 테스트가 실제 키를 쓰지 않는다
- DB, Redis, `JWT_SECRET`은 `local`처럼 환경변수로 받는다. 외부 API가 아니라 애플리케이션 자체의 설정이다

### 4-3. 호출 확인

```bash
curl http://localhost:18080/__stub/requests          # API·경로별 호출 수
curl -X DELETE http://localhost:18080/__stub/requests # 초기화
```

## 5. OpenAI 스텁과의 계약

OpenAI 스텁의 structured response는 Tool 호출 결과의 candidate ID를 그대로 써야 한다. 그 ID는
이 fixture가 정한다. **fixture 파일이 기준**이며 아래는 요약이다.

| 대상 | ID | 비고 |
|---|---|---|
| 관광지 12개 | `900001` ~ `900012` | 이름 `스텁 관광지 01` ~ `12`, 카테고리 HS01·NA01·VE02·EX01 순환 |
| 음식점 8개 | `910001` ~ `910008` | 대표 메뉴 비빔밥·불고기·칼국수·삼계탕·생선구이·된장찌개·설렁탕·냉면 (서로 다름) |
| Kakao 장소 | `kakao:<n><hash>` | `n`은 AT4=1, CE7=2, FD6=3. `hash`는 검색어의 고정 해시 |

지역: 시·도 17개와 도 지역 시군구 16개(강릉시, 경주시, 춘천시, 속초시, 포항시, 안동시, 전주시,
여수시, 순천시, 제주시, 서귀포시, 수원시, 청주시, 천안시, 창원시, 통영시)를 지원한다. 목록에 없는
이름은 `TourAPI 지역코드를 찾을 수 없습니다`로 실패한다.

## 6. 검증

`src/test/java/com/planb/performance/external/`의 테스트는 Spring 컨텍스트 없이 **실제 handler와
client**를 스텁에 연결한다. 요청 경로와 파라미터, 기존 DTO 파싱, helper 필터를 그대로 통과한다.

| 테스트 | 확인하는 것 |
|---|---|
| `ExternalHttpStubServerTest` (14) | 여덟 엔드포인트, 도 지역의 지역코드→시군구코드→목록 호출 순서, 관광지 fixture의 `TourismTool` 후보 필터 통과, 음식점별 서로 다른 메뉴, 음식명 정확 일치, 검색어별 장소 안정성, 자동차 경로 분 환산, 같은 요청 같은 응답, 없는 경로 404, 네 시나리오와 API별 적용, 환경변수 파싱, 요청 기록과 초기화 |
| `LoadTestProfileTest` (6) | `loadtest` 그룹, 외부 API와 OpenAI base URL이 로컬 스텁을 가리킴, 주소 덮어쓰기, 실행 환경의 실제 키를 읽지 않음 |

통합 실행(`./gradlew travelLoadTestStubs`)도 확인했다. OpenAI Tool 호출과 후속 일정 응답,
외부 API 경로별 `200`, 환경변수 시나리오, 호출 수 집계와 종료를 확인했다.

## 7. 한계와 통합 단계로 넘길 것

- 현재 부하 fixture는 AI가 빈 일정을 반환하고 Java가 전체 관광 후보에서 정렬된 장소를 채운다.
  향후 AI가 후보 순서에 따라 직접 장소를 선택하는 fixture로 바꾸면
  `TourismTool.selectAttractionCandidates`의 shuffle 결정성을 별도로 다뤄야 한다
- Kakao 검색어에 `+`가 있으면 스텁이 공백으로 해석한다. 현재 사용하는 검색어에는 해당하지 않는다
- 시군구 목록은 요청 지역과 무관하게 같다. 도 지역이면 목록에 있는 시군구명만 쓸 수 있다
- OpenAI 부하 fixture는 1박 2일 관광 일정 하나만 제공한다. 다른 일수·식사·복약 시나리오는
  Phase 3 기준선 범위가 확장될 때 추가한다
