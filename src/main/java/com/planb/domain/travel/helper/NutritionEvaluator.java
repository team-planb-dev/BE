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

import java.util.List;

@Component
public class NutritionEvaluator {

    public NutritionEvaluationResult evaluate(
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
                DiseaseType.DIABETES,
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
                DiseaseType.HIGH_BLOOD_PRESSURE,
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
                DiseaseType.DYSLIPIDEMIA,
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
                diseaseType,
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