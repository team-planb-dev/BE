package com.planb.ai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.client.OpenAiClient;
import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.domain.travel.policy.MealSlotPolicy;
import com.planb.domain.travel.policy.TouristPlaceCountPolicy;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.PlaceReselectResponse;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.ai.dto.response.PlanEditScope;
import com.planb.ai.prompt.PlanEditScopePrompt;
import com.planb.ai.prompt.PlaceReselectPrompt;
import com.planb.ai.prompt.RebuildPlanDayPrompt;
import com.planb.ai.mcp.PlanTourismTool;
import com.planb.ai.mcp.TourismTool;
import com.planb.ai.prompt.EditPlanPrompt;
import com.planb.ai.prompt.FoodRecommendPrompt;
import com.planb.ai.prompt.TravelPlanPrompt;
import com.planb.ai.prompt.VerifiedPlacePrompt;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.ScheduleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TravelRecommendHandler {

    /*
    Client
     */
    private final OpenAiClient openAiClient;

    /*
    Helper
     */
    private final ObjectMapper objectMapper;

    private final BeanOutputConverter<CreatePlanAiResponse> createPlanAiResponseConverter;

    private final BeanOutputConverter<EditPlanAiResponse> editPlanAiResponseConverter;
    private final BeanOutputConverter<RebuildPlanDayResponse> rebuildPlanDayResponseConverter;

    /*
    Tool
     */
    private final TourismTool tourismTool;

    // 지역에 따른 음식 추천 받기
    public MakeRecommendFoodResponse makeRecommendFood
    (MakeFoodRecommendCallRequest request){

        return openAiClient
                .call(
                        new FoodRecommendPrompt(
                                request
                                        .request()
                                        .fullLocation()),
                        MakeRecommendFoodResponse.class);
    }

    // AI로 사용자의 동행자 및 건강정보를 반영하여 일정생성
    // 날짜 또는 walkType 기준 관광지 개수가 맞지 않으면 조립 실패로 간주하고 재시도 대상에 포함
    public CreatePlanAiResponse createPlanByAi(TravelPlanContext travelPlanContext){

        return createPlanByAi(travelPlanContext, new PlaceCandidateContext());
    }

    // 호출 단위 검색 원본 수집
    public CreatePlanAiResponse createPlanByAi(
            TravelPlanContext travelPlanContext,
            PlaceCandidateContext candidates
    ) {

        CreatePlanAiResponse response = openAiClient
                .call(
                        new VerifiedPlacePrompt(new TravelPlanPrompt(
                                travelPlanContext,
                                objectMapper
                        )),
                        createPlanAiResponseConverter,
                        validatePlan(
                                travelPlanContext,
                                candidates
                        ),
                        new PlanTourismTool(tourismTool, candidates)
                );

        // 관광지 개수 보정과 빈 슬롯 채우기는 검증 직전에 PlanService가 한 번만 한다.
        // 생성·편집·재구성 응답이 모두 같은 검증을 지나므로 보정도 그 지점에 두어야 갈라지지 않는다.
        return response;
    }

    // AI로 기존 일정을 자연어 수정 요청에 맞춰 부분 수정
    // planDays가 비어있거나 dateType 기준 예상 일수와 다르면 무효 응답으로 간주하고 재시도 대상에 포함
    public EditPlanAiResponse editPlanByAi(PlanEditContext planEditContext){

        return editPlanByAi(planEditContext, new PlaceCandidateContext());
    }

    // 호출 단위 검색 원본 수집
    public EditPlanAiResponse editPlanByAi(
            PlanEditContext planEditContext,
            PlaceCandidateContext candidates
    ) {

        EditPlanAiResponse response = openAiClient
                .call(
                        new VerifiedPlacePrompt(new EditPlanPrompt(
                                planEditContext,
                                objectMapper
                        )),
                        editPlanAiResponseConverter,
                        validateEditPlan(
                                planEditContext
                                        .createTravelRequest()
                                        .dateType()
                        ),
                        new PlanTourismTool(tourismTool, candidates)
                );

        return response;
    }

    // 사용자 요청의 전체 날짜 재구성 범위 해석
    public PlanEditScope classifyEditScope(PlanEditContext context) {

        return openAiClient.call(new PlanEditScopePrompt(context),
                PlanEditScope.class);
    }

    // 지정 날짜 하나의 새 후보 검색 및 재구성
    public RebuildPlanDayResponse rebuildDay(
            PlanEditContext context,
            CreatePlanAiResponse current,
            Integer dayNumber,
            String reason,
            PlaceCandidateContext candidates
    ) {

        return openAiClient
                .call(
                        new VerifiedPlacePrompt(
                                new RebuildPlanDayPrompt(
                                        context,
                                        current,
                                        dayNumber,
                                        reason,
                                        objectMapper)),
                        rebuildPlanDayResponseConverter,
                        new PlanTourismTool(
                                tourismTool,
                                candidates));
    }

    // 실패 슬롯 하나의 제한 재선택
    public CreatePlanAiResponse.PlanScheduleDetail reselectPlace(
            PlaceReselectPrompt prompt,
            PlaceCandidateContext candidates
    ) {

        PlaceReselectResponse response = openAiClient
                .call(
                        new VerifiedPlacePrompt(prompt),
                        new BeanOutputConverter<>(PlaceReselectResponse.class),
                        new PlanTourismTool(
                                tourismTool,
                                candidates));

        if (response == null) {
            return null;
        }

        CreatePlanAiResponse.PlanScheduleDetail slot = prompt.slot();

        String candidateId = candidates
                .find(response.selectedCandidateId()) == null
                ? null
                : response.selectedCandidateId();

        return new CreatePlanAiResponse.PlanScheduleDetail(
                slot.scheduleType(),
                slot.courseType(),
                slot.startTime(),
                slot.endTime(),
                slot.locationName(),
                slot.location(),
                slot.longitude(),
                slot.latitude(),
                slot.imageUrl(),
                slot.thumbNailImageUrl(),
                slot.stayMinutes(),
                slot.travelMinutes(),
                slot.tags(),
                slot.medication(),
                response.restaurantDetail(),
                candidateId);
    }

    // 날짜 수와 walkType 기준 관광지 개수, 등록 식사시각을 지나는 날짜의 식사 슬롯이 모두 맞는 응답만 허용
    private static Function<CreatePlanAiResponse, List<String>> validatePlan(
            TravelPlanContext context,
            PlaceCandidateContext candidates
    ) {

        int expectedDayCount = context
                .createTravelRequest()
                .dateType()
                .getPlusDays() + 1;

        return response -> {
            if (response == null || response.planDays() == null) {
                return List.of("planDays: 일정 응답이 없습니다.");
            }

            List<String> failures = new ArrayList<>();

            if (response.planDays().size() != expectedDayCount) {
                failures.add(
                        "planDays: 여행 일수 " + expectedDayCount
                                + "일 필요 / 실제 " + response.planDays().size() + "일"
                );
            }

            // 부족분을 이번 호출의 검색 후보로 채울 수 있으면 재시도 대상이 아니다.
            // Java가 확정 후보로 채우는 편이 재시도보다 확실하고, 재시도 예산을 아낀다.
            failures.addAll(
                    unfillable(
                            touristPlaceCountFailures(
                                    response,
                                    context.healthContexts(),
                                    expectedDayCount
                            ),
                            unusedCandidateCount(response, candidates, true)
                    )
            );

            failures.addAll(
                    unfillable(
                            mealSlotFailures(
                                    response,
                                    context.healthContexts(),
                                    expectedDayCount
                            ),
                            unusedCandidateCount(response, candidates, false)
                    )
            );

            return failures;
        };
    }

    // 없으면 안 되는 식사가 빠진 날짜를 교정 사유로 만든다.
    // 면제된 끼니는 Java 보정기가 어차피 시도하므로 AI를 다시 부를 값이 없다.
    private static List<String> mealSlotFailures(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            int expectedDayCount
    ) {

        return response
                .planDays()
                .stream()
                .filter(Objects::nonNull)
                .flatMap(day -> MealSlotPolicy
                        .requiredMissingMeals(day, healthContexts, expectedDayCount)
                        .stream()
                        .map(mealType -> mealSlotFailure(day, mealType)))
                .toList();
    }

    /**
     * 후보로 채우고도 남는 부족분만 교정 사유로 남긴다.
     *
     * 사유 하나가 슬롯 하나에 대응하므로, 채울 수 있는 수만큼 앞에서 덜어낸다.
     */
    private static List<String> unfillable(
            List<String> failures,
            int fillableCount
    ) {

        return failures.size() <= fillableCount
                ? List.of()
                : failures.subList(fillableCount, failures.size());
    }

    // 응답에 아직 쓰이지 않은 후보 수. 이만큼은 Java가 채울 수 있다.
    private static int unusedCandidateCount(
            CreatePlanAiResponse response,
            PlaceCandidateContext candidates,
            boolean attraction
    ) {

        if (candidates == null) {
            return 0;
        }

        Set<String> usedNames = response
                .planDays()
                .stream()
                .filter(Objects::nonNull)
                .filter(day -> day.schedules() != null)
                .flatMap(day -> day.schedules().stream())
                .filter(Objects::nonNull)
                .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<PlaceCandidateContext.Candidate> pool = attraction
                ? candidates.attractionCandidates()
                : candidates.restaurantCandidates();

        return (int) pool
                .stream()
                .map(PlaceCandidateContext.Candidate::name)
                .filter(Objects::nonNull)
                .filter(name -> !usedNames.contains(name))
                .count();
    }

    private static String mealSlotFailure(
            CreatePlanAiResponse.PlanDayDetail day,
            ScheduleType mealType
    ) {

        return "planDays[day" + day.dayNumber()
                + "].schedules: " + mealType + " 식사 슬롯 필요 / 실제 없음"
                + " / 등록 식사시각을 지나는 일정이므로 음식점 후보로 식사 슬롯 추가";
    }

    private static List<String> touristPlaceCountFailures(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            int expectedDayCount
    ) {

        int expectedCount = TouristPlaceCountPolicy.expectedCount(healthContexts);

        if (expectedCount <= 0) {
            return List.of();
        }

        List<CreatePlanAiResponse.PlanDayDetail> planDays = response.planDays();

        List<String> failures = IntStream
                .range(0, expectedDayCount)
                .mapToObj(dayIndex -> {
                    CreatePlanAiResponse.PlanDayDetail day = dayIndex < planDays.size()
                            ? planDays.get(dayIndex)
                            : null;

                    List<CreatePlanAiResponse.PlanScheduleDetail> touristPlaces =
                            day == null || day.schedules() == null
                                    ? List.of()
                                    : day
                                            .schedules()
                                            .stream()
                                            .filter(Objects::nonNull)
                                            .filter(schedule -> schedule.courseType() == CourseType.ATTRACTION
                                                    || schedule.courseType() == CourseType.MUST_HAVE)
                                            .toList();

                    int actualCount = touristPlaces.size();

                    // 초과분은 TouristPlaceCountPolicy.trimExcess가 제거하므로 부족한 경우만 재시도 대상
                    if (actualCount >= expectedCount) {
                        return null;
                    }

                    String selectedCandidateIds = touristPlaces
                            .stream()
                            .map(CreatePlanAiResponse.PlanScheduleDetail::candidateId)
                            .filter(Objects::nonNull)
                            .toList()
                            .toString();

                    return "planDays[day" + (day == null ? dayIndex + 1 : day.dayNumber())
                            + "].schedules: 관광지 " + expectedCount
                            + "개 필요 / 실제 " + actualCount + "개"
                            + " / 추가 " + (expectedCount - actualCount) + "개"
                            + " / 유지 candidateId " + selectedCandidateIds
                            + " / 최초 관광지 candidate 목록 안에서 교정";
                })
                .filter(Objects::nonNull)
                .toList();

        return failures;
    }

    // 수정 응답의 모든 누락 조건을 한 번에 수집
    private static Function<EditPlanAiResponse, List<String>> validateEditPlan(
            DateType dateType
    ) {

        int expectedDayCount = dateType.getPlusDays() + 1;

        return response -> {
            if (response == null) {
                return List.of("response: 일정 수정 응답이 없습니다.");
            }

            List<String> failures = new ArrayList<>();

            if (response.planDays() == null) {
                failures.add("planDays: 수정된 일정이 없습니다.");
            } else if (response.planDays().size() != expectedDayCount) {
                failures.add(
                        "planDays: 여행 일수 " + expectedDayCount
                                + "일 필요 / 실제 " + response.planDays().size() + "일"
                );
            }

            if (response.processable()
                    && (response.changes() == null || response.changes().isEmpty())) {
                failures.add("changes: processable=true인 응답에는 수정 또는 미반영 내역이 필요합니다.");
            }

            if (!response.processable()
                    && response.changes() != null
                    && !response.changes().isEmpty()) {
                failures.add("changes: processable=false인 응답은 빈 목록이어야 합니다.");
            }

            return failures;
        };
    }

}
