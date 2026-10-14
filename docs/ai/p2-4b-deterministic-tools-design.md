# P2-4B 생성 Tool 축소 설계

기준: dev `2a99375`, P2-4A 결과. Issue #172. 상태: 설계 7→8/10, 구현 6→7→7→8/10 독립 검토 통과. 실제 API 생성 12회 완료, 9/12 통과·지연 gate 미달로 미채택.

## 책임과 실행 순서

요청 snapshot → 생성 전용 검색 Tool → Java 음식점 상세·메뉴 준비 → AI 후보·메뉴·표준명 선택 → structured selection 검증 → candidate 원본 매핑 → 기존 Java 식사 보정·경로·영양·복약·태그 → 최종 검증 → 원자적 저장.

- 생성: `GenerationTourismTool`의 검색 3개만 등록. 기존 `PlanTourismTool`을 composition으로 재사용하고 상속하지 않음.
- 편집·날짜 재구성·재선택: 기존 `PlanTourismTool`과 프롬프트 유지.
- 음식점 준비: 요청별 `GenerationRestaurantCandidates`가 상세 캐시, 제한, 메뉴·영업시간 판단을 소유. `PlaceCandidateContext`에 생성 요청의 준비 상태와 선택 표준명 보관. Java 보충도 같은 준비 상태 재사용.
- 기존 공통 응답·DB 스키마·트랜잭션 경계 유지. Facade 변경 없음.
- `OpenAiClient`는 생성 adapter의 시작 초기화도 수행. correction은 기존 후보를 유지하고 같은 adapter 사용.

## 충돌과 처리

| 현재 계약 | 변경 |
|---|---|
| 생성·편집 Tool 동일 | 생성 전용 adapter 도입, 편집 유지 |
| 표준명이 mapper에서 유실 | 요청 context의 candidateId·메뉴 쌍으로 보존, 최종 살아남은 식사에서 표준명 조회 |
| `VerifiedPlacePrompt`에 getRoute 호출 지시 | 생성에서는 사용하지 않고 생성 프롬프트에 동일 identity 검증 계약 명시 |
| DTO에 영업시간 없음 | TourAPI `opentimefood` nullable 필드 추가, 레거시 4인자 fixture 생성자 호환 |
| 보충에서 후보 전체 사용 | 생성에서 준비된 메뉴·식사 가능 유형으로 보충, 지역 fallback도 동일 준비 |
| 상세조회 실패와 결과없음 혼동 가능 | 실제 API 예외 전파, 빈 상세·메뉴없음은 후보 제외 |
| HIGH 대체 Tool 결과 소실 | Java 평가 유지, HIGH의 highCount·evaluableCount와 NOT_EVALUABLE·UNAVAILABLE 개수 구분. 평가 가능 분모가 0이면 비율 미측정(N/A). 감시 비교 불가를 통과로 바꾸지 않고 사용자가 범위 한계를 확인한 뒤 채택 여부 결정. 주 품질·지연 gate는 그대로 적용. P2-4A HIGH·영업시간 baseline 미측정이므로 비교 가능 여부를 별도 기록 |

## Grilling 결정 트리 — 권장안 자동 채택

Round 1: 생성과 편집을 분리; 공개 응답·DB 변경 없음; 새로운 병렬화 없음.
Round 2: 검색당 상세 후보 4개, 요청당 서로 다른 키워드 8개, 지역 보충 1회(후보 8개), 요청당 고유 상세 최대 40건. 동일 검색·상세 요청 메모이즈. 검색 결과 원본 순서 유지. 후보 상한은 앱 정책이고 공급자 허용량이 아님.
Round 3: `firstmenu` 우선, 없으면 `treatmenu`; 구분된 메뉴 중 알레르기·기피 키워드 제외. 제외 후 메뉴가 없으면 후보 제외. 알레르기 필터 해제 금지. 선택 메뉴를 반환한 후보의 메뉴 목록과 대조.
Round 4: 영업시간은 전체 문자열이 단일 `HH:mm~HH:mm` 또는 `HH:mm-HH:mm` 형식일 때만 사용. 자정 넘김 허용, 복수 구간·휴일·브레이크타임·빈 값·자유 텍스트는 판단 불가로 유지. 설정 식사 시각이 없는 유형은 배제하지 않음. 해당 식사 후보가 0이면 그 유형의 시간 필터만 해제.
Round 5: 표준명은 확인한 메뉴의 선택 부가값이며 원본 메뉴를 대체하지 않음. 메뉴 조회 실패 때만 기존 NutritionService 표준명 fallback. 최종 선택을 다시 매핑할 때 표준명 상태 갱신하여 correction의 실패 선택 유실.
Round 6: 테스트 seam은 등록된 Tool 집합, 음식점 검색 결과·보충 결과, 사용자 생성 결과, 실제 영양 조회 입력, edit/rebuild 결과. RED→GREEN 한 경로씩 진행.
Round 7: 생성 검색 지역은 요청의 locationDo·locationSigungu에 고정, 불일치 인자는 조회하지 않고 빈 후보 반환. 고유 검색 key는 trim한 keyword와 고정 지역으로 구성. 준비 조회 한도는 dispatch 전에 계수하고 실패도 소비. 생성 재선택은 기존 Tool을 유지하되 수락 전 기존 요청의 준비 상태로 메뉴 출처·금기 검증, 최종 영양 단계에 후보 context 유지. 40건은 주 생성·Java 준비 조회 상한이며 레거시 재선택 Tool 자체 조회량은 별도 기록.
Round 8: 시간 사전 필터는 설정 식사 시각 기준의 선택 보조. 정규화 후 실제 식사 시각과의 불일치는 definite-mismatch·unknown·relaxed로 관측하며 실시간 영업 가능 보증으로 주장하지 않음. 하드 저장 거부 정책은 새로 만들지 않음.
Round 9: 최종 식사 후보·메뉴 쌍에 표준명 연결. 비식사·알 수 없는 후보 제외, 같은 쌍의 중복 선택은 첫 값 유지, correction 재매핑 시 선택 상태 초기화. 생성 재선택 Tool이 수집한 영양평가는 최종 판단 입력에서 제외하고 Java가 확정 메뉴·건강 snapshot으로 평가. 편집 수집 결과는 유지.
프론티어: 설계 선택 없음. 실제 계정 쿼터·유료 호출 승인·실측 gate는 구현 후 실행 전 확인 대상.

