# P2 후속 4: 외부 API 오류·한도, 영양 기준량, STOMP 운영 근거 1차 출처 조사

확인일: **2026-10-15** (아래 모든 출처 동일). 읽기 전용 문서 조사. 인증키 사용 API 호출, `.env` 열람, 코드·설정 변경 없음.
공식 포털 첨부(zip/docx), 공식 표준데이터 JSON은 격리 디렉터리에 내려받아 텍스트만 확인.

라벨: **[공식]** 공식 확인됨 / **[UNKNOWN]** 공식 문서에 없음 / **[상충]** 문서 간 상충 / **[2차 힌트]** 사용자 Q&A, 판단 근거 아님.

## 출처 (확인일 모두 2026-10-15)

| ID | 출처 | URL |
|---|---|---|
| S1 | 공공데이터포털 TourAPI 상세(수정일 2026-02-26, 내장 Swagger KorService2) | https://www.data.go.kr/data/15101578/openapi.do |
| S2 | 한국관광공사 개방데이터 활용매뉴얼(국문) **v4.4, 2026-02-10** (S1 참고문서 zip) | https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000003603931&fileDetailSn=1 |
| S3 | TourAPI 4.0 활용신청 매뉴얼 **v3.3, 2024-03-27** (S2와 같은 zip) | 위 S2 URL |
| S4 | 포털 FAQ_0000000000000214 "OpenAPI 서비스 이용 중 에러 메시지" | https://www.data.go.kr/bbs/faq/selectFaqList.do (상세: POST /bbs/faq/selectFaqView.do, bbsId) |
| S5 | S4 첨부 "Open API 에러 코드 정리.docx" | https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000001260631&fileDetailSn=0 |
| S6 | 포털 FAQ_0000000000000208 "일일 사용량을 초과" | S4와 같은 FAQ 목록 |
| S7 | 포털 FAQ_0000000000000264 / 159 / 171 (승인 확인, 서비스키) | S4와 같은 FAQ 목록 |
| S8 | Kakao REST API 에러 코드 | https://developers.kakao.com/docs/ko/rest-api/error-code |
| S9 | Kakao REST API 레퍼런스(응답 형식·HTTP 상태) | https://developers.kakao.com/docs/ko/rest-api/reference |
| S10 | 카카오맵 이해하기(이용 정책·FAQ) | https://developers.kakao.com/docs/ko/kakaomap/common |
| S11 | Kakao 쿼터 | https://developers.kakao.com/docs/ko/getting-started/quota |
| S12 | 카카오맵 REST API 대중교통 경로 조회 | https://developers.kakao.com/docs/ko/kakaomap/rest-api#transit-info |
| S13 | Kakao 공지(카카오맵 무료 쿼터 변경, 2026-06-16 게시, 2026-07-21 시행) | https://devtalk.kakao.com/t/api/150222 |
| H1 | DevTalk 사용자 질문(2026-07-24) — **2차 힌트** | https://devtalk.kakao.com/t/api-rest-code-10/150841 |
| S14 | 식약처 식품영양성분DB정보(수정일 2026-09-16, Swagger FoodNtrCpntDbInfo03) | https://www.data.go.kr/data/15127578/openapi.do |
| S15 | 전국통합식품영양성분정보(음식) 표준데이터(수정일 2026-10-06) + 공식 JSON 내려받기 | https://www.data.go.kr/data/15100070/standard.do , https://www.data.go.kr/download/standard.json?publicDataPk=15100070&svcTableNm=tn_pubr_public_nutri_food_info_svc&perPage=20000&page=1 (colNmList 지정) |
| S16 | 식품영양성분 DB 상세(갈치구이 D508-355000000-0001) | https://various.foodsafetykorea.go.kr/nutrient/general/food/detail.do?searchFoodCd=D508-355000000-0001 |
| S17 | 식약처 "식품영양성분데이터베이스를 소개합니다"(2022-09-05) | https://www.mfds.go.kr/brd/m_580/view.do?seq=69 |
| S18 | Spring Framework reference Simple Broker (`reference/7.0/...` 요청 → 현재 7.0.9 문서로 리다이렉트) | https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-simple-broker.html |
| S19 | Spring Framework **7.0.3** Javadoc + 같은 버전 `spring-messaging-7.0.3-sources.jar`(Gradle 캐시) | https://docs.spring.io/spring-framework/docs/7.0.3/javadoc-api/org/springframework/messaging/simp/config/SimpleBrokerRegistration.html |
| S20 | STOMP 1.2 Heart-beating | https://stomp.github.io/stomp-specification-1.2.html#Heart-beating |
| S21 | Railway Public Networking Specs & Limits (`/reference/public-networking` → 리다이렉트) | https://docs.railway.com/networking/public-networking/specs-and-limits |
| S22 | Railway Deployment reference / teardown | https://docs.railway.com/deployments/reference , https://docs.railway.com/deployments/deployment-teardown |
| S23 | WHATWG WebSockets Standard (Last Updated 15 March 2026) | https://websockets.spec.whatwg.org/ |
| S24 | WHATWG HTML Server-sent events | https://html.spec.whatwg.org/multipage/server-sent-events.html |
| S25 | @stomp/stompjs 7.4.0 Client API | https://stomp-js.github.io/api-docs/latest/classes/Client.html |
| S26 | @stomp/stompjs 사용 가이드 | https://stomp-js.github.io/guide/stompjs/using-stompjs-v5.html |

