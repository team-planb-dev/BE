package com.planb.unit.ai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.client.OpenAiClient;
import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.TourismTool;
import com.planb.ai.prompt.AiPrompt;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.converter.BeanOutputConverter;

import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class TravelRecommendHandlerContractTest {

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private BeanOutputConverter<CreatePlanAiResponse> createPlanAiResponseConverter;

    @Mock
    private BeanOutputConverter<EditPlanAiResponse> editPlanAiResponseConverter;

    @Mock
    private BeanOutputConverter<RebuildPlanDayResponse> rebuildPlanDayResponseConverter;

    @Mock
    private TourismTool tourismTool;

    @Mock
    private AiPrompt prompt;

    @Test
    @DisplayName("재선택 응답의 후보 ID 누락 시 같은 호출의 유일한 검색 후보로 복구")
    void restoresMissingCandidateIdFromUniqueCandidate() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(new PlaceWithRouteResult(
                true,
                "첨성대",
                "경상북도 경주시 첨성로 140-25",
                "129.219063",
                "35.834683",
                15,
                "kakao:8137362",
                "AT4",
                "여행 > 관광,명소"));

        PlanScheduleDetail response = new PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                "첨성대",
                null,
                null,
                null,
                null,
                null,
                60,
                null,
                Set.of(),
                null,
                null,
                null);

        doReturn(response)
                .when(openAiClient)
                .call(
                        any(AiPrompt.class),
                        any(BeanOutputConverter.class),
                        any(Object[].class));

        TravelRecommendHandler handler = new TravelRecommendHandler(
                openAiClient,
                objectMapper,
                createPlanAiResponseConverter,
                editPlanAiResponseConverter,
                rebuildPlanDayResponseConverter,
                tourismTool);

        PlanScheduleDetail result = handler.reselectPlace(prompt, candidates);

        assertEquals("kakao:8137362", result.candidateId());
    }
}