## 조회량과 실패

TourAPI 공개 개발 신청량 1,000. 실제 계정 승인량·잔여량·숫자 QPS 미확인.
12회 cold 생성의 상세 상한은 480건. 키워드 검색 최대 96건, 지역 보충 최대 12건은 별도. 관광지·지역코드·재선택·SDK 재시도·품질 확인 상세 호출은 추가이므로 이 상한만으로 총 쿼터 안전을 보증하지 않음.
처음 K개가 무효면 결과가 줄 수 있음. 자동으로 무제한 다음 후보를 조회하지 않음. 한도와 메뉴없음은 후보 부족이며 API 예외와 구분. 품질 gate 미달 시 상한을 조정하거나 기각.

## 검증·채택

- 결정적 테스트: 생성 검색 3개 / 편집 기존 6개, 상세·키워드 한도·중복·빈 값·오류, 메뉴 출처·알레르기, 영업시간 불명·자정·fallback, 표준명 보존·correction·교차 요청 격리, 보충·영양 15초 timeout·편집 회귀.
- review-loop 최소 2회, 8/10 이상. Critical·Important 미해결 시 통과 처리 금지.
- 실제 생성 12회: 새 사용자 승인 후 실행. 주 품질 R0, 지연·token P2-4A 대비. 성공 중앙값 19.986초의 70% 이하(13.990초), token 중앙값 47,916.5의 110% 이하. 서로 다른 날 측정의 한계 표기.
- stub k6/Grafana는 같은 모델 응답 fixture·영양 지연·검색 seed·캐시 조건에서 paired 측정. 실제 AI 성능의 대용으로 주장하지 않음. 기존 stub은 관광지 검색 Tool 1회와 빈 일정 선택의 2회 응답을 반환하므로 제거한 Tool을 호출하지 않음. fixture 변경 없이 유지. 이 스텁에는 결정적 Tool의 LLM 왕복이 없으므로 모델 왕복 감소 효과 증명 불가.
- 결과 gate 미달 또는 감시 비교 불가 시 사용자에게 보고; 완료·운영 채택으로 기록하지 않음.
- 문서·데이터 보관 디렉터리 사본 동기화. 계획서 스테이징 금지.

## 공식 근거

- [KorService2 공식 Swagger·신청량](https://www.data.go.kr/data/15101578/openapi.do): contentTypeId39, firstmenu·treatmenu·opentimefood. 영업시간은 string이며 형식 보증 없음.
- [식약처 공개 명세](https://www.data.go.kr/data/15127578/openapi.do): 숫자 동시성 미공개, 현재03. 기존02 유지.
- [Spring AI 2.0.0 ToolCallbacks](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-model/src/main/java/org/springframework/ai/support/ToolCallbacks.java): 생성 adapter의 @Tool 등록.
- [Spring AI 2.0.0 DefaultChatClient](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/DefaultChatClient.java): tools는 기본 목록에 추가. 현재 ChatClientConfig 기본 Tool 미등록.
- [Spring WebClient 동기 사용](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-synchronous.html): 기존 MVC blocking 경계 유지.

## 독립 재검증 기록

| 검토 | 점수 | 주요 수정 |
|---|---|---|
| 설계 1·2 | 7→8 | 지역 고정·실패도 한도 소비·시간 판단 범위 |
| 구현 1 | 6 | LOCAL_FOOD 검증·지역 후보 추가 후 자격 재계산 |
| 구현 2 | 7 | 생성 재선택 영업시간의 API 원본 확정 |
| 구현 3 | 7 | 폐기 슬롯의 표준명 덮어쓰기 방지 |
| 구현 4 | 8 | 생성 재선택 Tool 평가 우회 방지, 주요 결함 없음 |

표준명·영업시간·collector 우회 결함은 공개 생성 결과 테스트의 실패를 확인한 뒤 수정. 유료 API 지연·token 및 품질 개선은 독립 코드 검토 점수와 별도 판정.

## 실제 검증 후 상태

2026-10-10 실제 생성 12회: 9/12 통과, 성공 중앙값 27.194초, token 중앙값 22,342, 모델 호출 총 111회. 상세는 `p2-4b-deterministic-tools-result.md`와 실제 집계 JSON 참조. 독립 코드 리뷰 8/10과 별개로 채택 기준 미달. 현재 변경의 병합 보류.
