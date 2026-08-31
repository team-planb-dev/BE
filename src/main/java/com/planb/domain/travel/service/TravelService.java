package com.planb.domain.travel.service;

import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.repository.TravelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TravelService {

    /*
     repository
     */
    private final TravelRepository travelRepository;

    /*
     handler
     */
    private final TravelRecommendHandler travelRecommendHandler;


    // Travel 객체 생성
    public Travel createTravel(CreateTravelRequest createTravelRequest){

        return Travel
                .builder()
                .travelName(createTravelRequest
                        .travelName())
                .locationDo(createTravelRequest
                        .locationDo())
                .locationSigungu(createTravelRequest
                        .locationSigungu())
                .startDate(createTravelRequest
                        .startDate())
                .dateType(createTravelRequest
                        .dateType())
                .endDate(calculateEndDate(
                        createTravelRequest
                                .startDate(),
                        createTravelRequest
                                .dateType()))
                .transportation(createTravelRequest
                        .transportation())
                .decidedLocation(createTravelRequest
                        .decidedLocation())
                .travelStyle(createTravelRequest
                        .travelStyle())
                .travelTheme(createTravelRequest
                        .travelTheme())
                .localFoods(createTravelRequest
                        .localFoods())
                .recommendFoods(createTravelRequest
                        .recommendFoods())
                .build();
    }


    //  OpenAI API 호출 후, 해당 지역 음식 추천
    public MakeRecommendFoodResponse makeRecommendFoodResponse
    (MakeRecommendFoodsRequest makeRecommendFoodsRequest){
        return travelRecommendHandler
                .makeRecommendFood(new MakeFoodRecommendCallRequest(makeRecommendFoodsRequest));
    }



    /*
    기본 CRUD 모음
     */

    // Travel 객체 저장하기
    public void saveTravel(Travel travel){
        travelRepository.save(travel);
    }

    // Travel 객체 삭제하기
    public void deleteTravel(Long travelId){
        travelRepository.deleteById(travelId);
    }



    /*
    내부 헬퍼 메서드 모음
     */

    // 여행 마지막일 계산
    private LocalDate calculateEndDate(LocalDate startDate,
                                       DateType dateType) {

        return startDate
                .plusDays(dateType
                        .getPlusDays());
    }

}
