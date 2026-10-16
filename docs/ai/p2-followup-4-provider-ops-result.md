# P2 후속4 — 공급자·운영 진단 결과

작성일: 2026-10-15
담당: Claude
기준: dev `d542c426baefb8b66af69d5bd5ec938f5c83a900`, main `a6271bf8bce26436b250b426f50fa30bd61a7601`
Issue·PR: #201 / 이 문서를 포함한 dev PR
범위: 기존 증거 재검토, 1차 출처 조사([공식 조사](p2-followup-4-official-research.md)), 게이트웨이 오류 코드 보존 최소 수정, 무료 검증. 실제 API 호출 0건

## 1. 결론

| 항목 | 판정 |
|---|---|
| 과거 Kakao 대중교통 HTTP 400(2026-10-12 20:30~01:00 KST) | 근본 원인 UNKNOWN 유지. 당시 응답 본문 미기록. 이후 200 응답과 #199 생성 12/12는 과거 원인 판정 근거 아님 |
| 현재 Kakao 오류 보존 | 상태·공급자 code·message·요청 좌표·예외 유형을 경고 로그에, 단계·유형·상태를 meter에 보존. 오류 본문에 `code`가 실려 오면(dapi 오류 본문 형식은 공식 문서에 없음, UNKNOWN) 다음 재발 시 쿼터·금액 한도·점검·내부 오류·입력·권한·연결/timeout 구분 가능(§4) |
| 공공데이터포털 게이트웨이(TourAPI·식약처) | 오류는 JSON 요청에도 XML로만 출력(TourAPI 매뉴얼). 수정 전에는 2xx XML이 Content-Type에 따라 `decode_error`(JSON 표기) 또는 `http_200`(text/xml 등, 판독기 없음), 오류 상태는 `http_상태`로만 남아 22(일일)·23(초당)·30(미등록 키) 구분 불가. **이번 수정으로 반환 코드 보존** |
| #199 실측의 공급자 상태 | TourAPI 131건 전부 `ok`(생성·복약 JSONL meterDelta 105+26). 실행 로그 5종(생성·복약·REST·STOMP·영양)에 `[ROUTE LOOKUP FAILED]`·`[TOURAPI] 비정상`·NutritionService 경고 0건(로그 파일 기준). 반환 12건에 공급자 실패로 인한 최종 누락 거부 0. Kakao 경로 meter는 하네스 수집 대상이 아니어서 `walk_estimate`·`kept_original`로 흡수된 조회 실패 수는 미수집 |
| 생선구이 100mL | 우리 매핑은 `SERVING_SIZE` 문자열 그대로 전달(단위 상수·변환 없음). 공식 DB에 같은 기준 존재(표준데이터 생선구이 73행 중 61행 `100ml`(산출), 갈치구이 상세 `100mL`). #199 관측 레코드와의 동일성은 원본 미보존으로 UNKNOWN. 매핑 결함 근거 없음, g 환산 금지 유지 |
| TourAPI·식약처 운영계정 승인 | 2026-10-16 사용자 전환 완료 확인. 응답으로 계정 등급 확인 불가, 실제 할당량 미확인 |
| STOMP 운영 | Railway WebSocket은 유휴·기간 제한 면제(공식). 서버 heartbeat 미설정(0,0). 재연결은 클라이언트 책임. 브라우저·실제 disconnect/reconnect·운영 프록시 미검증 |

"원인 해결"·"운영 검증 완료"로 표현하지 않는 결산. 이번 수정은 진단 기록 보강이며 공급자 장애·한도를 막지 않음.

## 2. 과거 Kakao 400 재검토

