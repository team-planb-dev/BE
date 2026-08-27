package com.planb.unit.domain.travel.facade;

import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.travel.dto.request.GetAiPlanRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.facade.TravelFacade;
import com.planb.domain.travel.service.PlannedPlaceService;
import com.planb.domain.travel.service.PlanDayService;
import com.planb.domain.travel.service.PlanScheduleService;
import com.planb.domain.travel.service.PlanService;
import com.planb.domain.travel.service.RestaurantDetailService;
import com.planb.domain.travel.service.TravelService;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
                        "부산 여행"
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
}