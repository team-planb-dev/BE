package com.planb.ai.prompt;

/**
 * OpenAI 호출에 전달할 실행 지시와 입력
 */
public interface AiPrompt {

    default com.planb.ai.context.AiCallBudget callBudget() {

        return null;
    }

    String system();

    String user();
}
