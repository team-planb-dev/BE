# 식당 메뉴명과 식약처 표준 품목명

한국관광공사 Kor2 API가 주는 메뉴명은 식당이 붙인 상품명이다. 식약처 식품영양성분 API는 표준 품목명만 안다. 둘 사이에 변환이 없어서, 메뉴명을 그대로 검색어로 보내고 결과가 없으면 영양성분이 전부 빈칸이 된다. `PlanService`가 그 비율을 재려고 로그를 남기고 있고, 주석이 "AI에게 표준 품목명을 따로 받는 방식"을 다음 수단으로 지목해 두었다(`src/main/java/com/planb/domain/travel/service/PlanService.java:1236`).

## 지금 메뉴명이 조회가 되는 경로

메뉴명은 어디에서도 가공되지 않고 그대로 외부 API의 검색어가 된다.

1. AI가 `getRestaurantDetail`로 받은 `firstmenu`(없으면 `treatmenu`)를 `menuName`으로 쓴다(`src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:261`, `src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:386`). Java가 식사 슬롯을 직접 채울 때도 같은 `firstmenu`를 쓴다(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:359`).
2. AI가 그 이름으로 `evaluateFoodNutrition` Tool을 부른다(`src/main/java/com/planb/ai/mcp/TourismTool.java:240`). `PlanTourismTool`은 같은 호출을 위임만 한다(`src/main/java/com/planb/ai/mcp/PlanTourismTool.java:212`).
3. `NutritionService.evaluateFoodNutrition`이 받은 문자열을 그대로 요청으로 감싼다(`src/main/java/com/planb/domain/travel/service/NutritionService.java:49`). `FoodNtrCpntSearchRequest.of`는 `DB_CLASS_NM`을 `"품목대표"`로, `numOfRows`를 100으로 고정한다(`src/main/java/com/planb/global/client/foodNtrCpnt/dto/request/FoodNtrCpntSearchRequest.java:9`).
4. `FoodNtrCpntHandler`가 그 문자열을 `FOOD_NM_KR` 쿼리 파라미터에 싣는다(`src/main/java/com/planb/global/client/foodNtrCpnt/handler/FoodNtrCpntHandler.java:48`). URL 인코딩 외에 손대는 것은 없다(`src/main/java/com/planb/global/client/helper/DataUriBuilder.java:79`).
5. 응답이 오면 `FoodNtrCpntHelper.filterFoodNutrition`이 거른다(`src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:12`).
6. `NutritionService`가 후보 중 하나를 고르고(`src/main/java/com/planb/domain/travel/service/NutritionService.java:110`), 한 끼 분량으로 환산해 평가하고(`src/main/java/com/planb/domain/travel/service/NutritionService.java:71`), 응답 수치는 실측으로 되돌린다(`src/main/java/com/planb/domain/travel/service/NutritionService.java:175`).

**핵심은 4번이다.** 검색어를 바꾸는 지점이 없으므로, 5번 이후의 어떤 필터도 애초에 응답에 없는 품목을 되살릴 수 없다.

### 정규화가 하는 일 — 이슈 #47 / PR #48이 바꾼 것

`normalizeFoodName`은 밑줄(`_`) 뒤만 남긴다(`src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:63`). `"부산_돼지국밥"` 같은 식약처 쪽 표기를 `"돼지국밥"`으로 만들기 위한 것이고, 공백·`+`·`·` 같은 구분자는 보지 않는다. 이 메서드는 이슈 #47 이전부터 있었다(`84b46d5 refactor: improve external public data API request handling`).

PR #48(`46f4075`)이 이 파일에서 실제로 한 일은 정규화 추가가 아니라 **필터 삭제**다. 커밋 `41a5309 refactor: drop food origin filters the nutrition API no longer feeds`가 `FOOD_OR_NM`(`foodOriginName`) 필드 자체와 그것에 걸려 있던 두 필터 — 급식 제외, 외식 우선 — 를 지웠다. 그 앞 단계인 PR #42(`79806e4 fix: keep food nutrition candidates that carry no origin name`)는 같은 필드가 실제 응답에서 비어 온다는 것을 발견하고 null-safe하게 바꾼 것이었다. 즉 #47/#48 계열의 수정은 **후보를 더 남기는 쪽**이었지 이름을 더 잘 맞추는 쪽이 아니었다.

지금 남아 있는 매칭 규칙은 두 단계다.

- 정확 일치: 정규화한 양쪽 이름이 같은 후보만, 최대 3개(`src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:37`).
- 부분 일치: 없으면 **품목명이 검색어를 포함하는** 후보 최대 3개(`src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:51`).

포함 방향이 한쪽이다. `"얼큰돼지국밥".contains("돼지국밥")`은 참이지만(`src/test/java/com/planb/unit/global/client/foodNtrCpnt/FoodNtrCpntHelperTest.java:86`이 고정한 동작), `"장칼국수".contains("검은콩 장칼국수")`는 거짓이다. 검색어가 표준 품목명보다 **긴** 경우 — 이 문서가 다루는 경우 전부 — 는 이 필터를 통과하지 못한다. 응답에 품목이 들어 있더라도 그렇다.

### 여러 개가 왔을 때 무엇을 고르는가

`findFoodItem`은 대소문자 무시 + `trim` 후 완전 일치하는 첫 후보를 찾고, 없으면 **목록의 첫 번째**를 쓴다(`src/main/java/com/planb/domain/travel/service/NutritionService.java:115`). 여기의 완전 일치는 `FoodNtrCpntHelper`의 정규화(`_` 절단)를 거치지 않은 원본 이름 비교라 기준이 서로 다르다. 그리고 `.orElse(items.getFirst())`(`:125`)는 순위 개념이 없다. 헬퍼가 넘긴 최대 3개 중 API 응답 순서상 앞선 것을 무조건 취한다. `"비빔밥"`으로 검색해 `"전주비빔밥"`, `"산채비빔밥"`이 왔다면 `"전주비빔밥"`이 쓰인다(`src/test/java/com/planb/unit/domain/travel/service/NutritionServiceTest.java:135`). 이것이 현재 유일한 선택 규칙이다.

결과가 아예 비면 `UNAVAILABLE`이 되고 수치 세 칸이 모두 null이 된다(`src/main/java/com/planb/domain/travel/service/NutritionService.java:55`, `:95`). 조회는 됐는데 필요한 성분이 비어 있으면 `NOT_EVALUABLE`이다(`src/main/java/com/planb/domain/travel/helper/NutritionEvaluator.java:130`, `:177`, `:207`). 이 문서의 문제는 전자다.

### 환산 상수가 적용되는 범위

`REFERENCE_SERVING_GRAMS = 300.0`(`src/main/java/com/planb/domain/travel/service/NutritionService.java:28`)은 **평가 등급에만** 쓰인다. 100g 기준 응답에 3배를 곱한 값을 `NutritionEvaluator`에 넘기고(`src/main/java/com/planb/domain/travel/service/NutritionService.java:71`, `:146`), 화면에 나가는 `carbohydrate`/`sodium`/`fat`은 환산 전 실측으로 되돌린다(`src/main/java/com/planb/domain/travel/service/NutritionService.java:175`). 이 문서의 문제와는 독립이다. 이름이 안 맞아 조회 자체가 실패하면 환산할 값이 없다. 다만 이름 해결이 개선되면 등급 정확도의 다음 병목이 이 상수가 된다. 파일 주석이 이미 "유일한 가정이자 조정 지점"이라고 밝혀 두었다(`:24`).

### 로컬 테이블은 없다

`food_info`는 사용자가 등록한 알레르기/기피 음식이다. `health_id`로만 조회되고(`src/main/java/com/planb/domain/health/repository/FoodInfoRepository.java:12`), 컬럼은 `food_name`, `food_type` 둘뿐이며(`src/main/resources/db/migration/V3__add_health_domain.sql:45`), 쓰이는 곳은 알레르기 태그 판정이다(`src/main/java/com/planb/domain/travel/service/PlanService.java:1372`). 표준 품목명 매핑과는 무관하다. 영양 조회 결과를 캐시하는 곳도 없다. Redis는 리프레시 토큰과 편집 캐시에만 쓰인다(`src/main/java/com/planb/domain/travel/repository/PlanEditCacheRepository.java:19`).

## 여덟 개 이름이 실패하는 이유

한국어 음식명은 핵심 명사가 뒤에 온다. 앞에 붙는 것은 재료, 조리법, 지명, 상호다. 실패 유형은 넷이다.

| 메뉴명 | 유형 | 표준 품목 후보 | 어휘 규칙으로 되는가 |
|---|---|---|---|
| 검은콩 장칼국수 | 재료 수식어 + 공백 | 장칼국수 | 된다. 공백 분리 |
| 멍게 비빔밥 | 재료 수식어 + 공백 | 비빔밥 | 된다. 공백 분리 |
| 모듬생선구이 | 수식어, 공백 없음 | 생선구이 | 품목명 목록이 있어야 된다 |
| 알곤이칼국수 | 재료, 공백 없음 | 칼국수 | 품목명 목록이 있어야 된다 |
| 한우광양불고기 | 재료 + 지명, 공백 없음 | 불고기 | 품목명 목록이 있어야 되고, 값은 근사치 |
| 영양솥밥+생선구이 | 복합 요리, `+` | 솥밥 / 생선구이 | 분리만 된다. 선택은 정책 문제 |
| 초당순두부·두부전골 | 지명 + 복합 요리, `·` | 순두부 / 두부전골 | 분리만 된다. 선택은 정책 문제 |
| 초당두부밥상 | 지명 + 상차림 | 없음 | **안 된다** |

세 덩어리로 갈린다.

**공백으로 갈리는 것(2개).** `"검은콩 장칼국수"`, `"멍게 비빔밥"`. 마지막 공백 뒤만 남기면 끝난다. 열 줄짜리 코드로 해결된다.

**공백이 없는 것(3개).** `"모듬생선구이"`, `"알곤이칼국수"`, `"한우광양불고기"`. 자를 자리를 문자열 자체로는 알 수 없다. `"알곤이칼국수"`를 `"이칼국수"`로 자를지 `"칼국수"`로 자를지는 **표준 품목명 목록을 갖고 있어야만** 정해진다. 그 목록을 이 저장소는 갖고 있지 않다 — `FoodNtrCpntHelper`는 API가 이미 돌려준 것만 거르지, 품목 사전을 들고 있지 않다(`src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:23`). `"한우광양불고기"`는 잘라도 절반의 성공이다. 한우는 재료가 아니라 값을 바꾸는 정보인데 `"불고기"` 품목대표로 떨어지면 그 차이가 사라진다.

**복합 요리(2개).** `"영양솥밥+생선구이"`, `"초당순두부·두부전골"`. 구분자로 나누는 것은 쉽다. 나눈 뒤 무엇을 한 끼로 볼지가 문제다. 앞을 택할지, 뒤를 택할지, 둘을 합산할지는 어휘 문제가 아니라 제품 결정이다. 지금 자료구조가 메뉴 하나에 수치 하나를 요구하므로(`src/main/java/com/planb/domain/travel/service/PlanService.java:1227`) 합산하려면 그 위까지 손봐야 한다.

**의미가 안 맞는 것(1개).** `"초당두부밥상"`. 핵심 명사가 `"밥상"`이고 이것은 요리가 아니라 상차림이다. 표준 품목에 대응이 없다. 어떤 형태소 분석기도, 어떤 편집거리도 이것을 `"순두부"`로 보내주지 않는다. 사람이나 언어모델이 "초당 = 강릉 초당동 = 순두부"를 알아야 나오는 답이다.

정리하면 **순수 어휘 규칙은 8개 중 5개(공백 2 + 사전 있으면 3)까지다.** 복합 2개는 분리까지만 하고 선택 규칙을 따로 정해야 하며, `"초당두부밥상"` 한 개는 원리상 닿지 않는다.

## 선택지

### 어휘 처리

외부 라이브러리 정보는 2026-09-19 기준 `repo1.maven.org` 메타데이터와 GitHub API로 확인했다.

**Apache Commons Text** — `org.apache.commons:commons-text:1.15.0`, Apache-2.0, 최신 릴리스 메타데이터 `20251207`, jar 265KB, 추가 전이 의존성 사실상 없음. `org.apache.commons.text.similarity` 패키지에 `LevenshteinDistance`, `JaroWinklerSimilarity`, `LongestCommonSubsequence`가 있다. 가볍고 유지되고 있다. 다만 **이 문제에 맞지 않는다.** 편집거리는 오타를 잡는 도구인데 `"초당두부밥상"`과 `"순두부"`는 오타 관계가 아니다. 그리고 비교할 후보 목록이 있어야 쓸 수 있는데, 지금 실패하는 이유가 바로 그 후보가 응답에 없다는 것이다.

**Lucene Korean analyser (Nori)** — `org.apache.lucene:lucene-analysis-nori:10.5.1`, Apache-2.0, 메타데이터 `20260812`, jar 7.8MB. 내용물을 열어 보면 `ConnectionCosts.dat` 11.2MB, `TokenInfoDictionary$buffer.dat` 7.3MB 등 mecab-ko-dic이 통째로 들어 있고 압축 해제 기준 약 25MB다. `lucene-core`(4.7MB)와 `lucene-analysis-common`을 끌고 온다. 사전은 정적 싱글턴으로 힙에 올라간다. 형태소는 잘 자른다. 그런데 자른 경계가 식약처 품목명 경계와 같다는 보장이 없고, `"초당두부밥상"`은 깨끗이 분석되고도 대응 품목이 없다. **필요한 사전은 한국어 형태소 사전이 아니라 식약처 품목명 목록이다.** 25MB를 지고 얻는 것이 앞의 5개뿐이다.

**open-korean-text (구 twitter-korean-text)** — `org.openkoreantext:open-korean-text:2.3.1`, Apache-2.0(pom 확인), jar 1.7MB. 마지막 릴리스 메타데이터가 `20180807`로 8년 전이다. GitHub 저장소는 archived가 아니고 마지막 푸시가 2024-03-12이지만 릴리스는 멈춰 있다. Scala 2.12 런타임(`scala-library`)을 끌어온다. Spring Boot 4 / Java 21 프로젝트에 Scala 표준 라이브러리를 들이는 값이 얻는 것보다 크다.

**KOMORAN** — `com.github.shin285:KOMORAN`. **Maven Central에 없다**(`repo1.maven.org`에서 404). JitPack에만 있고 최신이 `3.4.0-beta`이며 GitHub 릴리스 기준 2020-05-04다(저장소 자체는 2026-03-30까지 푸시됨). 쓰려면 `build.gradle:29`의 `repositories`에 jitpack.io를 추가해야 한다. 베타 버전 + 3rd-party 저장소 추가는 이 크기의 문제에 과하다.

**java-string-similarity** — `info.debatty:java-string-similarity:2.0.0`, MIT(pom 확인), 메타데이터 `20200512`. Commons Text와 같은 자리에 있고 6년 전 릴리스다. Commons Text를 두고 이것을 고를 이유가 없다.

의존성을 아예 안 쓰는 어휘 처리도 있다. 구분자(`+`, `·`, `,`, `/`)로 쪼개고 마지막 공백 뒤만 남기는 것은 `String.split`과 `lastIndexOf`로 끝난다(`FoodNtrCpntHelper.normalizeFoodName`이 이미 `lastIndexOf("_")`로 같은 일을 한다). 공백 2개 + 복합 2개의 분리까지를 0개 의존성으로 얻는다.

### AI에게 표준 품목명을 같이 받기

주석이 제안한 방법이다(`src/main/java/com/planb/domain/travel/service/PlanService.java:1238`).

비용이 생각보다 작다. AI는 이미 `evaluateFoodNutrition`을 메뉴마다 부르고 있고(`src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:275`), 그 시점에 메뉴명을 컨텍스트에 갖고 있다. 인자 하나를 더 받는 것이므로 **추가 모델 호출도, 추가 왕복도, 추가 지연도 없다.** 늘어나는 것은 Tool 호출 인자의 출력 토큰 몇 개와 프롬프트 지시문 몇 줄이다. 일정 하나당 RESTAURANT 슬롯 수만큼이므로 1박2일이면 서너 번, 즉 한 자릿수 토큰 × 서너 번이다. 사용하는 모델은 환경변수로 주입되어(`src/main/resources/application-common-prod.yml:31`) 이 저장소에서는 단가를 특정할 수 없지만, 어떤 단가에서도 무시할 수 있는 크기다.

정확도는 이 중 유일하게 8개를 전부 덮는다. `"초당두부밥상"` → `"순두부"`는 지역 상식이 있어야 나오고, `"한우광양불고기"`를 `"불고기"`가 아니라 `"소불고기"`로 보내는 판단도 마찬가지다. 대신 환각의 여지가 있다. 그래서 받은 이름은 **원래 메뉴명으로 먼저 조회해 보고 실패했을 때만** 쓰는 보조 검색어여야 한다. 지금 성공하는 2/3은 건드리지 않는다.

### 로컬 매핑 테이블

관찰된 실패를 씨앗으로 `Map<String, String>`을 두는 방법. 정확하고, 결정적이고, 비용이 0이다. 대신 커버리지가 넣은 만큼이고, 전국 식당 메뉴명은 꼬리가 길어서 손으로 못 따라간다. 새 테이블과 마이그레이션까지 만들 일은 아니다 — 지금 실패 로그(`src/main/java/com/planb/domain/travel/service/PlanService.java:1239`)가 쌓이는 양을 보고, 다른 수단이 반복해서 틀리는 몇 개만 상수 맵으로 박아 두는 **예외 처리** 자리가 맞다. 1순위가 아니다.

### 권고

**AI에게 표준 품목명을 같이 받는다. 새 의존성은 넣지 않는다.**

이유는 실패의 성격이다. 이 여덟 개는 철자가 틀린 것이 아니라 **뜻이 다른 것**이다. `"초당두부밥상"`이 순두부인 것은 문자열에 들어 있지 않은 정보다. 어휘 도구는 쉬운 절반을 고치고 의존성과 25MB 사전을 남기는데, 그 절반은 의존성 없이도 `split`과 `lastIndexOf`로 상당 부분 얻을 수 있다. 어려운 절반에는 어차피 닿지 못한다. 반면 AI는 이미 호출 경로 안에 있고, 답을 이미 알고 있고, 값이 거의 0이다.

다만 AI 답을 신뢰하지는 않는다. 순서를 이렇게 둔다.

1. 원래 메뉴명으로 조회한다 (지금 그대로).
2. 비면, AI가 준 표준 품목명으로 한 번 더 조회한다.
3. 그래도 비면 지금처럼 `UNAVAILABLE`로 둔다.

어휘 전처리를 2번 앞에 끼우는 것은 선택이다. 구분자 분리 정도는 열 줄이니 넣어도 되지만, AI가 이미 `"영양솥밥+생선구이"`를 `"솥밥"`으로 정리해 줄 것이므로 중복이 된다. **먼저 2번만 넣고 실패 로그를 다시 재는 쪽을 권한다.** 남는 실패가 특정 패턴에 몰려 있으면 그때 어휘 규칙이나 상수 맵을 붙인다.

## 어디에 끼우는가

두 자리가 있고, 둘 다 `NutritionService.evaluateFoodNutrition`이 중심이다(`src/main/java/com/planb/domain/travel/service/NutritionService.java:44`).

**재시도 자리 (반드시 여기).** 첫 조회가 빈 목록을 돌려주는 분기가 `src/main/java/com/planb/domain/travel/service/NutritionService.java:55`다. 지금은 곧장 `unavailable`로 간다. 두 번째 검색어로 다시 부르는 곳은 여기다. `flatMap`으로 바뀌므로 `:53`의 `map`이 흔들리고, 타임아웃 `LOOKUP_TIMEOUT`(`:37`)이 두 번의 호출을 합쳐 덮는지 각각 덮는지 정해야 한다 — 지금 배치대로면 합쳐서 15초다.

**보조 이름이 들어오는 자리.** `TourismTool.evaluateFoodNutrition`의 시그니처(`src/main/java/com/planb/ai/mcp/TourismTool.java:240`)에 인자를 하나 더한다. `PlanTourismTool`도 같이 바꾼다(`src/main/java/com/planb/ai/mcp/PlanTourismTool.java:212`). 프롬프트 쪽 지시는 `src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:271`과 `src/main/java/com/planb/ai/prompt/EditPlanPrompt.java:132`가 Tool 호출 순서를 규정하는 자리다.

**건드리면 안 되는 것 — 수집 키.** `nutritionEvaluationCollector.record(foodName, result)`(`src/main/java/com/planb/ai/mcp/TourismTool.java:258`)의 첫 인자는 **원래 메뉴명이어야 한다.** `PlanService`가 이 맵을 `restaurantDetail.menuName()`으로 되찾기 때문이다 — 수치 채우기(`src/main/java/com/planb/domain/travel/service/PlanService.java:1228`)와 영양 참고 태그(`src/main/java/com/planb/domain/travel/service/PlanService.java:1357`) 두 군데다. 여기에 표준 품목명을 넣으면 조회는 성공하는데 화면 수치는 그대로 빈칸이 되고, 증상이 똑같아서 찾기 어렵다. 보조 이름은 `NutritionService` 안에서만 살고 밖으로 나오지 않는다.

Java가 직접 채우는 식사 슬롯 경로(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:359`)에는 AI가 준 보조 이름이 없다. 그 슬롯의 메뉴는 지금도 영양 조회를 거치지 않으므로 이번 범위 밖이다.

