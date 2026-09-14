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

    private final LocalDate date = LocalDate.of(2026, 9, 10);

    @Test
    @DisplayName("이동시간 반영 시간표 정규화의 반복 적용 시 동일 결과")
    void normalizeScheduleTimesIsIdempotent() {

        CreatePlanAiResponse response = response(
                restaurant("아침 식당", ScheduleType.BREAKFAST, LocalTime.of(8, 0), null),
                attraction("해운대해수욕장", LocalTime.of(9, 0), 40),
                restaurant("점심 식당", ScheduleType.LUNCH, LocalTime.of(12, 0), 30),
                attraction("동백섬", LocalTime.of(14, 0), 25)
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
                restaurant("아침 식당", ScheduleType.BREAKFAST, LocalTime.of(8, 0), null),
                restaurant("점심 식당", ScheduleType.LUNCH, LocalTime.of(12, 0), 30),
                restaurant("저녁 식당", ScheduleType.DINNER, LocalTime.of(18, 0), 30)
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
        // 점심을 허용 상한 12:30 이내로 넣을 수 없고 앞당길 장소도 없다.
        CreatePlanAiResponse response = response(
                restaurant("아침 식당", ScheduleType.BREAKFAST, LocalTime.of(8, 0), null),
                restaurant("점심 식당", ScheduleType.LUNCH, LocalTime.of(12, 0), 200)
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

        // 이동시간을 무시한 시각으로 당기지 않는다
        assertEquals(
                breakfast.endTime().plusMinutes(200),
                lunch.startTime()
        );

        // 맞추지 못한 식사는 MEAL_TIME_APPLIED 대상이 아니다
        assertFalse(
                scheduleNormalizer.mealTimeSatisfied(
                        lunch,
                        healthContexts()
                )
        );

        // 맞춘 식사는 대상이다
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

        // 식사 슬롯이 90분이라 시작 기준으로 잡으면 "식후 30분"이 식사 도중이 된다.
        CreatePlanAiResponse response = response(
                restaurant("점심 식당", ScheduleType.LUNCH, LocalTime.of(12, 0), 0)
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

        // 설정 식사시각에는 종료시각이 없으므로 기본 식사 소요시간을 더해 "식후"를 지킨다.
        CreatePlanAiResponse response = response(
                attraction("해운대해수욕장", LocalTime.of(9, 0), 0)
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
    @DisplayName("장소 시간대 안에 들어간 복약의 장소 종료시각 배치")
    void movesMedicationOutOfPlaceSlot() {

        // 식후 30분은 14:00이지만 그 시각은 다음 관광지 13:40-15:10 한가운데다.
        CreatePlanAiResponse response = response(
                restaurant("점심 식당", ScheduleType.LUNCH, LocalTime.of(12, 0), 0),
                attraction("해운대해수욕장", LocalTime.of(13, 40), 10)
        );

        CreatePlanAiResponse result = scheduleNormalizer.ensureMedicationSchedules(
                scheduleNormalizer.normalizeScheduleTimes(response, healthContexts()),
                healthContexts());

        assertEquals(
                LocalTime.of(15, 10),
                medicationStartTime(result)
        );
    }

    @Test
    @DisplayName("식중 복약의 식사 시간대 유지")
    void keepsDuringMealMedicationInsideMealSlot() {

        // 식사 슬롯은 식중 복약의 기준이므로 겹쳐도 밀지 않는다.
        List<TravelHealthContext> duringMeal = healthContexts(MealTiming.DURING_MEAL, 0);

        CreatePlanAiResponse response = response(
                restaurant("점심 식당", ScheduleType.LUNCH, LocalTime.of(12, 0), 0)
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
                        DiseaseType.DIABETES,
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