## 1. 공공데이터포털 게이트웨이 + TourAPI KorService2

### 1-1. 오류 응답 형식

- **[공식]** S2 원문: "공공데이터포털에서 출력되는 오류 메세지는 **XML로만 출력**되며, 형태는 아래와 같습니다." 예시:
  `<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg><returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>` [S2]
- **[공식]** S2: "응답 표준은 XML이며, JSON을 요청할 경우 `&_type=json`을 추가". 정상 응답은 `response.header.resultCode="0000"`, `resultMsg="OK"`. [S2]
- 결론: `_type=json` 요청이어도 게이트웨이 오류는 XML이라는 점은 매뉴얼 문언으로 확인. 다만 S2(2026-02-10) 이후 포털 GW 오류표(S1, 2026-02-26)는 형식 미기재 → 이후 GW 형식 변경 여부 **[UNKNOWN]**.
- **[UNKNOWN]** 게이트웨이 오류의 **HTTP 상태 코드**(200/401/429/500). S1 Swagger는 15개 operation 모두 `200`(성공)만 정의, 오류 스키마 없음. S2·S4·S5도 상태 코드 미기재. [S1][S2][S4][S5]
- **[UNKNOWN]** `resultCode/resultMsg` JSON 형태의 게이트웨이 오류, plain text 오류. 두 필드는 정상 응답 header로만 문서화. [S1][S2]
- **[UNKNOWN]** S2 "제공기관 에러코드"(00~99)의 전달 형식(정상 envelope의 header.resultCode 여부). 표만 존재. [S2]
- 참고: 포털 공지 목록(최근 항목) 열람에서 GW 오류 형식 변경 공지 미발견. 키워드 검색 필터가 동작하지 않아 전수 확인은 못함. TourAPI 자체 사이트(api.visitkorea.or.kr)는 SPA라 본문 미확인.

### 1-2. 오류 코드 상수명과 출처별 수록 여부

