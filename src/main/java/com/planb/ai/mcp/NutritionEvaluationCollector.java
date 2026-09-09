package com.planb.ai.mcp;

import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// evaluateFoodNutrition Tool 호출 결과를 요청 단위로 수집
// TourismTool은 싱글턴으로 ThreadLocal로 요청별 격리 (동기 호출 흐름 전제)
@Component
public class NutritionEvaluationCollector {

    private final ThreadLocal<List<FoodNutritionEvaluation>> evaluations =
            ThreadLocal.withInitial(ArrayList::new);

    // 음식명 기준 영양평가 결과 하나
    public record FoodNutritionEvaluation(
            String foodName,
            NutritionEvaluationResult result
    ) {
    }

    // 수집 시작 (AI 일정 생성 호출 직전)
    public void start() {
        evaluations.set(new ArrayList<>());
    }

    // Tool 호출 결과 기록
    public void record(String foodName, NutritionEvaluationResult result) {
        evaluations.get().add(
                new FoodNutritionEvaluation(foodName, result)
        );
    }

    // 수집 종료, 누적된 결과 반환 후 ThreadLocal 정리
    public List<FoodNutritionEvaluation> finish() {

        List<FoodNutritionEvaluation> collected = evaluations.get();
        evaluations.remove();

        return collected;
    }
}
