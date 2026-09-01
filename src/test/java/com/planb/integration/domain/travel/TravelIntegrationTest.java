package com.planb.integration.domain.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.service.PlanService;
import com.planb.integration.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TravelRecommendHandlerTest는 travelRecommendHandler.createPlanByAi(...)를 직접 호출하므로
 * PlanService.makePlanByAi의 카페 중복 보정 로직(ensureUniqueCafes)을 거치지 않는다.
 * 이 테스트는 실제 PlanService.makePlanByAi를 통해 그 보정 로직까지 포함한
 * 최종 응답 기준으로 카페 중복이 없는지 검증한다.
  */
@Tag("external")
class TravelIntegrationTest extends IntegrationTest {

    @Autowired
    private PlanService planService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("실제 OpenAI 및 외부 API 기반 일정 생성 - PlanService 결과에는 카페 중복이 없다")
    void makePlanByAiHasNoDuplicateCafe() throws Exception {

        // given
        LocalDate startDate =
                LocalDate.now().plusDays(7);

        CreateTravelRequest createTravelRequest =
                new CreateTravelRequest(
                        "부산 건강 여행",
                        "부산",
                        "해운대구",
                        startDate,
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.TRANSIT,
                        "해운대",
                        List.of(
                                new CreateTravelRequest.PlannedPlaceDetail(
                                        "해운대해수욕장",
                                        "부산광역시 해운대구"
                                )
                        ),
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of("돼지국밥"),
                        List.of(
                                "돼지국밥",
                                "밀면",
                                "회"
                        )
                );

        TravelHealthContext healthContext =
                new TravelHealthContext(
                        "테스트 여행자",
                        DiseaseType.DIABETES,
                        WalkType.MODERATE,

                        new TravelHealthContext.MealInfoContext(
                                LocalTime.of(8, 0),
                                LocalTime.of(12, 0),
                                LocalTime.of(18, 0)
                        ),

                        List.of(
                                new TravelHealthContext.FoodInfoContext(
                                        "새우",
                                        FoodType.ALLERGY
                                ),
                                new TravelHealthContext.FoodInfoContext(
                                        "과도하게 단 음식",
                                        FoodType.AVOID
                                )
                        ),

                        List.of(
                                new TravelHealthContext.MedicationInfoContext(
                                        "테스트 복약",
                                        MedicationBasis.WITH_MEAL,
                                        LocalTime.of(12, 30),
                                        Set.of(
                                                new TravelHealthContext
                                                        .MedicationInfoContext
                                                        .MealMedicationRuleContext(
                                                        RelatedMeal.LUNCH,
                                                        MealTiming.AFTER_MEAL,
                                                        30
                                                )
                                        )
                                )
                        )
                );

        TravelPlanContext travelPlanContext =
                new TravelPlanContext(
                        createTravelRequest,
                        List.of(healthContext)
                );

        // when
        CreatePlanAiResponse response =
                makePlanByAiWithRetry(travelPlanContext);

        // then
        System.out.println(
                "===== PlanService.makePlanByAi (카페 중복 보정 후) 응답 ====="
        );

        System.out.println(
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(response)
        );

        assertThat(response)
                .isNotNull();

        assertThat(response.planDays())
                .isNotNull()
                .hasSize(2);

        List<String> cafeLocationNames =
                response.planDays().stream()
                        .flatMap(planDay -> planDay.schedules().stream())
                        .filter(schedule -> schedule.courseType() == CourseType.CAFE_REST)
                        .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                        .toList();

        assertThat(cafeLocationNames)
                .doesNotHaveDuplicates();
    }

    // AI가 간헐적으로 깨진(중복 키) JSON을 반환해 파싱이 실패하는 경우를 흡수하기 위한 재시도
    private CreatePlanAiResponse makePlanByAiWithRetry(
            TravelPlanContext travelPlanContext) {

        int maxAttempts = 3;
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return planService.makePlanByAi(travelPlanContext);
            } catch (RuntimeException e) {
                lastException = e;

                System.out.println(
                        "AI 응답 파싱 실패로 재시도합니다 (" + attempt + "/" + maxAttempts + "). 원인: " + e
                );
            }
        }

        throw lastException;
    }
}
