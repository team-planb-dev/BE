package com.planb.unit.global.ai.mcp;

import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class NutritionEvaluationCollectorTest {

    private final NutritionEvaluationCollector collector =
            new NutritionEvaluationCollector();

    private NutritionEvaluationResult result(String label) {

        return new NutritionEvaluationResult(
                null,
                NutritionEvaluationStatus.AVAILABLE,
                List.of(),
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("기록 순서대로 누적")
    void recordAccumulatesInOrder() {

        collector.start();

        collector.record("돼지국밥", result("A"));
        collector.record("밀면", result("B"));

        List<NutritionEvaluationCollector.FoodNutritionEvaluation> collected =
                collector.finish();

        assertEquals(2, collected.size());
        assertEquals("돼지국밥", collected.get(0).foodName());
        assertEquals("밀면", collected.get(1).foodName());
    }

    @Test
    @DisplayName("start 재호출 시 이전 기록 초기화")
    void startResetsPreviouslyAccumulatedEvaluations() {

        collector.start();
        collector.record("돼지국밥", result("A"));

        // finish 없이 start 재호출 시 이전 기록 폐기
        collector.start();
        collector.record("밀면", result("B"));

        List<NutritionEvaluationCollector.FoodNutritionEvaluation> collected =
                collector.finish();

        assertEquals(1, collected.size());
        assertEquals("밀면", collected.get(0).foodName());
    }

    @Test
    @DisplayName("finish 이후 재호출 시 빈 리스트 반환")
    void finishClearsStateSoNextFinishReturnsEmpty() {

        collector.start();
        collector.record("돼지국밥", result("A"));
        collector.finish();

        // start 없이 finish 재호출 시 ThreadLocal 정리 및 빈 리스트 반환
        List<NutritionEvaluationCollector.FoodNutritionEvaluation> secondFinish =
                collector.finish();

        assertTrue(secondFinish.isEmpty());
    }

    @Test
    @DisplayName("start 없이도 지연 초기화로 기록 가능")
    void recordWithoutStartStillAccumulatesViaLazyInit() {

        // start 미호출 시에도 ThreadLocal.withInitial 덕분에 즉시 기록 가능
        collector.record("돼지국밥", result("A"));

        List<NutritionEvaluationCollector.FoodNutritionEvaluation> collected =
                collector.finish();

        assertEquals(1, collected.size());
    }

    @Test
    @DisplayName("스레드별 기록 격리")
    void evaluationsAreIsolatedPerThread() throws InterruptedException {

        collector.start();
        collector.record("돼지국밥", result("main-thread"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Integer> otherThreadCollectedSize = new AtomicReference<>();

        Thread other = new Thread(() -> {
            // start 미호출 별도 스레드에서는 메인 스레드 기록 비노출 검증
            List<NutritionEvaluationCollector.FoodNutritionEvaluation> collected =
                    collector.finish();
            otherThreadCollectedSize.set(collected.size());
            latch.countDown();
        });

        other.start();
        latch.await();

        assertEquals(0, otherThreadCollectedSize.get());

        // 메인 스레드 기록 유지
        List<NutritionEvaluationCollector.FoodNutritionEvaluation> mainCollected =
                collector.finish();

        assertEquals(1, mainCollected.size());
    }
}
