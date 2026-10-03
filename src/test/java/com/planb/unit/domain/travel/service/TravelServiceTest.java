package com.planb.unit.domain.travel.service;

import com.planb.domain.user.entity.AccountRecovery;
import com.planb.domain.user.entity.constant.RecoveryQuestion;
import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.health.entity.vo.HealthInfo;
import com.planb.domain.health.entity.vo.MealInfo;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.repository.TravelRepository;
import com.planb.domain.travel.service.TravelService;
import com.planb.domain.travel.service.TravelTransactionService;
import com.planb.domain.travel.service.PlanService;
import com.planb.domain.travel.service.PlanEditCacheService;
import com.planb.domain.travel.service.PlanDayService;
import com.planb.domain.travel.service.PlanScheduleService;
import com.planb.domain.travel.service.RestaurantDetailService;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.RestaurantDetail;
import com.planb.domain.travel.dto.response.CreatePlanResponse;
import com.planb.query.travel.dto.response.PlanQueryResponse;
import com.planb.query.travel.service.PlanQueryService;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.TravelExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.health.service.HealthQueryService;
import com.planb.query.user.service.UserQueryService;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TravelServiceTest {

    @Mock
    private TravelRepository travelRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TravelRecommendHandler travelRecommendHandler;

    @Mock
    private TravelTransactionService travelTransactionService;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private HealthQueryService healthQueryService;

    @Mock
    private HealthService healthService;

    @Mock
    private FoodInfoService foodInfoService;

    @Mock
    private MedicationInfoService medicationInfoService;

    @Mock
    private PlanService planService;

    @Mock
    private PlanEditCacheService planEditCacheService;

    @Mock
    private PlanQueryService planQueryService;

    @Mock
    private PlanDayService planDayService;

    @Mock
    private PlanScheduleService planScheduleService;

    @Mock
    private RestaurantDetailService restaurantDetailService;

    @InjectMocks
    private TravelService travelService;

    @Test
    @DisplayName("민감정보 미동의 동행인의 AI 컨텍스트 제외")
    void loadHealthContextsExcludesNonConsentingCompanion() {

        CreateTravelRequest request = mock(CreateTravelRequest.class);
        when(request.healthIds())
                .thenReturn(List.of(100L, 101L));
        when(travelTransactionService.readOnly(any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        when(userQueryService.findById(1L))
                .thenReturn(mock(User.class));
        when(healthQueryService.checkHealthWithUser(100L, 1L))
                .thenReturn(true);
        when(healthQueryService.checkHealthWithUser(101L, 1L))
                .thenReturn(true);

        Health agreed = Health
                .builder()
                .id(100L)
                .travelerName("동의 동행인")
                .sensitiveAgree(true)
                .healthInfo(new HealthInfo(List.of(DiseaseType.DIABETES), WalkType.MODERATE))
                .mealInfo(new MealInfo(
                        true,
                        true,
                        LocalTime.of(8, 0),
                        true,
                        LocalTime.of(12, 0),
                        true,
                        LocalTime.of(18, 0)
                ))
                .build();
        Health notAgreed = Health
                .builder()
                .id(101L)
                .travelerName("미동의 동행인")
                .sensitiveAgree(false)
                .build();

        when(healthService.getHealthById(100L))
                .thenReturn(agreed);
        when(healthService.getHealthById(101L))
                .thenReturn(notAgreed);
        when(foodInfoService.getFoodInfoList(100L))
                .thenReturn(List.of());
        when(medicationInfoService.findAllByHealthId(100L))
                .thenReturn(List.of());

        List<TravelHealthContext> contexts = travelService.loadHealthContexts(request, 1L);

        assertEquals(1, contexts.size());
        assertEquals("동의 동행인", contexts
                        .getFirst()
                        .travelerName());
    }

    @Test
    @DisplayName("AI 생성 중 동행인 소유권 변경 시 저장 거부")
    void saveGeneratedPlanRechecksCompanionOwnership() {

        CreateTravelRequest request = mock(CreateTravelRequest.class);
        CreatePlanAiResponse aiResponse = new CreatePlanAiResponse(List.of());
        Health health = Health
                .builder()
                .id(100L)
                .sensitiveAgree(false)
                .build();

        when(request.healthIds())
                .thenReturn(List.of(100L));
        when(travelTransactionService.readOnly(any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        when(travelTransactionService.write(any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        when(userQueryService.findById(1L))
                .thenReturn(mock(User.class));
        when(healthQueryService.checkHealthWithUser(100L, 1L))
                .thenReturn(true, false);
        when(healthService.getHealthById(100L))
                .thenReturn(health);
        when(planService.aggregateTags(aiResponse.planDays()))
                .thenReturn(Set.of());

        travelService.loadHealthContexts(request, 1L);

        BaseException exception = assertThrows(
                BaseException.class,
                () -> travelService.saveGeneratedPlan(
                        request,
                        1L,
                        aiResponse
                )
        );

        assertEquals(
                TravelExceptionEnum.COMPANION_NOT_OWNED.getCode(),
                exception.getErrorCode()
        );
        verify(travelRepository, never())
                .save(any());
    }

    @Test
    @DisplayName("수정안 부재 시 일정 재저장 거부")
    void confirmEditPlanRejectsMissingResult() {

        when(planEditCacheService.consumeEditResult(1L))
                .thenReturn(Optional.empty());
        when(planEditCacheService.findConfirmedResult(1L))
                .thenReturn(Optional.empty());

        BaseException exception = assertThrows(
                BaseException.class,
                () -> travelService.confirmEditPlan(1L)
        );

        assertEquals(
                PlanEditExceptionEnum.EDIT_RESULT_NOT_FOUND.getCode(),
                exception.getErrorCode()
        );
        verify(planDayService, never())
                .deleteAllByPlan(any());
    }

    @Test
    @DisplayName("채팅 확정 요청의 쓰기 트랜잭션 경계")
    void confirmEditPlanInTransaction() {

        when(travelTransactionService.write(any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> operation = invocation.getArgument(0);
                    return operation.get();
                });
        when(planEditCacheService.consumeEditResult(1L))
                .thenReturn(Optional.empty());
        when(planEditCacheService.findConfirmedResult(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                BaseException.class,
                () -> travelService.confirmEditPlanInTransaction(1L)
        );

        verify(travelTransactionService)
                .write(any());
    }

    @Test
    @DisplayName("확정된 수정안의 재요청 시 일정 재저장 생략")
    void confirmEditPlanReturnsConfirmedResult() {

        EditPlanAiResponse confirmed = new EditPlanAiResponse(
                "부산 여행",
                List.of(),
                List.of("점심 식당 변경"),
                true
        );
        Travel travel = Travel
                .builder()
                .id(1L)
                .travelName("부산 여행")
                .build();

        when(planEditCacheService.consumeEditResult(1L))
                .thenReturn(Optional.empty());
        when(planEditCacheService.findConfirmedResult(1L))
                .thenReturn(Optional.of(confirmed));
        when(travelRepository.getReferenceById(1L))
                .thenReturn(travel);
        when(planService.aggregateTags(confirmed.planDays()))
                .thenReturn(Set.of());

        CreatePlanResponse response = travelService.confirmEditPlan(1L);

        assertEquals(1L, response.travelId());
        assertEquals(confirmed.planDays(), response.planDays());
        verify(planDayService, never())
                .deleteAllByPlan(any());
    }

    @Test
    @DisplayName("수정안 확정 시 기존 일정 삭제와 확정 결과 반환")
    void confirmEditPlanReplacesExistingDays() {

        EditPlanAiResponse editResult = new EditPlanAiResponse(
                "부산 여행",
                List.of(),
                List.of("점심 식당 변경"),
                true
        );
        Plan plan = Plan
                .builder()
                .id(10L)
                .build();
        PlanDay oldDay = PlanDay
                .builder()
                .id(100L)
                .plan(plan)
                .build();
        PlanSchedule oldSchedule = PlanSchedule
                .builder()
                .id(1000L)
                .planDay(oldDay)
                .build();
        Travel travel = Travel
                .builder()
                .id(1L)
                .build();

        when(planEditCacheService.consumeEditResult(1L))
                .thenReturn(Optional.of(editResult));
        when(planQueryService.getPlanByTravelId(1L))
                .thenReturn(new PlanQueryResponse(
                        10L,
                        "부산 여행",
                        Set.of()
                ));
        when(planService.findPlanById(10L))
                .thenReturn(plan);
        when(planDayService.findAllByPlan(plan))
                .thenReturn(List.of(oldDay));
        when(planScheduleService.findAllByPlanDayIn(List.of(oldDay)))
                .thenReturn(List.of(oldSchedule));
        when(planService.aggregateTags(editResult.planDays()))
                .thenReturn(Set.of());
        when(travelRepository.getReferenceById(1L))
                .thenReturn(travel);

        CreatePlanResponse response = travelService.confirmEditPlan(1L);

        assertEquals(1L, response.travelId());
        assertEquals(editResult.planDays(), response.planDays());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                restaurantDetailService,
                planScheduleService,
                planDayService,
                planService,
                planEditCacheService
        );
        order
                .verify(restaurantDetailService)
                .deleteAllByPlanScheduleIn(List.of(oldSchedule));
        order
                .verify(planScheduleService)
                .deleteAllByPlanDayIn(List.of(oldDay));
        order
                .verify(planDayService)
                .deleteAllByPlan(plan);
        order
                .verify(planService)
                .savePlan(plan);
        order
                .verify(planEditCacheService)
                .markConfirmedAfterCommit(1L, editResult);
    }

    @Test
    @DisplayName("Travel 객체 생성")
    void createTravel() {

        Long userId = 1L;

        User user =
                User
                        .builder()
                        .id(userId)
                        .accountRecovery(
                                AccountRecovery.of(
                                        RecoveryQuestion.FIRST_PET,
                                        "콩이"
                                ))
                        .build();

        when(
                userRepository.getReferenceById(userId)
        )
                .thenReturn(user);

        LocalDate startDate =
                LocalDate.of(
                        2026,
                        8,
                        26
                );

        List<CreateTravelRequest.PlannedPlaceDetail> plannedPlaces =
                List.of(
                        new CreateTravelRequest.PlannedPlaceDetail(
                                "해운대해수욕장",
                                "부산광역시 해운대구"
                        )
                );

        List<String> recommendFoods =
                List.of(
                        "돼지국밥",
                        "밀면"
                );

        CreateTravelRequest request =
                new CreateTravelRequest(
                        "부산 여행",
                        "부산",
                        "해운대구",
                        startDate,
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.TRANSIT,
                        "해운대해수욕장",
                        plannedPlaces,
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of("돼지국밥"),
                        recommendFoods
                );

        Travel travel =
                travelService.createTravel(
                        request,
                        userId
                );

        assertSame(
                user,
                travel.getUser()
        );

        assertEquals(
                "부산 여행",
                travel.getTravelName()
        );

        assertEquals(
                "부산",
                travel.getLocationDo()
        );

        assertEquals(
                "해운대구",
                travel.getLocationSigungu()
        );

        assertEquals(
                startDate,
                travel.getStartDate()
        );

        assertEquals(
                startDate.plusDays(1),
                travel.getEndDate()
        );

        assertEquals(
                DateType.ONE_NIGHT_TWO_DAYS,
                travel.getDateType()
        );

        assertEquals(
                Transportation.TRANSIT,
                travel.getTransportation()
        );

        assertEquals(
                "해운대해수욕장",
                travel.getDecidedLocation()
        );

        assertEquals(
                TravelStyle.MATCH_MEAL_TIME,
                travel.getTravelStyle()
        );

        assertEquals(
                TravelTheme.TASTE,
                travel.getTravelTheme()
        );

        assertEquals(
                List.of("돼지국밥"),
                travel.getLocalFoods()
        );

        assertEquals(
                recommendFoods,
                travel.getRecommendFoods()
        );
    }

    @Test
    @DisplayName("AI 기반 지역 음식 추천")
    void makeRecommendFoodResponse() {

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
                travelRecommendHandler.makeRecommendFood(
                        org.mockito.ArgumentMatchers.any(
                                MakeFoodRecommendCallRequest.class
                        )
                )
        )
                .thenReturn(response);

        MakeRecommendFoodResponse result =
                travelService.makeRecommendFoodResponse(
                        request
                );

        assertSame(
                response,
                result
        );

        ArgumentCaptor<MakeFoodRecommendCallRequest> captor =
                ArgumentCaptor.forClass(
                        MakeFoodRecommendCallRequest.class
                );

        verify(travelRecommendHandler)
                .makeRecommendFood(
                        captor.capture()
                );

        assertEquals(
                request,
                captor
                        .getValue()
                        .request()
        );
    }

    @Test
    @DisplayName("Travel 객체 저장")
    void saveTravel() {

        Travel travel =
                Travel
                        .builder()
                        .travelName("부산 여행")
                        .build();

        travelService.saveTravel(
                travel
        );

        verify(travelRepository)
                .save(travel);
    }

    @Test
    @DisplayName("Travel ID 기준 삭제")
    void deleteTravel() {

        Long travelId = 1L;

        travelService.deleteTravel(
                travelId
        );

        verify(travelRepository)
                .deleteById(travelId);
    }

}