| 증거 | 출처 | 판정 |
|---|---|---|
| 2026-10-12 20:30:50 첫 400, 23:02~23:12 대중교통 12/12 실패·자동차 6/6 성공, 23:15 STOMP 편집 400 직접 관측 | `p2-route-failure-recurrence-analysis.md` §3(보관 사본) | 대중교통 경로 API에 시간대 집중 |
| 다음날 01:07 같은 구간 200, 이후 재측정 4xx 0 | 같은 문서 §3 | 일시 현상 정황. 원인 판별 근거 아님 |
| 응답 본문·Kakao 오류 code | 당시 미기록 | 쿼터·점검·입력·내부 제한 판별 불가 |
| 쿼터 초과 응답 형식 | 공식 문서 상충: 오류 코드 문서 400/-10, 레퍼런스 상태표 429(경로 API 미열거), 카카오맵 FAQ 429 | 400만으로 쿼터 배제 불가, 429만 기다려도 누락 가능 |
| 사용자 확인(2026-10-13): 유료 설정 월 최대 500만 건, 기존 앱 키 | 같은 문서 §4 | 일일 무료 쿼터 소진 가설은 약함. 콘솔 사용량은 이번에도 미확인 |
| 2026-10-12 시간대 공지·장애 이력 | 미확인(콘솔·문의 이력 없음) | 공급자 장애 가설 미검증 |

가설 상태: 우리 코드 회귀 기각(`p2-4d-time-contract-result.md` §12), 일일 쿼터 소진 약함, 요청 형식 오류 약함(같은 좌표 이후 200), 초당 제한·공급자 일시 장애 미검증. **UNKNOWN 유지.**

## 3. 오류 정보 보존 현황

| 공급자 | HTTP 상태 | 공급자 코드 | 메시지 | 요청 정보 | meter | 수정 |
|---|---|---|---|---|---|---|
| Kakao 대중교통·자동차 경로 | 로그·meter | 로그(`code` 우선, 없으면 `errorType`; 200 본문 `status`·`result_code`) | 로그(인증값 마스킹·길이 제한) | 좌표 4개 로그 | `planb.external.kakao.route` outcome(`route_http_400` 등) | 없음. #190 관측 보강으로 충분 |
| TourAPI | meter | 본문 `resultCode`는 meter `result_코드`. **XML `returnReasonCode`는 수정 후 meter `gateway_코드`·`http_상태_gateway_코드`** | 없음 | 경로만(쿼리의 서비스키 제외) | `planb.external.kor2.response` | 이번 |
| 식약처 영양 | 경고 로그(`failure.toString()`) | **수정 후 예외 메시지에 `returnReasonCode`·`returnAuthMsg`** | 상수명만 | 음식명 로그 | 영양 결과 상태 meter(원인 미분리) | 이번(로그 한정) |

- Kakao 공급자 code를 meter tag에 넣지 않는 이유: 무제한 문자열 cardinality 방지. 로그로 판별
- 오류 상태(4xx/5xx)의 식약처 XML 본문은 `WebClientResponseException.toString()`에 본문이 없어 로그에 코드가 남지 않음. 남은 한계
- #199 생성 30/72는 영양 수치가 빈 식사 수(UNAVAILABLE 상태 수 아님). `PlanService`의 "영양성분 조회 실패" INFO 로그 30건(생성 22 + 복약 8)과 일치. NutritionService 예외 경고 0건으로 해당 예외의 직접 증거 미발견. 원본 응답·캐시 행 미보존으로 실제 발생 부재까지 증명 불가. 원인 후보는 빈 조회 결과·이름 필터, Tool 미호출·메뉴명 불일치(일부 메뉴는 영양 Tool 호출 로그 0건), 수치 파싱 불가. 개별 분류는 미기록
- 독립 조회 UNAVAILABLE 3/8(경고 0건)은 예외가 아닌 경로

## 4. 한도·입력·장애 구분 기준(수정 후)

| 원인 후보 | 공식 판별 신호 | 기록 위치 |
|---|---|---|
| TourAPI 일일 한도 | `returnReasonCode` 22 (`LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR`), 자정 초기화 | meter `gateway_22` 또는 `http_상태_gateway_22` |
| TourAPI 초당 한도 | 23 (`..._PER_SECOND_EXCEEDS_ERROR`). 매뉴얼에는 없고 포털 표·FAQ에만 존재(상충) | `gateway_23`. 2xx 23은 재시도 없음. HTTP 429와 함께 오면 기존 429 1회 재시도 적용(`http_429_gateway_23`, 23 전용 정책 아님). 23 전용 정책 UNDECIDED |
| TourAPI 키·권한·IP | 20, 30, 31, 32(또는 29) | `gateway_코드` |
| Kakao 쿼터 | 400 + code -10(허용 요청 회수 초과), 또는 429(FAQ) | 로그 providerCode, meter `route_http_400`·`route_http_429` |
| Kakao 유료 금액 한도 | -11(400, 유료 API 일·월 한도 금액 초과). 유료 설정 계정이라 -10보다 직접적 후보 | 로그 providerCode |
| Kakao 내부 오류·타임아웃 | -1(400, 재시도 권장), -603(400, 내부 타임아웃) | 로그 providerCode |
| Kakao 점검 | -7(400), -9798(503) | 로그·meter |
| Kakao 권한·키 | -3·-5(403), -401(401) | 로그·meter |
| Kakao 입력 | -2(400), 200 본문 `status` INVALID_REQUEST 등 | 로그(NO_RESULT의 providerCode) |
| 공급자 장애·연결 | 5xx, timeout, 연결 종료 | meter `http_5xx`·`error`, Kakao `route_timeout`·`route_connection` |

