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
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        CreatePlanAiResponse result = scheduleNormalizer.ensureMedicationSchedules(
                scheduleNormalizer.normalizeScheduleTimes(response, healthContexts()),
                healthContexts());

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

        CreatePlanAiResponse result = scheduleNormalizer.ensureMedicationSchedules(
                scheduleNormalizer.normalizeScheduleTimes(response, beforeMeal),
                beforeMeal);

        assertEquals(
                LocalTime.of(11, 30),
                medicationStartTime(result)
        );
    }

    @Test
    @DisplayName("간격 0·누락 식후 복약의 식사 종료시각 유지")
    void keepsZeroIntervalAtMealEnd() {

        // 점심 종료 13:30과 같은 시각에 시작하는 관광지
        for (Integer interval : new Integer[]{0, null}) {
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

            CreatePlanAiResponse result = scheduleNormalizer.ensureMedicationSchedules(
                    scheduleNormalizer.normalizeScheduleTimes(response, zeroInterval),
                    zeroInterval);

            assertEquals(
                    LocalTime.of(13, 30),
                    medicationStartTime(result)
            );
        }
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

        CreatePlanAiResponse result = scheduleNormalizer.ensureMedicationSchedules(
                scheduleNormalizer.normalizeScheduleTimes(response, independent),
                independent);

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

        CreatePlanAiResponse result = scheduleNormalizer.ensureMedicationSchedules(
                scheduleNormalizer.normalizeScheduleTimes(response, companions),
                companions);

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

        CreatePlanAiResponse once = scheduleNormalizer.ensureMedicationSchedules(
                scheduleNormalizer.normalizeScheduleTimes(response, healthContexts()),
                healthContexts());

        CreatePlanAiResponse twice = scheduleNormalizer.ensureMedicationSchedules(
                once,
                healthContexts());

        assertEquals(once, twice);
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

        return new CreatePlanAiResponse.PlanScheduleDetail(
                scheduleType,
                courseType,
                startTime,
                startTime.plusMinutes(90),
                name,
                "부산 해운대구",
                "129.16",
                "35.16",
                "image-url",
                "thumbnail-url",
                90,
                travelMinutes,
                Set.of(RecommendationTag.NATURAL_SCENERY),
                null,
                null
        );
    }
}
