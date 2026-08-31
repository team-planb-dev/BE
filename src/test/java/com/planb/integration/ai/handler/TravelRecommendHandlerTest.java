package com.planb.integration.ai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
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

@Tag("external")
class TravelRecommendHandlerTest extends IntegrationTest {

    @Autowired
    private TravelRecommendHandler travelRecommendHandler;

    @Autowired
    private ObjectMapper objectMapper;


    // 실제 OpenAI API를 통한 지역 대표 음식 추천
    @Test
    @DisplayName("실제 OpenAI API 지역 대표 음식 추천")
    void makeRecommendFoodWithRealOpenAi() throws Exception {

        // given
        MakeRecommendFoodsRequest makeRecommendFoodsRequest =
                new MakeRecommendFoodsRequest(
                        "부산",
                        "해운대구"
                );

        MakeFoodRecommendCallRequest request =
                new MakeFoodRecommendCallRequest(
                        makeRecommendFoodsRequest
                );

        // when
        MakeRecommendFoodResponse response =
                travelRecommendHandler
                        .makeRecommendFood(request);

        // then
        System.out.println(
                "===== OpenAI 지역 음식 추천 응답 ====="
        );

        System.out.println(
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(response)
        );

        assertThat(response)
                .isNotNull();

        assertThat(response.foods())
                .isNotNull()
                .isNotEmpty()
                .hasSize(5);
    }


    // 실제 OpenAI 및 외부 API를 통한 건강정보 기반 여행 일정 생성
    @Test
    @DisplayName("실제 OpenAI 및 외부 API 건강정보 기반 여행 일정 생성")
    void createPlanByAiWithRealOpenAiAndExternalApi() throws Exception {

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

        System.out.println(
                "===== OpenAI 여행 일정 생성 요청 Context ====="
        );

        System.out.println(
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(travelPlanContext)
        );

        // when
        CreatePlanAiResponse response =
                travelRecommendHandler
                        .createPlanByAi(travelPlanContext);

        // then
        System.out.println(
                "===== OpenAI 최종 여행 일정 응답 ====="
        );

        System.out.println(
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(response)
        );

        assertThat(response)
                .isNotNull();

        assertThat(response.planName())
                .isNotNull()
                .isNotBlank();

        assertThat(response.description())
                .isNotNull()
                .isNotBlank();

        assertThat(response.planDays())
                .isNotNull()
                .hasSize(2);

        assertThat(response.planDays())
                .allSatisfy(planDay -> {

                    assertThat(planDay.dayNumber())
                            .isNotNull();

                    assertThat(planDay.date())
                            .isNotNull();

                    assertThat(planDay.schedules())
                            .isNotNull()
                            .isNotEmpty();
                });
    }
}