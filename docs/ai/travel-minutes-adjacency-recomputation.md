# 식사 슬롯 보정 후 이동시간 재계산 계약

조사일: 2026-09-20

이 문서는 `MissingSlotCompleter`가 식사 슬롯을 추가하고 시간순으로 다시 정렬한 뒤에도 기존 슬롯의 `travelMinutes`가 남는 문제를 정리한다. 공식 Kakao Mobility 길찾기 문서와 현재 저장소 코드만 근거로 사용했다. 조사 당시 구현을 기준으로 원인을 확인했고, 마지막에 실제 적용 결과를 기록했다.

## 결론

`travelMinutes`는 슬롯 자체의 고정 속성이 아니라 **최종 순서에서 직전 장소부터 현재 장소까지의 방향성 있는 인접 간선 값**이다. 따라서 식사 슬롯 삽입이나 장소 제거·재배치로 선행 장소가 바뀐 슬롯은 기존 값을 재사용하면 안 된다.

전체 일정의 모든 인접 구간을 다시 조회할 필요는 없다. 최종 순서를 기준으로 `(직전 장소, 현재 장소)` 쌍이 바뀐 목적지 슬롯만 이동시간을 무효화하고 재계산하는 것이 최소이면서 정확하다. 변경된 쌍을 신뢰성 있게 판별하지 못하는 경우에만 전체 인접 구간 재계산이 안전한 대안이다.

## 1. 외부 API 계약