| 코드 | 상수명(원문) | S1 포털 GW표 | S2 포털표 | S2 제공기관표 | S4 FAQ 본문 | S5 FAQ 첨부 |
|---|---|---|---|---|---|---|
| 01 | APPLICATION_ERROR | O | O | O | O | O |
| 02 / 03 | DB_ERROR / NODATA_ERROR | - | - | O | - | O |
| 04 | HTTP_ERROR | O | O | O | O | O |
| 05 | SERVICETIMEOUT_ERROR | O | - | O | - | O |
| 10 / 11 | INVALID_REQUEST_PARAMETER_ERROR / NO_MANDATORY_REQUEST_PARAMETERS_ERROR | 10만 | - | O | - | O |
| 12 | NO_OPENAPI_SERVICE_ERROR | O | O | O | O | O |
| 20 | SERVICE_ACCESS_DENIED_ERROR | O(인증표) | O | O | O | O |
| 20 | SERVICE_KEY_IS_NULL, PERMISSION_DENIED | O | - | - | - | - |
| 21 | TEMPORARILY_DISABLE_THE_SERVICEKEY_ERROR | - | - | O | - | O |
| 22 | LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR | O | O | O | O | O |
| 23 | LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR | O | - | - | O | - |
| 29 | BLACKLIST_IP_ACCESS_ERROR | O | - | - | - | - |
| 30 | SERVICE_KEY_IS_NOT_REGISTERED_ERROR | O(인증표) | O | O | O | O |
| 31 | DEADLINE_HAS_EXPIRED_ERROR | O(인증표) | O | O | O | O |
| 32 | UNREGISTERED_IP_ERROR | - | O | O | - | O |
| 33 | UNSIGNED_CALL_ERROR | - | - | O | - | O |
| 99 | UNKNOWN_ERROR | - | O | O | O | O |

- **[공식] 22 = 일일 한도 초과.** S1 "API 서비스의 일일 호출 허용량을 초과했습니다. 호출량이 초기화된 이후 다시 이용하거나 트래픽 증설을 신청해 주세요." S4 "설정된 일일 호출 횟수(트래픽)를 초과한 경우". S2 "서비스 요청 제한 횟수 초과 에러"(일·초 구분 없음).
- **[공식] 23 = 초당 한도 초과.** S1 "짧은 시간에 많은 요청이 발생하여 초당 호출 허용량을 초과했습니다. 잠시 후 다시 호출해 주세요." S4 "단시간 내 과도한 요청(초당 요청 수)으로 인해 호출이 차단된 경우".
- **[상충]** 23은 S1·S4에만 있고 TourAPI 매뉴얼 S2와 FAQ 첨부 S5에는 없음. IP 관련은 S1=29 BLACKLIST_IP_ACCESS_ERROR, S2·S5=32 UNREGISTERED_IP_ERROR로 상이. S1은 코드 20에 상수 3개(SERVICE_KEY_IS_NULL, PERMISSION_DENIED, SERVICE_ACCESS_DENIED_ERROR) 병기.
- **[공식]** 20: S1 키 누락(SERVICE_KEY_IS_NULL)·GW 접근 권한 거부(PERMISSION_DENIED)·"활용신청 여부와 승인 또는 일시중지 상태" 확인(SERVICE_ACCESS_DENIED_ERROR). S4 "신청하지 않았거나, 변경신청 등으로 인해 일시 중지된 경우". 21 "일시적으로 사용할 수 없는 서비스 키"(S2·S5). 30 "등록되지 않은 서비스키", 31 기한 만료, 32 "등록되지 않은 IP", 99 "기타 에러"(S2·S4·S5).
- **[UNKNOWN]** 초당 허용량 수치, 23 이후 권장 대기 시간("잠시 후"만 기재), 22/23의 HTTP 상태.

### 1-3. 개발계정·운영계정

