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
```

실제 OpenAI API key와 외부 네트워크는 필요하지 않다.

## 후속 통합 조건

Claude 담당 Kor2·Kakao·음식 API stub과 병합한 뒤 한 개의 Travel 전체 경로 fixture를
맞춘다. 최종 AI 응답의 `candidateId`, contentId, 장소명과 좌표는 외부 stub 후보와
동일해야 한다. 공통 load-test profile과 build 설정은 병렬 작업 중 수정하지 않는다.

동시 부하 기준선에서는 순서를 소비하는 fixture 대신 요청 본문의 Tool 결과 유무로
응답하는 상태 비저장 성공 시나리오를 사용한다. 현재 queue 방식은 retry 순서를
검증하는 focused test 전용이다.

## 근거

- [Spring AI OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat#chat-completions)