Kakao 코드 판별은 오류 본문에 `code`가 있을 때만 가능(dapi 본문 형식 UNKNOWN, DevTalk 사용자 사례에 `code`·`errorType` 병기). 초당 허용량 수치, 23 이후 권장 대기, 게이트웨이 오류의 HTTP 상태는 공식 문서에 없음(UNKNOWN).

## 5. 수정

| 위치 | 변경 |
|---|---|
| `DataPortalGatewayException`(신규, `global/client`) | 2xx 본문이 게이트웨이 오류 XML이면 JSON 해석 전에 반환 코드를 담은 예외로 전환(Content-Type 무관). `DecodingException` 하위 타입이라 호출부의 기존 `CodecException`·`RuntimeException` 처리 유지. text/xml 2xx는 이전 `WebClientResponseException`(200)에서 이 예외로 바뀌나 호출부 catch 범위 안. JSON 본문은 문자열 변환 없이 같은 바이트로 전달. 메시지에는 숫자 코드와 상수명 형식만 포함 |
| `Kor2ServiceClient` | 위 필터 적용. outcome `gateway_코드`, 오류 상태 본문의 XML 코드는 `http_상태_gateway_코드`. 429 1회 재시도 조건 불변 |
| `FoodNtrCpntClient` | 같은 필터 적용. 영양 조회 실패 경고 로그에 반환 코드 노출. 같은 apis.data.go.kr 게이트웨이와 포털 공통 FAQ에 근거한 추론 적용(식약처 문서의 직접 근거 아님) |
| `ApiClient` | 본문 상한 상수 `protected`(필터 join 상한에 재사용) |
| `TravelLlmQualityBaselineTest`(실측 하네스) | meter 수집 대상에 `planb.external.kakao.route`, `planb.travel.route.segment` 추가. 다음 실측부터 경로 단계별 결과 보존 |

하지 않은 것: 23 재시도·backoff, 서버 heartbeat 설정, 공급자 교체, 쿼터 소진 실험, 생성 배치 재실행, 영양 기준량 환산.

## 6. 검증

| 항목 | 결과 |
|---|---|
| RED | 3건 실패: 2xx XML 23이 `decode_error`로 기록, 500 XML 22의 코드 미기록, 식약처 예외 메시지에 코드 없음. 리뷰 후 추가한 text/xml 2xx 22 케이스는 수정 전 `WebClientResponseException`(200)으로 실패(별도 RED) |
| GREEN | `Kor2ServiceTestClient` 9건, `FoodNtrCpntTestClient` 2건 통과. 기존 429 재시도·500 무재시도·`result_22`·JSON 정상 해석 유지 |
| 관련 회귀 | `global.client` 단위 전체, `NutritionServiceTest` 19건, `ExternalHttpStubServerTest`(HTTP stub 서버 경유 실제 WebClient 경로) 통과 |
| 전체 무료 회귀 | 145클래스 1,116건, 실패·오류·skip 0(기존 1,111 + 신규 5). `full-regression-summary.json`. 이후 두 client 서식(줄바꿈)만 변경, 대상 테스트 9·2건 재통과(`green-final/`) |
| 독립 review-loop | 8 → 8.5 → 9.5/10, 최종 Critical·Important 0. 지적(경로 결과 과추론, Kakao 400 계열 코드 누락, text/xml 경로, 23 재시도 조건, 영양 30/72 정의 등) 모두 반영 |
| 실제 API | 0건. 식약처 02 API 기준량 문자열 확인용 2건 조회는 실행 권한 거부로 미실행 |