- **[공식]** S1: 심의유형 "개발단계 : 자동승인 / 운영단계 : 심의승인", 신청 가능 트래픽 "개발계정 : 1,000 / 운영계정 : 활용사례 등록시 신청하면 트래픽 증가 가능". 단위 미표기. [S1]
- **[공식]** 단위: S3 "개발계정은 **각 오퍼레이션별 일일 1,000건**", S2 "개발계정은 일 1,000건". S6 "하나의 서비스키로 하루 동안 호출할 수 있는 API 호출 건수 … 매일 자정(00시)에 초기화", "특정 오퍼레이션의 트래픽 제한이 100건이라면" 예시. [S2][S3][S6]
- **[공식]** 확인·증설 경로(S6): "트래픽 정보는 '마이페이지 > 데이터 활용 > Open API > 활용신청 현황'에서 확인", "개발계정 → 운영계정 전환 후, 트래픽 상향 요청 가능", "운영계정 → … 변경신청을 통해 일일 호출량 증설 요청 가능", "트래픽 증설은 서비스 부하 상황에 따라 일부 제한될 수 있습니다." [S6]
- **[공식]** 운영계정 신청(S3, 2024-03-27): "[마이페이지 > 데이터활용 > Open API > 활용신청 현황 상세보기] 화면에서 [운영계정신청] 버튼", "한국관광공사 담당자의 승인이 필요하며, 약 1~3일", 확인 항목 "활용 어플 URL", "개발계정의 TourAPI 호출 이력", 승인 후 24개월·[연장신청]. 현행 포털 UI와 일치 여부 **[UNKNOWN]**. [S3]
- **[공식]** 승인 상태 확인: S7(264·159) "'마이페이지 > 데이터 활용 > Open API > 활용신청 현황'에서 상태 확인". [S7]
- **[UNKNOWN]** 운영계정 승인 후 TourAPI 일일 트래픽 수치.
- **[UNKNOWN]** API 응답으로 계정 등급·잔여 트래픽 노출. Swagger 응답 header는 `resultCode`/`resultMsg`뿐, 관련 헤더 문서 없음. S7(159·171) "서비스키는 사용자 단위로 발급되며, 1인당 1개", "하나의 서비스키로 모든 오픈API에 공통 사용" → 키 문자열로 등급 구분 근거 없음. [S1][S7]

## 2. Kakao 대중교통 경로·공통 오류

### 2-1. 공통 오류 코드(S8 원문 요약)

| code | HTTP | 원인(원문) |
|---|---|---|
| -1 | 400 | 서버 내부에서 처리 중에 에러가 발생한 경우 (해결: 재시도) |
| -2 | 400 | 필수 인자 미포함, 데이터 타입 부적절, 허용 범위 벗어남 |
| -3 | 403 | 필요한 기능 활성화 또는 사용 가능 API 호출 허용 설정을 하지 않은 경우 |
| -4 / -5 | 403 | 계정 제재 / 해당 API 요청 권한 없음 |
| -7 | 400 | 서비스 점검 또는 내부 문제가 있는 경우 (해결: 해당 서비스 공지사항 확인) |
| -8 / -9 | 400 | 올바르지 않은 헤더 / 서비스가 종료된 API 호출 |
| -10 | 400 | 허용된 요청 회수를 초과한 경우 (해결: 쿼터 확인 후 쿼터 범위 내로 호출 조정) |
| -11 / -13 | 400 | 유료 API 일·월 한도 금액 초과 / 앱 장기 미이용 |
| -401 | 401 | 유효하지 않은 앱키·액세스 토큰, 앱 정보 불일치 |
| -603 | 400 | 카카오 플랫폼 내부 요청 처리 중 타임아웃 |
| -9798 | 503 | 서비스 점검중 |

