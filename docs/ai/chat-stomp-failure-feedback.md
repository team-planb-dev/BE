# Spring STOMP 일정 편집 실패 피드백

조사일: 2026-09-20

## 공식 계약

Spring은 `@MessageMapping` 처리 중 발생한 예외를
`@MessageExceptionHandler`에서 처리할 수 있도록 지원한다. 여러 Controller에
공통 적용하려면 `@ControllerAdvice`를 사용할 수 있다.
([Spring Framework — Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-annotations.html#websocket-stomp-message-exception-handler),
[`MessageExceptionHandler` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/messaging/handler/annotation/MessageExceptionHandler.html))

`@SendToUser`와 `convertAndSendToUser`는 특정 사용자 목적지로 보내는 별도 계약이다.
`broadcast = false`를 사용하면 요청을 보낸 세션으로 범위를 더 좁힐 수 있다.
반대로 `SimpMessagingTemplate.convertAndSend`는 지정한 브로커 목적지의 구독자에게
메시지를 보낸다.
([Spring Framework — User Destinations](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html),
[`SimpMessagingTemplate` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/messaging/simp/SimpMessagingTemplate.html))

STOMP `ERROR` 프레임은 일반 채팅 응답과 다르다. Spring의
`SubProtocolErrorHandler` 계약은 `ERROR` 프레임 전송 후 연결 종료를 전제로 한다.
기본 `StompSubProtocolHandler`는 별도 오류 처리기가 없으면 예외 메시지를
`ERROR` 프레임의 `message` 헤더에 넣을 수 있으므로 내부 예외를 그대로 맡기면
상세 정보가 노출될 수 있다.
([`SubProtocolErrorHandler` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/socket/messaging/SubProtocolErrorHandler.html),
[`StompSubProtocolHandler` source](https://github.com/spring-projects/spring-framework/blob/main/spring-websocket/src/main/java/org/springframework/web/socket/messaging/StompSubProtocolHandler.java))

## 변경 전 문제

PlanB는 `/sub/api/v1/chat/{roomId}`를 구독하는 방 구성원에게 AI 채팅 응답을
저장하고 발행한다. `ChatController`도 편집 예외를 잡아 동일 경로로 실패 안내를
발행하고 있었으므로 전송 누락이나 STOMP 연결 종료가 문제는 아니었다.

문제는 `ChatController`가 받은 예외를 `ChatFacade.publishEditFailedReply(roomId)`에
전달하지 않아 실패 분류가 사라진다는 점이었다. 이후
`ChatAiReplyMessageHelper.makeEditFailedMessage()`가 모든 실패를 같은 문구로
바꿨다.

## 적용한 계약

- 기존 방 구독 주소와 `SendChatMessageResponse`를 유지한다.
- 예외 객체는 `Controller → Facade → Service → Helper`로 전달한다.
- `AiOrchestrationException`은 기존 `AiFailure → AiExceptionEnum` 분류를 재사용한다.
- 일정 검증 실패는 안전한 거절 안내로, 수정 결과 만료는 재요청 안내로 변환한다.
- 알 수 없는 예외는 기존 공통 문구를 유지한다.
- `BaseException.getMessage()`, 원인 예외, 스택, 외부 응답 본문은 사용자 응답에
  포함하지 않는다.

`@MessageExceptionHandler`는 여러 메시지 Controller가 같은 오류 정책을 공유하게
될 때 검토할 수 있다. 현재는 단일 Controller의 기존 저장·방송 흐름을 교체하지
않는 것이 변경 범위와 회귀 위험이 더 작다.
