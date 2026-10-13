package com.planb.unit.domain.travel.service;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.service.ScheduleNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 직전 식사 조정의 시간·복약 계약
 */
class MealTimeRecoveryContractTest {

    private final ScheduleNormalizer normalizer =
            new ScheduleNormalizer(new PlanPlaceResolver());

    @ParameterizedTest
    @CsvSource({"1, 12", "2, 11"})
    void recoversLateLunchByMovingPreviousMealWithinWindow(
            int day,
            int minutes
    ) {

        CreatePlanAiResponse input = fixture(day);
        CreatePlanAiResponse result = finish(input);

        assertEquals(
                LocalTime.of(8, 0)
                        .minusMinutes(minutes),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .stream()
                        .filter(slot -> slot.scheduleType() == ScheduleType.BREAKFAST)
                        .findFirst()
                        .orElseThrow()
                        .startTime()
        );

        assertEquals(LocalTime.of(12, 30), lunch(result).startTime());
        assertMedicationContract(result);
        assertEquals(result, finish(result));
        List<String> originalNames = input
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .toList();
        List<String> resultNames = result
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(slot -> slot.courseType() != CourseType.MEDICATION)
                .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .toList();
        assertEquals(originalNames, resultNames);
        List<CreatePlanAiResponse.PlanScheduleDetail> resultingPlaces = result
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(slot -> slot.courseType() != CourseType.MEDICATION)
                .toList();
        List<CreatePlanAiResponse.PlanScheduleDetail> originalPlaces = input
                .planDays()
                .getFirst()
                .schedules();
        for (int index = 0; index < originalPlaces.size(); index++) {
            assertEquals(
                    originalPlaces
                            .get(index)
                            .stayMinutes(),
                    resultingPlaces
                            .get(index)
                            .stayMinutes()
            );
            assertEquals(
                    originalPlaces
                            .get(index)
                            .travelMinutes(),
                    resultingPlaces
                            .get(index)
                            .travelMinutes()
            );
            CreatePlanAiResponse.PlanScheduleDetail current = resultingPlaces.get(index);
            assertEquals(
                    current.stayMinutes().longValue(),
                    Duration
                            .between(current.startTime(), current.endTime())
                            .toMinutes()
            );
            if (index > 0 && current.travelMinutes() != null) {
                LocalTime earliest = resultingPlaces
                        .get(index - 1)
                        .endTime()
                        .plusMinutes(current.travelMinutes());
                assertFalse(current.startTime().isBefore(earliest));
            }
        }
        for (CreatePlanAiResponse.PlanScheduleDetail meal : resultingPlaces) {
            LocalTime configured = switch (meal.scheduleType()) {
                case BREAKFAST -> LocalTime.of(8, 0);
                case LUNCH -> LocalTime.of(12, 0);
                case DINNER -> LocalTime.of(18, 0);
                default -> null;
            };
            if (configured != null) {
                assertFalse(meal.startTime().isBefore(configured.minusMinutes(30)));
                assertFalse(meal.startTime().isAfter(configured.plusMinutes(30)));
            }
        }
    }