- **[공식]** S9 응답 형식: "요청 실패 시: HTTP 상태 코드 및 JSON 형식의 에러 응답 필드". `kapi.kakao.com`은 `code`(Integer)·`msg`(String), `kauth.kakao.com`은 `error`·`error_description`. msg는 "변경될 수 있으므로, 예외 처리 시에는 에러 코드를 사용". 예시 `HTTP/1.1 400 Bad Request {"code": -10, "msg": "API limit has been exceeded."}`. [S9]
- **[공식]** S9 HTTP 상태표: 429 "쿼터 초과(**Daum 검색, 로컬, 모먼트, 키워드광고 API에만 해당**) 정해진 사용량이나 초당 요청 한도를 초과한 경우", 503 "서비스 점검중". [S9]
- **[상충] 쿼터 초과 응답:** S8 -10/400, S9 예시 400/-10, S9 상태표 429(경로 API 미열거), S10 FAQ "카카오맵 API는 앱별로 무료로 제공하는 쿼터가 있습니다. 무료 제공량을 모두 소진한 경우, **429 에러**". 대중교통 경로의 쿼터 초과가 400/-10인지 429인지 공식 문서만으로 확정 불가. [S8][S9][S10]
- **[공식]** 점검: -7(400)과 -9798(503) 두 코드 공존. [S8]
- **[공식]** 쿼터: 대중교통·도보·자전거 경로 **일간 1,000건**, 초과·두 번째 활성화 앱부터 10원/건(비즈월렛·유료 API 설정 필요), 무료 쿼터는 개발자 계정 기준 첫 활성화 앱만, 사용량은 앱 관리 [통계] > [쿼터]. "일간 쿼터는 추후 변경될 수 있습니다." 2026-07-21 이전 활성화 앱은 별도 공지 전까지 기존 무료 쿼터 유지. [S10][S11][S13]
- **[UNKNOWN]** 경로 API 초당 요청 한도 수치, 쿼터 초기화 시각.

### 2-2. 대중교통 API 고유 형식

- **[공식]** 응답 본문 `status` Enum: `OK`, `STARTNODES_NULL`, `ENDNODES_NULL`, `EQUAL_POINTS`, `INVALID_REQUEST`, `NO_RESULTS`. `routes`는 "status가 OK인 경우에만 응답에 포함". 예제는 `HTTP/1.1 200 OK` + `"status": "OK"`만 제시. 기본 정보의 "에러 코드" 링크는 공통 S8로 연결. [S12]
- **[UNKNOWN]** `status`≠OK일 때 HTTP 상태(200 여부).
- **[UNKNOWN]** `dapi.kakao.com`(로컬·경로) 오류 본문 형식. S9는 kauth·kapi만 정의. `errorType`/`message` 필드는 공식 문서에 없음.
- **[2차 힌트]** H1: 대중교통 경로 호출에서 HTTP 400 + `{"errorType":"BadRequest","message":"API limit has been exceeded.","code":-10,"msg":"API limit has been exceeded."}` 보고. "Map" 계정 답변 "테스트앱은 일반앱과 달리 별도의 소량 쿼터(일)가 적용". 공식 직원 표시 확인 불가.

## 3. 식약처 식품영양성분 DB: 기준량 단위

- **[공식]** S14 Swagger(03): `SERVING_SIZE` = "영양성분함량기준량"(**단위·허용값 미기재**), `NUTRI_AMOUNT_SERVING` = "1회 섭취참고량", `DISH_ONE_SERVING` = "1회분량 참고량", `Z10500` = "식품중량". 영양소 필드 단위는 이름에 표기(예: `AMT_NUM13` 나트륨(mg)). [S14]
- **[공식]** S15 표준데이터 `NUT_CON_SRTR_QUA`(영양성분함량기준량) 값은 단위 포함 문자열. 공식 JSON 19,738행 집계: **100g 13,954 / 100ml 5,784**, 다른 값 없음. [S15]
- **[공식, 집계]** 100ml 행은 음료 및 차류 2,629 외에 국·탕 410, 볶음 382, 생채·무침 379, 밥 297, **구이 203**, 조림 189 등 고형 음식 다수. 데이터생성방법 "산출" 3,140행은 전부 100ml. [S15]
- **[공식, 집계]** 구이류 289행 중 100ml 203. 생선구이(가자미·갈치·고등어 등) 73행 중 **100ml 61**, 식품코드 D4~D7 계열. [S15]
- **[공식]** S16 갈치구이(D508-355000000-0001): 식품기원 "초등학교급식", 총 내용량 "80mL", 영양성분 함량 기준량 "100mL", 데이터 생성 방법 "산출"(툴팁 "참고치로 활용"), 표 머리 "100mL 당 함량". [S16]
- 결론: 생선구이 레코드의 mL 기준은 **공식 원본 데이터에 실제로 존재하는 값**. 파싱 오류로 단정할 근거 없음.
- **[UNKNOWN]** mL 기준 적용 규칙(액체 한정 여부), 고형 음식에 mL을 쓴 이유, g↔mL 환산 밀도. S14·S15·S17 모두 미기재.
- **[UNKNOWN]** 02 API(`getFoodNtrCpntDbInq02`) `SERVING_SIZE`의 문자열 형식("100g"/"100ml" 단위 포함 여부)과 표준데이터와의 레코드 동일성. 공개 명세는 03만 확인.

