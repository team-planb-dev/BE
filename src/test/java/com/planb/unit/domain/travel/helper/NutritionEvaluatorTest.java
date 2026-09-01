package com.planb.unit.domain.travel.helper;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.dto.nutrition.NutritionInfo;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.entity.constant.NutritionLevel;
import com.planb.domain.travel.entity.constant.NutritionType;
import com.planb.domain.travel.helper.NutritionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class NutritionEvaluatorTest {

    private final NutritionEvaluator nutritionEvaluator =
            new NutritionEvaluator();

    @Test
    @DisplayName("당뇨병 영양성분 평가")
    void evaluateDiabetes() {

        // given
        NutritionInfo nutritionInfo = new NutritionInfo(
                50.0,
                9.0,
                3.0,
                800.0,
                6.0,
                0.7,
                150.0,
                10.0
        );

        // when
        NutritionEvaluationResult result =
                nutritionEvaluator.evaluate(
                        DiseaseType.DIABETES,
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseType())
                .isEqualTo(DiseaseType.DIABETES);

        assertThat(result.status())
                .isEqualTo(NutritionEvaluationStatus.AVAILABLE);

        assertThat(result.evaluations())
                .hasSize(3)
                .extracting(
                        evaluation -> evaluation.nutritionType(),
                        evaluation -> evaluation.nutritionLevel()
                )
                .containsExactly(
                        tuple(
                                NutritionType.CARBOHYDRATE,
                                NutritionLevel.CHECK
                        ),
                        tuple(
                                NutritionType.SUGAR,
                                NutritionLevel.LOW
                        ),
                        tuple(
                                NutritionType.DIETARY_FIBER,
                                NutritionLevel.HIGH
                        )
                );
    }

    @Test
    @DisplayName("고혈압 영양성분 평가")
    void evaluateHypertension() {

        // given
        NutritionInfo nutritionInfo = new NutritionInfo(
                50.0,
                15.0,
                6.0,
                1000.0,
                6.0,
                0.7,
                150.0,
                10.0
        );

        // when
        NutritionEvaluationResult result =
                nutritionEvaluator.evaluate(
                        DiseaseType.HIGH_BLOOD_PRESSURE,
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseType())
                .isEqualTo(DiseaseType.HIGH_BLOOD_PRESSURE);

        assertThat(result.status())
                .isEqualTo(NutritionEvaluationStatus.AVAILABLE);

        assertThat(result.evaluations())
                .hasSize(1)
                .extracting(
                        evaluation -> evaluation.nutritionType(),
                        evaluation -> evaluation.nutritionLevel()
                )
                .containsExactly(
                        tuple(
                                NutritionType.SODIUM,
                                NutritionLevel.HIGH
                        )
                );
    }

    @Test
    @DisplayName("이상지질혈증 영양성분 평가")
    void evaluateDyslipidemia() {

        // given
        NutritionInfo nutritionInfo = new NutritionInfo(
                50.0,
                15.0,
                8.0,
                800.0,
                5.0,
                0.4,
                150.0,
                10.0
        );

        // when
        NutritionEvaluationResult result =
                nutritionEvaluator.evaluate(
                        DiseaseType.DYSLIPIDEMIA,
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseType())
                .isEqualTo(DiseaseType.DYSLIPIDEMIA);

        assertThat(result.status())
                .isEqualTo(NutritionEvaluationStatus.AVAILABLE);

        assertThat(result.evaluations())
                .hasSize(4)
                .extracting(
                        evaluation -> evaluation.nutritionType(),
                        evaluation -> evaluation.nutritionLevel()
                )
                .containsExactly(
                        tuple(
                                NutritionType.SATURATED_FAT,
                                NutritionLevel.CHECK
                        ),
                        tuple(
                                NutritionType.TRANS_FAT,
                                NutritionLevel.LOW
                        ),
                        tuple(
                                NutritionType.DIETARY_FIBER,
                                NutritionLevel.LOW
                        ),
                        tuple(
                                NutritionType.CHOLESTEROL,
                                NutritionLevel.CHECK
                        )
                );
    }

    @Test
    @DisplayName("당뇨병 필수 영양성분 누락 시 평가 불가")
    void evaluateDiabetesNotEvaluable() {

        // given
        NutritionInfo nutritionInfo = new NutritionInfo(
                null,
                9.0,
                3.0,
                800.0,
                6.0,
                0.7,
                150.0,
                10.0
        );

        // when
        NutritionEvaluationResult result =
                nutritionEvaluator.evaluate(
                        DiseaseType.DIABETES,
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseType())
                .isEqualTo(DiseaseType.DIABETES);

        assertThat(result.status())
                .isEqualTo(NutritionEvaluationStatus.NOT_EVALUABLE);

        assertThat(result.evaluations())
                .isEmpty();
    }

    @Test
    @DisplayName("고혈압 필수 영양성분 누락 시 평가 불가")
    void evaluateHypertensionNotEvaluable() {

        // given
        NutritionInfo nutritionInfo = new NutritionInfo(
                50.0,
                15.0,
                6.0,
                null,
                6.0,
                0.7,
                150.0,
                10.0
        );

        // when
        NutritionEvaluationResult result =
                nutritionEvaluator.evaluate(
                        DiseaseType.HIGH_BLOOD_PRESSURE,
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseType())
                .isEqualTo(DiseaseType.HIGH_BLOOD_PRESSURE);

        assertThat(result.status())
                .isEqualTo(NutritionEvaluationStatus.NOT_EVALUABLE);

        assertThat(result.evaluations())
                .isEmpty();
    }

    @Test
    @DisplayName("이상지질혈증 필수 영양성분 누락 시 평가 불가")
    void evaluateDyslipidemiaNotEvaluable() {

        // given
        NutritionInfo nutritionInfo = new NutritionInfo(
                50.0,
                15.0,
                8.0,
                800.0,
                null,
                0.4,
                150.0,
                10.0
        );

        // when
        NutritionEvaluationResult result =
                nutritionEvaluator.evaluate(
                        DiseaseType.DYSLIPIDEMIA,
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseType())
                .isEqualTo(DiseaseType.DYSLIPIDEMIA);

        assertThat(result.status())
                .isEqualTo(NutritionEvaluationStatus.NOT_EVALUABLE);

        assertThat(result.evaluations())
                .isEmpty();
    }

    @Test
    @DisplayName("낮을수록 좋은 영양성분 경계값 평가")
    void evaluateLowerIsBetterBoundary() {

        // given
        NutritionInfo lowNutritionInfo =
                createCarbohydrateNutritionInfo(44.9);

        NutritionInfo checkLowBoundaryNutritionInfo =
                createCarbohydrateNutritionInfo(45.0);

        NutritionInfo checkNutritionInfo =
                createCarbohydrateNutritionInfo(69.9);

        NutritionInfo highBoundaryNutritionInfo =
                createCarbohydrateNutritionInfo(70.0);

        // when
        NutritionEvaluationResult lowResult =
                nutritionEvaluator.evaluate(
                        DiseaseType.DIABETES,
                        lowNutritionInfo
                );

        NutritionEvaluationResult checkLowBoundaryResult =
                nutritionEvaluator.evaluate(
                        DiseaseType.DIABETES,
                        checkLowBoundaryNutritionInfo
                );

        NutritionEvaluationResult checkResult =
                nutritionEvaluator.evaluate(
                        DiseaseType.DIABETES,
                        checkNutritionInfo
                );

        NutritionEvaluationResult highBoundaryResult =
                nutritionEvaluator.evaluate(
                        DiseaseType.DIABETES,
                        highBoundaryNutritionInfo
                );

        // then
        assertThat(
                getNutritionLevel(
                        lowResult,
                        NutritionType.CARBOHYDRATE
                )
        ).isEqualTo(NutritionLevel.LOW);

        assertThat(
                getNutritionLevel(
                        checkLowBoundaryResult,
                        NutritionType.CARBOHYDRATE
                )
        ).isEqualTo(NutritionLevel.CHECK);

        assertThat(
                getNutritionLevel(
                        checkResult,
                        NutritionType.CARBOHYDRATE
                )
        ).isEqualTo(NutritionLevel.CHECK);

        assertThat(
                getNutritionLevel(
                        highBoundaryResult,
                        NutritionType.CARBOHYDRATE
                )
        ).isEqualTo(NutritionLevel.HIGH);
    }

    @Test
    @DisplayName("식이섬유 역방향 경계값 평가")
    void evaluateDietaryFiberBoundary() {

        // given
        NutritionInfo highNutritionInfo =
                createDietaryFiberNutritionInfo(3.9);

        NutritionInfo checkLowBoundaryNutritionInfo =
                createDietaryFiberNutritionInfo(4.0);

        NutritionInfo checkNutritionInfo =
                createDietaryFiberNutritionInfo(7.9);

        NutritionInfo lowBoundaryNutritionInfo =
                createDietaryFiberNutritionInfo(8.0);

        // when
        NutritionEvaluationResult highResult =
                nutritionEvaluator.evaluate(
                        DiseaseType.DIABETES,
                        highNutritionInfo
                );

        NutritionEvaluationResult checkLowBoundaryResult =
                nutritionEvaluator.evaluate(
                        DiseaseType.DIABETES,
                        checkLowBoundaryNutritionInfo
                );

        NutritionEvaluationResult checkResult =
                nutritionEvaluator.evaluate(
                        DiseaseType.DIABETES,
                        checkNutritionInfo
                );

        NutritionEvaluationResult lowBoundaryResult =
                nutritionEvaluator.evaluate(
                        DiseaseType.DIABETES,
                        lowBoundaryNutritionInfo
                );

        // then
        assertThat(
                getNutritionLevel(
                        highResult,
                        NutritionType.DIETARY_FIBER
                )
        ).isEqualTo(NutritionLevel.HIGH);

        assertThat(
                getNutritionLevel(
                        checkLowBoundaryResult,
                        NutritionType.DIETARY_FIBER
                )
        ).isEqualTo(NutritionLevel.CHECK);

        assertThat(
                getNutritionLevel(
                        checkResult,
                        NutritionType.DIETARY_FIBER
                )
        ).isEqualTo(NutritionLevel.CHECK);

        assertThat(
                getNutritionLevel(
                        lowBoundaryResult,
                        NutritionType.DIETARY_FIBER
                )
        ).isEqualTo(NutritionLevel.LOW);
    }

    private NutritionInfo createCarbohydrateNutritionInfo(
            Double carbohydrate
    ) {

        return new NutritionInfo(
                carbohydrate,
                10.0,
                5.0,
                600.0,
                5.0,
                0.5,
                100.0,
                10.0
        );
    }

    private NutritionInfo createDietaryFiberNutritionInfo(
            Double dietaryFiber
    ) {

        return new NutritionInfo(
                45.0,
                10.0,
                dietaryFiber,
                600.0,
                5.0,
                0.5,
                100.0,
                10.0
        );
    }

    private NutritionLevel getNutritionLevel(
            NutritionEvaluationResult result,
            NutritionType nutritionType
    ) {

        return result.evaluations()
                .stream()
                .filter(evaluation ->
                        evaluation.nutritionType() == nutritionType
                )
                .findFirst()
                .orElseThrow()
                .nutritionLevel();
    }
}