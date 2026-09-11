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
import static org.mockito.Mockito.mock;

class ScheduleNormalizerTest {

    private final ScheduleNormalizer scheduleNormalizer =
            new ScheduleNormalizer(new PlanPlaceResolver(mock(KakaoMapServiceHandler.class)));

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

    private List<TravelHealthContext> healthContexts() {

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
                                                        MealTiming.AFTER_MEAL,
                                                        30
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
