# P2-6B 장시간 STOMP 성공 미리보기 전달 — 결과

작성일: 2026-10-13 (Claude 후속 4단계)
작업 트리: `/private/tmp/planb-p24d-time-contract` (브랜치 `codex/p2-4d-time-contract-recovery`, HEAD `096616e`, 미커밋)
증거: `evidence/p2-stage3-7-claude-20261013/stage4/`, 임시 진단 `evidence/p2-stage3-7-claude-20261013/stage4-char-limit-probe.xml`
상태: 원인 재현·하네스 수정·서버 관측 보강·무료 검증·독립 리뷰 통과·실제 STOMP 재측정 통과(2026-10-13). 브라우저 수신 미측정

## 1. 결론

- 실측 STOMP 편집의 약 122초 연결 종료는 측정 하네스의 Java 테스트 클라이언트(Tomcat `WsWebSocketContainer`)가 8,192**자**를 넘는 미리보기 TALK 프레임을 받자 연결을 닫은 것으로 판단. 재현과 서버 종료 코드로 강하게 뒷받침되나, 실측 당시 종료 코드는 기록되지 않아 실측 1009는 추정(다음 실측에서 확인)
- 122초 경과 자체는 이 하네스 경로(직접 연결, heartbeat `[0,0]`)에서 원인이 아님. AI 응답 발행 시점에 종료
- 이전 문서(`p2-4d-time-contract-result.md` §13-6)의 "21KB 정상 수신 → 8KB 클라이언트 버퍼 가설 반증"은 오류. 한도는 바이트가 아닌 문자 수이고, 그 시험 메시지는 한글 7,000자로 한도 아래였음
- 서버 전달 경로·STOMP schema·실행기는 변경하지 않음. 수정은 테스트 하네스의 클라이언트 한도와 서버 관측 로그

## 2. 근거

| 근거 | 내용 |
|---|---|
| Tomcat 11.0.24 소스 | `WsFrameBase`가 `CharBuffer.allocate(maxTextMessageBufferSize)`(기본 8192자)로 텍스트를 담고, 부분 메시지를 지원하지 않는 처리기면 `CloseCodes.TOO_BIG`(1009)로 종료. Spring STOMP 클라이언트 처리기는 부분 메시지 미지원 |
| 임시 진단(실행 후 소스 제거) | ASCII 8,000자·9,000자 종료. 실측 REST 미리보기 원문(18,396자·20,210바이트)은 기본 클라이언트에서 종료, 텍스트 버퍼·STOMP 수신 한도를 키운 클라이언트에서 정상 수신·연결 유지 |
| 서버 종료 코드(추가한 로그) | 기본 클라이언트 초과 사례 `STOMP DISCONNECT ... closeCode: 1009, closeReason: The decoded text message was too big for the output buffer and the endpoint does not support partial messages` |
| 실제 TALK 경로 RED→GREEN | `ChatAiEditPlanIntegrationTest.talkDeliversPreviewLargerThanDefaultClientTextBuffer`: 약 21K자 미리보기가 기존 하네스에서 LEAVE·미수신(RED), 하네스 수정 후 수신·연결 유지(GREEN) |
| 실측 정황 | 마지막 서버 로그(영양 조회) 39ms 뒤 클라이언트 `ConnectionLostException`과 서버 LEAVE가 동시. 발행 직전 구간과 일치 |

## 3. 변경

| 위치 | 변경 |
|---|---|
| `StompTestClientHelper.createStompClient`(테스트) | 텍스트 버퍼 1MB, STOMP 수신 한도 1MB. 브라우저 수준 대형 메시지 수신 허용이며 브라우저 수신을 측정한 것은 아님 |
| `StompOutboundChannelInterceptor`(신규) | clientOutboundChannel 실제 전송 스레드(`beforeHandle`)에서 7,680바이트(8192 − 헤더 여유 512) 초과 본문만 `STOMP OUTBOUND LARGE`(바이트·문자 수·전송 스레드·가상 여부) INFO, 나머지는 DEBUG 바이트만. 본문 내용 미기록 |
| `ChatSessionEventListener` | `STOMP DISCONNECT` 종료 코드·사유 기록 |
| `StompChannelInterceptor` | SEND의 컨트롤러 처리 작업 스레드·가상 여부(`STOMP HANDLE`, SEND당 1회), CONNECT의 클라이언트 heartbeat 기록 |
| `ChatIntegrationTest.receivesPublishedMessagesBySize` | 기본 Java 클라이언트 특성으로 재작성: 한글 7,000자(약 21KB) 수신, ASCII 9,000자(약 9KB) 종료. 이전 25,000자(64KB STOMP 한도) 확인은 제거 |

`STOMP OUTBOUND LARGE`는 바이트 기준 후보 표시이며 한도 초과 판정이 아님. 판정은 `payloadChars`에 헤더 약 200자를 더해 8192와 비교. 예: 한글 21,141바이트는 7,137자로 수신 가능.

## 4. 가상 스레드 적용 여부

