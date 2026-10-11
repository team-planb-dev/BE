# P2 선택 계약 — TourAPI 메뉴 문자열 조사

확인일: 2026-10-11. 조사 범위: 공개 공식 명세, Java 21·Jackson 3 공식 API 문서, 기존 선택 계약 조사와 저장된 재현 증거, DTO·수정 전 파서. 이번 문서 보완에서 코드 수정·실제 TourAPI/LLM 호출·인증정보 조회 없음.

**결론:** 확인한 공식 명세는 `firstmenu`·`treatmenu`를 설명 문자열로 정의한다. CSV 구분자나 개별 메뉴 배열 계약은 없으며, 한 문자열이 반드시 주문 가능한 단일 메뉴라는 보장도 없다. 문자열 분해는 애플리케이션의 해석 정책으로 명시해야 한다.

## 공식 계약

[공공데이터포털의 한국관광공사 국문 관광정보 서비스](https://www.data.go.kr/data/15101578/openapi.do) HTML에 포함된 `const swaggerJson`을 직접 파싱했다. Swagger 2.0, `info.version=1.0.0`, host `apis.data.go.kr/B551011/KorService2`, `/detailIntro2` 성공 응답의 `body.items.item.properties`에서 확인한 정의:

| 필드 | 타입 | 공식 설명 |
| --- | --- | --- |
| `firstmenu` | `string` | 음식점(39) : 대표메뉴 |
| `treatmenu` | `string` | 음식점(39) : 취급메뉴 |

두 필드에는 `array`·메뉴별 `items`·`pattern`·`enum`·CSV 형식·구분자·괄호/가운데점 처리 규정이 없다. 쉼표, `/`, `;`, `|`, 줄바꿈, `<br>`, `·`가 항상 메뉴 사이를 뜻한다는 근거도 이 명세에 없다. 공식 계약에서 보장하는 범위는 문자열 필드와 의미 설명까지다. [공식 Swagger가 내장된 포털](https://www.data.go.kr/data/15101578/openapi.do)

## 수정 전 코드와 합성 입력

`Kor2RestaurantIntroResponse.Item`은 두 값을 `String`으로 보존한다. `PlaceCandidateContext.recordRestaurantDetail`은 `[,/;|\n]|(?i)<br\s*/?>` 정규식으로 분리한 뒤 정확 일치를 검사한다. 기존 조사에서 요구한 “개별 메뉴”와 구분자 정규화는 공급자 명세가 아닌 앱의 선택 계약이다.

- 합성 입력 `교동쌈밥 한정식 (A, B, C)`는 `교동쌈밥 한정식 (A` / `B` / `C)`로 분리된다. 구성 설명이 독립 메뉴로 바뀌고 전체 표기는 사라지는 파서 위험이다.
- 합성 입력 `돼지 불고기 쌈밥 · 오리 불고기 쌈밥`은 현재 하나의 문자열로 남는다. `·`가 단품 목록인지 하나의 복합 메뉴 이름인지는 문자열 타입 정의만으로 판별할 수 없다.
- 최초 문서의 분리 결과는 당시 정규식과 동등한 합성 확인. 이후 저장된 [경주 메뉴 재현 기록](evidence/p2-selection-contract-20261011/gyeongju-menu-reproduction.json)에는 같은 문자열의 현재 상세 원문과 실제 Java 검증기의 3회 RED 재현 기록이 추가된 상태. 현재 원문의 파서 결함과 과거 거부 응답의 최종 선택값은 별도 증거.

**설계상 판단:** 괄호 안 설명을 보존하고 최상위 구분자만 분리하면 첫 번째 합성 위험을 줄일 수 있다. 다만 이는 앱 정책이며, 원문 전체를 허용 목록에 추가하는 것만으로 “단일 메뉴 선택”을 증명할 수 없다. 부분 문자열·유사도 승인 역시 음식점의 실제 단품 근거가 아니다. 가운데점·복합 표현의 정책은 실제 성공 상세 원문과 선택값을 함께 보존해 확인한 뒤 정해야 한다.

## Java 공식 동작과 최소 분해 정책

Java 21의 `String.split(regex)`는 정규식이 일치하는 위치에서 분리한다. 괄호 안에 있다는 이유로 일치를 생략하는 계약은 없으므로, 현재 구분자 정규식의 모든 쉼표는 분리 대상이다. 괄호 깊이를 별도로 관리하는 것은 앱 구현의 책임. [Java 21 String.split](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#split(java.lang.String))

`Matcher.find()`는 다음 일치 부분으로 이동하며 `start()`·`end()`로 해당 구간을 읽을 수 있다. 기존 구분자 패턴을 그대로 유지하고, 각 구분자까지의 괄호 깊이를 확인한 뒤 깊이가 0일 때만 자르는 구현도 가능한 범위. 이 문서는 특정 구현을 의무화하지 않는다. [Java 21 Matcher.find](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Matcher.html#find())

권고하는 앱 정책:

- 이번 분리 문법은 ASCII `(`·`)`와 기존 쉼표·슬래시·세미콜론·파이프·CR/LF·대소문자 구분 없는 `<br>`, `<br/>`, `<br />`에 한정. 새로운 구분자·HTML 디코딩·전각 괄호·따옴표 문법은 범위 밖.
- 균형 잡힌 중첩 괄호는 깊이 카운터로 보존. `정식 (A, 옵션 (B/C)); 국수`의 허용 목록은 `정식 (A, 옵션 (B/C))`, `국수`의 2개. 괄호 안 구분자와 `<br>`는 원문 그대로 보존.
- 비재귀 스캔은 입력 길이에 비례하는 처리이며 깊이도 입력 길이를 넘을 수 없는 범위. 근거 없는 최대 깊이나 별도 설정 추가는 불필요.
- 닫는 괄호가 먼저 나오거나 마지막 깊이가 0이 아니면 해당 `firstmenu` 또는 `treatmenu` 필드 전체에서 허용 항목을 만들지 않는 보수적 정책 권고. 정상인 다른 필드는 계속 사용. `정식 (A, B; 국수`를 통째로 하나의 메뉴로 승인하거나 `B`·`국수`를 조각으로 승인하는 fallback은 제외.
- `strip()`·빈 값 제외·정확 중복 제거·입력 순서·최종 정확 일치 검사 유지. 상세 ID·유형·성공 코드 검증과 메뉴 중복 사용 방지도 기존 계약 유지.

불균형 필드 제외는 정상 메뉴가 함께 있어도 후보에서 빠질 수 있는 의도적인 보수성. 이것은 Java나 TourAPI가 요구하는 동작이 아니라 모호한 입력에서 독립 메뉴를 추정하지 않기 위한 앱 정책이며, 회귀 테스트에서 명시할 결정.

## correction의 JSON 배열 경계

Java 컬렉션의 기본 문자열 표현은 원소를 대괄호 안에 쉼표·공백으로 나열한다. 메뉴 문자열 자체의 쉼표를 인용하는 계약이 없어 `List.of("정식 (A, B)")`와 `List.of("정식 (A", "B)")`가 같은 모양으로 보일 수 있는 구조. [Java 21 AbstractCollection.toString](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/AbstractCollection.html#toString())

correction에는 검증기와 보충 선택이 사용하는 동일한 `List<String>`을 JSON 배열로 직렬화해서 전달하는 정책 권고. 예: `허용 메뉴(JSON 배열): ["정식 (A, B)","국수"]`. 거부된 선택값도 JSON 문자열로 표시하면 쉼표·따옴표·역슬래시·줄바꿈이 항목 경계와 구분되는 형태. 수동 따옴표 연결이나 쉼표 join은 제외.

Jackson 3의 `ObjectMapper.writeValueAsString(Object)`는 Java 값을 문자열로 직렬화하는 API이며, 코드베이스에 이미 사용하는 `tools.jackson.databind.json.JsonMapper` 계열로 목록을 직렬화할 수 있다. 새 의존성이나 Jackson 2 패키지 도입 불필요. [Jackson 3.0.4 ObjectMapper 공식 API](https://javadoc.io/static/tools.jackson.core/jackson-databind/3.0.4/tools.jackson.databind/tools/jackson/databind/ObjectMapper.html#writeValueAsString(java.lang.Object))

대안으로 `ArrayNode`의 `toString()`도 유효한 JSON을 반환하는 공식 계약. `JsonNode.asString()`은 배열·객체에서 예외를 발생시키는 값 변환 API이고 JSON 직렬화가 아니므로 배열 전달에 사용하지 않는 선택. [Jackson 3.0.4 JsonNode 공식 API](https://javadoc.io/static/tools.jackson.core/jackson-databind/3.0.4/tools.jackson.databind/tools/jackson/databind/JsonNode.html#toString())

JSON 표현은 이미 만든 허용 목록의 경계를 명확히 할 뿐, 목록의 메뉴 단위가 TourAPI에서 보장된다는 뜻은 아니다. 공유 파서 수정과 함께 적용할 범위이며 LLM 재시도 회복률 개선은 실제 호출로 별도 검증할 사항.

## 데이터 불확실성

[한국관광공사의 교동쌈밥 소개](https://korean.visitkorea.or.kr/detail/ms_detail.do?cotid=9ff22b76-610d-4c0b-a56d-c3b2c8b00a5c)는 여러 종류의 쌈밥을 대표 메뉴로 설명한다. 이것은 대표 메뉴 설명에 여러 메뉴가 나타날 수 있음을 보여 주지만, 해당 실패 요청의 `contentid`·`firstmenu`·`treatmenu` 원문이나 선택값을 입증하지 않는다. 후속 조사에서 보존된 현재 원문의 근거는 이 소개 페이지가 아닌 [저장된 상세 응답](evidence/p2-selection-contract-20261011/current-gyeongju-detail.json)과 [재현 기록](evidence/p2-selection-contract-20261011/gyeongju-menu-reproduction.json).

거부된 최종 구조화 응답의 선택 메뉴는 후속 기록에도 미보존 상태. 현재 원문에 대한 분해 결함과 과거 성공 응답의 잘린 메뉴는 기록된 증거이며, 과거 거부 2건이 모두 동일 원문의 오거부였다는 확정은 불가능. 공개 내장 Swagger 외 별도 ZIP 활용매뉴얼의 모든 문구·운영 데이터 변형·실제 단품 주문 가능성은 미확인. Java·Jackson 문서는 API 동작의 근거이고, 앱 분해 정책이나 실제 LLM 회복률을 보장하는 근거가 아니다.

프로젝트 runtime classpath에서 `tools.jackson` core·databind 3.0.4 확인. 해당 버전 공식 API로 출처 일치 확인. 새 의존성 추가 없음.
