# P2 후속 결산 — 영양 누락·정책·운영 검증

작성일: 2026-10-16
Issue: #205 · 기준 dev `8787db6637e74865bc17111cb4424bca5aa55db9`
배포 main: `a6271bf8bce26436b250b426f50fa30bd61a7601` · 이번 범위에서 미변경
범위: 사용자 지정 후속 ①~⑤. DAG 설계·PoC와 main sync 제외

## 1. 영양 누락 재대조

불변 #199 생성 C1~C6 각 2회, 식사 슬롯 72개에서 수치 3개 존재 42개·수치 3개 모두 null 30개 재현. 요청별 로그인 경계 8+4개와 JSONL 8+4행의 순서·개수 대조, null 메뉴 다중집합과 최종 조회 실패 로그 다중집합 일치 확인. 분모는 메뉴명 종류가 아닌 반환 식사 슬롯.

| null 슬롯의 관측 경로 | 건수 | 증명 범위 |
|---|---:|---|
| 같은 요청에서 최종 메뉴명 영양 Tool 호출 기록 | 24 | 호출 시도 관측. 정상 수치 획득을 뜻하지 않는 기록 |
| 같은 요청에서 최종 메뉴명 Tool 호출 미관측 | 6 | Java 후처리 조회가 존재하므로 Tool 생략만으로 누락 원인 확정 불가 |
| 합계 | 30 | 모든 슬롯에서 최종 조회 실패 기록과 일치 |

### 1.1 원인 후보와 판정

- H1 공급자 빈 응답: filtered items가 빈 목록이면 `UNAVAILABLE`. 과거 raw items가 없어 확정 불가
- H2 분류·이름 필터: `dbGroupName=음식`과 정확명 또는 유일한 reference명 조건 미달 시 빈 목록. 과거 원본 행 없어 확정 불가
- H3 빈 결과 캐시: 정제된 빈 목록도 하루 캐시. request별 hit/miss 합계만 있고 key별 결과 미보존, H1/H2와 분리 불가
- H4 표준명 부재·표준명 조회 실패: 최종 메뉴가 보정·재선택 뒤 바뀔 수 있고 fallback에도 빈 목록 가능. Tool 입력의 표준명 존재와 Java 후보 상태는 별도
- H5 timeout·게이트웨이: 해당 request 로그에서 NutritionService WARN 0건. `onErrorResume` 경고 경로의 미관측이지 공급자 실패의 절대 부재 증거가 아닌 상태

`NutritionService.evaluated`는 비어 있지 않은 정제 결과를 수치가 모두 파싱 불가여도 `NOT_EVALUABLE`로 기록하고 `[NUTRITION LOOKUP RESULT]` 로그를 남김. 이번 30개는 최종 매칭 결과가 없다는 로그와 일치. 따라서 수치 파싱 결함으로 단정할 근거 없음. 메뉴 단위 collector·cache 원본과 조회 시작 trace가 없어 하위 원인은 UNKNOWN 유지.

### 1.2 재현과 보존

```bash
python3 docs/ai/evidence/p2-quality-closeout-20261016/analyse_nutrition.py --self-test
python3 docs/ai/evidence/p2-quality-closeout-20261016/analyse_nutrition.py \
  --root /private/tmp/planb-199-evidence/live-20261015 \
  --output /private/tmp/planb-p2-quality-closeout-evidence
```

분석기 self-check 5개 통과. 공개 summary는 메뉴·건강값·계정 없이 case/repeat·집계·원본 상대경로/hash만 보존. 메뉴 단위 private JSON과 원본 로그는 Git 제외, 지정 보관 디렉터리 사본에만 보존. 새 현재 API 응답은 당시 원인을 증명하지 않는 별도 표본. null30개 중 불고기 문자열 메뉴12개는 후속 표준명 후보 조사 대상이지만 임의 동등명 승인은 제외.

## 2. 정책 판정 — 위임 권장안

사용자의 권장 답변 위임과 이번 ①~⑤ 진행 지시에 따른 판정. 보류는 UNDECIDED와 다르게 이번 범위에서 적용하지 않는 명시적 결정이며 재개 조건을 함께 보존.