## 4. STOMP/WebSocket 운영 검증 범위

### 4-1. Spring simple broker heartbeat

- **[공식]** S18: "If configured with a task scheduler, the simple broker supports STOMP heartbeats." 예시 `enableSimpleBroker(...).setHeartbeatValue(new long[] {10000, 20000}).setTaskScheduler(...)`. 내장 스케줄러 사용 시 순환 방지를 위해 `@Lazy` 필요. [S18]
- **[공식]** S19(7.0.3): `setHeartbeatValue` "By default this is set to "0, 0" unless the taskScheduler … in which case the default becomes "10000,10000" (in milliseconds)". 첫 값은 서버 송신 주기, 둘째 값은 클라이언트 송신 기대 주기, 0은 heartbeat 없음. `setTaskScheduler`는 "By default this is not set." [S19]
- **[공식]** S19 소스 `SimpleBrokerMessageHandler`: heartbeat 값이 있는데 scheduler가 없으면 "Heartbeat values configured but no TaskScheduler provided" assert. 읽기 기한 `max(client cx, server sy) × 3` 초과 무수신 시 세션 disconnect 처리. 쓰기 간격 경과 시 HEARTBEAT 프레임 송신. [S19]
- 프로젝트 관찰(읽기 전용, HEAD d542c42): `WebSocketConfig`는 `enableSimpleBroker("/sub")`만 호출, scheduler·heartbeat 미설정 → S19 기준 서버 heartbeat "0, 0".

### 4-2. STOMP 1.2 heart-beating

- **[공식]** heart-beat 헤더 "<cx>,<cy>". 헤더 누락은 "heart-beat:0,0"과 동일. 방향별 주기 `MAX(<cx>,<sy>)`. 송신자는 실제 프레임이 없으면 EOL 송신 MUST. 수신자는 기한 내 무수신 시 연결을 "MAY consider the connection as dead", 오차 허용 SHOULD. 재연결 절차는 명세 범위 밖. [S20]

### 4-3. Railway 프록시·배포

- **[공식]** S21 Edge Traffic: "Support for websockets over HTTP/1.1." "Idle HTTP/1.1 connections are closed after 60 seconds between requests. This does not apply to HTTP/2 or websocket connections." "HTTP requests can run for up to 15 minutes if data keeps transferring …, and are otherwise closed after 5 minutes with no data transferred." "**Websocket connections are exempt from these duration and inactivity limits, and can stay open indefinitely, even while idle.**" 최대 동시 연결 10,000, 연결당 요청 10,000. "This information is subject to change at any time." [S21]
- **[공식]** S22: 새 배포가 Active가 되면 이전 배포에 SIGTERM, "By default, it is given 0 seconds to gracefully shutdown before being forcefully stopped with a SIGKILL." `RAILWAY_DEPLOYMENT_DRAINING_SECONDS`, `RAILWAY_DEPLOYMENT_OVERLAP_SECONDS`로 조정. [S22]
- **[UNKNOWN]** 재배포·엣지 장애 시 기존 WebSocket 연결의 처리(이전 또는 종료) 명시 문서 없음. 이전 프로세스 종료로 연결이 끊긴다는 점은 S22에서 도출한 추론. Railway 엣지의 ping/pong 생성 여부도 미기재.

