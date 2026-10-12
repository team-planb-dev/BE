package com.planb.unit.domain.travel.service;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.service.ScheduleNormalizer;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ScheduleNormalizerTest {

    private final ScheduleNormalizer scheduleNormalizer =
            new ScheduleNormalizer(new PlanPlaceResolver());

    private final LocalDate date = LocalDate.of(
            2026,
            9,
            10
    );

    @Test
    @DisplayName("이동시간 반영 시간표 정규화의 반복 적용 시 동일 결과")
    void normalizeScheduleTimesIsIdempotent() {

        CreatePlanAiResponse response = response(
                restaurant(
                        "아침 식당",
                        ScheduleType.BREAKFAST,
                        LocalTime.of(8, 0),
                        null
                ),
                attraction(
                        "해운대해수욕장",
                        LocalTime.of(9, 0),
                        40
                ),
                restaurant(
                        "점심 식당",
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        30
                ),
                attraction(
                        "동백섬",
                        LocalTime.of(14, 0),
                        25
                )
        );

        CreatePlanAiResponse once = scheduleNormalizer
                .normalizeScheduleTimes(
                        response,
                        healthContexts()
                );

        CreatePlanAiResponse twice = scheduleNormalizer
                .normalizeScheduleTimes(
                        once,
                        healthContexts()
                );

        assertEquals(once, twice);
    }

    @Test
    @DisplayName("복약 일정 생성의 반복 적용 시 동일 결과")
    void ensureMedicationSchedulesIsIdempotent() {

        CreatePlanAiResponse response = response(
                restaurant(
                        "아침 식당",
                        ScheduleType.BREAKFAST,
                        LocalTime.of(8, 0),
                        null
                ),
                restaurant(
                        "점심 식당",
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        30
                ),
                restaurant(
                        "저녁 식당",
                        ScheduleType.DINNER,
                        LocalTime.of(18, 0),
                        30
                )
        );

        CreatePlanAiResponse once = scheduleNormalizer
                .ensureMedicationSchedules(
                        response,
                        healthContexts()
                );

        CreatePlanAiResponse twice = scheduleNormalizer
                .ensureMedicationSchedules(
                        once,
                        healthContexts()
                );

        assertEquals(once, twice);
    }

    @Test
    @DisplayName("이동시간으로 식사시간을 맞출 수 없는 일정의 예외 없는 최선 배치")
    void keepsPlanWhenMealTimeCannotBeMet() {

        // 아침(08:00~09:30) 직후 곧바로 점심이고, 이동시간 200분이라
        // 점심 12:30 상한 초과와 앞당길 장소 부재
        CreatePlanAiResponse response = response(
                restaurant(
                        "아침 식당",
                        ScheduleType.BREAKFAST,
                        LocalTime.of(8, 0),
                        null
                ),
                restaurant(
                        "점심 식당",
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        200
                )
        );

        CreatePlanAiResponse normalized = scheduleNormalizer
                .normalizeScheduleTimes(
                        response,
                        healthContexts()
                );

        CreatePlanAiResponse.PlanScheduleDetail breakfast = normalized
                .planDays()
                .getFirst()
                .schedules()
                .getFirst();

        CreatePlanAiResponse.PlanScheduleDetail lunch = normalized
                .planDays()
                .getFirst()
                .schedules()
                .get(1);

        // 이동시간을 무시한 시각 당김 방지
        assertEquals(
                breakfast
                        .endTime()
                        .plusMinutes(200),
                lunch.startTime()
        );

        // 식사시각을 맞추지 못한 슬롯의 MEAL_TIME_APPLIED 제외
        assertFalse(
                scheduleNormalizer.mealTimeSatisfied(
                        lunch,
                        healthContexts()
                )
        );

        // 식사시각을 맞춘 슬롯의 MEAL_TIME_APPLIED 포함
        assertTrue(
                scheduleNormalizer.mealTimeSatisfied(
                        breakfast,
                        healthContexts()
                )
        );
    }

    @Test
    @DisplayName("식후 복약의 식사 종료 기준 배치")
    void placesAfterMealMedicationFromMealEnd() {

        // 90분 식사 슬롯의 종료 기준 식후 30분 복약
        CreatePlanAiResponse response = response(
                restaurant(
                        "점심 식당",
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        0
                )
        );

        CreatePlanAiResponse result = scheduleNormalizer.ensureMedicationSchedules(
                scheduleNormalizer.normalizeScheduleTimes(response, healthContexts()),
                healthContexts());

        assertEquals(
                LocalTime.of(14, 0),
                medicationStartTime(result)
        );
    }

    @Test
    @DisplayName("식사 슬롯 없는 날의 설정 식사시간 기준 식후 복약 배치")
    void placesAfterMealMedicationWithoutMealSlot() {

        // 설정 식사시각에 기본 식사 소요시간을 더한 식후 복약 기준
        CreatePlanAiResponse response = response(
                attraction(
                        "해운대해수욕장",
                        LocalTime.of(9, 0),
                        0
                )
        );

        CreatePlanAiResponse result = scheduleNormalizer.ensureMedicationSchedules(
                scheduleNormalizer.normalizeScheduleTimes(response, healthContexts()),
                healthContexts());

        assertEquals(
                LocalTime.of(13, 30),
                medicationStartTime(result)
        );
    }

    @Test
    @DisplayName("장소 시간대 안의 식후 복약의 등록 간격 유지")
    void keepsAfterMealIntervalInsidePlaceSlot() {

        // 점심 12:00~13:30 종료 30분 뒤 복약, 관광지 13:40~15:10 내부
        CreatePlanAiResponse response = response(
                restaurant(
                        "점심 식당",
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        0
                ),
                attraction(
                        "해운대해수욕장",
                        LocalTime.of(13, 40),
                        10
                )
        );

        CreatePlanAiResponse result = medicated(
                response,
                healthContexts()
        );

        assertEquals(
                LocalTime.of(14, 0),
                medicationStartTime(result)
        );
    }

    @Test
    @DisplayName("장소 시간대 안의 식전 복약의 등록 간격 유지")
    void keepsBeforeMealIntervalInsidePlaceSlot() {

        // 점심 12:00 시작 30분 전 복약, 관광지 10:30~12:00 내부
        List<TravelHealthContext> beforeMeal = healthContexts(MealTiming.BEFORE_MEAL, 30);

        CreatePlanAiResponse response = response(
                attraction(
                        "해운대해수욕장",
                        LocalTime.of(10, 30),
                        null
                ),
                restaurant(
                        "점심 식당",
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        0
                )
        );

        CreatePlanAiResponse result = medicated(
                response,
                beforeMeal
        );

        assertEquals(
                LocalTime.of(11, 30),
                medicationStartTime(result)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = 0)
    @DisplayName("간격 0·누락 식후 복약의 식사 종료시각 유지")
    void keepsZeroIntervalAtMealEnd(Integer interval) {

        // 점심 종료 13:30과 같은 시각에 시작하는 관광지
        List<TravelHealthContext> zeroInterval = healthContexts(MealTiming.AFTER_MEAL, interval);

        CreatePlanAiResponse response = response(
                restaurant(
                        "점심 식당",
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        0
                ),
                attraction(
                        "해운대해수욕장",
                        LocalTime.of(13, 30),
                        0
                )
        );

        CreatePlanAiResponse result = medicated(
                response,
                zeroInterval
        );

        assertEquals(
                LocalTime.of(13, 30),
                medicationStartTime(result)
        );
    }

    @Test
    @DisplayName("장소 시간대 안의 고정 시각 복약 유지")
    void keepsIndependentMedicationTimeInsidePlaceSlot() {

        // 등록 시각 14:00, 관광지 13:40~15:10 내부
        List<TravelHealthContext> independent = List.of(
                traveler(
                        "고정 시각 여행자",
                        new TravelHealthContext.MedicationInfoContext(
                                "혈압약",
                                MedicationBasis.INDEPENDENT,
                                LocalTime.of(14, 0),
                                Set.of()
                        )
                )
        );

        CreatePlanAiResponse response = response(
                restaurant(
                        "점심 식당",
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        0
                ),
                attraction(
                        "해운대해수욕장",
                        LocalTime.of(13, 40),
                        10
                )
        );

        CreatePlanAiResponse result = medicated(
                response,
                independent
        );

        assertEquals(
                LocalTime.of(14, 0),
                medicationStartTime(result)
        );
    }

    @Test
    @DisplayName("여러 동행인·복약 규칙별 등록 간격 유지와 같은 시각 병합")
    void keepsEachCompanionRuleAndMergesSameTime() {

        // 동행인 1: 점심 식후 30분, 저녁 식전 15분
        // 동행인 2: 점심 식후 30분
        List<TravelHealthContext> companions = List.of(
                traveler(
                        "동행인 1",
                        new TravelHealthContext.MedicationInfoContext(
                                "혈당약",
                                MedicationBasis.WITH_MEAL,
                                null,
                                Set.of(
                                        rule(
                                                RelatedMeal.LUNCH,
                                                MealTiming.AFTER_MEAL,
                                                30
                                        ),
                                        rule(
                                                RelatedMeal.DINNER,
                                                MealTiming.BEFORE_MEAL,
                                                15
                                        )
                                )
                        )
                ),
                traveler(
                        "동행인 2",
                        new TravelHealthContext.MedicationInfoContext(
                                "위장약",
                                MedicationBasis.WITH_MEAL,
                                null,
                                Set.of(
                                        rule(
                                                RelatedMeal.LUNCH,
                                                MealTiming.AFTER_MEAL,
                                                30
                                        )
                                )
                        )
                )
        );

        CreatePlanAiResponse response = response(
                restaurant(
                        "점심 식당",
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        0
                ),
                attraction(
                        "해운대해수욕장",
                        LocalTime.of(13, 40),
                        10
                ),
                restaurant(
                        "저녁 식당",
                        ScheduleType.DINNER,
                        LocalTime.of(18, 0),
                        20
                )
        );

        CreatePlanAiResponse result = medicated(
                response,
                companions
        );

        List<CreatePlanAiResponse.PlanScheduleDetail> medications = medications(result);

        assertEquals(
                List.of(
                        LocalTime.of(14, 0),
                        LocalTime.of(17, 45)
                ),
                medications
                        .stream()
                        .map(CreatePlanAiResponse.PlanScheduleDetail::startTime)
                        .toList()
        );

        String lunchDescription = medications
                .getFirst()
                .medication()
                .description();

        assertTrue(lunchDescription.contains("혈당약"));
        assertTrue(lunchDescription.contains("위장약"));

        String dinnerDescription = medications
                .getLast()
                .medication()
                .description();

        assertTrue(dinnerDescription.contains("혈당약"));
        assertFalse(dinnerDescription.contains("위장약"));
    }

    @Test
    @DisplayName("장소 내부 복약 일정 생성의 반복 적용 시 동일 결과")
    void keepsMedicationInsidePlaceIdempotent() {

        CreatePlanAiResponse response = response(
                restaurant(
                        "점심 식당",
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        0
                ),
                attraction(
                        "해운대해수욕장",
                        LocalTime.of(13, 40),
                        10
                )
        );

        CreatePlanAiResponse once = medicated(
                response,
                healthContexts()
        );

        CreatePlanAiResponse twice = scheduleNormalizer.ensureMedicationSchedules(
                once,
                healthContexts()
        );

        assertEquals(
                LocalTime.of(14, 0),
                medicationStartTime(twice)
        );

        assertEquals(once, twice);
    }

    @Test
    @DisplayName("같은 식사의 동행인별 다른 간격 복약 개별 유지")
    void keepsDifferentIntervalsOfSameMealSeparately() {

        // 점심 종료 13:30, 동행인별 식후 30분·60분
        List<TravelHealthContext> companions = List.of(
                traveler(
                        "동행인 1",
                        mealMedication(
                                "혈당약",
                                rule(
                                        RelatedMeal.LUNCH,
                                        MealTiming.AFTER_MEAL,
                                        30
                                )
                        )
                ),
                traveler(
                        "동행인 2",
                        mealMedication(
                                "위장약",
                                rule(
                                        RelatedMeal.LUNCH,
                                        MealTiming.AFTER_MEAL,
                                        60
                                )
                        )
                )
        );

        CreatePlanAiResponse result = medicated(
                response(
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        )
                ),
                companions
        );

        List<CreatePlanAiResponse.PlanScheduleDetail> medications = medications(result);

        assertEquals(
                List.of(
                        LocalTime.of(14, 0),
                        LocalTime.of(14, 30)
                ),
                medications
                        .stream()
                        .map(CreatePlanAiResponse.PlanScheduleDetail::startTime)
                        .toList()
        );

        assertEquals(
                List.of(
                        30,
                        60
                ),
                medications
                        .stream()
                        .map(medication -> medication
                                .medication()
                                .intervalMinutes())
                        .toList()
        );
    }

    @Test
    @DisplayName("장소 시간대 안의 식사 무관 복약 등록 시각 유지")
    void keepsRegardlessOfMealTimeInsidePlaceSlot() {

        // 식사 기준 복약의 식사 무관 규칙, 등록 시각 14:00
        List<TravelHealthContext> regardless = List.of(
                traveler(
                        "테스트 여행자",
                        new TravelHealthContext.MedicationInfoContext(
                                "영양제",
                                MedicationBasis.WITH_MEAL,
                                LocalTime.of(14, 0),
                                Set.of(
                                        rule(
                                                RelatedMeal.LUNCH,
                                                MealTiming.REGARDLESS_OF_MEAL,
                                                0
                                        )
                                )
                        )
                )
        );

        CreatePlanAiResponse result = medicated(
                response(
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        ),
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(13, 40),
                                10
                        )
                ),
                regardless
        );

        assertEquals(
                LocalTime.of(14, 0),
                medicationStartTime(result)
        );
    }

    @Test
    @DisplayName("식중 복약의 식사 시간대 유지")
    void keepsDuringMealMedicationInsideMealSlot() {

        // 식중 복약 기준인 식사 슬롯과의 겹침 허용
        List<TravelHealthContext> duringMeal = healthContexts(MealTiming.DURING_MEAL, 0);

        CreatePlanAiResponse response = response(
                restaurant(
                        "점심 식당",
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        0
                )
        );

        CreatePlanAiResponse result = scheduleNormalizer.ensureMedicationSchedules(
                scheduleNormalizer.normalizeScheduleTimes(response, duringMeal),
                duringMeal);

        assertEquals(
                LocalTime.of(12, 0),
                medicationStartTime(result)
        );
    }

    @Test
    @DisplayName("관광지 내부 시작 복약의 관광지 복약 종료 후 이동")
    void delaysPlaceContainingMedicationStart() {

        // 점심 12:00~13:30, 식후 14:00 복약, 관광지 13:40~15:10
        CreatePlanAiResponse result = finalized(
                response(
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        ),
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(13, 40),
                                10
                        )
                ),
                healthContexts()
        );

        assertEquals(
                LocalTime.of(14, 0),
                medicationStartTime(result)
        );

        assertEquals(
                LocalTime.of(14, 10),
                startTime(result, "해운대해수욕장")
        );

        assertEquals(
                LocalTime.of(15, 40),
                endTime(result, "해운대해수욕장")
        );
    }

    @Test
    @DisplayName("복약 끝부분만 겹치는 관광지의 복약 종료 후 이동")
    void delaysPlaceOverlappingMedicationTail() {

        // 식후 14:00~14:10 복약, 이동 35분 뒤 14:05 시작 관광지
        CreatePlanAiResponse result = finalized(
                response(
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        ),
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(14, 5),
                                35
                        )
                ),
                healthContexts()
        );

        assertEquals(
                LocalTime.of(14, 10),
                startTime(result, "해운대해수욕장")
        );
    }

    @Test
    @DisplayName("복약 구간 안의 짧은 장소와 동일 시작 장소의 복약 종료 후 이동")
    void delaysPlaceInsideOrStartingWithMedication() {

        // 14:02~14:07 짧은 장소, 14:00 동일 시작 장소
        List<LocalTime> placeStarts = List.of(
                LocalTime.of(14, 2),
                LocalTime.of(14, 0)
        );

        for (LocalTime placeStart : placeStarts) {
            CreatePlanAiResponse result = finalized(
                    response(
                            restaurant(
                                    "점심 식당",
                                    ScheduleType.LUNCH,
                                    LocalTime.of(12, 0),
                                    0
                            ),
                            slot(
                                    ScheduleType.ACTIVITY,
                                    CourseType.ATTRACTION,
                                    "포토존",
                                    placeStart,
                                    10,
                                    5
                            )
                    ),
                    healthContexts()
            );

            assertEquals(
                    LocalTime.of(14, 10),
                    startTime(result, "포토존")
            );
        }
    }

    @Test
    @DisplayName("복약과 경계만 맞닿은 장소의 시각 유지")
    void keepsPlaceTouchingMedicationBoundary() {

        // 복약 14:00~14:10 종료와 같은 14:10 시작 관광지
        CreatePlanAiResponse result = finalized(
                response(
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        ),
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(14, 10),
                                40
                        )
                ),
                healthContexts()
        );

        assertEquals(
                LocalTime.of(14, 10),
                startTime(result, "해운대해수욕장")
        );

        // 식전 11:30 복약 시작과 같은 11:30 종료 관광지
        List<TravelHealthContext> beforeMeal = healthContexts(MealTiming.BEFORE_MEAL, 30);

        CreatePlanAiResponse touchingEnd = finalized(
                response(
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(10, 0),
                                null
                        ),
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        )
                ),
                beforeMeal
        );

        assertEquals(
                LocalTime.of(10, 0),
                startTime(touchingEnd, "해운대해수욕장")
        );
    }

    @Test
    @DisplayName("연속 장소의 체류·이동시간 유지 이동과 저녁 연동 복약 재계산")
    void cascadesDelayAndRecalculatesDinnerMedication() {

        // 점심 식후 30분, 저녁 식후 30분 복약
        List<TravelHealthContext> lunchAndDinner = List.of(
                traveler(
                        "테스트 여행자",
                        new TravelHealthContext.MedicationInfoContext(
                                "혈당약",
                                MedicationBasis.WITH_MEAL,
                                null,
                                Set.of(
                                        rule(
                                                RelatedMeal.LUNCH,
                                                MealTiming.AFTER_MEAL,
                                                30
                                        ),
                                        rule(
                                                RelatedMeal.DINNER,
                                                MealTiming.AFTER_MEAL,
                                                30
                                        )
                                )
                        )
                )
        );

        // 관광지 13:40~15:10, 15:20~16:50, 저녁 17:30
        CreatePlanAiResponse result = finalized(
                response(
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        ),
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(13, 40),
                                10
                        ),
                        attraction(
                                "동백섬",
                                LocalTime.of(15, 20),
                                10
                        ),
                        restaurant(
                                "저녁 식당",
                                ScheduleType.DINNER,
                                LocalTime.of(17, 30),
                                20
                        )
                ),
                lunchAndDinner
        );

        assertEquals(
                List.of(
                        LocalTime.of(14, 10),
                        LocalTime.of(15, 50),
                        LocalTime.of(17, 40)
                ),
                List.of(
                        startTime(result, "해운대해수욕장"),
                        startTime(result, "동백섬"),
                        startTime(result, "저녁 식당")
                )
        );

        assertEquals(
                List.of(
                        LocalTime.of(14, 0),
                        LocalTime.of(19, 40)
                ),
                medications(result)
                        .stream()
                        .map(CreatePlanAiResponse.PlanScheduleDetail::startTime)
                        .toList()
        );
    }

    @Test
    @DisplayName("카페 겹침의 이동과 식사·이동 슬롯 겹침의 판정 제외")
    void judgesOnlyNonMealPlaceSlots() {

        // 카페 13:40~15:10 내부 14:00 복약
        CreatePlanAiResponse cafe = finalized(
                response(
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        ),
                        slot(
                                ScheduleType.ACTIVITY,
                                CourseType.CAFE_REST,
                                "바다 카페",
                                LocalTime.of(13, 40),
                                10
                        )
                ),
                healthContexts()
        );

        assertEquals(
                LocalTime.of(14, 10),
                startTime(cafe, "바다 카페")
        );

        // 식중 복약과 점심, 13:40 이동 슬롯과 14:00 복약
        List<TravelHealthContext> duringMeal = healthContexts(MealTiming.DURING_MEAL, 0);

        CreatePlanAiResponse excluded = finalized(
                response(
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        ),
                        slot(
                                ScheduleType.ACTIVITY,
                                CourseType.TRANSPORTATION,
                                "버스 이동",
                                LocalTime.of(11, 50),
                                null
                        )
                ),
                duringMeal
        );

        assertEquals(
                LocalTime.of(12, 0),
                startTime(excluded, "점심 식당")
        );

        assertEquals(
                LocalTime.of(11, 50),
                startTime(excluded, "버스 이동")
        );
    }

    @Test
    @DisplayName("늦은 점심 보정의 장소 앞당김이 필요한 이동의 거부")
    void rejectsDelayRequiringPlaceToMoveEarlier() {

        // 고정 11:30 복약이 관광지 10:30~12:00 내부
        // 관광지 11:40 이동 시 점심 13:10, 늦은 점심 보정의 관광지 앞당김 발생
        List<TravelHealthContext> independent = List.of(
                traveler(
                        "고정 시각 여행자",
                        independentMedication(
                                "혈압약",
                                LocalTime.of(11, 30)
                        )
                )
        );

        CreatePlanAiResponse normalized = scheduleNormalizer.normalizeScheduleTimes(
                response(
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(10, 30),
                                null
                        ),
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        )
                ),
                independent
        );

        BaseException failure = assertThrows(
                BaseException.class,
                () -> scheduleNormalizer.finalizeMedicationSchedules(
                        normalized,
                        independent
                )
        );

        assertEquals(
                PlanEditExceptionEnum.INVALID_AI_PLACE.getCode(),
                failure.getErrorCode()
        );

        assertTrue(failure
                .getMessage()
                .contains("장소 앞당김 필요"));
    }

    @Test
    @DisplayName("직전 장소와 겹치는 식전 복약의 식사 창 안 지연")
    void delaysMealForBeforeMealMedication() {

        // 식전 30분 11:30 복약이 관광지 10:30~12:00 내부
        // 점심 12:30 이동, 복약 12:00 관광지 종료와 맞닿음
        List<TravelHealthContext> beforeMeal = healthContexts(MealTiming.BEFORE_MEAL, 30);

        CreatePlanAiResponse result = finalized(
                response(
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(10, 30),
                                null
                        ),
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        )
                ),
                beforeMeal
        );

        assertEquals(
                LocalTime.of(10, 30),
                startTime(result, "해운대해수욕장")
        );

        assertEquals(
                LocalTime.of(12, 30),
                startTime(result, "점심 식당")
        );

        assertEquals(
                LocalTime.of(12, 0),
                medicationStartTime(result)
        );
    }

    @Test
    @DisplayName("같은 식사의 동행인별 다른 식전 간격 중 큰 간격 기준 식사 지연")
    void delaysMealForLargestBeforeMealInterval() {

        // 관광지 10:20~11:50, 점심 12:00, 식전 30분 11:30·15분 11:45
        // 지연 20분, 점심 12:20, 복약 11:50·12:05
        List<TravelHealthContext> companions = List.of(
                traveler(
                        "동행인 1",
                        mealMedication(
                                "혈당약",
                                rule(
                                        RelatedMeal.LUNCH,
                                        MealTiming.BEFORE_MEAL,
                                        30
                                )
                        )
                ),
                traveler(
                        "동행인 2",
                        mealMedication(
                                "위장약",
                                rule(
                                        RelatedMeal.LUNCH,
                                        MealTiming.BEFORE_MEAL,
                                        15
                                )
                        )
                )
        );

        CreatePlanAiResponse result = finalized(
                response(
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(10, 20),
                                null
                        ),
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                10
                        )
                ),
                companions
        );

        assertEquals(
                LocalTime.of(12, 20),
                startTime(result, "점심 식당")
        );

        assertEquals(
                List.of(
                        LocalTime.of(11, 50),
                        LocalTime.of(12, 5)
                ),
                medications(result)
                        .stream()
                        .map(CreatePlanAiResponse.PlanScheduleDetail::startTime)
                        .toList()
        );
    }

    @Test
    @DisplayName("식전 복약 간격 확보가 식사 창을 넘는 배치의 거부")
    void rejectsBeforeMealDelayBeyondMealWindow() {

        // 식전 45분 11:15 복약, 점심 12:45 필요, 상한 12:30 초과
        List<TravelHealthContext> beforeMeal = healthContexts(MealTiming.BEFORE_MEAL, 45);

        CreatePlanAiResponse normalized = scheduleNormalizer.normalizeScheduleTimes(
                response(
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(10, 30),
                                null
                        ),
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        )
                ),
                beforeMeal
        );

        BaseException failure = assertThrows(
                BaseException.class,
                () -> scheduleNormalizer.finalizeMedicationSchedules(
                        normalized,
                        beforeMeal
                )
        );

        assertTrue(failure
                .getMessage()
                .contains("식사시간 이탈"));
    }

    @Test
    @DisplayName("앞 장소를 당길 수 없는 점심의 창 밖 이동 거부")
    void rejectsDelayLeavingMealWindowWithoutShift() {

        // 아침 08:00~09:00 직후 장소 09:00~10:00, 관광지 10:00~12:00, 점심 12:00
        // 고정 11:00 복약으로 관광지 11:10 이동, 점심 13:10, 아침 종료로 앞당김 불가
        List<TravelHealthContext> independent = List.of(
                traveler(
                        "고정 시각 여행자",
                        independentMedication(
                                "혈압약",
                                LocalTime.of(11, 0)
                        )
                )
        );

        CreatePlanAiResponse normalized = scheduleNormalizer.normalizeScheduleTimes(
                response(
                        slot(
                                ScheduleType.BREAKFAST,
                                CourseType.RESTAURANT,
                                "아침 식당",
                                LocalTime.of(8, 0),
                                null,
                                60
                        ),
                        slot(
                                ScheduleType.ACTIVITY,
                                CourseType.ATTRACTION,
                                "동백섬",
                                LocalTime.of(9, 0),
                                0,
                                60
                        ),
                        slot(
                                ScheduleType.ACTIVITY,
                                CourseType.ATTRACTION,
                                "해운대해수욕장",
                                LocalTime.of(10, 0),
                                0,
                                120
                        ),
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        )
                ),
                independent
        );

        BaseException failure = assertThrows(
                BaseException.class,
                () -> scheduleNormalizer.finalizeMedicationSchedules(
                        normalized,
                        independent
                )
        );

        assertTrue(failure
                .getMessage()
                .contains("식사시간 이탈"));
    }

    @Test
    @DisplayName("이동 연쇄로 뒤 장소가 자정을 넘기는 배치의 거부")
    void rejectsCascadeCrossingMidnight() {

        // 고정 22:00 복약, 관광지 21:00~22:30, 뒤 장소 22:30~23:30
        // 관광지 22:10~23:40 이동, 뒤 장소 23:40~00:40 순환
        List<TravelHealthContext> independent = List.of(
                traveler(
                        "고정 시각 여행자",
                        independentMedication(
                                "혈압약",
                                LocalTime.of(22, 0)
                        )
                )
        );

        CreatePlanAiResponse normalized = scheduleNormalizer.normalizeScheduleTimes(
                response(
                        attraction(
                                "야경 명소",
                                LocalTime.of(21, 0),
                                null
                        ),
                        slot(
                                ScheduleType.ACTIVITY,
                                CourseType.CAFE_REST,
                                "심야 카페",
                                LocalTime.of(22, 30),
                                0,
                                60
                        )
                ),
                independent
        );

        BaseException failure = assertThrows(
                BaseException.class,
                () -> scheduleNormalizer.finalizeMedicationSchedules(
                        normalized,
                        independent
                )
        );

        assertTrue(failure
                .getMessage()
                .contains("자정 초과"));
    }

    @Test
    @DisplayName("이동시간 계산이 자정을 넘겨 뒤 장소가 남는 배치의 거부")
    void rejectsCascadeWithWrappedTravelStart() {

        // 고정 22:00 복약, 관광지 21:00~22:30, 이동 30분 뒤 카페 23:00~00:00
        // 관광지 22:10~23:40 이동, 카페 최소 시작 00:10 순환으로 23:00 잔존
        List<TravelHealthContext> independent = List.of(
                traveler(
                        "고정 시각 여행자",
                        independentMedication(
                                "혈압약",
                                LocalTime.of(22, 0)
                        )
                )
        );

        CreatePlanAiResponse withTravel = response(
                attraction(
                        "야경 명소",
                        LocalTime.of(21, 0),
                        null
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.CAFE_REST,
                        "심야 카페",
                        LocalTime.of(23, 0),
                        30,
                        60
                )
        );

        BaseException failure = assertThrows(
                BaseException.class,
                () -> scheduleNormalizer.finalizeMedicationSchedules(
                        withTravel,
                        independent
                )
        );

        assertTrue(failure
                .getMessage()
                .contains("자정 초과"));
    }

    @Test
    @DisplayName("등록 시각 없는 식사의 식전 복약 지연 허용")
    void delaysMealWithoutConfiguredTime() {

        // 점심 등록 시각 없음, 실제 점심 12:00 기준 식전 30분 복약
        List<TravelHealthContext> noLunchTime = List.of(
                new TravelHealthContext(
                        "테스트 여행자",
                        List.of(DiseaseType.DIABETES),
                        WalkType.ACTIVE,
                        new TravelHealthContext.MealInfoContext(
                                LocalTime.of(8, 0),
                                null,
                                LocalTime.of(18, 0)
                        ),
                        List.of(),
                        List.of(
                                mealMedication(
                                        "혈당약",
                                        rule(
                                                RelatedMeal.LUNCH,
                                                MealTiming.BEFORE_MEAL,
                                                30
                                        )
                                )
                        )
                )
        );

        CreatePlanAiResponse result = finalized(
                response(
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(10, 30),
                                null
                        ),
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        )
                ),
                noLunchTime
        );

        assertEquals(
                LocalTime.of(12, 30),
                startTime(result, "점심 식당")
        );

        assertEquals(
                LocalTime.of(12, 0),
                medicationStartTime(result)
        );
    }

    @Test
    @DisplayName("자정에 끝나는 장소와 복약 겹침의 검출")
    void detectsOverlapWithPlaceEndingAtMidnight() {

        // 관광지 23:00~00:00, 고정 23:30 복약, 이동 시 자정 초과
        List<TravelHealthContext> independent = List.of(
                traveler(
                        "고정 시각 여행자",
                        independentMedication(
                                "혈압약",
                                LocalTime.of(23, 30)
                        )
                )
        );

        CreatePlanAiResponse normalized = scheduleNormalizer.normalizeScheduleTimes(
                response(
                        slot(
                                ScheduleType.ACTIVITY,
                                CourseType.ATTRACTION,
                                "야경 명소",
                                LocalTime.of(23, 0),
                                null,
                                60
                        )
                ),
                independent
        );

        assertThrows(
                BaseException.class,
                () -> scheduleNormalizer.finalizeMedicationSchedules(
                        normalized,
                        independent
                )
        );
    }

    @Test
    @DisplayName("이동으로 자정을 넘기는 배치의 거부")
    void rejectsDelayCrossingMidnight() {

        // 고정 23:00 복약, 관광지 22:00~23:30
        List<TravelHealthContext> independent = List.of(
                traveler(
                        "고정 시각 여행자",
                        new TravelHealthContext.MedicationInfoContext(
                                "혈압약",
                                MedicationBasis.INDEPENDENT,
                                LocalTime.of(23, 0),
                                Set.of()
                        )
                )
        );

        CreatePlanAiResponse normalized = scheduleNormalizer.normalizeScheduleTimes(
                response(
                        attraction(
                                "야경 명소",
                                LocalTime.of(22, 0),
                                null
                        )
                ),
                independent
        );

        BaseException failure = assertThrows(
                BaseException.class,
                () -> scheduleNormalizer.finalizeMedicationSchedules(
                        normalized,
                        independent
                )
        );

        assertTrue(failure
                .getMessage()
                .contains("자정 초과"));
    }

    @Test
    @DisplayName("이동 상한 3회 초과 겹침의 거부")
    void rejectsOverlapRemainingAfterDelayLimit() {

        // 고정 복약 4개가 매 이동 후 같은 관광지와 다시 겹치는 배치
        List<TravelHealthContext> repeated = List.of(
                traveler(
                        "여행자 1",
                        independentMedication(
                                "약 1",
                                LocalTime.of(10, 0)
                        )
                ),
                traveler(
                        "여행자 2",
                        independentMedication(
                                "약 2",
                                LocalTime.of(11, 30)
                        )
                ),
                traveler(
                        "여행자 3",
                        independentMedication(
                                "약 3",
                                LocalTime.of(13, 0)
                        )
                ),
                traveler(
                        "여행자 4",
                        independentMedication(
                                "약 4",
                                LocalTime.of(14, 30)
                        )
                )
        );

        CreatePlanAiResponse normalized = scheduleNormalizer.normalizeScheduleTimes(
                response(
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(9, 50),
                                null
                        )
                ),
                repeated
        );

        BaseException failure = assertThrows(
                BaseException.class,
                () -> scheduleNormalizer.finalizeMedicationSchedules(
                        normalized,
                        repeated
                )
        );

        assertTrue(failure
                .getMessage()
                .contains("복약·장소 시간 겹침"));
    }

    @Test
    @DisplayName("이동 상한 3회 이내 겹침의 해소")
    void resolvesOverlapWithinDelayLimit() {

        // 고정 복약 3개, 이동 3회 후 관광지 13:10~14:40
        List<TravelHealthContext> repeated = List.of(
                traveler(
                        "여행자 1",
                        independentMedication(
                                "약 1",
                                LocalTime.of(10, 0)
                        )
                ),
                traveler(
                        "여행자 2",
                        independentMedication(
                                "약 2",
                                LocalTime.of(11, 30)
                        )
                ),
                traveler(
                        "여행자 3",
                        independentMedication(
                                "약 3",
                                LocalTime.of(13, 0)
                        )
                )
        );

        CreatePlanAiResponse result = finalized(
                response(
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(9, 50),
                                null
                        )
                ),
                repeated
        );

        assertEquals(
                LocalTime.of(13, 10),
                startTime(result, "해운대해수욕장")
        );
    }

    @Test
    @DisplayName("식사 슬롯 없는 날의 설정 식사시간 기준 복약 겹침 해소")
    void resolvesOverlapWithoutMealSlot() {

        // 설정 점심 12:00 + 기본 60분 + 식후 30분 = 13:30, 관광지 13:00~14:30
        CreatePlanAiResponse result = finalized(
                response(
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(13, 0),
                                null
                        )
                ),
                healthContexts()
        );

        assertEquals(
                LocalTime.of(13, 30),
                medicationStartTime(result)
        );

        assertEquals(
                LocalTime.of(13, 40),
                startTime(result, "해운대해수욕장")
        );
    }

    @Test
    @DisplayName("입력 AI 복약 슬롯의 제거와 재생성 기준 겹침 해소")
    void ignoresAiMedicationSlotInInput() {

        // AI 복약 13:45~13:55, 재생성 복약 14:00
        CreatePlanAiResponse.PlanScheduleDetail aiMedication =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.CHECK_IN,
                        CourseType.MEDICATION,
                        LocalTime.of(13, 45),
                        LocalTime.of(13, 55),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        10,
                        null,
                        Set.of(),
                        new CreatePlanAiResponse.MedicationSchedule(
                                30,
                                "AI 복약"
                        ),
                        null
                );

        CreatePlanAiResponse result = finalized(
                response(
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        ),
                        aiMedication,
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(13, 40),
                                10
                        )
                ),
                healthContexts()
        );

        assertEquals(
                List.of(LocalTime.of(14, 0)),
                medications(result)
                        .stream()
                        .map(CreatePlanAiResponse.PlanScheduleDetail::startTime)
                        .toList()
        );

        assertEquals(
                LocalTime.of(14, 10),
                startTime(result, "해운대해수욕장")
        );
    }

    @Test
    @DisplayName("다중 동행인 같은 시각 복약 병합 후 겹침 해소와 반복 적용 동일 결과")
    void resolvesMergedMedicationOverlapIdempotently() {

        List<TravelHealthContext> companions = List.of(
                traveler(
                        "동행인 1",
                        new TravelHealthContext.MedicationInfoContext(
                                "혈당약",
                                MedicationBasis.WITH_MEAL,
                                null,
                                Set.of(
                                        rule(
                                                RelatedMeal.LUNCH,
                                                MealTiming.AFTER_MEAL,
                                                30
                                        )
                                )
                        )
                ),
                traveler(
                        "동행인 2",
                        new TravelHealthContext.MedicationInfoContext(
                                "위장약",
                                MedicationBasis.WITH_MEAL,
                                null,
                                Set.of(
                                        rule(
                                                RelatedMeal.LUNCH,
                                                MealTiming.AFTER_MEAL,
                                                30
                                        )
                                )
                        )
                )
        );

        CreatePlanAiResponse once = finalized(
                response(
                        restaurant(
                                "점심 식당",
                                ScheduleType.LUNCH,
                                LocalTime.of(12, 0),
                                0
                        ),
                        attraction(
                                "해운대해수욕장",
                                LocalTime.of(13, 40),
                                10
                        )
                ),
                companions
        );

        CreatePlanAiResponse twice = scheduleNormalizer.finalizeMedicationSchedules(
                once,
                companions
        );

        assertEquals(
                LocalTime.of(14, 10),
                startTime(once, "해운대해수욕장")
        );

        assertEquals(
                1,
                medications(once).size()
        );

        assertEquals(once, twice);
    }

    private CreatePlanAiResponse medicated(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        return scheduleNormalizer.ensureMedicationSchedules(
                scheduleNormalizer.normalizeScheduleTimes(
                        response,
                        healthContexts
                ),
                healthContexts
        );
    }

    private CreatePlanAiResponse finalized(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        return scheduleNormalizer.finalizeMedicationSchedules(
                scheduleNormalizer.normalizeScheduleTimes(
                        response,
                        healthContexts
                ),
                healthContexts
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail named(
            CreatePlanAiResponse response,
            String name
    ) {

        return response
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(schedule -> name.equals(schedule.locationName()))
                .findFirst()
                .orElseThrow();
    }

    private LocalTime startTime(
            CreatePlanAiResponse response,
            String name
    ) {

        return named(
                response,
                name
        )
                .startTime();
    }

    private LocalTime endTime(
            CreatePlanAiResponse response,
            String name
    ) {

        return named(
                response,
                name
        )
                .endTime();
    }

    private TravelHealthContext.MedicationInfoContext independentMedication(
            String drugName,
            LocalTime medicationTime
    ) {

        return new TravelHealthContext.MedicationInfoContext(
                drugName,
                MedicationBasis.INDEPENDENT,
                medicationTime,
                Set.of()
        );
    }

    private LocalTime medicationStartTime(CreatePlanAiResponse response) {

        return response
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(schedule -> schedule.courseType() == CourseType.MEDICATION)
                .map(CreatePlanAiResponse.PlanScheduleDetail::startTime)
                .findFirst()
                .orElseThrow();
    }

    private List<CreatePlanAiResponse.PlanScheduleDetail> medications(CreatePlanAiResponse response) {

        return response
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(schedule -> schedule.courseType() == CourseType.MEDICATION)
                .toList();
    }

    private TravelHealthContext traveler(
            String name,
            TravelHealthContext.MedicationInfoContext medicationInfo
    ) {

        return new TravelHealthContext(
                name,
                List.of(DiseaseType.DIABETES),
                WalkType.ACTIVE,
                new TravelHealthContext.MealInfoContext(
                        LocalTime.of(8, 0),
                        LocalTime.of(12, 0),
                        LocalTime.of(18, 0)
                ),
                List.of(),
                List.of(medicationInfo)
        );
    }

    private TravelHealthContext.MedicationInfoContext mealMedication(
            String drugName,
            TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext rule
    ) {

        return new TravelHealthContext.MedicationInfoContext(
                drugName,
                MedicationBasis.WITH_MEAL,
                null,
                Set.of(rule)
        );
    }

    private TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext rule(
            RelatedMeal relatedMeal,
            MealTiming mealTiming,
            Integer intervalMinutes
    ) {

        return new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                relatedMeal,
                mealTiming,
                intervalMinutes
        );
    }

    private List<TravelHealthContext> healthContexts() {

        return healthContexts(MealTiming.AFTER_MEAL, 30);
    }

    private List<TravelHealthContext> healthContexts(
            MealTiming mealTiming,
            Integer intervalMinutes
    ) {

        return List.of(
                new TravelHealthContext(
                        "테스트 여행자",
                        List.of(DiseaseType.DIABETES),
                        WalkType.ACTIVE,
                        new TravelHealthContext.MealInfoContext(
                                LocalTime.of(8, 0),
                                LocalTime.of(12, 0),
                                LocalTime.of(18, 0)
                        ),
                        List.of(),
                        List.of(
                                new TravelHealthContext.MedicationInfoContext(
                                        "혈당약",
                                        MedicationBasis.WITH_MEAL,
                                        null,
                                        Set.of(
                                                new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                                                        RelatedMeal.LUNCH,
                                                        mealTiming,
                                                        intervalMinutes
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private CreatePlanAiResponse response(CreatePlanAiResponse.PlanScheduleDetail... schedules) {

        return new CreatePlanAiResponse(
                List.of(
                        new CreatePlanAiResponse.PlanDayDetail(
                                1,
                                date,
                                List.of(schedules)
                        )
                )
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail attraction(
            String name,
            LocalTime startTime,
            Integer travelMinutes
    ) {

        return slot(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                name,
                startTime,
                travelMinutes
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail restaurant(
            String name,
            ScheduleType scheduleType,
            LocalTime startTime,
            Integer travelMinutes
    ) {

        return slot(
                scheduleType,
                CourseType.RESTAURANT,
                name,
                startTime,
                travelMinutes
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail slot(
            ScheduleType scheduleType,
            CourseType courseType,
            String name,
            LocalTime startTime,
            Integer travelMinutes
    ) {

        return slot(
                scheduleType,
                courseType,
                name,
                startTime,
                travelMinutes,
                90
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail slot(
            ScheduleType scheduleType,
            CourseType courseType,
            String name,
            LocalTime startTime,
            Integer travelMinutes,
            int stayMinutes
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                scheduleType,
                courseType,
                startTime,
                startTime.plusMinutes(stayMinutes),
                name,
                "부산 해운대구",
                "129.16",
                "35.16",
                "image-url",
                "thumbnail-url",
                stayMinutes,
                travelMinutes,
                Set.of(RecommendationTag.NATURAL_SCENERY),
                null,
                null
        );
    }
}
