package com.planb.ai.prompt;

/**
 * OpenAI 호출에 전달할 실행 지시와 입력.
 *
 * Prompt는 사용자 의도 해석, Tool 사용과 구조화 응답 형식을 안내한다.
 * 식사·관광지 개수·장소 유형·태그·시간 같은 최종 일정 규칙은 Java policy와
 * validation이 결정하며, Prompt 문구만으로 결과를 승인하지 않는다.
 */
public interface AiPrompt {

    String system();

    String user();
}
