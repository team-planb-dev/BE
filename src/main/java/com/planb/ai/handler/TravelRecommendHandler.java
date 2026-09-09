package com.planb.ai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.client.OpenAiClient;
import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
                                travelPlanContext
                        ),
                        new PlanTourismTool(tourismTool, candidates)
                );

        return trimExcessTouristPlaces(
                response,
                travelPlanContext.healthContexts()
        );
    }

    // 날짜 또는 walkType 기준 하루 관광지 개수
    private static int expectedTouristPlaceCount(
            List<TravelHealthContext> healthContexts
    ) {

        if (healthContexts == null || healthContexts.isEmpty()) {
            return 0;
        }

        boolean hasMinimalTraveler = healthContexts
                .stream()
                .anyMatch(context -> context.walkType() == WalkType.MINIMAL);

        return hasMinimalTraveler ? 2 : 3;
    }

    // 관광지 초과분은 AI 재시도 없이 뒤에서부터 제거한다. 사용자가 지정한 MUST_HAVE는 남긴다.
    // ponytail: 제거 이후 남은 슬롯의 travelMinutes는 이전 장소 기준 그대로 둔다.
    // 일정 시간이 앞당겨지지 않을 뿐 순서와 시간 검증은 통과하며, 정확한 이동시간이 필요해지면 재계산을 붙인다.
    private static CreatePlanAiResponse trimExcessTouristPlaces(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        if (response == null || response.planDays() == null) {
            return response;
        }

        int expectedCount = expectedTouristPlaceCount(healthContexts);

        if (expectedCount <= 0) {
            return response;
        }

        return new CreatePlanAiResponse(
                response
                        .planDays()
                        .stream()
                        .map(day ->
                                trimDayTouristPlaces(
                                        day,
                                        expectedCount
                                )
                        )
                        .toList()
        );
    }

    // 하루치 관광지 초과분 제거
    private static CreatePlanAiResponse.PlanDayDetail trimDayTouristPlaces(
            CreatePlanAiResponse.PlanDayDetail day,
            int expectedCount
    ) {

        if (day == null || day.schedules() == null) {
            return day;
        }

        List<CreatePlanAiResponse.PlanScheduleDetail> schedules = day.schedules();

        List<Integer> touristIndexes = IntStream
                .range(0, schedules.size())
                .filter(index -> {
                    CreatePlanAiResponse.PlanScheduleDetail schedule = schedules.get(index);

                    return schedule != null
                            && (schedule.courseType() == CourseType.ATTRACTION
                                    || schedule.courseType() == CourseType.MUST_HAVE);
                })
                .boxed()
                .toList();

        int excess = touristIndexes.size() - expectedCount;

        if (excess <= 0) {
            return day;
        }

        Set<Integer> removeIndexes = new HashSet<>();

        for (int cursor = touristIndexes.size() - 1;
                cursor >= 0 && removeIndexes.size() < excess;
                cursor--) {

            int index = touristIndexes.get(cursor);

            if (schedules.get(index).courseType() == CourseType.MUST_HAVE) {
                continue;
            }

            removeIndexes.add(index);
        }

        if (removeIndexes.isEmpty()) {
            return day;
        }

        return new CreatePlanAiResponse.PlanDayDetail(
                day.dayNumber(),
                day.date(),
                IntStream
                        .range(0, schedules.size())
                        .filter(index -> !removeIndexes.contains(index))
                        .mapToObj(schedules::get)
                        .toList()
        );
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

        return openAiClient
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

    // 날짜 수와 walkType 기준 관광지 개수가 모두 맞는 응답만 허용
    private static Function<CreatePlanAiResponse, List<String>> validatePlan(
            TravelPlanContext context
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

            failures.addAll(
                    touristPlaceCountFailures(
                            response,
                            context.healthContexts(),
                            expectedDayCount
                    )
            );

            return failures;
        };
    }

    private static List<String> touristPlaceCountFailures(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            int expectedDayCount
    ) {

        int expectedCount = expectedTouristPlaceCount(healthContexts);

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

                    // 초과분은 trimExcessTouristPlaces가 제거하므로 부족한 경우만 재시도 대상
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
