package com.planb.unit.global.ai.tool;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.mcp.GenerationTourismTool;
import com.planb.ai.mcp.PlanTourismTool;
import com.planb.ai.mcp.TourismTool;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class GenerationTourismToolTest {

    @Test
    void exposesOnlySearchToolsAndPreservesEditingTools() {

        TourismTool tourism = mock(TourismTool.class);
        PlaceCandidateContext candidates = new PlaceCandidateContext();

        GenerationTourismTool generation = new GenerationTourismTool(
                tourism,
                candidates,
                List.of()
        );

        assertThat(Arrays
                .stream(ToolCallbacks.from(generation))
                .map(callback -> callback.getToolDefinition().name())
                .toList())
                .containsExactlyInAnyOrder(
                        "searchAttractionsByRegion",
                        "searchRestaurantsByLocation",
                        "findPlaceWithRoute"
                );

        assertThat(Arrays
                .stream(ToolCallbacks.from(new PlanTourismTool(tourism, candidates)))
                .map(callback -> callback.getToolDefinition().name())
                .toList())
                .containsExactlyInAnyOrder(
                        "searchAttractionsByRegion",
                        "searchRestaurantsByLocation",
                        "findPlaceWithRoute",
                        "getRoute",
                        "getRestaurantDetail",
                        "evaluateFoodNutrition"
                );
    }

    @Test
    void returnsVerifiedMenusAndFetchesDetailOnlyOnce() {

        TourismTool tourism = mock(TourismTool.class);
        PlaceCandidateContext candidates = new PlaceCandidateContext();
        Kor2KeywordSearchResponse.Item item = mock(Kor2KeywordSearchResponse.Item.class);
        when(item.contentid()).thenReturn("123");
        when(item.contenttypeid()).thenReturn("39");
        when(item.title()).thenReturn("춘천식당");
        when(tourism.searchRestaurantsByLocation("막국수", "강원", "춘천"))
                .thenReturn(new Kor2KeywordSearchResponse(
                        new Kor2KeywordSearchResponse.Response(
                                null,
                                new Kor2KeywordSearchResponse.Body(
                                        new Kor2KeywordSearchResponse.Items(List.of(item)),
                                        1,
                                        1,
                                        1
                                )
                        )
                ));
        when(tourism.getRestaurantDetail("123"))
                .thenReturn(new Kor2RestaurantIntroResponse(
                        new Kor2RestaurantIntroResponse.Response(
                                null,
                                new Kor2RestaurantIntroResponse.Body(
                                        new Kor2RestaurantIntroResponse.Items(List.of(
                                                new Kor2RestaurantIntroResponse.Item("123", "39", "막국수 / 닭갈비", null)
                                        )),
                                        1,
                                        1,
                                        1
                                )
                        )
                ));

        GenerationTourismTool tool = new GenerationTourismTool(tourism, candidates, List.of());
        var result = tool.searchRestaurantsByLocation("막국수", "강원", "춘천");
        assertThat(result.getFirst().menus()).containsExactly("막국수", "닭갈비");
        assertThat(result.getFirst().place().candidateId()).isEqualTo("tour:123");
        assertThat(candidates.find("tour:123").name()).isEqualTo("춘천식당");
        assertThat(tool.searchRestaurantsByLocation("막국수", "강원", "춘천")).isEqualTo(result);
        verify(tourism, times(1)).getRestaurantDetail("123");
        verify(tourism, times(1)).searchRestaurantsByLocation("막국수", "강원", "춘천");
    }
}
