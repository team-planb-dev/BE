package com.planb.unit.domain.travel.service;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationDetail;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.entity.constant.NutritionLevel;
import com.planb.domain.travel.entity.constant.NutritionType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.helper.PlanPlaceHelper;
import com.planb.domain.travel.repository.PlanRepository;
import com.planb.domain.travel.service.PlanService;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private TravelRecommendHandler travelRecommendHandler;

    @Mock
    private KakaoMapServiceHandler kakaoMapServiceHandler;

    @Mock
    private NutritionEvaluationCollector nutritionEvaluationCollector;

    @Mock
    private PlanPlaceHelper planPlaceHelper;

    @InjectMocks
    private PlanService planService;

    @BeforeEach
    void acceptAlreadyValidatedSlots() {

        org.mockito.Mockito
                .lenient()
                .when(kakaoMapServiceHandler.getRoute(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> kakaoMapServiceHandler.getRoute(
                        invocation.<String>getArgument(0),
                        invocation.<String>getArgument(1),
                        invocation.<com.planb.domain.travel.entity.constant.Transportation>getArgument(2)));

        // 복약·태그 후처리 단위 테스트의 장소 검증 경계 대역
        lenient().when(planPlaceHelper.validate(any(), any(), anySet(), anySet()))
                .thenAnswer(invocation -> new PlanPlaceHelper.Validation(invocation.getArgument(0), null));
    }

    @Test
    @DisplayName("Plan 객체 생성")
    void createPlan() {

        Travel travel =
                Travel.builder()
                        .travelName("부산 여행")
                        .build();

        CreatePlanRequest request =
                new CreatePlanRequest(
                        travel,
                        "부산 여행 일정"
                );

        Plan plan =
                planService.createPlan(
                        request
                );

        assertEquals(
                "부산 여행 일정",
                plan.getPlanName()
        );

        assertSame(
                travel,
                plan.getTravel()
        );
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - 카페 중복이 없으면 그대로 반환")
    void makePlanByAiWithoutDuplicateCafe() {

        TravelPlanContext context =
                travelPlanContext();

        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(
                        1,
                        List.of(
                                attraction("해운대해수욕장"),
                                cafe("스타벅스 하버타운점")
                        )
                );

        CreatePlanAiResponse.PlanDayDetail day2 =
                planDay(
                        2,
                        List.of(
                                attraction("이기대"),
                                cafe("이디야커피 부산달맞이점")
                        )
                );

        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of(day1, day2)
                );

        when(
                travelRecommendHandler.createPlanByAi(eq(context), any(PlaceCandidateContext.class))
        ).thenReturn(response);

        when(
                kakaoMapServiceHandler
                        .getRoute(
                                anyString(),
                                anyString(),
                                any(Transportation.class)
                        )
        ).thenReturn(
                Mono.just(
                        new KakaoRouteResult(
                                null,
                                null,
                                null,
                                null
                        )
                )
        );

        CreatePlanAiResponse result =
                planService.makePlanByAi(context);

        // 카페 중복이 없으므로 내용이 그대로 보존된다 (record 값 동등성)
        assertEquals(
                response,
                result
        );

        verify(travelRecommendHandler, never())
                .reselectPlace(any(), any());
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - MEDICATION 슬롯에 MEDICATION_SCHEDULE 태그를 자동으로 부여한다")
    void makePlanByAiAddsMedicationScheduleTag() {

        TravelPlanContext context =
                travelPlanContext();

        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(
                        1,
                        List.of(medicationWithoutTag())
                );

        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of(day1)
                );

        when(
                travelRecommendHandler.createPlanByAi(eq(context), any(PlaceCandidateContext.class))
        ).thenReturn(response);

        when(
                kakaoMapServiceHandler
                        .getRoute(
                                anyString(),
                                anyString(),
                                any(Transportation.class)
                        )
        ).thenReturn(
                Mono.just(
                        new KakaoRouteResult(
                                null,
                                null,
                                null,
                                null
                        )
                )
        );

        CreatePlanAiResponse result =
                planService.makePlanByAi(context);

        assertTrue(
                result.planDays().get(0).schedules().get(0).tags()
                        .contains(RecommendationTag.MEDICATION_SCHEDULE)
        );

        // 요청 시작 시 수집 초기화, AI 호출 이후 결과 회수
        verify(nutritionEvaluationCollector).start();
        verify(nutritionEvaluationCollector).finish();
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - WITH_MEAL/AFTER_MEAL 복약 규칙에 따라 실제 식사시간 기준으로 복약 시각을 재계산한다")
    void makePlanByAiRecalculatesMedicationTimeByMealRule() {

        TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext rule =
                new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                        RelatedMeal.LUNCH,
                        MealTiming.AFTER_MEAL,
                        30
                );

        TravelHealthContext.MedicationInfoContext medicationInfo =
                new TravelHealthContext.MedicationInfoContext(
                        "테스트약",
                        MedicationBasis.WITH_MEAL,
                        LocalTime.of(9, 0),
                        Set.of(rule)
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
                        List.of(),
                        List.of(medicationInfo)
                );

        TravelPlanContext context =
                travelPlanContext(Transportation.TRANSIT, List.of(healthContext));

        CreatePlanAiResponse.PlanScheduleDetail lunch =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        LocalTime.of(12, 0),
                        LocalTime.of(13, 0),
                        "테스트 식당",
                        "부산 해운대구",
                        "129.16",
                        "35.16",
                        "image-url",
                        "thumbnail-url",
                        60,
                        null,
                        Set.of(),
                        null,
                        new CreatePlanAiResponse.RestaurantDetail(
                                "테스트 메뉴",
                                10.0, 100.0, 5.0,
                                "", "부산 해운대구", "129.16", "35.16", "image-url"
                        )
                );

        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(1, List.of(lunch, medicationWithoutTag()));

        CreatePlanAiResponse response = new CreatePlanAiResponse(List.of(day1));

        when(travelRecommendHandler.createPlanByAi(eq(context), any(PlaceCandidateContext.class))).thenReturn(response);

        when(
                kakaoMapServiceHandler
                        .getRoute(anyString(), anyString(), any(Transportation.class))
        ).thenReturn(
                Mono.just(new KakaoRouteResult(null, null, null, null))
        );

        CreatePlanAiResponse result = planService.makePlanByAi(context);

        List<CreatePlanAiResponse.PlanScheduleDetail> medicationSchedules =
                result.planDays().get(0).schedules().stream()
                        .filter(schedule -> schedule.courseType() == CourseType.MEDICATION)
                        .toList();

        assertEquals(1, medicationSchedules.size());

        CreatePlanAiResponse.PlanScheduleDetail medicationSchedule = medicationSchedules.get(0);

        assertEquals(LocalTime.of(12, 30), medicationSchedule.startTime());
        assertEquals(LocalTime.of(12, 40), medicationSchedule.endTime());
        assertTrue(medicationSchedule.medication().description().contains("점심"));
        assertTrue(medicationSchedule.medication().description().contains("식후"));
        assertTrue(medicationSchedule.medication().description().contains("30분"));
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - TRANSPORTATION 슬롯에 여행 요청의 이동수단(TRANSIT) 태그를 자동으로 부여한다")
    void makePlanByAiAddsTransitTagForTransportationSlot() {

        TravelPlanContext context =
                travelPlanContext();

        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(
                        1,
                        List.of(transportationSchedule())
                );

        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of(day1)
                );

        when(
                travelRecommendHandler.createPlanByAi(eq(context), any(PlaceCandidateContext.class))
        ).thenReturn(response);

        CreatePlanAiResponse result =
                planService.makePlanByAi(context);

        assertTrue(
                result.planDays().get(0).schedules().get(0).tags()
                        .contains(RecommendationTag.TRANSIT)
        );
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - TRANSPORTATION 슬롯에 여행 요청의 이동수단(CAR) 태그를 자동으로 부여한다")
    void makePlanByAiAddsCarTagForTransportationSlot() {

        TravelPlanContext context =
                travelPlanContext(Transportation.CAR, List.of());

        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(
                        1,
                        List.of(transportationSchedule())
                );

        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of(day1)
                );

        when(
                travelRecommendHandler.createPlanByAi(eq(context), any(PlaceCandidateContext.class))
        ).thenReturn(response);

        CreatePlanAiResponse result =
                planService.makePlanByAi(context);

        assertTrue(
                result.planDays().get(0).schedules().get(0).tags()
                        .contains(RecommendationTag.CAR)
        );
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - RESTAURANT 메뉴가 지역음식 후보와 일치하면 LOCAL_FOOD 태그를 자동으로 부여한다")
    void makePlanByAiAddsLocalFoodTagWhenMenuMatchesLocalFood() {

        TravelPlanContext context =
                travelPlanContext();

        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(
                        1,
                        List.of(restaurant("돼지국밥"))
                );

        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of(day1)
                );

        when(
                travelRecommendHandler.createPlanByAi(eq(context), any(PlaceCandidateContext.class))
        ).thenReturn(response);

        when(
                kakaoMapServiceHandler
                        .getRoute(
                                anyString(),
                                anyString(),
                                any(Transportation.class)
                        )
        ).thenReturn(
                Mono.just(
                        new KakaoRouteResult(
                                null,
                                null,
                                null,
                                null
                        )
                )
        );

        CreatePlanAiResponse result =
                planService.makePlanByAi(context);

        assertTrue(
                result.planDays().get(0).schedules().get(0).tags()
                        .contains(RecommendationTag.LOCAL_FOOD)
        );
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - 여행자 중 알레르기/기피 음식이 있으면 RESTAURANT 슬롯에 ALLERGY_CHECK 태그를 자동으로 부여한다")
    void makePlanByAiAddsAllergyCheckTagWhenTravelerHasAllergyFood() {

        TravelHealthContext healthContext =
                new TravelHealthContext(
                        "우주",
                        DiseaseType.DIABETES,
                        WalkType.MODERATE,
                        new TravelHealthContext.MealInfoContext(
                                LocalTime.of(8, 0),
                                LocalTime.of(12, 0),
                                LocalTime.of(18, 0)
                        ),
                        List.of(
                                new TravelHealthContext.FoodInfoContext("새우", FoodType.ALLERGY)
                        ),
                        List.of()
                );

        TravelPlanContext context =
                travelPlanContext(Transportation.TRANSIT, List.of(healthContext));

        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(
                        1,
                        List.of(restaurant("제육볶음"))
                );

        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of(day1)
                );

        when(
                travelRecommendHandler.createPlanByAi(eq(context), any(PlaceCandidateContext.class))
        ).thenReturn(response);

        when(
                kakaoMapServiceHandler
                        .getRoute(
                                anyString(),
                                anyString(),
                                any(Transportation.class)
                        )
        ).thenReturn(
                Mono.just(
                        new KakaoRouteResult(
                                null,
                                null,
                                null,
                                null
                        )
                )
        );

        CreatePlanAiResponse result =
                planService.makePlanByAi(context);

        Set<RecommendationTag> tags =
                result.planDays().get(0).schedules().get(0).tags();

        assertTrue(tags.contains(RecommendationTag.ALLERGY_CHECK));

        // 메뉴가 지역음식 후보와 불일치하여 LOCAL_FOOD 미부여
        assertFalse(tags.contains(RecommendationTag.LOCAL_FOOD));
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - 수집된 영양평가 결과 중 CHECK/HIGH 성분만 참고 태그로 부여하고 LOW는 제외한다")
    void makePlanByAiAddsNutritionReferenceTagsFromCollectedEvaluations() {

        TravelPlanContext context =
                travelPlanContext();

        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(
                        1,
                        List.of(restaurant("제육볶음"))
                );

        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of(day1)
                );

        when(
                travelRecommendHandler.createPlanByAi(eq(context), any(PlaceCandidateContext.class))
        ).thenReturn(response);

        NutritionEvaluationResult evaluationResult =
                new NutritionEvaluationResult(
                        DiseaseType.DIABETES,
                        NutritionEvaluationStatus.AVAILABLE,
                        List.of(
                                new NutritionEvaluationDetail(NutritionType.CARBOHYDRATE, NutritionLevel.HIGH),
                                new NutritionEvaluationDetail(NutritionType.SODIUM, NutritionLevel.LOW)
                        ),
                        80.0,
                        300.0,
                        5.0
                );

        when(
                nutritionEvaluationCollector.finish()
        ).thenReturn(
                List.of(
                        new NutritionEvaluationCollector.FoodNutritionEvaluation(
                                "제육볶음",
                                evaluationResult
                        )
                )
        );

        when(
                kakaoMapServiceHandler
                        .getRoute(
                                anyString(),
                                anyString(),
                                any(Transportation.class)
                        )
        ).thenReturn(
                Mono.just(
                        new KakaoRouteResult(
                                null,
                                null,
                                null,
                                null
                        )
                )
        );

        CreatePlanAiResponse result =
                planService.makePlanByAi(context);

        Set<RecommendationTag> tags =
                result.planDays().get(0).schedules().get(0).tags();

        assertTrue(tags.contains(RecommendationTag.CARBOHYDRATE_REFERENCE));
        assertFalse(tags.contains(RecommendationTag.SODIUM_REFERENCE));
    }

    @Test
    @DisplayName("RecommendationTag 집계 - PlanDay 목록의 모든 스케줄 태그를 모은다")
    void aggregateTags() {

        // given
        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(
                        1,
                        List.of(
                                attraction("해운대해수욕장"),
                                cafe("스타벅스 하버타운점")
                        )
                );

        CreatePlanAiResponse.PlanDayDetail day2 =
                planDay(
                        2,
                        List.of(
                                medication()
                        )
                );

        // when
        Set<RecommendationTag> result =
                planService.aggregateTags(
                        List.of(day1, day2)
                );

        // then
        assertEquals(
                Set.of(
                        RecommendationTag.NATURAL_SCENERY,
                        RecommendationTag.REST_POINT,
                        RecommendationTag.MEDICATION_SCHEDULE
                ),
                result
        );
    }

    @Test
    @DisplayName("Plan 객체 저장")
    void savePlan() {

        Plan plan =
                Plan.builder()
                        .planName("부산 여행 일정")
                        .build();

        planService.savePlan(
                plan
        );

        verify(planRepository)
                .save(plan);
    }

    @Test
    @DisplayName("Plan 객체 단건 조회")
    void findPlanById() {

        Long planId = 10L;

        Plan plan =
                Plan.builder()
                        .id(planId)
                        .planName("부산 여행 일정")
                        .build();

        when(
                planRepository
                        .getReferenceById(planId)
        ).thenReturn(
                plan
        );

        Plan result =
                planService.findPlanById(
                        planId
                );

        assertSame(
                plan,
                result
        );
    }

    /*
    테스트 데이터 헬퍼
     */

    private TravelPlanContext travelPlanContext() {
        return travelPlanContext(Transportation.TRANSIT, List.of());
    }

    private TravelPlanContext travelPlanContext(
            Transportation transportation,
            List<TravelHealthContext> healthContexts
    ) {

        CreateTravelRequest createTravelRequest =
                new CreateTravelRequest(
                        "부산 건강 여행",
                        "부산",
                        "해운대구",
                        LocalDate.now().plusDays(7),
                        DateType.ONE_NIGHT_TWO_DAYS,
                        transportation,
                        "해운대",
                        List.of(),
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of("돼지국밥"),
                        List.of("돼지국밥", "밀면", "회")
                );

        return new TravelPlanContext(
                createTravelRequest,
                healthContexts
        );
    }

    private CreatePlanAiResponse.PlanDayDetail planDay(
            int dayNumber,
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules
    ) {

        return new CreatePlanAiResponse.PlanDayDetail(
                dayNumber,
                LocalDate.now().plusDays(6 + dayNumber),
                schedules
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail attraction(String name) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(9, 0),
                LocalTime.of(10, 30),
                name,
                "부산 해운대구",
                "129.16",
                "35.16",
                "image-url",
                "thumbnail-url",
                90,
                null,
                Set.of(RecommendationTag.NATURAL_SCENERY),
                null,
                null
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail cafe(String name) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.CAFE_REST,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                name,
                "부산 해운대구",
                "129.0",
                "35.0",
                null,
                null,
                60,
                null,
                Set.of(RecommendationTag.REST_POINT),
                null,
                null
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail medication() {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.CHECK_IN,
                CourseType.MEDICATION,
                LocalTime.of(12, 0),
                LocalTime.of(12, 0),
                "테스트 복약",
                "",
                null,
                null,
                null,
                null,
                0,
                null,
                Set.of(RecommendationTag.MEDICATION_SCHEDULE),
                new CreatePlanAiResponse.MedicationSchedule(
                        30,
                        "식후 30분 복약"
                ),
                null
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail medicationWithoutTag() {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.CHECK_IN,
                CourseType.MEDICATION,
                LocalTime.of(12, 0),
                LocalTime.of(12, 0),
                "테스트 복약",
                "",
                null,
                null,
                null,
                null,
                0,
                null,
                Set.of(),
                new CreatePlanAiResponse.MedicationSchedule(
                        30,
                        "식후 30분 복약"
                ),
                null
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail transportationSchedule() {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.TRANSPORTATION,
                LocalTime.of(11, 0),
                LocalTime.of(11, 30),
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                30,
                Set.of(),
                null,
                null
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail restaurant(String menuName) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.RESTAURANT,
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                "테스트 음식점",
                "부산 해운대구",
                "129.0",
                "35.0",
                "image-url",
                "thumbnail-url",
                60,
                null,
                Set.of(),
                null,
                new CreatePlanAiResponse.RestaurantDetail(
                        menuName,
                        null,
                        null,
                        null,
                        "10:00~21:00",
                        "부산 해운대구",
                        "129.0",
                        "35.0",
                        "image-url"
                )
        );
    }
}