임시 진단(컨텍스트 `spring.threads.virtual.enabled=true`, XML `stage4/virtual-thread-executor-probe.xml`) 결과, Boot 4.0.2 `SpringBootWebSocketMessageBrokerConfiguration` 조건은 충족(`jacksonJsonMapper`)했으나 `AsyncTaskExecutor` 빈이 `clientInboundChannelExecutor, clientOutboundChannelExecutor, brokerChannelExecutor, messageBrokerTaskScheduler` 4개뿐이고 `applicationTaskExecutor`가 없음. 실행기 결정 결과 null로 두 클라이언트 채널은 Framework 기본 `ThreadPoolTaskExecutor`(플랫폼 스레드). 이 앱에서 해당 속성은 STOMP 처리에 효과가 없음. 실측 스레드 이름 `clientInboundChannel-N`과 일치. 이번 종료의 원인이 아니므로 변경 없음.

## 5. 현재 동작 특성 (정책 질문 대상)

아래는 바람직한 동작으로 확정한 것이 아니라 현재 동작을 고정한 특성 테스트. 정책 결정 후 기대값 반전 대상.

| 테스트 | 현재 동작 | 위험 |
|---|---|---|
| `cancelDuringTalkLeavesLatePreviewCached` | TALK 처리 중 CANCEL을 보내면 취소 응답이 먼저 오고, 늦게 끝난 미리보기가 발행되며 캐시가 다시 생성 | 사용자가 취소했는데 이후 확정 시 늦은 수정안 적용 가능 |
| `slowerEarlierTalkOverwritesLaterPreviewCache` | 느린 이전 TALK 결과가 나중 TALK의 캐시를 덮어씀 | 마지막으로 본 미리보기와 확정 대상 불일치 가능 |
| 재연결 계약(코드 확인) | 연결이 끊긴 뒤 남은 수정안을 다시 받을 API·재전송 없음, 캐시는 유지 | 보지 못한 수정안이 확정될 여지 |
| 방 권한 | 기존 `StompChannelInterceptorTest`가 비멤버 SUBSCRIBE·SEND 거부 확인 | 신규 위험 없음 |

## 6. 범위와 남은 위험

- 실측 당시 종료 코드 1009는 추정. 새 로그로 7단계 실측에서 확인
- "경과 시간 무관"은 하네스 경로 한정. heartbeat `[0,0]`(simple broker heartbeat 미설정)에서 운영 프런트가 ALB·nginx 등 프록시(기본 60초 유휴 종료) 뒤에서 무응답 122초 TALK를 기다리면 끊길 수 있으며 이번에 배제하지 않음. 필요 시 이미 존재하는 `messageBrokerTaskScheduler`로 서버 heartbeat 설정 가능(이번 범위 밖)
- Jakarta/Tomcat 기본값을 쓰는 Java·Android 클라이언트도 같은 방식으로 실패. 실제 프런트(브라우저) 수신 확인 필요
- 가상 스레드를 STOMP에 쓰려면 명시적 `registration.executor(...)` 필요(이번 범위 밖)
- 동시 TALK 처리, 취소 후 늦은 결과, 재연결 시 결과 확인은 정책 결정 필요

## 7. 검증

| 항목 | 결과 |
|---|---|
| RED→GREEN | 실측 크기 미리보기 STOMP 수신 1건 |
| 특성 | 기본 클라이언트 문자 수 한도 1건, 경합 2건 |
| 전체 무료 회귀 | 1050건 실패 0 |
| 독립 review-loop | 7 → **9/10**, 최종 Critical·Important 0 |

## 8. 실제 STOMP 재측정 (2026-10-13, 7단계)

증거 `evidence/p2-stage7-live-20261013T062122Z/stomp-edit/`. 요청 "2일차 관광지를 전부 다른 곳으로 바꿔줘"(REST 메뉴 편집 실패로 대형 미리보기 검증을 위해 변경, 승인 1건 범위).

| 항목 | 결과 |
|---|---|
| 처리 | 28.1초, LLM 3회, SEND 처리 `clientInboundChannel-7`(virtual false) |
| 발행 | `STOMP OUTBOUND LARGE` payloadBytes 19,699·payloadChars 17,965(8192자 초과), 전송 `clientOutboundChannel-6` |
| 수신 | 미리보기 수신, CANCEL 응답 수신, 연결 유지, 종료 시 closeCode 1000 |
| 데이터 | 원본 보존, 캐시 정리, 범위 대상 밖 11/11 유지 |

- 대형 미리보기의 실제 전달이 수정한 하네스 클라이언트에서 확인됨. 이번 처리 시간은 28초로 122초 장시간 조건은 재현하지 않음(경과 시간 비원인 판단은 §1·§6 범위 유지)
- 실측 당시 종료 코드 기록은 이번 정상 종료(1000)만 확인. 과거 122초 사례의 1009는 여전히 재현 기반 판단

## 후속4 운영 검증 범위 갱신 (2026-10-15, #201)

- Railway 공식 문서: WebSocket은 60초 유휴·5/15분 기간 제한 면제. 122초 무응답 TALK가 Railway 엣지에서 끊길 근거 없음(실측 아님). 다른 프록시 경로는 미평가
- 서버 heartbeat 미설정 유지(simple broker는 TaskScheduler 없으면 0,0). 반쯤 열린 연결 감지 불가. 도입은 운영 결정
- 재배포 시 이전 배포 SIGTERM 후 기본 0초 SIGKILL로 연결 종료 추정. 재연결은 클라이언트 책임이며 #199의 인증 GET 재조회가 재연결 후 미리보기 복구 경로. 실제 disconnect/reconnect·브라우저·프런트 설정은 미검증
- 상세와 근거: `p2-followup-4-provider-ops-result.md` §7, `p2-followup-4-official-research.md` §4
