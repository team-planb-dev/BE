package com.planb.ai.client;

import com.planb.ai.context.AiCallBudget;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * 모델 요청 전과 Tool 실행 전의 요청 전체 호출 제한
 */
public final class AiCallBudgetAdvisor implements CallAdvisor {

    public static final String CONTEXT_KEY = "planb.ai.callBudget";

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {

        AiCallBudget budget = (AiCallBudget) request.context().get(CONTEXT_KEY);
        budget.reserveModel();
        ChatClientResponse response = chain.nextCall(request);

        if (response.chatResponse() != null) {
            response
                    .chatResponse()
                    .getResults()
                    .stream()
                    .map(result -> result.getOutput().getToolCalls())
                    .filter(calls -> !calls.isEmpty())
                    .findFirst()
                    .ifPresent(calls -> budget.reserveTools(calls.size()));
        }

        return response;
    }

    @Override
    public String getName() {

        return "planb-ai-call-budget";
    }

    @Override
    public int getOrder() {

        return ToolCallingAdvisor.DEFAULT_ORDER + 1;
    }
}
