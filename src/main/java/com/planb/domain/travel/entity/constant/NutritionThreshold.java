package com.planb.domain.travel.entity.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NutritionThreshold {

    // 당뇨병 - 탄수화물(g)
    public static final Threshold DIABETES_CARBOHYDRATE =
            new Threshold(
                    45.0,
                    70.0
            );

    // 당뇨병 - 당류(g)
    public static final Threshold DIABETES_SUGAR =
            new Threshold(
                    10.0,
                    20.0
            );

    // 당뇨병 - 식이섬유(g)
    public static final Threshold DIABETES_DIETARY_FIBER =
            new Threshold(
                    4.0,
                    8.0
            );

    // 고혈압 - 나트륨(mg)
    public static final Threshold HYPERTENSION_SODIUM =
            new Threshold(
                    600.0,
                    1000.0
            );

    // 이상지질혈증 - 포화지방(g)
    public static final Threshold DYSLIPIDEMIA_SATURATED_FAT =
            new Threshold(
                    5.0,
                    8.0
            );

    // 이상지질혈증 - 트랜스지방(g)
    public static final Threshold DYSLIPIDEMIA_TRANS_FAT =
            new Threshold(
                    0.5,
                    1.0
            );

    // 이상지질혈증 - 식이섬유(g)
    public static final Threshold DYSLIPIDEMIA_DIETARY_FIBER =
            new Threshold(
                    4.0,
                    8.0
            );

    // 이상지질혈증 - 콜레스테롤(mg)
    public static final Threshold DYSLIPIDEMIA_CHOLESTEROL =
            new Threshold(
                    100.0,
                    200.0
            );

    public record Threshold(
            Double lowBoundary,
            Double highBoundary
    ) {
    }
}