| 항목 | 이번 결정 | 이유·재개 조건 |
|---|---|---|
| TourAPI 일일 22 + HTTP429 | 1초 뒤 재시도 제외 | 일일 한도에 단기 재호출의 회복 근거 부재. 공유 client에서 XML 판별기 재사용 |
| 23 전용 재시도·새 backoff | 도입 보류 | 계정 초당 수치·복구시간·반복 관측 부재. 기존 HTTP429의 최대1회만 유지, 비429 XML23 무재시도 유지 |
| 22 사용자 안내·새 오류 코드 | 기존 공개 오류 계약 유지 | 별도 API/프런트 계약 없이 새 문구·자동 자정 재실행 미도입. 필요 시 공급자 typed 오류→공개 응답 연결 검증 |
| STOMP heartbeat | 현행0,0 유지·도입 보류 | 클라이언트 협상/백그라운드 탭/무수신 종료와 재연결 확인 후 설정. 서버 설정만으로 결과 복구 보장 불가 |
| R2·R4 재선택 입력·F1 후보 복사 | 추가 구현 보류 | #199 실제 메뉴 편집4/4과 결정적 guard 외 새 실패 인과 미확보. 요청별 원본/상태·완료 표시 보존은 후속 F1 계약 |
| 선조회 후보 전체 허용 | 현재 검증된 후보 범위 유지 | 같은 음식점/다른 메뉴 guard 유지. 이름만으로 후보 축소 시 미검색/다른 후보 회귀 가능, 저장 identity 근거 확보 후 재검토 |
| guide 알레르기 선필터 A2 | 도입 보류 | 최종 등록명/승인 분류어 guard 유지. 성분 정보 없는 이름 사전 확대와 후보 누락/오탐을 혼합하지 않는 범위 |
| P2-5·identity 저장·원거리 선택 | 기존 보류 유지 | 증명된 원인과 새 구조·정책 효과를 분리. 강제 장소 삭제·이동시간 축소·영양 수치 생성 제외 |

공식 사실·정책 추론 구분: [공식 근거 조사](p2-pre-dag-closeout-official-research.md). 1초는 기존 계약값이며 공급자가 보장한 복구시간이 아닌 값.

### 2.1 검증

`Kor2ServiceTestClient.doesNotRetryDailyQuotaEvenOnTooManyRequests`: 수정 전 실제 client 호출2회로 실패(RED), 공유 재시도 필터에서 식별된22 제외 후1회(GREEN). 23+429는 성공까지2회, 알 수 없는429 최대2회, 기존 XML/JSON 정상·오류 기록 유지. targeted 영양19·편집33·Kor2 11건 전부 통과. 추가 네트워크 호출 없는 로컬 WebClient exchange seam.

## 3. 최종 보고서 통합

#197 결산을 역사적 기록으로 유지하고 #199의 편집 수명·요청 예산·국소 복구·영양 출처, #201 공급자 오류 보존, #202 Timer 수정 결과를 최신 상태에 반영. 서로 다른 실행의 회귀1111/1116/1112건은 당시 증거이며 감소 원인 추정이나 누적 합산 제외. 이번 최종 실행 건수는 별도 검증 절에 기록.

수치 비교는 #199 동일 case/repeat 매칭12건에 한정: 생성11/12→12/12, 필수 식사42/44→48/48, 복약42/42→44/44, 영양36/66→42/72. 다른 날짜·모델·공급자·캐시·성공 슬롯 수가 달라 인과/통계적 개선율 미산출. 약물 규칙은 동일 drug/rule 중복 제거 기준이며 임상 복약 적합성 평가와 별도. REST 대상 밖60/60과 STOMP15/15는 분모가 다른 별도 표본.

## 4. 미채택 실험 정리