## 7. STOMP 운영 검증 범위

| 항목 | 상태 | 근거 |
|---|---|---|
| 대형 미리보기 수신(Java 하네스) | 검증 | P2-6B §8, #199 STOMP 1건 |
| 연결 유지 중 인증 GET 재조회 | 검증 | #199 |
| Railway 엣지 유휴 종료 | 해당 없음(공식 문서 기준) | WebSocket은 60초 유휴·5/15분 기간 제한 면제, "can stay open indefinitely" |
| 재배포 시 연결 | 끊김 추정 | 이전 배포 SIGTERM 후 기본 0초 SIGKILL. 연결 처리 명시 문서 없음 |
| 서버 heartbeat | 미설정 | `enableSimpleBroker("/sub")`만 호출. TaskScheduler 없으면 기본 0,0. 반쯤 열린 연결 감지 불가 |
| 재연결 | 미검증 | WebSocket 명세에 재연결 없음. stompjs 기본 5초 재연결·`onConnect` 재구독 권장. 실제 프런트 설정 UNKNOWN. 재연결 후 GET 재조회로 미리보기 복구 가능한 서버 계약은 #199에 있음 |
| 브라우저 수신·운영 프록시 | 미검증 | 저장소 범위 밖 |

122초 무응답 TALK가 Railway 엣지에서 끊길 근거는 공식 문서상 없음. 다른 프록시(회사망·모바일망)와 반쯤 열린 연결은 별도.

## 8. 미결·후속

| 항목 | 상태 |
|---|---|
| 23(초당) 재시도·backoff, 22(일일) 사용자 안내 | 정책 UNDECIDED |
| 서버 heartbeat(`setHeartbeatValue`·`setTaskScheduler`) 도입 | 운영 결정 필요. 클라이언트 heartbeat와 함께 확인 |
| 프런트 재연결·재구독·GET 재조회 연동, 브라우저 실측 | 프런트 저장소·실측 필요 |
| Kakao 콘솔 2026-10-12 사용량·오류, 데브톡 문의 | 계정 접근 필요 |
| TourAPI·식약처 운영계정 승인 상태 | 사용자 전환 완료 확인(2026-10-16). 실제 할당량은 마이페이지 자료 필요 |
| 식약처 02 API 기준량 문자열과 표준데이터 동일성, 이름 불일치 시 첫 항목 선택의 적합성 | 소량 실측 승인 필요 |
| 오류 상태 식약처 XML 코드의 로그 보존 | 남은 한계 |
| UTF-8 BOM으로 시작하는 게이트웨이 XML | 감지 못 하고 기존 경로(문서상 사례 없음) |

## 9. 보관·보안

- 문서 사본: `텍스트 문서/p2-followup-201-20261015/` 보관 확인. #205 최신 수정본도 루트·기존 사본 위치에 SHA256 대조 후 동기화
- 증거(RED/GREEN XML, 로그, 회귀 요약): `텍스트 문서/p2-followup-201-20261015/` 보관 사본 확인. 과거 임시 경로만 존재한다는 작성 시점 상태에서 갱신. Git 제외
- 키·JWT·건강 seed·raw·로그 미커밋. 보호 계획서 3개·DAG evidence 스테이징 금지

## 10. 후속 상태 정정 — 2026-10-16, #205

§1·§8의 운영계정 UNKNOWN과 §9의 동기화 예정은 작성 당시 기록. 사용자는2026-10-16 TourAPI·식약처 운영계정 전환 완료 확인. 계정별 실제 할당량은 미확인. 보관 `p2-followup-201-20261015/` 사본 존재 확인, 이번 수정본도 사본 동기화.

#199의 null30개는 Tool 호출 관측24/미관측6개로 대조. 빈 결과·필터·캐시·표준명 하위 원인은 UNKNOWN. Tool 미호출을 Java 후처리 미호출로 확대하지 않는 판정. [후속 결산](p2-pre-dag-quality-closeout-result.md) §1·2에 원인 경계와 22/23/heartbeat 최종 정책 기록.
