# Phase 2 OpenAI Stub

## 목적

실제 OpenAI 호출 없이 다음 운영 경로를 결정적으로 검증한다.

```text
OpenAiClient
  -> ChatClient
  -> ToolCallingAdvisor
  -> OpenAiChatModel
  -> OpenAI 호환 HTTP stub
```

기존 `ChatClient` mock 테스트와 달리 strict structured output 요청 직렬화와
Spring AI의 Tool 호출 왕복을 포함한다.

## 선택한 seam

Spring AI가 지원하는 OpenAI `baseUrl`을 JDK `HttpServer`로 연 로컬 주소로 바꾼다.
운영 `OpenAiClient`, Prompt, validation과 retry 로직은 바꾸지 않는다.

테스트 모델 설정은 다음 조건을 사용한다.

- 로컬 `/v1/chat/completions`만 호출
- 고정 모델명과 비밀값이 아닌 API key 사용
- SDK retry 0회
- 2초 timeout

새 HTTP stub 라이브러리나 테스트 전용 운영 interface는 추가하지 않았다.

## 결정적 시나리오

| 시나리오 | 응답 순서 | 검증 |
|---|---|---|
| Tool 호출 성공 | `tool-call.json` → `success.json` | 실제 Tool 1회 실행, 후속 요청의 `tool_call_id`와 결과 |
| 파싱 복구 | `parse-failure.json` → `success.json` | 유효한 ChatCompletion 내부의 잘못된 content 처리와 1회 retry |
| validation 교정 | `validation-failure.json` → `success.json` | 실패 사유와 이전 응답을 포함한 correction 요청 |
| 동일 응답 반복 | `validation-failure.json` 2회 | `RESPONSE_REPEATED_INVALID` 종료 |

모든 fixture는 외부 ChatCompletion envelope를 정상 JSON으로 유지한다. 파싱 실패는
`message.content`만 깨뜨려 OpenAI SDK 역직렬화 실패와 PlanB structured output 실패를
구분한다.

## 실행

```bash
./gradlew test \
  --tests "com.planb.performance.openai.OpenAiProtocolStubTest"

# OpenAI와 외부 API 스텁을 함께 실행
./gradlew travelLoadTestStubs
```

실제 OpenAI API key와 외부 네트워크는 필요하지 않다.

## 부하 테스트 모드

focused retry 테스트는 기존 FIFO fixture를 유지한다. 부하 테스트 모드는 공유 queue를
사용하지 않고 요청 본문에 `tool_call_id`가 있는지로 응답을 고른다.

- 최초 요청: `searchAttractionsByRegion` Tool 호출
- Tool 결과 후 요청: 2일짜리 빈 일정
- Java 후처리: 외부 스텁 후보 중 하루 2개 관광지를 결정적으로 채우고 검증·저장

기본 일정은 `2030-01-01`부터 1박 2일이다. `STUB_PLAN_START_DATE`로 시작일을 바꿀 수
있으며 부하 요청도 같은 날짜를 사용해야 한다. `OPENAI_STUB_PORT` 기본값은 `18081`이다.

`TravelLoadTestSmokeIntegrationTest`가 실제 Spring AI Tool 호출부터 candidate identity,
좌표, 이동시간, Java validation, 저장과 재조회까지 한 경로로 검증한다.

## 근거

- [Spring AI OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat#chat-completions)
