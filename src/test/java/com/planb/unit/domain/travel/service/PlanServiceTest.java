package com.planb.unit.domain.travel.service;

import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.prompt.CafeRecommendPrompt;
import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.repository.PlanRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private TravelRecommendHandler travelRecommendHandler;

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
                        "부산 건강 여행",
                        "설명",
                        List.of(day1, day2)
                );

        when(
                travelRecommendHandler.createPlanByAi(context)
        ).thenReturn(response);

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
                        "부산 건강 여행",
                        "설명",
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

        CreatePlanAiResponse result =
                planService.makePlanByAi(context);

        // recommendCafe는 중복된 슬롯 하나에 대해서만 정확히 1회 호출된다
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

        // 복약 일정은 실제 장소가 아니므로 건너뛰고, 바로 앞의 실제 장소(이기대)가 previousLocation이어야 한다
        assertEquals(
                "이기대",
                usedPrompt.previousLocation()
        );

        // 지금까지 확정된 ATTRACTION/CAFE_REST 이름이 모두 excludeNames로 전달되어야 한다
        assertEquals(
                Set.of(
                        "해운대해수욕장",
                        "스타벅스 하버타운점",
                        "이기대"
                ),
                new HashSet<>(usedPrompt.excludeNames())
        );

        // 1일차는 변경되지 않는다
        assertEquals(
                day1,
                result.planDays().get(0)
        );

        // 2일차의 중복 카페 슬롯만 재추천 결과로 교체된다
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
                        "부산 건강 여행",
                        "설명",
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

    /*
    테스트 데이터 헬퍼
     */

    private TravelPlanContext travelPlanContext() {

        CreateTravelRequest createTravelRequest =
                new CreateTravelRequest(
                        "부산 건강 여행",
                        "부산",
                        "해운대구",
                        LocalDate.now().plusDays(7),
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.TRANSIT,
                        "해운대",
                        List.of(),
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of("돼지국밥"),
                        List.of("돼지국밥", "밀면", "회")
                );

        return new TravelPlanContext(
                createTravelRequest,
                List.of()
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
}
