package com.planb.unit.domain.travel.service;

import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.repository.TravelRepository;
import com.planb.domain.travel.service.TravelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelServiceTest {

    @Mock
    private TravelRepository travelRepository;

    @Mock
    private TravelRecommendHandler travelRecommendHandler;

    @InjectMocks
    private TravelService travelService;

    @Test
    @DisplayName("Travel 객체 생성")
    void createTravel() {

        LocalDate startDate =
                LocalDate.of(
                        2026,
                        8,
                        26
                );

        List<CreateTravelRequest.PlannedPlaceDetail> plannedPlaces =
                List.of(
                        new CreateTravelRequest.PlannedPlaceDetail(
                                "해운대해수욕장",
                                "부산광역시 해운대구"
                        )
                );

        List<String> recommendFoods =
                List.of(
                        "돼지국밥",
                        "밀면"
                );

        CreateTravelRequest request =
                new CreateTravelRequest(
                        "부산 여행",
                        "부산광역시",
                        "해운대구",
                        startDate,
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.TRANSIT,
                        "해운대해수욕장",
                        plannedPlaces,
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of("돼지국밥"),
                        recommendFoods
                );

        Travel travel =
                travelService.createTravel(
                        request
                );

        assertEquals(
                "부산 여행",
                travel.getTravelName()
        );

        assertEquals(
                "부산광역시",
                travel.getLocationDo()
        );

        assertEquals(
                "해운대구",
                travel.getLocationSigungu()
        );

        assertEquals(
                startDate,
                travel.getStartDate()
        );

        assertEquals(
                startDate.plusDays(1),
                travel.getEndDate()
        );

        assertEquals(
                DateType.ONE_NIGHT_TWO_DAYS,
                travel.getDateType()
        );

        assertEquals(
                Transportation.TRANSIT,
                travel.getTransportation()
        );

        assertEquals(
                "해운대해수욕장",
                travel.getDecidedLocation()
        );

        assertEquals(
                TravelStyle.MATCH_MEAL_TIME,
                travel.getTravelStyle()
        );

        assertEquals(
                TravelTheme.TASTE,
                travel.getTravelTheme()
        );

        assertEquals(
                List.of("돼지국밥"),
                travel.getLocalFoods()
        );

        assertEquals(
                recommendFoods,
                travel.getRecommendFoods()
        );
    }

    @Test
    @DisplayName("AI 기반 지역 음식 추천")
    void makeRecommendFoodResponse() {

        MakeRecommendFoodsRequest request =
                new MakeRecommendFoodsRequest(
                        "부산광역시",
                        "해운대구"
                );

        MakeRecommendFoodResponse response =
                new MakeRecommendFoodResponse(
                        List.of(
                                "돼지국밥",
                                "밀면"
                        )
                );

        when(
                travelRecommendHandler.makeRecommendFood(
                        org.mockito.ArgumentMatchers.any(
                                MakeFoodRecommendCallRequest.class
                        )
                )
        ).thenReturn(response);

        MakeRecommendFoodResponse result =
                travelService.makeRecommendFoodResponse(
                        request
                );

        assertSame(
                response,
                result
        );

        ArgumentCaptor<MakeFoodRecommendCallRequest> captor =
                ArgumentCaptor.forClass(
                        MakeFoodRecommendCallRequest.class
                );

        verify(travelRecommendHandler)
                .makeRecommendFood(
                        captor.capture()
                );

        assertEquals(
                request,
                captor.getValue()
                        .request()
        );
    }

    @Test
    @DisplayName("Travel 객체 저장")
    void saveTravel() {

        Travel travel =
                Travel.builder()
                        .travelName("부산 여행")
                        .build();

        travelService.saveTravel(
                travel
        );

        verify(travelRepository)
                .save(travel);
    }

    @Test
    @DisplayName("Travel ID 기준 삭제")
    void deleteTravel() {

        Long travelId = 1L;

        travelService.deleteTravel(
                travelId
        );

        verify(travelRepository)
                .deleteById(travelId);
    }
}