카카오모빌리티 자동차 길찾기는 `origin`과 `destination`을 각각 X,Y 좌표로 받는다. X는 경도, Y는 위도다. `priority=TIME`은 최단 시간 경로를 뜻하며, `summary=true`는 요약 정보만 반환한다. 응답 `summary`에는 요청에 대응하는 출발지와 도착지 좌표, 전체 거리(m), 목적지까지의 소요 시간(sec)이 담긴다. 즉 `duration`은 특정한 출발 좌표와 도착 좌표의 순서 있는 쌍에 종속된다. ([자동차 길찾기 공식 문서](https://developers.kakaomobility.com/guide/navi-api/directions))

현재 구현도 `/v1/directions`에 `origin`, `destination`, `priority=TIME`, `summary=true`를 전달한다(`src/main/java/com/planb/global/client/kakaoMapService/handler/KakaoMapServiceHandler.java:85`). 확정 좌표가 있으면 장소명을 다시 검색하지 않고 그 좌표를 우선 사용한다(`src/main/java/com/planb/global/client/kakaoMapService/handler/KakaoMapServiceHandler.java:122`, `src/main/java/com/planb/global/client/kakaoMapService/handler/KakaoMapServiceHandler.java:179`). 자동차 응답의 `distance`와 `duration`은 각각 미터와 초로 매핑되어 있다(`src/main/java/com/planb/global/client/kakaoMobilityService/dto/response/KakaoCarRouteResponse.java:15`). 애플리케이션은 초 단위 `duration`을 올림하여 분 단위 `travelMinutes`로 변환한다(`src/main/java/com/planb/global/client/kakaoMobilityService/helper/KakaoMobilityRouteHelper.java:22`, `src/main/java/com/planb/global/client/kakaoMobilityService/helper/KakaoMobilityRouteHelper.java:62`).

## 2. 실패와 경로 없음의 의미

공식 결과 코드에서 `0`만 성공이다. `1`은 경로 없음이며, `102`와 `103`은 각각 출발지와 도착지 주변 도로 탐색 실패, `104`는 두 지점이 5m 이내여서 경로를 탐색할 수 없는 경우다. 따라서 경로 없음은 성공한 0분 경로가 아니다. ([길찾기 결과 코드 공식 문서](https://developers.kakaomobility.com/guide/navi-api/reference.html))

HTTP 수준에서도 `200`만 성공이며, 잘못된 요청·인증·권한·쿼터 초과·서버 장애는 각각 `400`, `401`, `403`, `429`, `5xx`로 실패한다. 특히 모든 구간을 무조건 재조회하면 필요 이상의 요청으로 쿼터와 초당 요청 한도에 가까워질 수 있다. ([길찾기 오류 공식 문서](https://developers.kakaomobility.com/guide/navi-api/solution.html))

현재 자동차 응답 변환기는 경로가 없거나 첫 경로의 `result_code`가 0이 아니거나 `summary`가 없으면 예외를 발생시킨다(`src/main/java/com/planb/global/client/kakaoMobilityService/helper/KakaoMobilityRouteHelper.java:32`). `KakaoMapServiceHandler`는 빈 응답, 잘못된 이동시간, HTTP 및 변환 예외를 한곳에서 받아 `distanceMeters=null`, `travelMinutes=null`인 결과로 바꾼다(`src/main/java/com/planb/global/client/kakaoMapService/handler/KakaoMapServiceHandler.java:156`).

따라서 변경된 간선의 재조회가 실패했을 때 지켜야 할 계약은 다음과 같다.

- 실패를 `0분` 성공으로 바꾸지 않는다.
- 다른 출발지·도착지 쌍에서 계산된 기존 값을 되살리지 않는다.
- 최종 이동시간 검증에서 `null`을 명시적으로 거부한다.

## 3. 현재 stale 값이 생기는 흐름

`MissingSlotCompleter.fillDay`는 관광 슬롯과 식사 슬롯을 추가한 뒤 전체 슬롯을 `startTime`으로 다시 정렬한다(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:288`). 새 장소 슬롯은 이동시간을 `null`로 만든다(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:601`). 그러므로 새 식사 슬롯으로 들어가는 간선은 이후 조회 대상이 된다.

문제는 새 슬롯 바로 다음의 기존 슬롯이다. 예를 들어 기존 순서가 `A -> B`이고 그 사이에 식사 `M`이 들어가면 최종 간선은 `A -> M`, `M -> B`다. 새 `M.travelMinutes`는 `null`이지만 기존 `B.travelMinutes`에는 여전히 `A -> B` 값이 남아 있다.

`PlanService.fillMissingTravelMinutes`는 날짜 경계를 넘어 직전 확정 장소를 계속 전달하지만(`src/main/java/com/planb/domain/travel/service/PlanService.java:1497`), 값이 `null` 또는 0인 슬롯만 조회한다(`src/main/java/com/planb/domain/travel/service/PlanService.java:1549`). 따라서 0보다 큰 오래된 `B.travelMinutes`는 `M -> B`로 다시 계산되지 않는다.

이 값은 단순 표시 정보가 아니다. `ScheduleNormalizer`는 현재 슬롯의 `travelMinutes`를 직전 장소 종료시각에 더해 가장 이른 시작시각을 결정한다(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:98`). 오래된 값은 최종 일정 시각까지 잘못 이동시킬 수 있다.

`RouteAnchor`도 `travelMinutes`가 직전 확정 장소에서 현재 슬롯으로 들어오는 시간이라는 계약을 명시하고, 일부 날짜 재구성 시 앞 날짜의 마지막 장소를 별도 기준점으로 전달한다(`src/main/java/com/planb/domain/travel/service/RouteAnchor.java:8`). 그러므로 날짜 첫 장소도 전날 마지막 장소가 달라지면 변경된 간선이다.

## 4. 재계산 범위

### 권장 기준

최종 일정의 각 장소 슬롯을 목적지로 보고 다음 키를 비교한다.

```text
(직전 장소 identity/좌표, 현재 장소 identity/좌표)
```

이 키가 보정 전과 보정 후에 같으면 기존 `travelMinutes`를 유지할 수 있다. 다르면 해당 목적지 슬롯의 값을 무효화하고 기존 `fillMissingTravelMinutes`가 다시 조회하도록 해야 한다. 좌표가 있으면 현재 경로 조회와 동일하게 좌표를 우선하고, 좌표가 없는 경우에만 장소 identity 또는 이름을 보조 기준으로 쓴다.

식사 삽입만 놓고 보면 재계산 대상은 작다.

| 변경 | 다시 계산할 목적지 슬롯 |
| --- | --- |
| `A -> B` 사이에 `M` 삽입 | `M`과 `B` |
| 하루 첫 위치에 `M` 삽입 | `M`과 기존 첫 장소 |
| 하루 마지막에 `M` 삽입 | `M`만 |
| 장소 제거 | 제거된 장소의 다음 장소 |
| 장소 재배치 | 이전 또는 현재 선행 장소가 달라진 모든 목적지 |
| 전날 마지막 장소 변경 | 다음 날 첫 장소 |

현재 새 슬롯은 이미 `travelMinutes=null`이므로 **식사 삽입 버그의 최소 누락분은 삽입 뒤 첫 기존 장소의 이동시간 무효화**다. 다만 같은 원리는 초과 관광지 제거에도 적용된다. 현재 `TouristPlaceCountPolicy`도 제거 뒤 남은 슬롯의 이동시간을 그대로 둔다고 명시한다(`src/main/java/com/planb/domain/travel/policy/TouristPlaceCountPolicy.java:87`). 이 사실은 동일한 인접 간선 계약의 추가 증거이며, 이번 수정 범위를 불필요하게 넓히라는 뜻은 아니다.

### 모든 구간 재계산을 기본값으로 권하지 않는 이유

- 순서가 바뀌지 않은 구간은 출발지·도착지 쌍도 그대로다.
- 외부 호출 수와 지연이 일정 슬롯 수에 비례해 늘어난다.
- 공식 문서상 `429` 쿼터·초당 요청 한도 실패가 존재한다.
- 현재 교통 상황으로 결과가 달라질 수 있어, 관련 없는 구간까지 다시 조회하면 한 번의 보정이 기존 일정 전체를 불필요하게 흔든다.

변경 간선을 확실히 표시할 수 없다면 정확성을 위해 전체 재계산을 선택할 수 있다. 하지만 현재 식사 삽입 지점은 새 슬롯과 정렬 결과를 모두 알고 있으므로, 다음 목적지 슬롯만 무효화하는 작은 수정으로 해결할 수 있다.

## 5. 현재 검증의 빈틈

최초 `fillMissingTravelMinutes` 뒤에는 `validateTravelMinutes`가 호출되어 장소 슬롯의 `null` 또는 음수 이동시간을 거부한다(`src/main/java/com/planb/domain/travel/service/PlanService.java:506`, `src/main/java/com/planb/domain/travel/service/PlanService.java:981`).

그러나 수정 전 `refillMissingMeals`는 새 슬롯을 추가하고 이동시간을 조회한 뒤 정규화만 반환했다(`src/main/java/com/planb/domain/travel/service/PlanService.java:629`). 새 경로 조회가 실패해도 이동시간 검증을 다시 호출하지 않는 빈틈이 있었다.

따라서 변경 간선을 무효화하는 수정과 함께 최종 보정 결과에도 기존 `validateTravelMinutes` 계약이 적용되어야 한다. 실패 시 오래된 값을 유지하는 동작은 선행 장소가 바뀌지 않은 동일 간선에서만 호환성을 위한 fallback으로 의미가 있다(`src/main/java/com/planb/domain/travel/service/PlanService.java:1549`). 변경된 간선에는 적용할 수 없다.

## 6. 최소 회귀 테스트 기준

외부 API를 실제 호출하지 않고 `KakaoMapServiceHandler`를 mock하여 다음을 고정할 수 있다.

1. `A -> B` 사이에 식사 `M`이 삽입되면 `A -> M`과 `M -> B`를 조회하고 두 결과를 각각 `M`, `B`의 inbound `travelMinutes`에 저장한다.
2. 삽입과 무관한 `C -> D` 구간은 기존 값을 유지하며 조회하지 않는다.
3. 아침 식사가 하루 첫 장소 앞으로 들어가면 anchor/전날 마지막 장소에서 아침까지, 아침에서 기존 첫 장소까지 계산한다.
4. 저녁 식사가 마지막에 추가되면 새 저녁의 inbound만 계산한다.
5. 변경된 간선 조회가 실패하면 이전 간선의 값을 유지하거나 0으로 바꾸지 않고 최종 검증에서 실패한다.
6. 다음 날 첫 장소의 선행 장소가 바뀌면 날짜 경계 간선도 재계산한다.

## 결정

**최종 일정의 모든 구간이 아니라, 최종 인접 관계가 바뀐 구간만 재계산한다.** 구현 관점에서는 `travelMinutes`를 목적지 슬롯에 저장하는 현재 모델을 유지하고, 삽입·제거·재배치가 일어난 뒤 선행 장소가 달라진 목적지의 값을 `null`로 무효화한 다음 기존 좌표 기반 조회 경로를 재사용하는 방식이 가장 작다.

## 적용 결과

`MissingSlotCompleter`는 보정 전 장소별 선행 장소를 요청 내부에서 기록한다. 보정과 정렬이 끝난 뒤 선행 장소가 바뀐 기존 슬롯의 `travelMinutes`만 `null`로 무효화한다. 새 슬롯은 기존처럼 처음부터 `null`이므로 `PlanService.fillMissingTravelMinutes`가 새 슬롯과 변경된 다음 슬롯을 확정 좌표로 다시 조회한다.

선행 장소 비교는 날짜별로 초기화하지 않는다. 전날 마지막 장소가 식사 보정으로 바뀌면 다음 날 첫 장소의 이동시간도 같은 계약으로 무효화된다. 영향받지 않은 인접 구간은 기존 값을 유지한다.

두 번째 식사 보정 뒤에도 `validateTravelMinutes`를 다시 호출한다. 변경된 경로 조회가 실패하면 오래된 값이나 0분을 사용하지 않고 기존 `INVALID_AI_PLACE` 예외 체계로 거부한다.
