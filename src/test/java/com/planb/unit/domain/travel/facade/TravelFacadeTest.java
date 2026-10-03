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
import com.planb.global.config.exception.TravelExceptionEnum;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
                        "부산",
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
        )
                .thenReturn(
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
        )
                .isSameAs(
                response
        );

        verify(
                travelService
        ).makeRecommendFoodResponse(
                request
        );
    }

    @Test
    @DisplayName("Kor2Service API 키워드 기반 숙박·관광지 검색")
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
        )
                .thenReturn(
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
        )
                .isSameAs(
                response
        );

        verify(
                plannedPlaceService
        ).searchPlannedPlace(
                request
        );
    }

    @Test
    @DisplayName("여행 일정 생성의 Service 호출 순서")
    void makeTravelOptionsAndRecommend() {

        String username = "testUser@example.com";
        CreateTravelRequest request = mock(CreateTravelRequest.class);
        List<TravelHealthContext> healthContexts = List.of();
        CreatePlanAiResponse aiResponse = new CreatePlanAiResponse(List.of());
        CreatePlanResponse response = mock(CreatePlanResponse.class);

        when(userQueryService.findUserIdInCache(username))
                .thenReturn(1L);
        when(travelService.loadHealthContexts(request, 1L))
                .thenReturn(healthContexts);
        when(planService.makePlanByAi(request, healthContexts))
                .thenReturn(aiResponse);
        when(travelService.saveGeneratedPlan(request, 1L, aiResponse))
                .thenReturn(response);

        CreatePlanResponse result = travelFacade.makeTravelOptionsAndRecommend(
                request,
                username
        );

        assertThat(result).isSameAs(response);

        InOrder order = inOrder(userQueryService, travelService, planService);
        order.verify(userQueryService).findUserIdInCache(username);
        order.verify(travelService).loadHealthContexts(request, 1L);
        order.verify(planService).makePlanByAi(request, healthContexts);
        order.verify(travelService).saveGeneratedPlan(request, 1L, aiResponse);
    }




    @Test
    @DisplayName("Travel ID와 Username을 기준으로 AI 여행일정 전체 조회")
    void getAiPlan() {

        String username = "testUser@example.com";
        GetAiPlanRequest request = new GetAiPlanRequest(1L);
        GetAiPlanResponse response = mock(GetAiPlanResponse.class);

        when(userQueryService.findUserIdInCache(username))
                .thenReturn(2L);
        when(travelService.findHealthIdsByTravelId(1L))
                .thenReturn(List.of());
        when(healthQueryService.getHealthSummaryListByHealthIds(List.of()))
                .thenReturn(List.of());
        when(medicationInfoQueryService.getMedicationTimesByHealthIds(List.of()))
                .thenReturn(List.of());
        when(planQueryService.getPlanDetailResponse(1L, List.of(), List.of()))
                .thenReturn(response);

        GetAiPlanResponse result = travelFacade.getAiPlan(request, username);

        assertThat(result).isSameAs(response);
        verify(travelQueryService).validateOwner(1L, 2L);
    }

    @Test
    @DisplayName("Travel 소유자가 아닌 경우 접근 거부")
    void getAiPlanThrowsForbiddenWhenNotOwner() {

        String username = "testUser@example.com";
        GetAiPlanRequest request = new GetAiPlanRequest(1L);
        when(userQueryService.findUserIdInCache(username))
                .thenReturn(2L);
        org.mockito.Mockito.doThrow(new ForbiddenException(new Object[]{"권한 없음"}))
                .when(travelQueryService)
                .validateOwner(1L, 2L);

        assertThatThrownBy(() -> travelFacade.getAiPlan(request, username))
                .isInstanceOf(ForbiddenException.class);

        verify(planQueryService, never())
                .getPlanDetailResponse(any(), any(), any());
    }

    @Test
    @DisplayName("수정안 저장 확정 시 기존 PlanDay 삭제 후 수정안 기반 재생성")
    void confirmEditPlan() {

        String username = "testUser@example.com";
        GetAiPlanRequest request = new GetAiPlanRequest(1L);
        CreatePlanResponse expected = new CreatePlanResponse(
                1L,
                false,
                Set.of(RecommendationTag.LOCAL_FOOD),
                List.of()
        );

        when(userQueryService.findUserIdInCache(username))
                .thenReturn(2L);
        when(travelService.confirmEditPlan(1L))
                .thenReturn(expected);

        CreatePlanResponse result = travelFacade.confirmEditPlan(request, username);

        assertThat(result).isSameAs(expected);
        verify(travelQueryService)
                .validateOwner(1L, 2L);
        verify(travelService)
                .confirmEditPlan(1L);
    }

    @Test
    @DisplayName("Redis 수정안 부재 또는 만료 시 예외 발생")
    void confirmEditPlanThrowsWhenEditResultNotFound() {

        String username = "testUser@example.com";
        GetAiPlanRequest request = new GetAiPlanRequest(1L);

        when(userQueryService.findUserIdInCache(username))
                .thenReturn(2L);
        when(travelService.confirmEditPlan(1L))
                .thenThrow(new BaseException(PlanEditExceptionEnum.EDIT_RESULT_NOT_FOUND));

        assertThatThrownBy(() -> travelFacade.confirmEditPlan(request, username))
                .isInstanceOf(BaseException.class)
                .hasMessage(PlanEditExceptionEnum.EDIT_RESULT_NOT_FOUND.getMessage());

        verify(travelQueryService)
                .validateOwner(1L, 2L);
    }

    @Test
    @DisplayName("확정 완료 후 재확정 요청의 동일 응답 반환과 재저장 생략")
    void confirmEditPlanReturnsSameResponseWhenAlreadyConfirmed() {

        String username = "testUser@example.com";
        GetAiPlanRequest request = new GetAiPlanRequest(1L);
        CreatePlanResponse expected = new CreatePlanResponse(
                1L,
                false,
                Set.of(),
                List.of()
        );

        when(userQueryService.findUserIdInCache(username))
                .thenReturn(2L);
        when(travelService.confirmEditPlan(1L))
                .thenReturn(expected);

        CreatePlanResponse result = travelFacade.confirmEditPlan(request, username);

        assertThat(result).isSameAs(expected);
        verify(travelService)
                .confirmEditPlan(1L);
    }

    @Test
    @DisplayName("Travel 소유자가 아닌 경우 저장 확정 접근 거부")
    void confirmEditPlanThrowsForbiddenWhenNotOwner() {

        String username = "testUser@example.com";
        GetAiPlanRequest request = new GetAiPlanRequest(1L);

        when(userQueryService.findUserIdInCache(username))
                .thenReturn(2L);
        org.mockito.Mockito.doThrow(new ForbiddenException(new Object[]{"권한 없음"}))
                .when(travelQueryService)
                .validateOwner(1L, 2L);

        assertThatThrownBy(() -> travelFacade.confirmEditPlan(request, username))
                .isInstanceOf(ForbiddenException.class);

        verify(travelService, never())
                .confirmEditPlan(1L);
    }

    @Test
    @DisplayName("수정 미리보기 취소 시 Redis 캐시만 삭제")
    void cancelEditPlan() {

        String username = "testUser@example.com";
        GetAiPlanRequest request = new GetAiPlanRequest(1L);
        when(userQueryService.findUserIdInCache(username))
                .thenReturn(2L);

        travelFacade.cancelEditPlan(request, username);

        verify(travelQueryService)
                .validateOwner(1L, 2L);
        verify(planEditCacheService)
                .deleteEditResult(1L);
    }

    @Test
    @DisplayName("Travel 소유자가 아닌 경우 취소 접근 거부")
    void cancelEditPlanThrowsForbiddenWhenNotOwner() {

        String username = "testUser@example.com";
        GetAiPlanRequest request = new GetAiPlanRequest(1L);
        when(userQueryService.findUserIdInCache(username))
                .thenReturn(2L);
        org.mockito.Mockito.doThrow(new ForbiddenException(new Object[]{"권한 없음"}))
                .when(travelQueryService)
                .validateOwner(1L, 2L);

        assertThatThrownBy(() -> travelFacade.cancelEditPlan(request, username))
                .isInstanceOf(ForbiddenException.class);

        verify(planEditCacheService, never())
                .deleteEditResult(1L);
    }

    @Test
    @DisplayName("등록한 관리 질환을 모두 AI 컨텍스트로 전달")
    void travelHealthContextCarriesEveryRegisteredDisease() {

        // given
        Health health =
                Health.builder()
                        .id(100L)
                        .travelerName("본인")
                        .sensitiveAgree(true)
                        .hasMedication(false)
                        .healthInfo(
                                new HealthInfo(
                                        List.of(
                                                DiseaseType.DIABETES,
                                                DiseaseType.HIGH_BLOOD_PRESSURE
                                        ),
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

        // when
        TravelHealthContext context =
                TravelHealthContext.from(
                        health,
                        List.of(),
                        List.of()
                );

        // then - 질환마다 보는 영양성분이 달라 하나라도 빠지면 그 기준이 평가에서 사라진다
        assertThat(context.diseaseTypes())
                .containsExactly(
                        DiseaseType.DIABETES,
                        DiseaseType.HIGH_BLOOD_PRESSURE
                );
    }






}
