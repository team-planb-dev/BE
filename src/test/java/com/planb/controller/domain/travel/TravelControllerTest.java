package com.planb.controller.domain.travel;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.controller.TravelController;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.GetAiPlanRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;
import com.planb.domain.travel.dto.request.SearchPlannedPlaceRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.facade.TravelFacade;
import com.planb.global.config.app.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TravelController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AppConfig.class)
class TravelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TravelFacade travelFacade;

    @Test
    @DisplayName("여행지 음식 추천 성공")
    void recommendLocalFoodSuccess() throws Exception {

        // given
        MakeRecommendFoodResponse response =
                new MakeRecommendFoodResponse(
                        List.of(
                                "돼지국밥",
                                "밀면"
                        )
                );

        when(travelFacade.showRecommendFoods(
                any(MakeRecommendFoodsRequest.class)
        )).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/v1/travel/recommend-local-food")
                                .param("locationDo", "부산광역시")
                                .param("locationSigungu", "해운대구")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        verify(travelFacade)
                .showRecommendFoods(
                        any(MakeRecommendFoodsRequest.class)
                );
    }

    @Test
    @DisplayName("계획 장소 검색 성공")
    void searchPlannedPlaceSuccess() throws Exception {

        // given
        SearchPlannedPlaceResponse response =
                new SearchPlannedPlaceResponse(
                        List.of(
                                new SearchPlannedPlaceResponse
                                        .PlannedPlaceDetail(
                                        "해운대해수욕장",
                                        "부산광역시 해운대구"
                                )
                        )
                );

        when(travelFacade.searchPlannedPlaceByText(
                any(SearchPlannedPlaceRequest.class)
        )).thenReturn(
                Mono.just(response)
        );

        // when
        MvcResult mvcResult =
                mockMvc.perform(
                                get("/api/v1/travel/search-planned-place")
                                        .param("searchText", "해운대")
                        )
                        .andExpect(
                                request().asyncStarted()
                        )
                        .andReturn();

        // then
        mockMvc.perform(
                        asyncDispatch(mvcResult)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        verify(travelFacade)
                .searchPlannedPlaceByText(
                        any(SearchPlannedPlaceRequest.class)
                );
    }

    @Test
    @WithMockUser(
            username = "testUser@example.com",
            roles = "USER"
    )
    @DisplayName("여행조건 등록 및 AI 일정 생성 성공")
    void addTravelOptionsAndRecommendSuccess() throws Exception {

        // given
        CreatePlanAiResponse response =
                new CreatePlanAiResponse(
                        List.of()
                );

        when(travelFacade.makeTravelOptionsAndRecommend(
                any(CreateTravelRequest.class),
                eq("testUser@example.com")
        )).thenReturn(response);

        String request = """
                {
                  "travelName": "부산 여행",
                  "locationDo": "부산광역시",
                  "locationSigungu": "해운대구",
                  "startDate": "2026-09-10",
                  "dateType": null,
                  "transportation": null,
                  "decidedLocation": "해운대해수욕장",
                  "plannedPlaces": [
                    {
                      "locationName": "해운대해수욕장",
                      "location": "부산광역시 해운대구"
                    }
                  ],
                  "travelStyle": null,
                  "travelTheme": null,
                  "localFood": "돼지국밥",
                  "recommendFoods": [
                    "밀면",
                    "돼지국밥"
                  ]
                }
                """;

        // when & then
        mockMvc.perform(
                        post("/api/v1/travel/add-with-recommend")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        verify(travelFacade)
                .makeTravelOptionsAndRecommend(
                        any(CreateTravelRequest.class),
                        eq("testUser@example.com")
                );
    }

    @Test
    @WithMockUser(
            username = "testUser@example.com",
            roles = "USER"
    )
    @DisplayName("AI 여행 계획 전체 조회 성공")
    void getAiPlanDetailAllSuccess() throws Exception {

        // given
        GetAiPlanResponse response =
                new GetAiPlanResponse(
                        "부산 여행",
                        TravelStyle.values()[0],
                        TravelTheme.values()[0],
                        List.of(),
                        List.of(),
                        Set.of(),
                        List.of()
                );

        when(travelFacade.getAiPlan(
                any(GetAiPlanRequest.class),
                eq("testUser@example.com")
        )).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/v1/travel/get-ai-travel-plan")
                                .param("travelId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        verify(travelFacade)
                .getAiPlan(
                        any(GetAiPlanRequest.class),
                        eq("testUser@example.com")
                );
    }
}