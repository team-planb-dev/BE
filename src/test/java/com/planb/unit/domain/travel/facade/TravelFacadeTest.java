package com.planb.unit.domain.travel.facade;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.health.entity.FoodInfo;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.MedicationInfo;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.health.entity.vo.HealthInfo;
import com.planb.domain.health.entity.vo.MealInfo;
import com.planb.domain.health.entity.vo.MealMedicationRule;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.travel.dto.request.CreatePlanDayRequest;
import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.dto.request.CreatePlannedPlaceRequest;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.GetAiPlanRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;
import com.planb.domain.travel.dto.request.SearchPlannedPlaceRequest;
import com.planb.domain.travel.dto.response.CreatePlanResponse;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.PlannedPlace;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.RestaurantDetail;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.facade.TravelFacade;
import com.planb.domain.travel.service.PlannedPlaceService;
import com.planb.domain.travel.service.PlanDayService;
import com.planb.domain.travel.service.PlanEditCacheService;
import com.planb.domain.travel.service.PlanScheduleService;
import com.planb.domain.travel.service.PlanService;
import com.planb.domain.travel.service.RestaurantDetailService;
import com.planb.domain.travel.service.TravelService;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.config.exception.domain.ForbiddenException;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.query.health.service.HealthQueryService;
import com.planb.query.health.service.MedicationInfoQueryService;
import com.planb.query.travel.dto.response.PlanDayQueryResponse;
import com.planb.query.travel.dto.response.PlanQueryResponse;
import com.planb.query.travel.dto.response.RestaurantDetailQueryResponse;
import com.planb.query.travel.dto.response.TravelConditionQueryResponse;
import com.planb.query.travel.service.PlanDayQueryService;
import com.planb.query.travel.service.PlanQueryService;
import com.planb.query.travel.service.PlanScheduleQueryService;
import com.planb.query.travel.service.RestaurantDetailQueryService;
import com.planb.query.travel.service.TravelQueryService;
import com.planb.query.user.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelFacadeTest {

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private HealthService healthService;

    @Mock
    private FoodInfoService foodInfoService;

    @Mock
    private MedicationInfoService medicationInfoService;

    @Mock
    private HealthQueryService healthQueryService;

    @Mock
    private MedicationInfoQueryService medicationInfoQueryService;

    @Mock
    private TravelService travelService;

    @Mock
    private PlannedPlaceService plannedPlaceService;

    @Mock
    private PlanService planService;

    @Mock
    private PlanDayService planDayService;

    @Mock
    private PlanScheduleService planScheduleService;

    @Mock
    private RestaurantDetailService restaurantDetailService;

    @Mock
    private PlanEditCacheService planEditCacheService;

    @Mock
    private TravelQueryService travelQueryService;

    @Mock
    private PlanQueryService planQueryService;

    @Mock
    private PlanScheduleQueryService planScheduleQueryService;

    @Mock
    private PlanDayQueryService planDayQueryService;

    @Mock
    private RestaurantDetailQueryService restaurantDetailQueryService;

    @InjectMocks
    private TravelFacade travelFacade;

    @Test
    @DisplayName("AI로 해당 지역 추천음식 키워드 받기")
    void showRecommendFoods() {

        // given
        MakeRecommendFoodsRequest request =
                new MakeRecommendFoodsRequest(
                        "부산광역시",
                        "해운대구"
                );

        MakeRecommendFoodResponse response =
                new MakeRecommendFoodResponse(
                        List.of(
                                "돼지국밥",
                                "밀면"
                        )
                );

        when(
                travelService
                        .makeRecommendFoodResponse(
                                request
                        )
        ).thenReturn(
                response
        );

        // when
        MakeRecommendFoodResponse result =
                travelFacade.showRecommendFoods(
                        request
                );

        // then
        assertThat(
                result
        ).isSameAs(
                response
        );

        verify(
                travelService
        ).makeRecommendFoodResponse(
                request
        );
    }

    @Test
    @DisplayName("Kor2Service API로 키워드에 따른 숙박,관광지 검색하기")
    void searchPlannedPlaceByText() {

        // given
        SearchPlannedPlaceRequest request =
                new SearchPlannedPlaceRequest(
                        "해운대"
                );

        SearchPlannedPlaceResponse response =
                new SearchPlannedPlaceResponse(
                        List.of(
                                new SearchPlannedPlaceResponse.PlannedPlaceDetail(
                                        "해운대해수욕장",
                                        "부산광역시 해운대구"
                                )
                        )
                );

        when(
                plannedPlaceService
                        .searchPlannedPlace(
                                request
                        )
        ).thenReturn(
                Mono.just(
                        response
                )
        );

        // when
        SearchPlannedPlaceResponse result =
                travelFacade.searchPlannedPlaceByText(
                        request
                ).block();

        // then
        assertThat(
                result
        ).isSameAs(
                response
        );

        verify(
                plannedPlaceService
        ).searchPlannedPlace(
                request
        );
    }

    @Test
    @DisplayName("사용자 입력 받은 후, 해당 데이터 기반으로 여행일정 생성하기")
    void makeTravelOptionsAndRecommend() {

        // given
        Long userId = 1L;
        String username = "testUser@example.com";

        UserAuthCache userAuthCache =
                new UserAuthCache(
                        userId,
                        username,
                        "ROLE_USER"
                );

        CreateTravelRequest createTravelRequest =
                new CreateTravelRequest(
                        "부산 여행",
                        "부산광역시",
                        "해운대구",
                        LocalDate.of(2026, 9, 1),
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.CAR,
                        "해운대해수욕장",
                        List.of(
                                new CreateTravelRequest.PlannedPlaceDetail(
                                        "해운대해수욕장",
                                        "부산광역시 해운대구"
                                )
                        ),
                        TravelStyle.LESS_WALK,
                        TravelTheme.TASTE,
                        List.of("돼지국밥"),
                        List.of("밀면")
                );

        Travel travel =
                Travel.builder()
                        .id(1L)
                        .travelName("부산 여행")
                        .locationDo("부산광역시")
                        .locationSigungu("해운대구")
                        .startDate(LocalDate.of(2026, 9, 1))
                        .endDate(LocalDate.of(2026, 9, 2))
                        .dateType(DateType.ONE_NIGHT_TWO_DAYS)
                        .transportation(Transportation.CAR)
                        .decidedLocation("해운대해수욕장")
                        .travelStyle(TravelStyle.LESS_WALK)
                        .travelTheme(TravelTheme.TASTE)
                        .localFoods(List.of("돼지국밥"))
                        .recommendFoods(List.of("밀면"))
                        .build();

        List<PlannedPlace> plannedPlaces =
                List.of(
                        PlannedPlace.builder()
                                .travel(travel)
                                .locationName("해운대해수욕장")
                                .location("부산광역시 해운대구")
                                .build()
                );

        Plan plan =
                Plan.builder()
                        .id(10L)
                        .travel(travel)
                        .planName("부산 여행")
                        .build();

        Health health =
                Health.builder()
                        .id(100L)
                        .travelerName("본인")
                        .sensitiveAgree(true)
                        .hasMedication(true)
                        .healthInfo(
                                new HealthInfo(
                                        DiseaseType.DIABETES,
                                        WalkType.MODERATE
                                )
                        )
                        .mealInfo(
                                new MealInfo(
                                        true,
                                        true,
                                        LocalTime.of(8, 0),
                                        true,
                                        LocalTime.of(12, 0),
                                        true,
                                        LocalTime.of(18, 0)
                                )
                        )
                        .build();

        List<FoodInfo> foodInfos =
                List.of(
                        FoodInfo.builder()
                                .health(health)
                                .foodName("새우")
                                .foodType(FoodType.ALLERGY)
                                .build()
                );

        List<MedicationInfo> medicationInfos =
                List.of(
                        MedicationInfo.builder()
                                .health(health)
                                .drugName("혈압약")
                                .medicationBasis(MedicationBasis.WITH_MEAL)
                                .mealMedicationRules(
                                        Set.of(
                                                new MealMedicationRule(
                                                        RelatedMeal.LUNCH,
                                                        MealTiming.AFTER_MEAL,
                                                        30
                                                )
                                        )
                                )
                                .build()
                );

        List<TravelHealthContext> healthContexts =
                List.of(
                        TravelHealthContext.from(
                                health,
                                foodInfos,
                                medicationInfos
                        )
                );

        CreatePlanAiResponse.RestaurantDetail aiRestaurantDetail =
                new CreatePlanAiResponse.RestaurantDetail(
                        "돼지국밥",
                        50.0,
                        800.0,
                        15.0,
                        "09:00 ~ 21:00",
                        "부산광역시 부산진구",
                        "129.0756",
                        "35.1795",
                        "restaurant.jpg"
                );

        CreatePlanAiResponse.PlanScheduleDetail scheduleDetail =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        LocalTime.of(12, 0),
                        LocalTime.of(13, 0),
                        "부산돼지국밥",
                        "부산광역시 부산진구",
                        "129.0756",
                        "35.1795",
                        "image-url",
                        "thumbnail-url",
                        60,
                        20,
                        Set.of(RecommendationTag.LOCAL_FOOD),
                        null,
                        aiRestaurantDetail
                );

        CreatePlanAiResponse.PlanDayDetail planDayDetail =
                new CreatePlanAiResponse.PlanDayDetail(
                        1,
                        LocalDate.of(2026, 9, 1),
                        List.of(scheduleDetail)
                );

        CreatePlanAiResponse createPlanAiResponse =
                new CreatePlanAiResponse(
                        List.of(planDayDetail)
                );

        Set<RecommendationTag> aggregatedTags =
                Set.of(RecommendationTag.LOCAL_FOOD);

        PlanDay planDay =
                PlanDay.builder()
                        .id(1000L)
                        .plan(plan)
                        .dayNumber(1)
                        .planDate(LocalDate.of(2026, 9, 1))
                        .build();

        List<PlanSchedule> planSchedules =
                List.of(
                        PlanSchedule.builder()
                                .id(10000L)
                                .planDay(planDay)
                                .scheduleType(ScheduleType.LUNCH)
                                .courseType(CourseType.RESTAURANT)
                                .locationName("부산돼지국밥")
                                .build()
                );

        List<RestaurantDetail> restaurantDetails =
                List.of(
                        RestaurantDetail.builder()
                                .planSchedule(planSchedules.get(0))
                                .menuName("돼지국밥")
                                .carbohydrate(50.0)
                                .sodium(800.0)
                                .fat(15.0)
                                .build()
                );

        when(
                userQueryService
                        .findByUsernameInCache(
                                username
                        )
        ).thenReturn(
                userAuthCache
        );

        when(
                travelService
                        .createTravel(
                                createTravelRequest,
                                userId
                        )
        ).thenReturn(
                travel
        );

        when(
                plannedPlaceService
                        .makePlannedPlace(
                                CreatePlannedPlaceRequest.from(
                                        travel,
                                        createTravelRequest
                                )
                        )
        ).thenReturn(
                plannedPlaces
        );

        when(
                planService
                        .createPlan(
                                new CreatePlanRequest(
                                        travel,
                                        createTravelRequest.travelName()
                                )
                        )
        ).thenReturn(
                plan
        );

        when(
                healthService
                        .getHealthListByUserId(
                                userId
                        )
        ).thenReturn(
                List.of(health)
        );

        when(
                foodInfoService
                        .getFoodInfoList(
                                health.getId()
                        )
        ).thenReturn(
                foodInfos
        );

        when(
                medicationInfoService
                        .findAllByHealthId(
                                health.getId()
                        )
        ).thenReturn(
                medicationInfos
        );

        when(
                planService
                        .makePlanByAi(
                                new TravelPlanContext(
                                        createTravelRequest,
                                        healthContexts
                                )
                        )
        ).thenReturn(
                createPlanAiResponse
        );

        when(
                planService
                        .aggregateTags(
                                createPlanAiResponse.planDays()
                        )
        ).thenReturn(
                aggregatedTags
        );

        when(
                planDayService
                        .createPlanDay(
                                new CreatePlanDayRequest(
                                        plan,
                                        planDayDetail.dayNumber(),
                                        planDayDetail.date()
                                )
                        )
        ).thenReturn(
                planDay
        );

        when(
                planScheduleService
                        .makePlanScheduleList(
                                planDay,
                                planDayDetail.schedules()
                        )
        ).thenReturn(
                planSchedules
        );

        when(
                restaurantDetailService
                        .makeRestaurantDetailList(
                                planSchedules,
                                planDayDetail.schedules()
                        )
        ).thenReturn(
                restaurantDetails
        );

        // when
        CreatePlanResponse result =
                travelFacade.makeTravelOptionsAndRecommend(
                        createTravelRequest,
                        username
                );

        // then
        assertThat(
                result.tags()
        ).isEqualTo(
                aggregatedTags
        );

        assertThat(
                result.planDays()
        ).isSameAs(
                createPlanAiResponse.planDays()
        );

        assertThat(
                plan.getTags()
        ).isEqualTo(
                aggregatedTags
        );

        verify(
                travelService
        ).saveTravel(
                travel
        );

        verify(
                plannedPlaceService
        ).savePlannedPlaceList(
                plannedPlaces
        );

        verify(
                planService,
                times(2)
        ).savePlan(
                plan
        );

        verify(
                planDayService
        ).savePlanDay(
                planDay
        );

        verify(
                planScheduleService
        ).savePlanScheduleAll(
                planSchedules
        );

        verify(
                restaurantDetailService
        ).saveRestaurantDetailAll(
                restaurantDetails
        );
    }

    @Test
    @DisplayName("Travel ID와 Username을 기준으로 AI 여행일정 전체 조회")
    void getAiPlan() {

        // given
        Long travelId = 1L;
        Long userId = 1L;
        String username = "testUser@example.com";
        Long planId = 10L;
        Long planDayId = 100L;
        Long planScheduleId = 1000L;

        GetAiPlanRequest request =
                new GetAiPlanRequest(
                        travelId
                );

        UserAuthCache userAuthCache =
                new UserAuthCache(
                        userId,
                        username,
                        "ROLE_USER"
                );

        TravelConditionQueryResponse travelCondition =
                new TravelConditionQueryResponse(
                        TravelStyle.LESS_WALK,
                        TravelTheme.TASTE
                );

        List<HealthSummaryQueryResponse> healthSummaries =
                List.of(
                        new HealthSummaryQueryResponse(
                                1L,
                                "동행인1",
                                true,
                                DiseaseType.DIABETES,
                                false
                        ),
                        new HealthSummaryQueryResponse(
                                2L,
                                "동행인2",
                                true,
                                DiseaseType.HIGH_BLOOD_PRESSURE,
                                false
                        )
                );

        List<LocalTime> medicationTimes =
                List.of(
                        LocalTime.of(8, 0),
                        LocalTime.of(20, 0)
                );

        PlanQueryResponse plan =
                new PlanQueryResponse(
                        planId,
                        "부산 여행",
                        Set.of(
                                RecommendationTag.MEAL_TIME_APPLIED,
                                RecommendationTag.LOCAL_FOOD
                        )
                );

        PlanDayQueryResponse planDay =
                new PlanDayQueryResponse(
                        planDayId,
                        1,
                        LocalDate.of(
                                2026,
                                9,
                                1
                        )
                );

        Plan planEntity =
                Plan.builder()
                        .id(planId)
                        .planName("부산 여행")
                        .build();

        PlanDay planDayEntity =
                PlanDay.builder()
                        .id(planDayId)
                        .plan(planEntity)
                        .dayNumber(1)
                        .planDate(
                                LocalDate.of(
                                        2026,
                                        9,
                                        1
                                )
                        )
                        .build();

        PlanSchedule planSchedule =
                PlanSchedule.builder()
                        .id(planScheduleId)
                        .planDay(planDayEntity)
                        .scheduleType(
                                ScheduleType.LUNCH
                        )
                        .courseType(
                                CourseType.RESTAURANT
                        )
                        .startTime(
                                LocalTime.of(12, 0)
                        )
                        .endTime(
                                LocalTime.of(13, 0)
                        )
                        .locationName(
                                "부산 식당"
                        )
                        .location(
                                "부산광역시"
                        )
                        .imageUrl(
                                "image.jpg"
                        )
                        .thumbNailImageUrl(
                                "thumbnail.jpg"
                        )
                        .stayMinutes(
                                60
                        )
                        .travelMinutes(
                                20
                        )
                        .tags(
                                Set.of(
                                        RecommendationTag.LOCAL_FOOD
                                )
                        )
                        .build();

        RestaurantDetailQueryResponse restaurantDetail =
                new RestaurantDetailQueryResponse(
                        planScheduleId,
                        "돼지국밥",
                        50.0,
                        800.0,
                        15.0,
                        "09:00 ~ 21:00",
                        "부산광역시 해운대구",
                        "129.1604",
                        "35.1631",
                        "restaurant.jpg"
                );

        when(
                userQueryService
                        .findByUsernameInCache(
                                username
                        )
        ).thenReturn(
                userAuthCache
        );

        when(
                travelQueryService
                        .existsByIdAndUserId(
                                travelId,
                                userId
                        )
        ).thenReturn(
                true
        );

        when(
                travelQueryService
                        .getTravelConditionQueryResponse(
                                travelId
                        )
        ).thenReturn(
                travelCondition
        );

        when(
                healthQueryService
                        .getHealthSummaryList(
                                userId
                        )
        ).thenReturn(
                healthSummaries
        );

        when(
                medicationInfoQueryService
                        .getMedicationTimes(
                                userId
                        )
        ).thenReturn(
                medicationTimes
        );

        when(
                planQueryService
                        .getPlanByTravelId(
                                travelId
                        )
        ).thenReturn(
                plan
        );

        when(
                planDayQueryService
                        .getPlanDaysByPlanId(
                                planId
                        )
        ).thenReturn(
                List.of(
                        planDay
                )
        );

        when(
                planScheduleQueryService
                        .getPlanSchedulesByPlanDayIds(
                                List.of(
                                        planDayId
                                )
                        )
        ).thenReturn(
                List.of(
                        planSchedule
                )
        );

        when(
                restaurantDetailQueryService
                        .getRestaurantDetailsByPlanScheduleIds(
                                List.of(
                                        planScheduleId
                                )
                        )
        ).thenReturn(
                List.of(
                        restaurantDetail
                )
        );

        // when
        GetAiPlanResponse result =
                travelFacade.getAiPlan(
                        request,
                        username
                );

        // then
        assertThat(
                result.planName()
        ).isEqualTo(
                "부산 여행"
        );

        assertThat(
                result.travelStyle()
        ).isEqualTo(
                TravelStyle.LESS_WALK
        );

        assertThat(
                result.travelTheme()
        ).isEqualTo(
                TravelTheme.TASTE
        );

        assertThat(
                result.diseaseTypes()
        ).containsExactlyInAnyOrder(
                DiseaseType.DIABETES,
                DiseaseType.HIGH_BLOOD_PRESSURE
        );

        assertThat(
                result.medicationTimes()
        ).containsExactly(
                LocalTime.of(8, 0),
                LocalTime.of(20, 0)
        );

        assertThat(
                result.tags()
        ).containsExactlyInAnyOrder(
                RecommendationTag.MEAL_TIME_APPLIED,
                RecommendationTag.LOCAL_FOOD
        );

        assertThat(
                result.planDays()
        ).hasSize(1);

        GetAiPlanResponse.PlanDayDetail resultPlanDay =
                result.planDays()
                        .get(0);

        assertThat(
                resultPlanDay.dayNumber()
        ).isEqualTo(
                1
        );

        assertThat(
                resultPlanDay.date()
        ).isEqualTo(
                LocalDate.of(
                        2026,
                        9,
                        1
                )
        );

        assertThat(
                resultPlanDay.schedules()
        ).hasSize(1);

        GetAiPlanResponse.PlanScheduleDetail resultSchedule =
                resultPlanDay.schedules()
                        .get(0);

        assertThat(
                resultSchedule.scheduleType()
        ).isEqualTo(
                ScheduleType.LUNCH
        );

        assertThat(
                resultSchedule.courseType()
        ).isEqualTo(
                CourseType.RESTAURANT
        );

        assertThat(
                resultSchedule.locationName()
        ).isEqualTo(
                "부산 식당"
        );

        assertThat(
                resultSchedule.tags()
        ).containsExactly(
                RecommendationTag.LOCAL_FOOD
        );

        assertThat(
                resultSchedule.restaurantDetail()
        ).isNotNull();

        assertThat(
                resultSchedule
                        .restaurantDetail()
                        .menuName()
        ).isEqualTo(
                "돼지국밥"
        );

        assertThat(
                resultSchedule
                        .restaurantDetail()
                        .carbohydrate()
        ).isEqualTo(
                50.0
        );

        assertThat(
                resultSchedule
                        .restaurantDetail()
                        .sodium()
        ).isEqualTo(
                800.0
        );

        assertThat(
                resultSchedule
                        .restaurantDetail()
                        .fat()
        ).isEqualTo(
                15.0
        );

        verify(
                userQueryService
        ).findByUsernameInCache(
                username
        );

        verify(
                travelQueryService
        ).existsByIdAndUserId(
                travelId,
                userId
        );

        verify(
                travelQueryService
        ).getTravelConditionQueryResponse(
                travelId
        );

        verify(
                healthQueryService
        ).getHealthSummaryList(
                userId
        );

        verify(
                medicationInfoQueryService
        ).getMedicationTimes(
                userId
        );

        verify(
                planQueryService
        ).getPlanByTravelId(
                travelId
        );

        verify(
                planDayQueryService
        ).getPlanDaysByPlanId(
                planId
        );

        verify(
                planScheduleQueryService
        ).getPlanSchedulesByPlanDayIds(
                List.of(
                        planDayId
                )
        );

        verify(
                restaurantDetailQueryService
        ).getRestaurantDetailsByPlanScheduleIds(
                List.of(
                        planScheduleId
                )
        );
    }

    @Test
    @DisplayName("Travel 소유자가 아니면 접근이 거부된다")
    void getAiPlanThrowsForbiddenWhenNotOwner() {

        // given
        Long travelId = 1L;
        Long userId = 1L;
        String username = "testUser@example.com";

        GetAiPlanRequest request =
                new GetAiPlanRequest(
                        travelId
                );

        UserAuthCache userAuthCache =
                new UserAuthCache(
                        userId,
                        username,
                        "ROLE_USER"
                );

        when(
                userQueryService
                        .findByUsernameInCache(
                                username
                        )
        ).thenReturn(
                userAuthCache
        );

        when(
                travelQueryService
                        .existsByIdAndUserId(
                                travelId,
                                userId
                        )
        ).thenReturn(
                false
        );

        // when & then
        assertThatThrownBy(
                () -> travelFacade.getAiPlan(
                        request,
                        username
                )
        ).isInstanceOf(
                ForbiddenException.class
        );

        verify(
                travelQueryService
        ).existsByIdAndUserId(
                travelId,
                userId
        );

        verify(
                travelQueryService,
                never()
        ).getTravelConditionQueryResponse(
                travelId
        );
    }

    @Test
    @DisplayName("수정안을 저장 확정하면 기존 PlanDay를 지우고 수정안으로 다시 생성한다")
    void confirmEditPlan() {

        // given
        Long travelId = 1L;
        Long userId = 1L;
        String username = "testUser@example.com";
        Long planId = 10L;

        GetAiPlanRequest request =
                new GetAiPlanRequest(
                        travelId
                );

        UserAuthCache userAuthCache =
                new UserAuthCache(
                        userId,
                        username,
                        "ROLE_USER"
                );

        Plan plan =
                Plan.builder()
                        .id(planId)
                        .planName("부산 여행")
                        .build();

        PlanDay existingPlanDay =
                PlanDay.builder()
                        .id(100L)
                        .plan(plan)
                        .dayNumber(1)
                        .build();

        PlanSchedule existingPlanSchedule =
                PlanSchedule.builder()
                        .id(1000L)
                        .planDay(existingPlanDay)
                        .scheduleType(ScheduleType.LUNCH)
                        .courseType(CourseType.RESTAURANT)
                        .build();

        CreatePlanAiResponse.PlanScheduleDetail scheduleDetail =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        LocalTime.of(12, 0),
                        LocalTime.of(13, 0),
                        "수정된 식당",
                        "부산광역시 부산진구",
                        "129.0756",
                        "35.1795",
                        "image-url",
                        "thumbnail-url",
                        60,
                        20,
                        Set.of(RecommendationTag.LOCAL_FOOD),
                        null,
                        null
                );

        CreatePlanAiResponse.PlanDayDetail planDayDetail =
                new CreatePlanAiResponse.PlanDayDetail(
                        1,
                        LocalDate.of(2026, 9, 1),
                        List.of(scheduleDetail)
                );

        EditPlanAiResponse editPlanAiResponse =
                new EditPlanAiResponse(
                        "부산 여행",
                        List.of(planDayDetail),
                        List.of("점심 식당을 변경했습니다")
                );

        PlanQueryResponse planQueryResponse =
                new PlanQueryResponse(
                        planId,
                        "부산 여행",
                        Set.of(RecommendationTag.LOCAL_FOOD)
                );

        Set<RecommendationTag> aggregatedTags =
                Set.of(RecommendationTag.LOCAL_FOOD);

        PlanDay newPlanDay =
                PlanDay.builder()
                        .id(200L)
                        .plan(plan)
                        .dayNumber(1)
                        .planDate(LocalDate.of(2026, 9, 1))
                        .build();

        List<PlanSchedule> newPlanSchedules =
                List.of(
                        PlanSchedule.builder()
                                .id(2000L)
                                .planDay(newPlanDay)
                                .scheduleType(ScheduleType.LUNCH)
                                .courseType(CourseType.RESTAURANT)
                                .locationName("수정된 식당")
                                .build()
                );

        List<RestaurantDetail> newRestaurantDetails = List.of();

        when(
                userQueryService
                        .findByUsernameInCache(username)
        ).thenReturn(
                userAuthCache
        );

        when(
                travelQueryService
                        .existsByIdAndUserId(travelId, userId)
        ).thenReturn(
                true
        );

        when(
                planEditCacheService
                        .findEditResult(travelId)
        ).thenReturn(
                Optional.of(editPlanAiResponse)
        );

        when(
                planQueryService
                        .getPlanByTravelId(travelId)
        ).thenReturn(
                planQueryResponse
        );

        when(
                planService
                        .findPlanById(planId)
        ).thenReturn(
                plan
        );

        when(
                planDayService
                        .findAllByPlan(plan)
        ).thenReturn(
                List.of(existingPlanDay)
        );

        when(
                planScheduleService
                        .findAllByPlanDayIn(
                                List.of(existingPlanDay)
                        )
        ).thenReturn(
                List.of(existingPlanSchedule)
        );

        when(
                planService
                        .aggregateTags(
                                editPlanAiResponse.planDays()
                        )
        ).thenReturn(
                aggregatedTags
        );

        when(
                planDayService
                        .createPlanDay(
                                new CreatePlanDayRequest(
                                        plan,
                                        planDayDetail.dayNumber(),
                                        planDayDetail.date()
                                )
                        )
        ).thenReturn(
                newPlanDay
        );

        when(
                planScheduleService
                        .makePlanScheduleList(
                                newPlanDay,
                                planDayDetail.schedules()
                        )
        ).thenReturn(
                newPlanSchedules
        );

        when(
                restaurantDetailService
                        .makeRestaurantDetailList(
                                newPlanSchedules,
                                planDayDetail.schedules()
                        )
        ).thenReturn(
                newRestaurantDetails
        );

        // when
        CreatePlanResponse result =
                travelFacade.confirmEditPlan(
                        request,
                        username
                );

        // then
        assertThat(
                result.tags()
        ).isEqualTo(
                aggregatedTags
        );

        assertThat(
                result.planDays()
        ).isSameAs(
                editPlanAiResponse.planDays()
        );

        assertThat(
                plan.getTags()
        ).isEqualTo(
                aggregatedTags
        );

        verify(
                restaurantDetailService
        ).deleteAllByPlanScheduleIn(
                List.of(existingPlanSchedule)
        );

        verify(
                planScheduleService
        ).deleteAllByPlanDayIn(
                List.of(existingPlanDay)
        );

        verify(
                planDayService
        ).deleteAllByPlan(
                plan
        );

        verify(
                planService
        ).savePlan(
                plan
        );

        verify(
                planDayService
        ).savePlanDay(
                newPlanDay
        );

        verify(
                planScheduleService
        ).savePlanScheduleAll(
                newPlanSchedules
        );

        verify(
                restaurantDetailService
        ).saveRestaurantDetailAll(
                newRestaurantDetails
        );

        verify(
                planEditCacheService
        ).deleteEditResult(
                travelId
        );
    }

    @Test
    @DisplayName("Redis에 저장된 수정안이 없거나 만료되었으면 예외가 발생한다")
    void confirmEditPlanThrowsWhenEditResultNotFound() {

        // given
        Long travelId = 1L;
        Long userId = 1L;
        String username = "testUser@example.com";

        GetAiPlanRequest request =
                new GetAiPlanRequest(
                        travelId
                );

        UserAuthCache userAuthCache =
                new UserAuthCache(
                        userId,
                        username,
                        "ROLE_USER"
                );

        when(
                userQueryService
                        .findByUsernameInCache(username)
        ).thenReturn(
                userAuthCache
        );

        when(
                travelQueryService
                        .existsByIdAndUserId(travelId, userId)
        ).thenReturn(
                true
        );

        when(
                planEditCacheService
                        .findEditResult(travelId)
        ).thenReturn(
                Optional.empty()
        );

        // when & then
        assertThatThrownBy(
                () -> travelFacade.confirmEditPlan(
                        request,
                        username
                )
        ).isInstanceOf(
                BaseException.class
        ).satisfies(exception -> {

            BaseException baseException = (BaseException) exception;

            assertThat(
                    baseException.getMessage()
            ).isEqualTo(
                    PlanEditExceptionEnum.EDIT_RESULT_NOT_FOUND.getMessage()
            );
        });

        verify(
                planQueryService,
                never()
        ).getPlanByTravelId(
                travelId
        );
    }

    @Test
    @DisplayName("Travel 소유자가 아니면 저장 확정 시 접근이 거부된다")
    void confirmEditPlanThrowsForbiddenWhenNotOwner() {

        // given
        Long travelId = 1L;
        Long userId = 1L;
        String username = "testUser@example.com";

        GetAiPlanRequest request =
                new GetAiPlanRequest(
                        travelId
                );

        UserAuthCache userAuthCache =
                new UserAuthCache(
                        userId,
                        username,
                        "ROLE_USER"
                );

        when(
                userQueryService
                        .findByUsernameInCache(username)
        ).thenReturn(
                userAuthCache
        );

        when(
                travelQueryService
                        .existsByIdAndUserId(travelId, userId)
        ).thenReturn(
                false
        );

        // when & then
        assertThatThrownBy(
                () -> travelFacade.confirmEditPlan(
                        request,
                        username
                )
        ).isInstanceOf(
                ForbiddenException.class
        );

        verify(
                planEditCacheService,
                never()
        ).findEditResult(
                travelId
        );
    }

    @Test
    @DisplayName("수정 미리보기를 취소하면 Redis 캐시만 삭제한다")
    void cancelEditPlan() {

        // given
        Long travelId = 1L;
        Long userId = 1L;
        String username = "testUser@example.com";

        GetAiPlanRequest request =
                new GetAiPlanRequest(
                        travelId
                );

        UserAuthCache userAuthCache =
                new UserAuthCache(
                        userId,
                        username,
                        "ROLE_USER"
                );

        when(
                userQueryService
                        .findByUsernameInCache(username)
        ).thenReturn(
                userAuthCache
        );

        when(
                travelQueryService
                        .existsByIdAndUserId(travelId, userId)
        ).thenReturn(
                true
        );

        // when
        travelFacade.cancelEditPlan(
                request,
                username
        );

        // then
        verify(
                planEditCacheService
        ).deleteEditResult(
                travelId
        );
    }

    @Test
    @DisplayName("Travel 소유자가 아니면 취소 시 접근이 거부된다")
    void cancelEditPlanThrowsForbiddenWhenNotOwner() {

        // given
        Long travelId = 1L;
        Long userId = 1L;
        String username = "testUser@example.com";

        GetAiPlanRequest request =
                new GetAiPlanRequest(
                        travelId
                );

        UserAuthCache userAuthCache =
                new UserAuthCache(
                        userId,
                        username,
                        "ROLE_USER"
                );

        when(
                userQueryService
                        .findByUsernameInCache(username)
        ).thenReturn(
                userAuthCache
        );

        when(
                travelQueryService
                        .existsByIdAndUserId(travelId, userId)
        ).thenReturn(
                false
        );

        // when & then
        assertThatThrownBy(
                () -> travelFacade.cancelEditPlan(
                        request,
                        username
                )
        ).isInstanceOf(
                ForbiddenException.class
        );

        verify(
                planEditCacheService,
                never()
        ).deleteEditResult(
                travelId
        );
    }
}
