package com.planb.unit.domain.travel.service;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.prompt.AttractionRecommendPrompt;
import com.planb.ai.prompt.CafeRecommendPrompt;
import reactor.core.publisher.Mono;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
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
import com.planb.domain.travel.repository.PlanRepository;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.domain.travel.service.PlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @InjectMocks
    private PlanService planService;

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
                travelRecommendHandler.createPlanByAi(context)
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
                .recommendCafe(any());
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - 여행 전체 기간 중복된 카페 슬롯을 재추천한 결과로 교체한다")
    void makePlanByAiReplacesDuplicateCafe() {

        TravelPlanContext context =
                travelPlanContext();

        CreatePlanAiResponse.PlanScheduleDetail day1Cafe =
                cafe("스타벅스 하버타운점");

        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(
                        1,
                        List.of(
                                attraction("해운대해수욕장"),
                                day1Cafe
                        )
                );

        // day2: 관광지 -> 복약(실제 장소 아님) -> 1일차와 같은 이름의 카페(중복)
        CreatePlanAiResponse.PlanDayDetail day2 =
                planDay(
                        2,
                        List.of(
                                attraction("이기대"),
                                medication(),
                                cafe("스타벅스 하버타운점")
                        )
                );

        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of(day1, day2)
                );

        when(
                travelRecommendHandler.createPlanByAi(context)
        ).thenReturn(response);

        PlaceWithRouteResult replacement =
                new PlaceWithRouteResult(
                        true,
                        "이디야커피 부산달맞이점",
                        "부산 해운대구 달맞이길 193",
                        "129.182",
                        "35.158",
                        25
                );

        when(
                travelRecommendHandler.recommendCafe(any(CafeRecommendPrompt.class))
        ).thenReturn(replacement);

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

        // recommendCafe는 중복된 슬롯 하나에 대해서만 정확히 1회 호출
        ArgumentCaptor<CafeRecommendPrompt> captor =
                ArgumentCaptor.forClass(CafeRecommendPrompt.class);

        verify(travelRecommendHandler)
                .recommendCafe(captor.capture());

        CafeRecommendPrompt usedPrompt = captor.getValue();

        assertEquals(
                "부산",
                usedPrompt.locationDo()
        );

        assertEquals(
                "해운대구",
                usedPrompt.locationSigungu()
        );

        assertEquals(
                "해운대",
                usedPrompt.decidedLocation()
        );

        assertEquals(
                Transportation.TRANSIT,
                usedPrompt.transportation()
        );

        // 복약 일정을 건너뛴 직전 실제 장소(이기대) 기준 previousLocation 검증
        assertEquals(
                "이기대",
                usedPrompt.previousLocation()
        );

        // 지금까지 확정된 ATTRACTION/CAFE_REST 이름의 excludeNames 전달 검증
        assertEquals(
                Set.of(
                        "해운대해수욕장",
                        "스타벅스 하버타운점",
                        "이기대"
                ),
                new HashSet<>(usedPrompt.excludeNames())
        );

        // 1일차 변경 없음
        assertEquals(
                day1,
                result.planDays().get(0)
        );

        // 2일차 중복 카페 슬롯만 재추천 결과로 교체
        CreatePlanAiResponse.PlanScheduleDetail fixedCafe =
                result.planDays().get(1).schedules().get(2);

        assertEquals(
                CourseType.CAFE_REST,
                fixedCafe.courseType()
        );

        assertEquals(
                "이디야커피 부산달맞이점",
                fixedCafe.locationName()
        );

        assertEquals(
                "부산 해운대구 달맞이길 193",
                fixedCafe.location()
        );

        assertEquals(
                "129.182",
                fixedCafe.longitude()
        );

        assertEquals(
                "35.158",
                fixedCafe.latitude()
        );

        assertEquals(
                25,
                fixedCafe.travelMinutes()
        );

        assertNull(
                fixedCafe.imageUrl()
        );

        assertNull(
                fixedCafe.thumbNailImageUrl()
        );
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - 재추천도 실패하면 해당 카페 슬롯은 제거된다")
    void makePlanByAiDropsCafeSlotWhenReplacementNotFound() {

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
                                cafe("스타벅스 하버타운점")
                        )
                );

        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of(day1, day2)
                );

        when(
                travelRecommendHandler.createPlanByAi(context)
        ).thenReturn(response);

        when(
                travelRecommendHandler.recommendCafe(any(CafeRecommendPrompt.class))
        ).thenReturn(
                new PlaceWithRouteResult(
                        false,
                        null,
                        null,
                        null,
                        null,
                        null
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

        List<CreatePlanAiResponse.PlanScheduleDetail> day2Schedules =
                result.planDays().get(1).schedules();

        assertEquals(
                1,
                day2Schedules.size()
        );

        assertEquals(
                "이기대",
                day2Schedules.get(0).locationName()
        );
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - 여행 전체 기간 중복된 관광지 슬롯을 재추천 결과로 교체")
    void makePlanByAiReplacesDuplicateAttraction() {

        TravelPlanContext context =
                travelPlanContext();

        CreatePlanAiResponse.PlanScheduleDetail day1Attraction =
                attraction("해운대해수욕장");

        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(
                        1,
                        List.of(
                                cafe("스타벅스 하버타운점"),
                                day1Attraction
                        )
                );

        // day2: 카페 -> 복약(실제 장소 아님) -> 1일차와 같은 이름의 관광지(중복)
        CreatePlanAiResponse.PlanDayDetail day2 =
                planDay(
                        2,
                        List.of(
                                cafe("이기대카페"),
                                medication(),
                                attraction("해운대해수욕장")
                        )
                );

        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of(day1, day2)
                );

        when(
                travelRecommendHandler.createPlanByAi(context)
        ).thenReturn(response);

        PlaceWithRouteResult replacement =
                new PlaceWithRouteResult(
                        true,
                        "동백섬",
                        "부산 해운대구 동백로",
                        "129.150",
                        "35.153",
                        15
                );

        when(
                travelRecommendHandler.recommendAttraction(any(AttractionRecommendPrompt.class))
        ).thenReturn(replacement);

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

        // recommendAttraction은 중복된 슬롯 하나에 대해서만 정확히 1회 호출
        ArgumentCaptor<AttractionRecommendPrompt> captor =
                ArgumentCaptor.forClass(AttractionRecommendPrompt.class);

        verify(travelRecommendHandler)
                .recommendAttraction(captor.capture());

        AttractionRecommendPrompt usedPrompt = captor.getValue();

        assertEquals(
                "부산",
                usedPrompt.locationDo()
        );

        assertEquals(
                "해운대구",
                usedPrompt.locationSigungu()
        );

        assertEquals(
                "해운대",
                usedPrompt.decidedLocation()
        );

        assertEquals(
                Transportation.TRANSIT,
                usedPrompt.transportation()
        );

        // 복약 일정을 건너뛴 직전 실제 장소(이기대카페) 기준 previousLocation 검증
        assertEquals(
                "이기대카페",
                usedPrompt.previousLocation()
        );

        // 지금까지 확정된 ATTRACTION/CAFE_REST 이름의 excludeNames 전달 검증
        assertEquals(
                Set.of(
                        "스타벅스 하버타운점",
                        "해운대해수욕장",
                        "이기대카페"
                ),
                new HashSet<>(usedPrompt.excludeNames())
        );

        // 1일차 변경 없음
        assertEquals(
                day1,
                result.planDays().get(0)
        );

        // 2일차 중복 관광지 슬롯만 재추천 결과로 교체
        CreatePlanAiResponse.PlanScheduleDetail fixedAttraction =
                result.planDays().get(1).schedules().get(2);

        assertEquals(
                CourseType.ATTRACTION,
                fixedAttraction.courseType()
        );

        assertEquals(
                "동백섬",
                fixedAttraction.locationName()
        );

        assertEquals(
                "부산 해운대구 동백로",
                fixedAttraction.location()
        );

        assertEquals(
                "129.150",
                fixedAttraction.longitude()
        );

        assertEquals(
                "35.153",
                fixedAttraction.latitude()
        );

        assertEquals(
                15,
                fixedAttraction.travelMinutes()
        );

        assertNull(
                fixedAttraction.imageUrl()
        );

        assertNull(
                fixedAttraction.thumbNailImageUrl()
        );
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성 - 재추천 실패 시 해당 관광지 슬롯 제거")
    void makePlanByAiDropsAttractionSlotWhenReplacementNotFound() {

        TravelPlanContext context =
                travelPlanContext();

        CreatePlanAiResponse.PlanDayDetail day1 =
                planDay(
                        1,
                        List.of(
                                cafe("스타벅스 하버타운점"),
                                attraction("해운대해수욕장")
                        )
                );

        CreatePlanAiResponse.PlanDayDetail day2 =
                planDay(
                        2,
                        List.of(
                                cafe("이기대카페"),
                                attraction("해운대해수욕장")
                        )
                );

        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of(day1, day2)
                );

        when(
                travelRecommendHandler.createPlanByAi(context)
        ).thenReturn(response);

        when(
                travelRecommendHandler.recommendAttraction(any(AttractionRecommendPrompt.class))
        ).thenReturn(
                new PlaceWithRouteResult(
                        false,
                        null,
                        null,
                        null,
                        null,
                        null
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

        List<CreatePlanAiResponse.PlanScheduleDetail> day2Schedules =
                result.planDays().get(1).schedules();

        assertEquals(
                1,
                day2Schedules.size()
        );

        assertEquals(
                "이기대카페",
                day2Schedules.get(0).locationName()
        );
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
                travelRecommendHandler.createPlanByAi(context)
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
                travelRecommendHandler.createPlanByAi(context)
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
                travelRecommendHandler.createPlanByAi(context)
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
                travelRecommendHandler.createPlanByAi(context)
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
                travelRecommendHandler.createPlanByAi(context)
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
                travelRecommendHandler.createPlanByAi(context)
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
                null,
                null,
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
                null,
                null,
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
