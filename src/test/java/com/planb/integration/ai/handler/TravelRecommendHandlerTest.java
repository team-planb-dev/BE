package com.planb.integration.ai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.integration.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TravelRecommendHandlerTest extends IntegrationTest {

    private static final int EXPECTED_DAY_COUNT = 2;

    @Autowired
    private TravelRecommendHandler travelRecommendHandler;

    @Autowired
    private ObjectMapper objectMapper;

    // editPlanByAi 테스트 전체가 공유하는 기본 여행 조건·건강정보·현재 확정 일정
    private static CreateTravelRequest baseCreateTravelRequest;
    private static List<TravelHealthContext> baseHealthContexts;
    private static GetAiPlanResponse baseCurrentPlan;

    // 실제 OpenAI로 기본 일정을 1회 생성해 이후 모든 수정 요청 테스트에서 currentPlan으로 공유
    @BeforeEach
    void setUpBaseCurrentPlanIfNeeded() throws Exception {

        if (baseCurrentPlan != null) {
            return;
        }

        LocalDate startDate =
                LocalDate.now().plusDays(7);

        baseCreateTravelRequest =
                new CreateTravelRequest(
                        "부산 수정 요청 테스트 여행",
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
                        List.of("돼지국밥", "밀면")
                );

        TravelHealthContext baseHealthContext =
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
                                )
                        ),
                        List.of()
                );

        baseHealthContexts = List.of(baseHealthContext);

        TravelPlanContext travelPlanContext =
                new TravelPlanContext(
                        baseCreateTravelRequest,
                        baseHealthContexts
                );

        CreatePlanAiResponse createdPlan =
                travelRecommendHandler.createPlanByAi(travelPlanContext);

        baseCurrentPlan =
                toGetAiPlanResponse(
                        baseCreateTravelRequest,
                        createdPlan
                );

        System.out.println(
                "===== 수정 요청 테스트용 기본 일정 ====="
        );

        System.out.println(
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(baseCurrentPlan)
        );
    }


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
                        "경주 건강 여행",
                        "경상북도",
                        "경주시",
                        startDate,
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.TRANSIT,
                        "경주",
                        List.of(
                                new CreateTravelRequest.PlannedPlaceDetail(
                                        "불국사",
                                        "경상북도 경주시"
                                )
                        ),
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of("황남빵"),
                        List.of(
                                "황남빵",
                                "쌈밥",
                                "연잎밥"
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

        // 카페(CAFE_REST) 장소가 날짜(day)를 넘어 중복 배치되지 않는지 검증
        List<String> cafeLocationNames =
                response.planDays().stream()
                        .flatMap(planDay -> planDay.schedules().stream())
                        .filter(schedule -> schedule.courseType() == CourseType.CAFE_REST)
                        .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                        .toList();

        assertThat(cafeLocationNames)
                .doesNotHaveDuplicates();
    }

    // 이동/밀도 조정(감소) 요청: 걷는 양을 줄여달라는 요청이면 관광지 개수가 늘어나지 않아야 함
    @Test
    @DisplayName("실제 OpenAI 밀도 감소 수정 요청 - 걷기 줄이기")
    void editPlanReducesWalkingDensityWithRealOpenAi() throws Exception {

        // given
        long baseAttractionCount =
                countAttractionSchedulesInBasePlan();

        PlanEditContext planEditContext =
                new PlanEditContext(
                        baseCreateTravelRequest,
                        baseHealthContexts,
                        baseCurrentPlan,
                        "덜 걷고 싶어요. 관광지 개수를 좀 줄여주세요."
                );

        // when
        EditPlanAiResponse response =
                travelRecommendHandler.editPlanByAi(planEditContext);

        // then
        printEditPlanResponse(
                "밀도 감소 수정 요청 응답",
                response
        );

        assertStructurallyValidEditResponse(response);

        assertThat(response.processable())
                .isTrue();

        assertThat(response.changes())
                .isNotEmpty();

        assertThat(countAttractionSchedulesInEditResponse(response))
                .isLessThanOrEqualTo(baseAttractionCount);
    }

    // 이동/밀도 조정(증가) 요청: 더 알차게 다니고 싶다는 요청이면 관광지 개수가 줄어들지 않아야 함
    @Test
    @DisplayName("실제 OpenAI 밀도 증가 수정 요청 - 더 알차게")
    void editPlanIncreasesDensityWithRealOpenAi() throws Exception {

        // given
        long baseAttractionCount =
                countAttractionSchedulesInBasePlan();

        PlanEditContext planEditContext =
                new PlanEditContext(
                        baseCreateTravelRequest,
                        baseHealthContexts,
                        baseCurrentPlan,
                        "관광지 추천을 늘려서 더 알차게 다니고 싶어요."
                );

        // when
        EditPlanAiResponse response =
                travelRecommendHandler.editPlanByAi(planEditContext);

        // then
        printEditPlanResponse(
                "밀도 증가 수정 요청 응답",
                response
        );

        assertStructurallyValidEditResponse(response);

        assertThat(response.processable())
                .isTrue();

        assertThat(response.changes())
                .isNotEmpty();

        assertThat(countAttractionSchedulesInEditResponse(response))
                .isGreaterThanOrEqualTo(baseAttractionCount);
    }

    // 특정 장소 교체/삭제 요청: "1일차 OO는 빼주세요" 요청이면 해당 슬롯(같은 시작 시각)의 장소가 바뀌어야 함
    @Test
    @DisplayName("실제 OpenAI 특정 장소 삭제 수정 요청 - 1일차 특정 장소 제외")
    void editPlanRemovesSpecificDaySlotWithRealOpenAi() throws Exception {

        // given
        GetAiPlanResponse.PlanScheduleDetail targetSchedule =
                day1UniqueNamedScheduleInBasePlan();

        String targetLocationName =
                targetSchedule.locationName();

        LocalTime targetStartTime =
                targetSchedule.startTime();

        PlanEditContext planEditContext =
                new PlanEditContext(
                        baseCreateTravelRequest,
                        baseHealthContexts,
                        baseCurrentPlan,
                        "1일차 " + targetLocationName + "는 빼주세요. 다른 곳으로 바꿔주세요."
                );

        // when
        EditPlanAiResponse response =
                travelRecommendHandler.editPlanByAi(planEditContext);

        // then
        printEditPlanResponse(
                "특정 장소 삭제 수정 요청 응답",
                response
        );

        assertStructurallyValidEditResponse(response);

        assertThat(response.processable())
                .isTrue();

        assertThat(response.changes())
                .isNotEmpty();

        Optional<String> locationNameAtTargetSlot =
                locationNameAtStartTimeInEditResponseDay1(
                        response,
                        targetStartTime
                );

        assertThat(locationNameAtTargetSlot)
                .isNotEqualTo(Optional.of(targetLocationName));
    }

    // 음식/식사 조정 요청: 매운 음식을 못 먹는다는 요청이면 처리 가능하다고 판단하고 변경 사항을 남겨야 함
    @Test
    @DisplayName("실제 OpenAI 음식 조정 수정 요청 - 매운 음식 제외")
    void editPlanAdjustsFoodPreferenceWithRealOpenAi() throws Exception {

        // given
        PlanEditContext planEditContext =
                new PlanEditContext(
                        baseCreateTravelRequest,
                        baseHealthContexts,
                        baseCurrentPlan,
                        "매운 음식은 못 먹으니까 식사 메뉴를 다른 걸로 바꿔주세요."
                );

        // when
        EditPlanAiResponse response =
                travelRecommendHandler.editPlanByAi(planEditContext);

        // then
        printEditPlanResponse(
                "음식 조정 수정 요청 응답",
                response
        );

        assertStructurallyValidEditResponse(response);

        assertThat(response.processable())
                .isTrue();

        assertThat(response.changes())
                .isNotEmpty();
    }

    // 건강 조건 재반영 요청: 새로 추가된 알레르기 정보를 반영해달라는 요청이면 처리 가능하다고 판단해야 함
    @Test
    @DisplayName("실제 OpenAI 건강 조건 재반영 수정 요청 - 알레르기 추가")
    void editPlanReflectsUpdatedHealthConditionWithRealOpenAi() throws Exception {

        // given
        TravelHealthContext updatedHealthContext =
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
                                        "우유",
                                        FoodType.ALLERGY
                                )
                        ),
                        List.of()
                );

        PlanEditContext planEditContext =
                new PlanEditContext(
                        baseCreateTravelRequest,
                        List.of(updatedHealthContext),
                        baseCurrentPlan,
                        "제가 우유 알레르기가 있는 걸 깜빡했어요. 식사 메뉴에 반영해주세요."
                );

        // when
        EditPlanAiResponse response =
                travelRecommendHandler.editPlanByAi(planEditContext);

        // then
        printEditPlanResponse(
                "건강 조건 재반영 수정 요청 응답",
                response
        );

        assertStructurallyValidEditResponse(response);

        assertThat(response.processable())
                .isTrue();

        assertThat(response.changes())
                .isNotEmpty();
    }

    // 복합 요청: 특정 날짜 전체를 다시 짜달라는 요청이면 해당 날짜 구성 자체가 원본과 달라져야 함
    @Test
    @DisplayName("실제 OpenAI 복합 수정 요청 - 1일차 통째로 재구성")
    void editPlanRebuildsWholeDayWithRealOpenAi() throws Exception {

        // given
        List<String> baseDay1LocationNames =
                day1LocationNamesInBasePlan();

        PlanEditContext planEditContext =
                new PlanEditContext(
                        baseCreateTravelRequest,
                        baseHealthContexts,
                        baseCurrentPlan,
                        "1일차 일정을 관광지 위주로 통째로 다시 짜주세요."
                );

        // when
        EditPlanAiResponse response =
                travelRecommendHandler.editPlanByAi(planEditContext);

        // then
        printEditPlanResponse(
                "복합 수정 요청(1일차 재구성) 응답",
                response
        );

        assertStructurallyValidEditResponse(response);

        assertThat(response.processable())
                .isTrue();

        assertThat(response.changes())
                .isNotEmpty();

        assertThat(day1LocationNamesInEditResponse(response))
                .isNotEqualTo(baseDay1LocationNames);
    }

    // 처리 불가능 요청: 일정 수정/삭제와 무관한 요청이면 processable=false, changes=[]이며 일정이 그대로 유지되어야 함
    @Test
    @DisplayName("실제 OpenAI 처리 불가능 수정 요청 - 일정과 무관한 요청")
    void editPlanUnprocessableWhenUnrelatedRequestWithRealOpenAi() throws Exception {

        // given
        List<String> baseDay1LocationNames =
                day1LocationNamesInBasePlan();

        PlanEditContext planEditContext =
                new PlanEditContext(
                        baseCreateTravelRequest,
                        baseHealthContexts,
                        baseCurrentPlan,
                        "오늘 부산 날씨 어때요?"
                );

        // when
        EditPlanAiResponse response =
                travelRecommendHandler.editPlanByAi(planEditContext);

        // then
        printEditPlanResponse(
                "처리 불가능 수정 요청 응답",
                response
        );

        assertStructurallyValidEditResponse(response);

        assertThat(response.processable())
                .isFalse();

        assertThat(response.changes())
                .isEmpty();

        assertThat(day1LocationNamesInEditResponse(response))
                .isEqualTo(baseDay1LocationNames);
    }

    // 지원되지 않는 하위 요청: 여행 기간 변경은 processable=true이지만 일수는 그대로 유지되고 changes에 안내만 남아야 함
    @Test
    @DisplayName("실제 OpenAI 지원되지 않는 수정 요청 - 여행 기간 변경")
    void editPlanKeepsDayCountWhenDateChangeRequestedWithRealOpenAi() throws Exception {

        // given
        PlanEditContext planEditContext =
                new PlanEditContext(
                        baseCreateTravelRequest,
                        baseHealthContexts,
                        baseCurrentPlan,
                        "여행 기간을 3일로 늘려주세요."
                );

        // when
        EditPlanAiResponse response =
                travelRecommendHandler.editPlanByAi(planEditContext);

        // then
        printEditPlanResponse(
                "여행 기간 변경 수정 요청 응답",
                response
        );

        assertStructurallyValidEditResponse(response);

        assertThat(response.processable())
                .isTrue();

        assertThat(response.changes())
                .isNotEmpty();
    }


    // EditPlanAiResponse 응답 pretty print 출력
    private void printEditPlanResponse(
            String label,
            EditPlanAiResponse response
    ) throws Exception {

        System.out.println(
                "===== " + label + " ====="
        );

        System.out.println(
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(response)
        );
    }

    // EditPlanAiResponse 구조적 불변조건 검증 (일수, dayNumber/date, 스케줄 존재, tags null 아님)
    private void assertStructurallyValidEditResponse(
            EditPlanAiResponse response
    ) {

        assertThat(response)
                .isNotNull();

        assertThat(response.planDays())
                .isNotNull()
                .hasSize(EXPECTED_DAY_COUNT);

        assertThat(response.planDays())
                .allSatisfy(planDay -> {

                    assertThat(planDay.dayNumber())
                            .isNotNull();

                    assertThat(planDay.date())
                            .isNotNull();

                    assertThat(planDay.schedules())
                            .isNotNull()
                            .isNotEmpty();

                    assertThat(planDay.schedules())
                            .allSatisfy(schedule ->
                                    assertThat(schedule.tags())
                                            .isNotNull()
                            );
                });
    }

    // 기본 일정(currentPlan)의 ATTRACTION 슬롯 개수
    private long countAttractionSchedulesInBasePlan() {

        return baseCurrentPlan.planDays().stream()
                .flatMap(planDay -> planDay.schedules().stream())
                .filter(schedule -> schedule.courseType() == CourseType.ATTRACTION)
                .count();
    }

    // 수정 응답(EditPlanAiResponse)의 ATTRACTION 슬롯 개수
    private long countAttractionSchedulesInEditResponse(
            EditPlanAiResponse response
    ) {

        return response.planDays().stream()
                .flatMap(planDay -> planDay.schedules().stream())
                .filter(schedule -> schedule.courseType() == CourseType.ATTRACTION)
                .count();
    }

    // 기본 일정(currentPlan) 1일차 장소명 목록
    private List<String> day1LocationNamesInBasePlan() {

        return baseCurrentPlan.planDays().stream()
                .filter(planDay -> planDay.dayNumber() == 1)
                .findFirst()
                .orElseThrow()
                .schedules().stream()
                .map(GetAiPlanResponse.PlanScheduleDetail::locationName)
                .toList();
    }

    // 수정 응답(EditPlanAiResponse) 1일차 장소명 목록
    private List<String> day1LocationNamesInEditResponse(
            EditPlanAiResponse response
    ) {

        return response.planDays().stream()
                .filter(planDay -> planDay.dayNumber() == 1)
                .findFirst()
                .orElseThrow()
                .schedules().stream()
                .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .toList();
    }

    // 기본 일정(currentPlan) 1일차에서 장소명이 유일한(중복되지 않는) 일정
    private GetAiPlanResponse.PlanScheduleDetail day1UniqueNamedScheduleInBasePlan() {

        List<GetAiPlanResponse.PlanScheduleDetail> day1Schedules =
                baseCurrentPlan.planDays().stream()
                        .filter(planDay -> planDay.dayNumber() == 1)
                        .findFirst()
                        .orElseThrow()
                        .schedules();

        Map<String, Long> day1LocationNameCounts =
                day1Schedules.stream()
                        .map(GetAiPlanResponse.PlanScheduleDetail::locationName)
                        .filter(locationName -> locationName != null && !locationName.isBlank())
                        .collect(Collectors.groupingBy(
                                locationName -> locationName,
                                Collectors.counting()
                        ));

        return day1Schedules.stream()
                .filter(schedule ->
                        schedule.locationName() != null
                                && !schedule.locationName().isBlank()
                                && day1LocationNameCounts.get(schedule.locationName()) == 1L
                )
                .findFirst()
                .orElseThrow();
    }

    // 수정 응답(EditPlanAiResponse) 1일차 특정 시작 시각 슬롯 장소명
    private Optional<String> locationNameAtStartTimeInEditResponseDay1(
            EditPlanAiResponse response,
            LocalTime startTime
    ) {

        return response.planDays().stream()
                .filter(planDay -> planDay.dayNumber() == 1)
                .findFirst()
                .orElseThrow()
                .schedules().stream()
                .filter(schedule -> schedule.startTime().equals(startTime))
                .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .findFirst();
    }

    // CreatePlanAiResponse(생성 응답)를 currentPlan 형태(GetAiPlanResponse)로 변환 (editPlanByAi 입력용)
    private GetAiPlanResponse toGetAiPlanResponse(
            CreateTravelRequest createTravelRequest,
            CreatePlanAiResponse createPlanAiResponse
    ) {

        List<GetAiPlanResponse.PlanDayDetail> planDayDetails =
                createPlanAiResponse.planDays().stream()
                        .map(this::toGetAiPlanDayDetail)
                        .toList();

        return new GetAiPlanResponse(
                createTravelRequest.travelName(),
                createTravelRequest.travelStyle(),
                createTravelRequest.travelTheme(),
                List.of(),
                List.of(),
                Set.of(),
                planDayDetails
        );
    }

    private GetAiPlanResponse.PlanDayDetail toGetAiPlanDayDetail(
            CreatePlanAiResponse.PlanDayDetail planDayDetail
    ) {

        return new GetAiPlanResponse.PlanDayDetail(
                planDayDetail.dayNumber(),
                planDayDetail.date(),
                planDayDetail.schedules().stream()
                        .map(this::toGetAiPlanScheduleDetail)
                        .toList()
        );
    }

    private GetAiPlanResponse.PlanScheduleDetail toGetAiPlanScheduleDetail(
            CreatePlanAiResponse.PlanScheduleDetail scheduleDetail
    ) {

        return new GetAiPlanResponse.PlanScheduleDetail(
                scheduleDetail.scheduleType(),
                scheduleDetail.courseType(),
                scheduleDetail.startTime(),
                scheduleDetail.endTime(),
                scheduleDetail.locationName(),
                scheduleDetail.location(),
                scheduleDetail.longitude(),
                scheduleDetail.latitude(),
                scheduleDetail.imageUrl(),
                scheduleDetail.thumbNailImageUrl(),
                scheduleDetail.stayMinutes(),
                scheduleDetail.travelMinutes(),
                scheduleDetail.tags(),
                toGetAiPlanMedicationSchedule(scheduleDetail.medication()),
                toGetAiPlanRestaurantDetail(scheduleDetail.restaurantDetail())
        );
    }

    private GetAiPlanResponse.MedicationSchedule toGetAiPlanMedicationSchedule(
            CreatePlanAiResponse.MedicationSchedule medicationSchedule
    ) {

        if (medicationSchedule == null) {
            return null;
        }

        return new GetAiPlanResponse.MedicationSchedule(
                medicationSchedule.intervalMinutes(),
                medicationSchedule.description()
        );
    }

    private GetAiPlanResponse.RestaurantDetail toGetAiPlanRestaurantDetail(
            CreatePlanAiResponse.RestaurantDetail restaurantDetail
    ) {

        if (restaurantDetail == null) {
            return null;
        }

        return new GetAiPlanResponse.RestaurantDetail(
                restaurantDetail.menuName(),
                restaurantDetail.carbohydrate(),
                restaurantDetail.sodium(),
                restaurantDetail.fat(),
                restaurantDetail.openTime(),
                restaurantDetail.address(),
                restaurantDetail.longitude(),
                restaurantDetail.latitude(),
                restaurantDetail.imageUrl()
        );
    }
}