### 이 자리를 지금 덮고 있는 테스트

- `src/test/java/com/planb/unit/domain/travel/service/NutritionServiceTest.java` — `FoodNtrCpntHandler.getFoodNutrition`을 모킹한다. `"영양정보 조회 결과 없음"`(`:227`)이 재시도가 바꿀 바로 그 분기이고, 재시도를 넣으면 스텁을 두 번 지정해야 해서 깨진다. `"음식 이름 불일치 시 첫 번째 영양정보 평가"`(`:135`)가 `.orElse(items.getFirst())` 선택 규칙을 고정한다. `"한 끼 분량으로 환산해 평가하고 응답 수치는 실측 그대로 반환"`(`:342`), `"영양정보 조회가 실패해도 예외 없이 조회 불가로 반환"`(`:420`).
- `src/test/java/com/planb/unit/global/client/foodNtrCpnt/FoodNtrCpntHelperTest.java` — 필터만 본다. `"정규화된 식품명 정확 일치"`(`:45`), `"부분 일치 식품명 검색"`(`:86`)이 앞서 말한 포함 방향을 고정한다. **검색어가 어떻게 만들어지는지를 보는 테스트는 없다.**
- `src/test/java/com/planb/unit/global/ai/tool/TourismToolTest.java:267` — 위임과 수집 기록을 고정한다. Tool 시그니처를 바꾸면 여기가 깨진다. `PlanTourismToolTest`가 같은 짝이다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java` — `"영양정보를 찾지 못한 메뉴의 AI 수치는 비운다"`(`:1410`), `"조회한 영양성분으로 AI 수치를 덮어쓴다"`(`:1473`), `"수집된 영양평가 결과의 CHECK/HIGH 성분만 참고 태그 부여"`(`:1336`). 수집 키를 잘못 바꾸면 여기가 잡아 준다.
- `src/test/java/com/planb/integration/external/foodNtrCpnt/FoodNtrCpntHandlerTest.java` — `@Tag("external")`(`:23`)이라 기본 `test`에서 제외되고(`build.gradle:142`) `externalTest`로만 돈다(`build.gradle:146`). 실제 API를 `"막국수"`로 부른다(`:36`, `:74`).

## 확인하지 못한 것

- **식약처 API가 `FOOD_NM_KR`을 완전 일치로 보는지 부분 일치로 보는지 확인하지 못했다.** 서비스 키가 환경변수로만 들어오고(`src/main/resources/application-common-prod.yml:44`) 이번 조사에서 실제 호출을 하지 않았다. 코드에서 읽히는 정황은 두 가지다. 헬퍼가 클라이언트 쪽 `contains` 필터를 두고 있다는 것(`src/main/java/com/planb/global/client/foodNtrCpnt/helper/FoodNtrCpntHelper.java:51`)은 응답이 상위집합이라는 전제이고, 지금 2/3이 성공한다는 보고도 서버가 어느 정도 부분 일치를 한다는 뜻이다. 어느 쪽이든 결론은 같다 — 검색어가 표준 품목명보다 길면 안 잡힌다.
  이것을 재는 자리는 이미 있다. `FoodNtrCpntHandlerTest`(`src/test/java/com/planb/integration/external/foodNtrCpnt/FoodNtrCpntHandlerTest.java:30`)에 `"장칼국수"`와 `"검은콩 장칼국수"` 두 줄을 넣고 `./gradlew externalTest`를 돌리면 한 번에 확정된다.
- **실패율 "약 1/3"은 운영 로그의 수치이고 저장소에서 확인할 수 없었다.** 그 수치가 나오는 로그는 `src/main/java/com/planb/domain/travel/service/PlanService.java:1239`다.
- 여덟 개 메뉴명 각각에 대응하는 표준 품목이 식약처 품목대표 DB에 실제로 존재하는지 확인하지 못했다. 표의 "표준 품목 후보"는 어휘 분석에 따른 추정이다.
- 이번 조사에서는 코드를 읽기만 했고 테스트를 실행하지 않았다.
