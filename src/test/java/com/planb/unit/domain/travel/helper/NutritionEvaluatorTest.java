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

import java.util.List;

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
                        List.of(DiseaseType.DIABETES),
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseTypes())
                .containsExactly(DiseaseType.DIABETES);

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
                        List.of(DiseaseType.HIGH_BLOOD_PRESSURE),
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseTypes())
                .containsExactly(DiseaseType.HIGH_BLOOD_PRESSURE);

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
                        List.of(DiseaseType.DYSLIPIDEMIA),
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseTypes())
                .containsExactly(DiseaseType.DYSLIPIDEMIA);

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
                        List.of(DiseaseType.DIABETES),
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseTypes())
                .containsExactly(DiseaseType.DIABETES);

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
                        List.of(DiseaseType.HIGH_BLOOD_PRESSURE),
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseTypes())
                .containsExactly(DiseaseType.HIGH_BLOOD_PRESSURE);

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
                        List.of(DiseaseType.DYSLIPIDEMIA),
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseTypes())
                .containsExactly(DiseaseType.DYSLIPIDEMIA);

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
                        List.of(DiseaseType.DIABETES),
                        lowNutritionInfo
                );

        NutritionEvaluationResult checkLowBoundaryResult =
                nutritionEvaluator.evaluate(
                        List.of(DiseaseType.DIABETES),
                        checkLowBoundaryNutritionInfo
                );

        NutritionEvaluationResult checkResult =
                nutritionEvaluator.evaluate(
                        List.of(DiseaseType.DIABETES),
                        checkNutritionInfo
                );

        NutritionEvaluationResult highBoundaryResult =
                nutritionEvaluator.evaluate(
                        List.of(DiseaseType.DIABETES),
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
                        List.of(DiseaseType.DIABETES),
                        highNutritionInfo
                );

        NutritionEvaluationResult checkLowBoundaryResult =
                nutritionEvaluator.evaluate(
                        List.of(DiseaseType.DIABETES),
                        checkLowBoundaryNutritionInfo
                );

        NutritionEvaluationResult checkResult =
                nutritionEvaluator.evaluate(
                        List.of(DiseaseType.DIABETES),
                        checkNutritionInfo
                );

        NutritionEvaluationResult lowBoundaryResult =
                nutritionEvaluator.evaluate(
                        List.of(DiseaseType.DIABETES),
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

    @Test
    @DisplayName("질환이 여럿이면 각 질환의 평가 항목을 모두 담아 한 건으로 반환")
    void mergesEvaluationsAcrossDiseases() {

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
                        List.of(
                                DiseaseType.DIABETES,
                                DiseaseType.HIGH_BLOOD_PRESSURE
                        ),
                        nutritionInfo
                );

        // then
        assertThat(result.diseaseTypes())
                .containsExactly(
                        DiseaseType.DIABETES,
                        DiseaseType.HIGH_BLOOD_PRESSURE
                );

        assertThat(result.status())
                .isEqualTo(NutritionEvaluationStatus.AVAILABLE);

        assertThat(result.evaluations())
                .extracting(evaluation -> evaluation.nutritionType())
                .containsExactly(
                        NutritionType.CARBOHYDRATE,
                        NutritionType.SUGAR,
                        NutritionType.DIETARY_FIBER,
                        NutritionType.SODIUM
                );
    }

    @Test
    @DisplayName("한 질환이라도 평가 불가이면 전체 상태를 평가 불가로 반환")
    void takesWorstStatusAcrossDiseases() {

        // given - 나트륨이 없어 고혈압 평가가 불가능하다
        NutritionInfo nutritionInfo = new NutritionInfo(
                50.0,
                9.0,
                3.0,
                null,
                6.0,
                0.7,
                150.0,
                10.0
        );

        // when
        NutritionEvaluationResult result =
                nutritionEvaluator.evaluate(
                        List.of(
                                DiseaseType.DIABETES,
                                DiseaseType.HIGH_BLOOD_PRESSURE
                        ),
                        nutritionInfo
                );

        // then
        assertThat(result.status())
                .isEqualTo(NutritionEvaluationStatus.NOT_EVALUABLE);
    }

    @Test
    @DisplayName("두 질환이 같은 영양성분을 보면 중복 없이 한 번만 담김")
    void keepsSharedNutritionTypeOnce() {

        // given - 식이섬유는 당뇨와 이상지질혈증이 모두 보는 성분이다
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
                        List.of(
                                DiseaseType.DIABETES,
                                DiseaseType.DYSLIPIDEMIA
                        ),
                        nutritionInfo
                );

        // then
        assertThat(result.evaluations())
                .extracting(evaluation -> evaluation.nutritionType())
                .doesNotHaveDuplicates()
                .contains(NutritionType.DIETARY_FIBER);
    }

}