2026-10-16 PR [#174](https://github.com/team-planb-dev/BE/pull/174)·[#175](https://github.com/team-planb-dev/BE/pull/175) 종료. `state=closed`, `merged=false`, 원격 head branch·SHA 보존 확인. 제목/실험 코드/과거 결과를 성공으로 변경하거나 브랜치 삭제하지 않은 처리. #172의9/12·#173의11/12와 품질/당시 채택 gate 미달을 종료 comment에 기록.

후속 DAG/Java 동일 계약 비교는 현재 채택 baseline으로 별도 실험. 과거 draft를 그대로 재개해 구조 효과와 당시 동작 변경을 섞지 않는 조건.

## 5. 운영 검증

### 5.1 로컬 실제 STOMP 연결 종료·재연결

`ChatAiEditPlanIntegrationTest.reconnectRetrievesSamePreviewAndResubscribes` 추가·통과. 실제 random-port WebSocket/STOMP+Testcontainers MySQL/Redis 사용, AI/지도/영양은 고정 응답. 첫 preview 수신→기존 세션 disconnect 및 disconnected 확인→다른 sessionId로 CONNECT→같은 방 재구독·ENTER 수신→소유자 인증 GET의 같은editRequestId/processable 확인→다른 사용자의GET403 확인.

재연결 서버 계약은 무료 통합 증거. GET은 MockMvc 경로이며 실제 브라우저/운영 프록시·소켓 강제 단절·AI 진행 중 단절·네트워크 자동재연결을 검증한 결과는 아님. 서버heartbeat 설정 미변경.

### 5.2 실제 프런트 코드 대조

`team-planb-dev/FE`의 dev `98f7965ec60e87bca959cc7c59d3c60848213def` 고정 읽기 확인. main `0b833e89e998e30668b941b1d619b382f5f61f84`는22개 항목 초기 scaffold로 실제 배포 기준 UNKNOWN.

| 파일 | 관측 | 영향 |
|---|---|---|
| `src/api/stomp.ts` | `reconnectDelay:0`, `onConnect` 내subscribe, heartbeat override 없음, package 선언 `@stomp/stompjs:^7.3.0` | 자동 재연결 비활성. 재연결되면 재구독할 구조이나 실제 실행은 별도 |
| `src/pages/Trip/useEditChat.ts` | `onReady`는ready 상태만 설정, CONFIRM/CANCEL은type만 전송 | 검토 경로에 previewGET 복구와editRequestId 전송 없음. legacy 최신요청 호환이 선택한preview 토큰 보장은 아님 |
| `src/api/chat.ts` | 채팅방 조회/생성만 노출 | 이 파일에 previewGET wrapper 없음. 전체 프런트 모든 경로 부재로 확대하지 않는 범위 |

따라서 브라우저 복구를 검증 완료 처리하지 않는 판정. 프런트 수정은 이번 backend 범위 밖. 재개 조건: 실제 배포commit 확인→재연결/재구독/소유자GET→보인토큰 confirm/cancel→강제단절/백그라운드 탭 시나리오. 보관 snapshot은 commit/blob SHA와 함께 비밀값 없는4개 파일, 프런트 작업트리 변경0.

### 5.3 식약처 현재 원본 조회 — 최대6건, 실제6건

2026-10-16 KST13:55 기준,02 endpoint에 앱과 같은 `품목대표`·첫페이지100행·음식명 검색으로 순차6회. 자동재시도0, 모든 응답HTTP200/resultCode00. 운영계정 등급은 응답으로 판정하지 않으며 실제 계정별 quota 수치는 미확인.

| 조회 | 원본 행/음식 분류 행 | 현 필터 | 현재 원본에 따른 판정 |
|---|---:|---:|---|
| 불고기 | 39/30 | 0 | 소불고기 등 부분 검색결과는 있으나 음식명/참조명 정확일치 없음. DB에 불고기 관련값이 없다는 주장은 잘못된 해석 |
| 돼지국밥 | 0/0 | 0 | 이번 파라미터의 조회결과0. 식약처 전체 DB에 영구 부재라는 뜻 아님 |
| 공깃밥 | 0/0 | 0 | 이번 파라미터의 조회결과0. ‘쌀밥’으로 임의 대체하지 않은 결과 |
| 갈치구이 | 10/10 | 3 | 정확명 우선 후보의 기준량100mL. 실제 원본 문자열 확인 |
| 고등어구이 | 13/12 | 3 | 정확명 우선 후보의 기준량100mL. 실제 원본 문자열 확인 |
| 비빔밥 | 33/33 | 3 | 첫 정확명 후보100g, 세 후보 중100mL도 존재 |

필터3은 정확일치 반환 상한3개. 같은이름 다중 항목의 첫 항목 선택은 데이터 기준값 선택이지 식당실제 레시피 정답 보장 아님. 느슨한 부분일치로 불고기버거·덮밥 등의 수치를 불고기에 주입하거나100mL→100g 변환은 미실시.

원본 응답6개는 key 마스킹 후private 보관, 파생 집계만 Git. 공급자 원본에 현재 필터 조건을 Python으로 재생한 파생 집계이며 Java NutritionService end-to-end/실제 모델 평가와 별도. 현재 필터/빈응답 원인을 확인한6건을 과거 null30개 각각의 원인 확정으로 소급하지 않는 조건.

첫Python 시도는 기본CA 누락(`SSLCertVerificationError`, verifyCode20)으로 HTTP 전 실패. 원본 실패도 보존하고 macOS `/etc/ssl/cert.pem`으로 인증서 검증을 유지한 뒤 별도 경로에서6회 진행. 인증서 검증 해제·유료 배치 자동재실행 없음. 공개호스트 TLS 진단1회는 영양검색0건.

### 5.4 외부 조건이 필요한 잔여

- 실제 프런트 배포URL·commit·로그인 세션 없으므로 운영 브라우저/프록시/강제단절 미검증. URL 확인 요청 후 독립 작업 계속
- Kakao 과거2026-10-12 콘솔 이력·데브톡 접근 없음, 원인UNKNOWN. 승인 없는 외부 문의·메시지 전송0
- 운영계정 전환은 사용자 확인으로 기록, 계정별 실제 할당량은 인증된 포털 화면/자료 필요
- 기존 Prometheus/Grafana 컨테이너 유지. 이번은 품질·연결 계약 검증으로 새 부하/Prometheus import/GrafanaURL 생성 없음

## 6. 검토·검증·반영 기록

- 독립 검토1차9.2/10, Critical0·Important0·Minor3. 비빔밥 후보의100mL 혼재·FE package범위·429설명 보완
- 전체 무료 회귀 1,120건(145클래스), 실패·오류·skip0. `clean test` 4분49초 성공. 신규3건 포함. #201의1116·#202의1112는 각 브랜치 당시 실행이며 최종 통합 건수와 별도
- 독립 검토2차9.6/10, Critical0·Important0·Minor1. 보고서의 운영재연결 미평가를 로컬native 재연결 통과와 분리해 보완
- exact-head CI·dev publication 상태는 연결된 [Issue #205](https://github.com/team-planb-dev/BE/issues/205)·PR checks와 보관 publication.json 기준. main sync·DAG 구현 제외
