package com.planb.integration.domain.travel;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 모델 호출 1건마다 시간·token·tool 요청 여부를 기록하는 실험 전용 handler
 *
 * Spring AI 2.0.0의 최종 응답 usage는 마지막 왕복분만 담으므로
 * tool loop 전체의 호출별 분해는 observation 단위로 수집
 */
public class LlmCallRecorder implements ObservationHandler<ChatModelObservationContext> {

    private static final String STARTED_AT = LlmCallRecorder.class.getName() + ".startedAt";

    public record LlmCall(
            long durationMs,
            Integer inputTokens,
            Integer outputTokens,
            Long cacheReadTokens,
            boolean hasToolCalls
    ) {
    }

    private final List<LlmCall> calls = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onStart(ChatModelObservationContext context) {

        context.put(
                STARTED_AT,
                System.nanoTime()
        );
    }

    @Override
    public void onStop(ChatModelObservationContext context) {

        Long startedAt = context.get(STARTED_AT);

        long durationMs = startedAt == null
                ? -1
                : (System.nanoTime() - startedAt) / 1_000_000;

        ChatResponse response = context.getResponse();

        Usage usage = response == null || response.getMetadata() == null
                ? null
                : response
                        .getMetadata()
                        .getUsage();

        calls.add(new LlmCall(
                durationMs,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getCacheReadInputTokens(),
                response != null && response.hasToolCalls()
        ));
    }

    @Override
    public boolean supportsContext(Observation.Context context) {

        return context instanceof ChatModelObservationContext;
    }

    // 실행 시작 전 초기화
    public void reset() {

        calls.clear();
    }

    // 기록된 호출 목록 복사본
    public List<LlmCall> calls() {

        synchronized (calls) {
            return List.copyOf(calls);
        }
    }
}
