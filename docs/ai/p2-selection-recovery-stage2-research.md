# 2단계 공원 후보 계약: 공식 근거 조사

조사일: 2026-10-11. 읽기 기준: `/private/tmp/planb-selection-contract-177`의 생산 코드와 `/Users/wooju-kang/Desktop/planB/docs/ai/p2-selection-quality-followup-research.md`. 생산 코드 변경·테스트 실행·인증 API 호출 없이 공식 Kakao 문서와 Spring AI `v2.0.0` 태그의 원본 소스 대조.

**결론:** `PARK_WALK`만 현재 최종 검증의 `AT4`와 원본 `categoryName`의 공원 근거로 후보를 거르는 변경이 최소 범위. 같은 검색 결과의 첫 항목이 호수이고 둘째가 공원인 경우도 처리하려면 대표 후보 선택·경로 조회 전에 공원 조건을 적용할 필요. 적격 후보가 없으면 기존 `found=false` 결과로 반환하고 후보 컨텍스트에 등록하지 않는 방식. `ATTRACTION`·`MUST_HAVE`의 일반 관광지 계약, 외부 검색 횟수, 기존 슬롯당 두 번 재선택은 유지 대상으로 권고.

## 1. Kakao가 보장하는 필드와 공원 판정의 경계

Kakao Local의 `CategoryGroupCode`에서 `AT4`는 관광명소, `CE7`은 카페, `FD6`은 음식점. `category_group_code`는 주요 카테고리를 묶은 그룹 코드이며 `category_name`은 별도 문자열 필드. 따라서 `AT4`만으로 공원 판정을 할 공식 근거는 없음. [Kakao Local: 키워드 검색의 카테고리 그룹 및 응답 필드](https://developers.kakao.com/docs/ko/local/dev-guide#search-by-keyword)

공식 예시의 `category_name`은 `>`로 이어진 여러 단계의 경로이며 같은 결과의 그룹 코드가 빈 문자열인 예시도 존재. 마지막 조각이나 장소 이름으로 대체하지 않고 원본 전체 경로를 유지하는 근거. 다만 문서는 공원·호수에 대한 전체 하위 분류 목록, 영구적으로 고정된 온톨로지, 모든 실세계 공원의 공원 분류 존재를 보장하지 않음. [Kakao Local: 응답 예시](https://developers.kakao.com/docs/ko/local/dev-guide#search-by-keyword-response)

**로컬 정책 해석:** `여행 > 관광,명소 > 공원 > 도시근린공원`은 공원 근거를 포함하고 `여행 > 관광,명소 > 호수`는 포함하지 않는 테스트 예시로 사용할 수 있음. 이 두 문자열은 이번 공식 문서에서 확인한 특정 장소의 실제 응답이 아니라 계약 fixture. `호수공원`이라는 장소명만으로 호수 분류 후보를 공원으로 승인하는 근거도 없음. 현재 `contains("공원")` 정책을 공유하는 것은 기존 동작 정렬이며, 정확한 분류 세그먼트 비교로 바꾸는 것은 별도 의미 변경.

## 2. 현재 코드의 불일치와 보존 범위

- `PlanTourismTool.java:211`은 `ATTRACTION`·`MUST_HAVE`·`PARK_WALK`에 모두 `AT4` 검색 범위를 선택. `:230`은 검색 결과를 받은 뒤 `:241`에서 그대로 후보에 등록하고 반환. 이 구간에 공원 자격 검사는 없음.
- `KakaoMapServiceHandler.java:250`은 검색 응답을 그룹 코드로 필터링하며 대표 후보 선택·경로 조회 후 결과 생성. `KakaoPlaceSearchHelper.java:114`의 `toResult`는 원본 `category_group_code`와 전체 `category_name`을 그대로 전달.
- `PlanPlaceResolver.java:404`는 `PARK_WALK`에 관광지 유형과 `categoryName != null && categoryName.contains("공원")`을 함께 요구. 도구에서 성공으로 공개된 호수 등 `AT4` 결과가 최종 슬롯에서 거절될 수 있는 구조.
- `PlanPlaceResolver.java:401`의 `ATTRACTION`·`MUST_HAVE`는 일반 관광지 허용 정책이며 공원 전용 제한이 아님. `AT4`인 일반 관광지는 상세 공원 분류가 없어도 이 유형에서 허용. 신규 null/빈 분류 제한은 공원 슬롯에만 적용할 필요.
- `PlaceCandidateContext.java:56`은 TourAPI 후보의 `contentTypeId`와 `lclsSystm2`를 보존하지만 `categoryName`에는 null 사용. 따라서 현 계약에서 TourAPI 후보가 `12` 관광지라는 사실만으로 공원 후보가 되지 않음. 제목의 공원 단어, `lclsSystm2` 코드, Kakao의 상세 경로를 서로 추정·전환하는 변경 근거는 이번 범위에서 확인하지 못함.

위 위치는 로컬 코드 근거. 검색 결과 없음·`found=false`·null 응답의 기존 안전 처리도 유지 대상. 새 검사는 결과의 공원 자격만 다루고 주소·좌표·중복·메뉴 등 최종 검증은 기존 resolver의 책임.

## 3. Spring AI 2.0.0에서 반환값 필터링의 의미

프로젝트 `build.gradle:20`의 버전은 2.0.0. `@Tool.returnDirect` 기본값은 false이며 결과는 기본 변환기를 거쳐 모델로 전달. `MethodToolCallback`은 Java 메서드를 호출한 결과를 `ToolCallResultConverter`로 변환하고 기본 변환기는 일반 결과를 JSON으로 직렬화. 그러므로 `PlanTourismTool`이 반환 전에 `found=false`로 바꾼 결과가 모델이 받는 도구 응답 계약이며, 컨텍스트 등록도 반환 전 검사와 같이 처리해야 노출·출처 허용의 차이가 사라짐. [2.0.0 Tool](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-model/src/main/java/org/springframework/ai/tool/annotation/Tool.java) · [2.0.0 MethodToolCallback](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-model/src/main/java/org/springframework/ai/tool/method/MethodToolCallback.java) · [2.0.0 기본 결과 변환기](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-model/src/main/java/org/springframework/ai/tool/execution/DefaultToolCallResultConverter.java)

`DefaultToolCallingManager`는 callback 결과를 `ToolResponseMessage`에 넣고 대화 이력에 추가. `ToolCallingAdvisor`의 동기 루프는 모델에 tool call이 있을 동안 계속하고 `returnDirect`이면 종료. 이는 공원 의미 검증이나 동일 검색의 유효 후보 발견을 보장하지 않음. 모델이 다른 이름을 검색할 수 있다는 가능성과 Java가 자동으로 추가 검색하는 정책을 구별할 필요. [2.0.0 DefaultToolCallingManager](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-model/src/main/java/org/springframework/ai/model/tool/DefaultToolCallingManager.java) · [2.0.0 ToolCallingAdvisor](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/ToolCallingAdvisor.java)

같은 Advisor의 streaming 필터는 tool call을 포함한 중간 모델 응답의 클라이언트 노출을 제어. 이는 `categoryName`에 따라 장소를 제외하는 의미 필터가 아님. 현재 `OpenAiClient.java:372`는 `.call().content()` 경로이므로 이번 변경의 근거로 streaming 동작을 가져올 필요도 없음. [2.0.0 ToolCallingAdvisor: streamWithToolCallResponses](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/ToolCallingAdvisor.java#L245)

## 4. 가장 좁은 변경과 제한 복구

1. 기존 공원 판정식을 작은 공통 메서드로 공유하거나 이에 준하는 같은 계약으로 사용. `PlanTourismTool.findPlaceWithRoute`의 `PARK_WALK` 검색 자격을 하위 후보 필터에 전달하고 대표 후보 선택 전에 적용. 최종 `PlanPlaceResolver`도 같은 자격으로 승인. 새 전역 분류 프레임워크나 전체 courseType 매핑 추출은 근거 없음.
2. `PARK_WALK`에서 null/빈 상세 분류 또는 공원 근거 없는 `AT4` 항목을 제외하고, 적격 항목이 없으면 기존 `found=false` DTO로 반환하고 등록하지 않음. 정상 공원은 candidateId·원본 categoryCode·전체 categoryName·좌표·선택된 공원에 대한 경로 결과를 보존. 일반 `ATTRACTION`·`MUST_HAVE` 및 카페·식당은 현재 계약 유지.
3. 이미 받은 검색 목록을 공원 자격으로 거르는 것은 추가 검색이 아님. 첫 호수를 먼저 단일 DTO로 만든 뒤 거절하면 같은 응답의 둘째 공원을 잃으므로 결과 DTO 검사만으로는 이 요구를 충족하지 못함. 동일 키워드 재검색, 광역 검색, 검색어 자동 생성은 추가하지 않음. 추가 검색 전략의 효용·비용 근거는 별도 측정 대상.
4. `PlanService.java:1484`의 실패 슬롯당 재선택 최대 두 번과 시도별 새 `PlaceCandidateContext`, 정상 슬롯 보존 및 소진 후 기존 검증 fallback을 유지. 도구 내 재검색을 추가하면 이 기존 복구 흐름과 별도의 호출 예산이 생김.
5. 회귀 fixture는 공원 전체 경로의 정상 공개·등록·최종 승인, 첫 호수·둘째 공원 응답에서 공원 선택과 공원 좌표 기준 경로, 호수만 있는 응답의 미공개·미등록·최종 거절, null/빈 상세 분류의 공원 거절, 같은 null/빈 분류의 일반 `AT4` 관광지 유지, `found=false`와 null 응답의 안전 처리, 제목에 공원이 있어도 TourAPI의 null 상세 분류를 공원으로 추정하지 않는 계약을 우선 확인.

재선택 두 번은 실제 HTTP 호출 전체 상한이 아님. Spring AI 2.0.0 tool loop에는 이 소스 기준 도구 횟수 상한이 없고, OpenAI 옵션의 전송 재시도 기본값은 3이며 프로젝트 correction 경로도 별도. live 2.0.1 문서의 새로운 tool limit 기능을 설치된 2.0.0의 기본값으로 인용하지 않을 필요. 이번 공원 계약 변경을 위해 새 Advisor·retry 계층을 도입할 근거 없음. [2.0.0 ToolCallingAdvisor](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/ToolCallingAdvisor.java) · [2.0.0 AbstractOpenAiOptions](https://github.com/spring-projects/spring-ai/blob/v2.0.0/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/AbstractOpenAiOptions.java#L37)

미실시: 실제 Kakao·TourAPI 호출, 생산 코드 변경, 테스트 실행, 실제 운영 retry 설정 확인. 위험: 공원 경로 문자열 정책은 제공자의 완전한 공원 온톨로지 보장이 아니며 거절 정책 정렬만으로 모델의 공원 검색 성공률 향상을 단정할 수 없음.
