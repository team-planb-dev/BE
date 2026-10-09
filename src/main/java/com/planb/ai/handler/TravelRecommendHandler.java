package com.planb.ai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.client.OpenAiClient;
import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.domain.travel.policy.TouristPlaceCountPolicy;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.CreatePlanSelection;
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
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final BeanOutputConverter<CreatePlanSelection> createPlanSelectionConverter;

    private final BeanOutputConverter<EditPlanAiResponse> editPlanAiResponseConverter;
    private final BeanOutputConverter<RebuildPlanDayResponse> rebuildPlanDayResponseConverter;

    /*
    Tool
     */
    private final TourismTool tourismTool;

    private final PlanGenerationSelectionMapper selectionMapper;

    // 지역에 따른 음식 추천 받기
    public MakeRecommendFoodResponse makeRecommendFood
    (MakeFoodRecommendCallRequest request) {

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
    public CreatePlanAiResponse createPlanByAi(TravelPlanContext travelPlanContext) {

        return createPlanByAi(travelPlanContext, new PlaceCandidateContext());
    }

    // 호출 단위 검색 원본 수집
    public CreatePlanAiResponse createPlanByAi(
            TravelPlanContext travelPlanContext,
            PlaceCandidateContext candidates
    ) {

        // 지정 장소를 무작위 관광지 후보와 별도로 고정 (모델 계약 불변, tool 결과 내용만 보강)
        List<Kor2KeywordSearchResponse.Item> plannedPlaces = tourismTool.findPlannedPlaces(
                travelPlanContext
                        .createTravelRequest()
                        .plannedPlaces(),
                travelPlanContext
                        .createTravelRequest()
                        .locationDo(),
                travelPlanContext
                        .createTravelRequest()
                        .locationSigungu()
        );

        Function<CreatePlanAiResponse, List<String>> validation = validatePlan(
                travelPlanContext,
                candidates
        );

        Function<CreatePlanSelection, List<String>> selectionValidation = response -> validation.apply(
                selectionMapper.toResponse(
                        response,
                        candidates
                )
        );

        CreatePlanSelection selection = openAiClient
                .call(
                        new VerifiedPlacePrompt(new TravelPlanPrompt(
                                travelPlanContext,
                                objectMapper
                        )),
                        createPlanSelectionConverter,
                        selectionValidation,
                        new PlanTourismTool(
                                tourismTool,
                                candidates,
                                plannedPlaces
                        )
                );

        // 재시도 초기화나 모델의 관광지 검색 생략과 무관하게 후속 보충 단계에 고정 후보 전달
        plannedPlaces.forEach(candidates::pin);

        // PlanService 검증 직전의 관광지 개수·빈 슬롯 단일 보정
        // 생성·편집·재구성의 공통 보정 지점
        return selectionMapper.toResponse(
                selection,
                candidates
        );
    }

    // AI로 기존 일정을 자연어 수정 요청에 맞춰 부분 수정
    // planDays가 비어있거나 dateType 기준 예상 일수와 다르면 무효 응답으로 간주하고 재시도 대상에 포함
    public EditPlanAiResponse editPlanByAi(PlanEditContext planEditContext) {

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

    /**
     * AI 검색 후보 부족 시 여행 지역의 음식점 보충
     */
    public void collectRestaurantCandidates(
            TravelPlanContext context,
            PlaceCandidateContext candidates
    ) {

        new PlanTourismTool(
                tourismTool,
                candidates
        ).searchRestaurantCandidatesByRegion(
                context
                        .createTravelRequest()
                        .locationDo(),
                context
                        .createTravelRequest()
                        .locationSigungu()
        );
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
                                        objectMapper
                                )),
                rebuildPlanDayResponseConverter,
                new PlanTourismTool(
                                tourismTool,
                                candidates)
        );
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
                                candidates)
        );

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
                candidateId
        );
    }

    // 날짜·관광지 개수 검증과 후보로 보충할 수 없는 부족분의 AI 교정
    private Function<CreatePlanAiResponse, List<String>> validatePlan(
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

            if (response
                    .planDays()
                    .size() != expectedDayCount) {
                failures.add(
                        "planDays: 여행 일수 " + expectedDayCount
                                + "일 필요 / 실제 "
                                + response
                                        .planDays()
                                        .size()
                                + "일"
                );
            }

            // 검색 후보로 보충 가능한 부족분의 AI 재시도 제외
            // 확정 후보의 Java 보충으로 AI 재시도 예산 보존
            failures.addAll(
                    unfillableShortages(
                            response,
                            context.healthContexts(),
                            unusedAttractionCandidateCount(response, candidates)
                    )
            );

            return failures;
        };
    }

    /**
     * 미사용 후보로 날짜 순서대로 보충한 뒤에도 남는 관광지 부족분의 교정 사유
     * 부족 개수(슬롯 단위)와 미사용 후보 수(후보 단위)를 같은 단위로 비교
     * 개수 판정은 최종 검증과 같은 TouristPlaceCountPolicy.violations 사용
     */
    private static List<String> unfillableShortages(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            int fillableCount
    ) {

        int remaining = fillableCount;

        List<String> failures = new ArrayList<>();

        for (TouristPlaceCountPolicy.Violation violation : TouristPlaceCountPolicy.violations(
                response,
                healthContexts,
                Set.of()
        )) {
            int shortage = violation.shortage();

            // 초과분은 TouristPlaceCountPolicy.trimExcess가 제거하므로 부족한 경우만 재시도 대상
            if (shortage == 0) {
                continue;
            }

            if (shortage <= remaining) {
                remaining -= shortage;

                continue;
            }

            remaining = 0;

            failures.add(shortageReason(
                    response,
                    violation
            ));
        }

        return failures;
    }

    // Java가 보충할 수 있는 미사용 관광지 후보 수, 이름이 아닌 candidateId 기준
    private static int unusedAttractionCandidateCount(
            CreatePlanAiResponse response,
            PlaceCandidateContext candidates
    ) {

        if (candidates == null) {
            return 0;
        }

        Set<String> usedCandidateIds = response
                .planDays()
                .stream()
                .filter(Objects::nonNull)
                .filter(day -> day.schedules() != null)
                .flatMap(day -> day
                        .schedules()
                        .stream())
                .filter(Objects::nonNull)
                .map(CreatePlanAiResponse.PlanScheduleDetail::candidateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return (int) candidates
                .attractionCandidates()
                .stream()
                .map(PlaceCandidateContext.Candidate::candidateId)
                .filter(Objects::nonNull)
                .filter(candidateId -> !usedCandidateIds.contains(candidateId))
                .count();
    }

    private static String shortageReason(
            CreatePlanAiResponse response,
            TouristPlaceCountPolicy.Violation violation
    ) {

        String selectedCandidateIds = response
                .planDays()
                .stream()
                .filter(Objects::nonNull)
                .filter(day -> day.dayNumber() == violation.dayNumber())
                .filter(day -> day.schedules() != null)
                .flatMap(day -> day
                        .schedules()
                        .stream())
                .filter(Objects::nonNull)
                .filter(schedule -> schedule.courseType() == CourseType.ATTRACTION
                        || schedule.courseType() == CourseType.MUST_HAVE)
                .map(CreatePlanAiResponse.PlanScheduleDetail::candidateId)
                .filter(Objects::nonNull)
                .toList()
                .toString();

        return "planDays[day" + violation.dayNumber()
                + "].schedules: 관광지 " + violation.minimumCount()
                + "개 필요 / 실제 " + violation.actualCount() + "개"
                + " / 추가 " + violation.shortage() + "개"
                + " / 유지 candidateId " + selectedCandidateIds
                + " / 최초 관광지 candidate 목록 안에서 교정";
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
            } else if (response
                    .planDays()
                    .size() != expectedDayCount) {
                failures.add(
                        "planDays: 여행 일수 " + expectedDayCount
                                + "일 필요 / 실제 "
                                + response
                                        .planDays()
                                        .size()
                                + "일"
                );
            }

            if (response.processable()
                    && (response.changes() == null || response
                            .changes()
                            .isEmpty())) {
                failures.add("changes: processable=true인 응답에는 수정 또는 미반영 내역이 필요합니다.");
            }

            if (!response.processable()
                    && response.changes() != null
                    && !response
                            .changes()
                            .isEmpty()) {
                failures.add("changes: processable=false인 응답은 빈 목록이어야 합니다.");
            }

            return failures;
        };
    }

}