### 4-4. 재연결 책임

- **[공식]** S23: 연결 종료 시 readyState를 CLOSED(3)로 바꾸고 `close` 이벤트(wasClean, code, reason) 발생까지만 규정. 재연결 단계 없음. 대조: S24 EventSource는 UA의 "reestablish the connection" 절차를 규정. → 브라우저 WebSocket 재연결은 애플리케이션·라이브러리 책임(두 공식 명세 대조로 도출). [S23][S24]
- **[공식]** S25(stompjs 7.4.0): Client "Handles automatic reconnections." `reconnectDelay` "Set to 0 to disable automatic reconnections. The default value is 5000 ms", `heartbeatIncoming`/`heartbeatOutgoing` 기본 10000. `onConnect` "Callback executed upon every successful connection". [S25]
- **[공식]** S26: "all subscriptions must be done in this callback. This is needed because it runs after a (re)connect." 지수 backoff(`ReconnectionTimeMode.EXPONENTIAL`, `maxReconnectDelay`) 옵션. [S26]
- **[UNKNOWN]** 실제 프런트엔드의 STOMP 라이브러리·재연결·heartbeat 설정(이 저장소 범위 밖).

## 요약표

| item | 확정 사실 | UNKNOWN | 프로젝트 판단에 주는 의미(한 줄) |
|---|---|---|---|
| 1 TourAPI·포털 GW | 매뉴얼 v4.4 "오류는 XML로만", 22=일일·23=초당 초과(S1·S4), 개발 1,000/일(오퍼레이션별), 자정 초기화, 운영계정은 마이페이지 신청·관광공사 승인 | GW 오류 HTTP 상태, 2026-02 이후 JSON 오류 형식 유무, 운영 트래픽 수치, 응답의 계정 등급 노출 | `_type=json`에도 XML 오류 본문 파싱 필요. 현재 `Kor2ServiceClient`의 HTTP 429 전용 재시도는 공식 근거 없는 가정이므로 `returnReasonCode` 22/23 판별 필요 |
| 2 Kakao 경로 | 공통 `{code,msg}`(kapi), -10/400 "허용된 요청 회수 초과", -7/400·-9798/503 점검, 경로 일간 1,000건, status Enum | 경로 쿼터 초과 400/-10 vs 429(상충), dapi 오류 본문 형식, status≠OK의 HTTP 상태 | 쿼터 초과 판별을 HTTP 429 하나에 의존하지 말고 400+code -10도 처리, `errorType`/`code` 병행 파서 유지 |
| 3 식약처 기준량 | `SERVING_SIZE`=영양성분함량기준량, 공식 데이터에 100g/100ml만 존재, 생선구이 73행 중 61행이 100ml(산출) | mL 적용 규칙·이유, g↔mL 환산, 02 API 문자열 형식 | 생선구이 100ml는 정상 원본 값. 단위 보존 + 환산 금지 + 판정 불가 처리 유지 |
| 4 STOMP·Railway | simple broker heartbeat는 TaskScheduler 필요, 미설정 기본 0,0, STOMP 누락=0,0, Railway WS는 유휴·기간 제한 면제, 재배포 시 SIGTERM 후 기본 0초 SIGKILL, 재연결은 클라이언트 몫(stompjs 기본 5초) | 재배포 시 WS 처리 명시, 엣지 ping 여부, 프런트 실제 설정 | 유휴 끊김은 Railway 한도가 아님. 반쯤 열린 연결 감지는 서버 heartbeat 설정 필요, 재배포 끊김 대비는 클라이언트 재연결·onConnect 재구독으로 검증 |