    @Test
    void recoversAdjacentMealsWithoutChangingTravelTime() {

        CreatePlanAiResponse input = response(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침",
                        "08:00",
                        90,
                        null
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심",
                        "12:00",
                        50,
                        200
                )
        ));
        CreatePlanAiResponse result = normalizer
                .normalizeScheduleTimes(input, contexts());

        assertEquals(
                LocalTime.of(7, 40),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .getFirst()
                        .startTime()
        );
        assertEquals(LocalTime.of(12, 30), lunch(result).startTime());
        assertEquals(200, lunch(result).travelMinutes());
        assertEquals(
                LocalTime.of(8, 0),
                input
                        .planDays()
                        .getFirst()
                        .schedules()
                        .getFirst()
                        .startTime()
        );
    }

    @Test
    void preservesPreviousMealWhenIncomingPlacePreventsEarlierStart() {

        CreatePlanAiResponse input = response(List.of(
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "앞 장소",
                        "07:00",
                        40,
                        null
                ),
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침",
                        "08:00",
                        90,
                        20
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심",
                        "12:00",
                        50,
                        200
                )
        ));
        CreatePlanAiResponse result = normalizer
                .normalizeScheduleTimes(input, contexts());

        assertEquals(
                LocalTime.of(8, 0),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .get(1)
                        .startTime()
        );
        assertEquals(LocalTime.of(12, 50), lunch(result).startTime());
        assertEquals(
                LocalTime.of(7, 0),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .getFirst()
                        .startTime()
        );
    }

    @Test
    void respectsEveryCompanionsMealWindow() {

        TravelHealthContext laterBreakfast = new TravelHealthContext(
                "늦은 아침 동행인",
                List.of(),
                null,
                new TravelHealthContext.MealInfoContext(
                        LocalTime.of(8, 20),
                        LocalTime.of(12, 0),
                        LocalTime.of(18, 0)
                ),
                List.of(),
                List.of()
        );
        CreatePlanAiResponse input = response(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침",
                        "08:00",
                        90,
                        null
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심",
                        "12:00",
                        50,
                        200
                )
        ));
        CreatePlanAiResponse result = normalizer
                .normalizeScheduleTimes(input, List.of(contexts().getFirst(), laterBreakfast));

        assertEquals(
                LocalTime.of(8, 0),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .getFirst()
                        .startTime()
        );
        assertEquals(LocalTime.of(12, 50), lunch(result).startTime());
    }

    @Test
    void shiftsPreviousMealOnlyByMissingSlack() {

        CreatePlanAiResponse input = response(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침",
                        "08:00",
                        90,
                        null
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "관광지",
                        "09:35",
                        90,
                        0
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심",
                        "12:00",
                        50,
                        95
                )
        ));
        CreatePlanAiResponse result = normalizer
                .normalizeScheduleTimes(input, contexts());

        assertEquals(
                LocalTime.of(7, 55),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .getFirst()
                        .startTime()
        );
        assertEquals(
                LocalTime.of(9, 25),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .get(1)
                        .startTime()
        );
        assertEquals(LocalTime.of(12, 30), lunch(result).startTime());
    }

    @Test
    void refusesRecoveryThatMovesBeforeMealMedicationToPreviousDay() {

        List<TravelHealthContext> earlyMeals = List.of(new TravelHealthContext(
                "이른 식사 여행자",
                List.of(),
                null,
                new TravelHealthContext.MealInfoContext(
                        LocalTime.of(0, 45),
                        LocalTime.of(4, 40),
                        null
                ),
                List.of(),
                List.of(new TravelHealthContext.MedicationInfoContext(
                        "식전 복약",
                        MedicationBasis.WITH_MEAL,
                        null,
                        Set.of(new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                                RelatedMeal.BREAKFAST,
                                MealTiming.BEFORE_MEAL,
                                30
                        ))
                ))
        ));
        CreatePlanAiResponse input = response(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침",
                        "00:45",
                        90,
                        null
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심",
                        "04:40",
                        50,
                        200
                )
        ));
        CreatePlanAiResponse normalized = normalizer
                .normalizeScheduleTimes(input, earlyMeals);
        CreatePlanAiResponse result = normalizer
                .finalizeMedicationSchedules(normalized, earlyMeals);

        assertEquals(LocalTime.of(5, 35), lunch(result).startTime());
        assertEquals(
                LocalTime.of(0, 15),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .stream()
                        .filter(schedule -> schedule.courseType() == CourseType.MEDICATION)
                        .findFirst()
                        .orElseThrow()
                        .startTime()
        );
    }

    @Test
    void preservesFixedMedicationWithoutRelatedMealDuringRecovery() {

        TravelHealthContext traveler = contexts()
                .getFirst();
        List<TravelHealthContext> fixedMedication = List.of(new TravelHealthContext(
                traveler.travelerName(),
                traveler.diseaseTypes(),
                traveler.walkType(),
                traveler.mealInfo(),
                traveler.foodInfos(),
                List.of(new TravelHealthContext.MedicationInfoContext(
                        "고정 복약",
                        MedicationBasis.WITH_MEAL,
                        LocalTime.of(14, 0),
                        Set.of(new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                                null,
                                MealTiming.REGARDLESS_OF_MEAL,
                                null
                        ))
                ))
        ));
        CreatePlanAiResponse normalized = normalizer
                .normalizeScheduleTimes(fixture(1), fixedMedication);
        CreatePlanAiResponse result = normalizer
                .finalizeMedicationSchedules(normalized, fixedMedication);

        assertEquals(LocalTime.of(12, 30), lunch(result).startTime());
        assertEquals(
                LocalTime.of(14, 0),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .stream()
                        .filter(schedule -> schedule.courseType() == CourseType.MEDICATION)
                        .findFirst()
                        .orElseThrow()
                        .startTime()
        );
    }

    private CreatePlanAiResponse response(List<CreatePlanAiResponse.PlanScheduleDetail> schedules) {

        return new CreatePlanAiResponse(List.of(
                new CreatePlanAiResponse.PlanDayDetail(
                        1,
                        LocalDate.of(2026, 10, 19),
                        schedules
                )
        ));
    }

    private void assertMedicationContract(CreatePlanAiResponse response) {

        List<CreatePlanAiResponse.PlanScheduleDetail> slots = response
                .planDays()
                .getFirst()
                .schedules();
        List<CreatePlanAiResponse.PlanScheduleDetail> meds = slots
                .stream()
                .filter(slot -> slot.courseType() == CourseType.MEDICATION)
                .toList();
        assertEquals(6, meds.size());

        for (ScheduleType mealType : List.of(ScheduleType.BREAKFAST, ScheduleType.LUNCH, ScheduleType.DINNER)) {
            CreatePlanAiResponse.PlanScheduleDetail meal = slots
                    .stream()
                    .filter(slot -> slot.scheduleType() == mealType)
                    .findFirst()
                    .orElseThrow();
            assertTrue(meds
                    .stream()
                    .anyMatch(med -> med
                            .startTime()
                            .equals(meal
                                    .startTime()
                                    .minusMinutes(30))));
            assertTrue(meds
                    .stream()
                    .anyMatch(med -> med
                            .startTime()
                            .equals(meal
                                    .endTime()
                                    .plusMinutes(30))));
        }

        for (CreatePlanAiResponse.PlanScheduleDetail med : meds) {
            assertEquals(
                    med
                            .startTime()
                            .plusMinutes(10),
                    med.endTime()
            );

        }
    }

    private CreatePlanAiResponse finish(CreatePlanAiResponse input) {

        CreatePlanAiResponse normalized = normalizer
                .normalizeScheduleTimes(
                        input,
                        contexts()
                );
        return normalizer
                .finalizeMedicationSchedules(
                        normalized,
                        contexts()
                );
    }

    private CreatePlanAiResponse.PlanScheduleDetail lunch(CreatePlanAiResponse response) {

        return response
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .findFirst()
                .orElseThrow();
    }

    private List<TravelHealthContext> contexts() {

        Set<TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext> rules =
                java.util.Arrays
                        .stream(RelatedMeal.values())
                        .flatMap(meal -> List.of(MealTiming.BEFORE_MEAL, MealTiming.AFTER_MEAL)
                                .stream()
                                .map(timing -> new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                                        meal,
                                        timing,
                                        30
                                )))
                        .collect(java.util.stream.Collectors.toSet());
        return List.of(new TravelHealthContext(
                "합성 여행자",
                List.of(),
                null,
                new TravelHealthContext.MealInfoContext(
                        LocalTime.of(8, 0),
                        LocalTime.of(12, 0),
                        LocalTime.of(18, 0)
                ),
                List.of(),
                List.of(new TravelHealthContext.MedicationInfoContext(
                        "테스트 복약",
                        MedicationBasis.WITH_MEAL,
                        null,
                        rules
                ))
        ));
    }

    private CreatePlanAiResponse.PlanScheduleDetail slot(
            ScheduleType type,
            CourseType course,
            String name,
            String time,
            int stay,
            Integer travel
    ) {

        LocalTime start = LocalTime.parse(time);
        return new CreatePlanAiResponse.PlanScheduleDetail(
                type,
                course,
                start,
                start.plusMinutes(stay),
                name,
                "서울",
                "127",
                "37",
                null,
                null,
                stay,
                travel,
                Set.of(),
                null,
                null
        );
    }

    private CreatePlanAiResponse fixture(int day) {

        if (day == 1) {
            return new CreatePlanAiResponse(List.of(
                    new CreatePlanAiResponse.PlanDayDetail(
                            day,
                            LocalDate.of(2026, 10, 19),
                            List.of(
                                slot(
                                        ScheduleType.BREAKFAST,
                                        CourseType.LOCAL_FOOD,
                                        "구수옥설렁탕",
                                        "08:00:00",
                                        50,
                                        50
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.MUST_HAVE,
                                        "경복궁",
                                        "09:48:00",
                                        100,
                                        58
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.CAFE_REST,
                                        "테라로사 광화문점",
                                        "11:34:00",
                                        30,
                                        6
                                ),
                                slot(
                                        ScheduleType.LUNCH,
                                        CourseType.RESTAURANT,
                                        "연희동칼국수 본점",
                                        "12:42:00",
                                        50,
                                        38
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.ATTRACTION,
                                        "덕수궁 돌담길",
                                        "14:12:00",
                                        60,
                                        33
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.ATTRACTION,
                                        "창덕궁",
                                        "15:41:00",
                                        100,
                                        29
                                ),
                                slot(
                                        ScheduleType.DINNER,
                                        CourseType.RESTAURANT,
                                        "고려삼계탕",
                                        "18:00:00",
                                        50,
                                        30
                                )
                            )
                    )
            ));
        }
        if (day == 2) {
            return new CreatePlanAiResponse(List.of(
                    new CreatePlanAiResponse.PlanDayDetail(
                            day,
                            LocalDate.of(2026, 10, 20),
                            List.of(
                                slot(
                                        ScheduleType.BREAKFAST,
                                        CourseType.RESTAURANT,
                                        "가산물갈비&백년불고기",
                                        "08:00:00",
                                        50,
                                        48
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.ATTRACTION,
                                        "경희궁공원",
                                        "09:44:00",
                                        80,
                                        54
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.CAFE_REST,
                                        "통인동커피공방 위켄드",
                                        "11:28:00",
                                        30,
                                        24
                                ),
                                slot(
                                        ScheduleType.LUNCH,
                                        CourseType.RESTAURANT,
                                        "청아라 생선구이",
                                        "12:41:00",
                                        50,
                                        43
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.ATTRACTION,
                                        "롯데월드 아쿠아리움",
                                        "14:40:00",
                                        60,
                                        69
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.ATTRACTION,
                                        "덕수궁 대한문",
                                        "16:25:00",
                                        60,
                                        45
                                ),
                                slot(
                                        ScheduleType.DINNER,
                                        CourseType.RESTAURANT,
                                        "광양불고기",
                                        "18:10:00",
                                        50,
                                        45
                                )
                            )
                    )
            ));
        }
        throw new IllegalArgumentException("날짜 범위");
    }
}
