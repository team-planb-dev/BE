package com.planb.ai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.client.OpenAiClient;
import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.ai.dto.response.PlanEditScope;
import com.planb.ai.prompt.PlanEditScopePrompt;
import com.planb.ai.prompt.RebuildPlanDayPrompt;
import com.planb.ai.mcp.PlanTourismTool;
import com.planb.ai.mcp.TourismTool;
import com.planb.ai.prompt.AiPrompt;
import com.planb.ai.prompt.EditPlanPrompt;
import com.planb.ai.prompt.FoodRecommendPrompt;
import com.planb.ai.prompt.TravelPlanPrompt;
import com.planb.ai.prompt.VerifiedPlacePrompt;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.entity.constant.DateType;

import java.util.function.Predicate;

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
    // planDays가 비어있거나 dateType 기준 예상 일수와 다르면 STEP 9 조립 실패로 간주하고 재시도 대상에 포함
    public CreatePlanAiResponse createPlanByAi(TravelPlanContext travelPlanContext){

        return createPlanByAi(travelPlanContext, new PlaceCandidateContext());
    }

    // 호출 단위 검색 원본 수집
    public CreatePlanAiResponse createPlanByAi(
            TravelPlanContext travelPlanContext,
            PlaceCandidateContext candidates
    ) {

        return openAiClient
                .call(
                        new VerifiedPlacePrompt(new TravelPlanPrompt(
                                travelPlanContext,
                                objectMapper
                        )),
                        createPlanAiResponseConverter,
                        hasPlanDays(
                                travelPlanContext
                                        .createTravelRequest()
                                        .dateType()
                        ),
                        new PlanTourismTool(tourismTool, candidates)
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
                        hasEditPlanDays(
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
            AiPrompt prompt,
            PlaceCandidateContext candidates
    ) {

        CreatePlanAiResponse.PlanScheduleDetail response = openAiClient.call(new VerifiedPlacePrompt(prompt),
                new BeanOutputConverter<>(CreatePlanAiResponse.PlanScheduleDetail.class),
                new PlanTourismTool(tourismTool, candidates));

        if (response == null || response.candidateId() != null) {
            return response;
        }

        PlaceCandidateContext.Candidate candidate = candidates
                .findUniqueByName(response.locationName());

        if (candidate == null) {
            return response;
        }

        return new CreatePlanAiResponse.PlanScheduleDetail(
                response.scheduleType(),
                response.courseType(),
                response.startTime(),
                response.endTime(),
                response.locationName(),
                response.location(),
                response.longitude(),
                response.latitude(),
                response.imageUrl(),
                response.thumbNailImageUrl(),
                response.stayMinutes(),
                response.travelMinutes(),
                response.tags(),
                response.medication(),
                response.restaurantDetail(),
                candidate.candidateId());
    }

    // planDays가 null이거나 비어있으면 무효, dateType 기준 예상 일수와 다르면 무효
    // (조립을 완료하지 못했거나 일부 날짜를 누락한 응답)
    private static Predicate<CreatePlanAiResponse> hasPlanDays(DateType dateType) {

        int expectedDayCount = dateType.getPlusDays() + 1;

        return response -> response != null
                && response.planDays() != null
                && response.planDays().size() == expectedDayCount;
    }

    // editPlanByAi 응답 검증용 (EditPlanAiResponse는 CreatePlanAiResponse와 별개 타입이라 동일 로직 재정의)
    private static Predicate<EditPlanAiResponse> hasEditPlanDays(DateType dateType) {

        int expectedDayCount = dateType.getPlusDays() + 1;

        return response -> response != null
                && response.planDays() != null
                && response.planDays().size() == expectedDayCount;
    }

}
