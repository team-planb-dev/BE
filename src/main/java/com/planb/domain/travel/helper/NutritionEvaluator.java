package com.planb.domain.travel.helper;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationDetail;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.dto.nutrition.NutritionInfo;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.entity.constant.NutritionLevel;
import com.planb.domain.travel.entity.constant.NutritionThreshold;
import com.planb.domain.travel.entity.constant.NutritionType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NutritionEvaluator {

    private static final Map<NutritionEvaluationStatus, Integer> STATUS_SEVERITY = Map.of(
            NutritionEvaluationStatus.AVAILABLE, 0,
            NutritionEvaluationStatus.NOT_EVALUABLE, 1,
            NutritionEvaluationStatus.UNAVAILABLE, 2
    );

    // 같은 성분을 두 질환이 서로 다른 기준으로 볼 때 어느 쪽이 더 나쁜지 정한다.
    // 현재 임계값 표에서는 겹치는 성분(식이섬유)의 기준이 같아 실제로 갈리지 않는다.
    private static final Map<NutritionLevel, Integer> LEVEL_SEVERITY = Map.of(
            NutritionLevel.LOW, 0,
            NutritionLevel.CHECK, 1,
            NutritionLevel.HIGH, 2
    );

    /**
     * 관리 질환별 기준으로 한 음식의 영양성분을 평가한다.
     *
     * 질환마다 보는 영양성분이 달라 각각 평가한 뒤 항목을 합친다. 같은 영양성분을
     * 두 질환이 함께 보면 한 번만 남기며, 기준이 갈릴 경우에 대비해 나쁜 쪽을 취한다.
     * 상태도 가장 나쁜 것을 취한다. 한 질환이라도 판단할 수 없으면
     * 그 음식이 적합하다고 말할 수 없기 때문이다.
     */
    public NutritionEvaluationResult evaluate(
            List<DiseaseType> diseaseTypes,
            NutritionInfo nutritionInfo
    ) {

        List<NutritionEvaluationResult> results = diseaseTypes
                .stream()
                .map(diseaseType -> evaluateOne(diseaseType, nutritionInfo))
                .toList();

        return new NutritionEvaluationResult(
                List.copyOf(diseaseTypes),
                worstStatus(results),
                mergeEvaluations(results),
                nutritionInfo.carbohydrate(),
                nutritionInfo.sodium(),
                nutritionInfo.fat()
        );
    }

    private NutritionEvaluationResult evaluateOne(
            DiseaseType diseaseType,
            NutritionInfo nutritionInfo
    ) {

        return switch (diseaseType) {

            case DIABETES ->
                    evaluateDiabetes(nutritionInfo);

            case HIGH_BLOOD_PRESSURE ->
                    evaluateHypertension(nutritionInfo);

            case DYSLIPIDEMIA ->
                    evaluateDyslipidemia(nutritionInfo);
        };
    }

    // 상태는 나쁜 쪽이 이긴다. 열거 순서가 곧 나쁨의 순서다.
    private NutritionEvaluationStatus worstStatus(
            List<NutritionEvaluationResult> results
    ) {

        return results
                .stream()
                .map(NutritionEvaluationResult::status)
                .max(Comparator.comparingInt(STATUS_SEVERITY::get))
                .orElse(NutritionEvaluationStatus.NOT_EVALUABLE);
    }

    // 같은 영양성분이 겹치면 나쁜 평가만 남긴다. 먼저 평가된 질환의 순서를 지킨다.
    private List<NutritionEvaluationDetail> mergeEvaluations(
            List<NutritionEvaluationResult> results
    ) {

        Map<NutritionType, NutritionEvaluationDetail> merged =
                new LinkedHashMap<>();

        results
                .stream()
                .flatMap(result -> result
                        .evaluations()
                        .stream())
                .forEach(detail -> merged.merge(
                        detail.nutritionType(),
                        detail,
                        this::worseDetail));

        return List.copyOf(merged.values());
    }

    private NutritionEvaluationDetail worseDetail(
            NutritionEvaluationDetail left,
            NutritionEvaluationDetail right
    ) {

        return LEVEL_SEVERITY.get(right.nutritionLevel())
                > LEVEL_SEVERITY.get(left.nutritionLevel())
                ? right
                : left;
    }

    // 당뇨병 영양성분 평가
    private NutritionEvaluationResult evaluateDiabetes(
            NutritionInfo nutritionInfo
    ) {

        if (nutritionInfo.carbohydrate() == null
                || nutritionInfo.sugar() == null
                || nutritionInfo.dietaryFiber() == null) {

            return notEvaluable(
                    DiseaseType.DIABETES,
                    nutritionInfo
            );
        }

        return new NutritionEvaluationResult(
                List.of(DiseaseType.DIABETES),
                NutritionEvaluationStatus.AVAILABLE,
                List.of(
                        new NutritionEvaluationDetail(
                                NutritionType.CARBOHYDRATE,
                                evaluateLowerIsBetter(
                                        nutritionInfo.carbohydrate(),
                                        NutritionThreshold.DIABETES_CARBOHYDRATE
                                )
                        ),
                        new NutritionEvaluationDetail(
                                NutritionType.SUGAR,
                                evaluateLowerIsBetter(
                                        nutritionInfo.sugar(),
                                        NutritionThreshold.DIABETES_SUGAR
                                )
                        ),
                        new NutritionEvaluationDetail(
                                NutritionType.DIETARY_FIBER,
                                evaluateHigherIsBetter(
                                        nutritionInfo.dietaryFiber(),
                                        NutritionThreshold.DIABETES_DIETARY_FIBER
                                )
                        )
                ),
                nutritionInfo.carbohydrate(),
                nutritionInfo.sodium(),
                nutritionInfo.fat()
        );
    }

    // 고혈압 영양성분 평가
    private NutritionEvaluationResult evaluateHypertension(
            NutritionInfo nutritionInfo
    ) {

        if (nutritionInfo.sodium() == null) {
            return notEvaluable(
                    DiseaseType.HIGH_BLOOD_PRESSURE,
                    nutritionInfo
            );
        }

        return new NutritionEvaluationResult(
                List.of(DiseaseType.HIGH_BLOOD_PRESSURE),
                NutritionEvaluationStatus.AVAILABLE,
                List.of(
                        new NutritionEvaluationDetail(
                                NutritionType.SODIUM,
                                evaluateLowerIsBetter(
                                        nutritionInfo.sodium(),
                                        NutritionThreshold.HYPERTENSION_SODIUM
                                )
                        )
                ),
                nutritionInfo.carbohydrate(),
                nutritionInfo.sodium(),
                nutritionInfo.fat()
        );
    }

    // 이상지질혈증 영양성분 평가
    private NutritionEvaluationResult evaluateDyslipidemia(
            NutritionInfo nutritionInfo
    ) {

        if (nutritionInfo.saturatedFat() == null
                || nutritionInfo.transFat() == null
                || nutritionInfo.dietaryFiber() == null
                || nutritionInfo.cholesterol() == null) {

            return notEvaluable(
                    DiseaseType.DYSLIPIDEMIA,
                    nutritionInfo
            );
        }

        return new NutritionEvaluationResult(
                List.of(DiseaseType.DYSLIPIDEMIA),
                NutritionEvaluationStatus.AVAILABLE,
                List.of(
                        new NutritionEvaluationDetail(
                                NutritionType.SATURATED_FAT,
                                evaluateLowerIsBetter(
                                        nutritionInfo.saturatedFat(),
                                        NutritionThreshold.DYSLIPIDEMIA_SATURATED_FAT
                                )
                        ),
                        new NutritionEvaluationDetail(
                                NutritionType.TRANS_FAT,
                                evaluateLowerIsBetter(
                                        nutritionInfo.transFat(),
                                        NutritionThreshold.DYSLIPIDEMIA_TRANS_FAT
                                )
                        ),
                        new NutritionEvaluationDetail(
                                NutritionType.DIETARY_FIBER,
                                evaluateHigherIsBetter(
                                        nutritionInfo.dietaryFiber(),
                                        NutritionThreshold.DYSLIPIDEMIA_DIETARY_FIBER
                                )
                        ),
                        new NutritionEvaluationDetail(
                                NutritionType.CHOLESTEROL,
                                evaluateLowerIsBetter(
                                        nutritionInfo.cholesterol(),
                                        NutritionThreshold.DYSLIPIDEMIA_CHOLESTEROL
                                )
                        )
                ),
                nutritionInfo.carbohydrate(),
                nutritionInfo.sodium(),
                nutritionInfo.fat()
        );
    }

    // 영양성분 누락으로 평가할 수 없는 결과 생성
    private NutritionEvaluationResult notEvaluable(
            DiseaseType diseaseType,
            NutritionInfo nutritionInfo
    ) {

        return new NutritionEvaluationResult(
                List.of(diseaseType),
                NutritionEvaluationStatus.NOT_EVALUABLE,
                List.of(),
                nutritionInfo.carbohydrate(),
                nutritionInfo.sodium(),
                nutritionInfo.fat()
        );
    }

    // 수치가 낮을수록 낮은 편
    private NutritionLevel evaluateLowerIsBetter(
            Double value,
            NutritionThreshold.Threshold threshold
    ) {

        if (value < threshold.lowBoundary()) {
            return NutritionLevel.LOW;
        }

        if (value < threshold.highBoundary()) {
            return NutritionLevel.CHECK;
        }

        return NutritionLevel.HIGH;
    }

    // 수치가 높을수록 낮은 편 (식이섬유)
    private NutritionLevel evaluateHigherIsBetter(
            Double value,
            NutritionThreshold.Threshold threshold
    ) {

        if (value >= threshold.highBoundary()) {
            return NutritionLevel.LOW;
        }

        if (value >= threshold.lowBoundary()) {
            return NutritionLevel.CHECK;
        }

        return NutritionLevel.HIGH;
    }
}