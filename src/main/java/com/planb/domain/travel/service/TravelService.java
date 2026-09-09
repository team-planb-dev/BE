package com.planb.domain.travel.service;

import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.health.entity.Health;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.TravelHealth;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.repository.TravelHealthRepository;
import com.planb.domain.travel.repository.TravelRepository;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TravelService {

    /*
     repository
     */
    private final TravelRepository travelRepository;
    private final TravelHealthRepository travelHealthRepository;
    private final UserRepository userRepository;

    /*
     handler
     */
    private final TravelRecommendHandler travelRecommendHandler;


    // Travel 객체 생성
    public Travel createTravel(CreateTravelRequest createTravelRequest, Long userId){

        User user = userRepository.getReferenceById(userId);

        return Travel
                .builder()
                .user(user)
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

    // Travel 객체 단건 조회하기 (존재 검증은 호출부에서 이미 끝난 상태를 전제)
    public Travel findTravelById(Long travelId){
        return travelRepository.getReferenceById(travelId);
    }

    // Travel 객체 저장하기
    public void saveTravel(Travel travel){
        travelRepository.save(travel);
    }

    // Travel 객체 삭제하기
    public void deleteTravel(Long travelId){
        travelRepository.deleteById(travelId);
    }

    /**
     * 이번 여행에 참여할 구성원을 Travel과 연결해 저장한다.
     *
     * 같은 구성원이 중복 전달되어도 관계는 한 번만 저장한다.
     *
     * @param travel  구성원을 연결할 여행
     * @param healths 이번 여행에 참여할 구성원
     */
    public void saveTravelHealths(
            Travel travel,
            List<Health> healths
    ) {

        travelHealthRepository.saveAll(healths
                .stream()
                .distinct()
                .map(health -> TravelHealth
                        .builder()
                        .travel(travel)
                        .health(health)
                        .build())
                .toList());
    }

    /**
     * 여행에 연결된 구성원을 조회한다.
     *
     * @param travelId 조회할 여행 id
     * @return 여행 생성 당시 선택한 구성원
     */
    public List<Health> findHealthListByTravelId(Long travelId) {

        return travelHealthRepository
                .findAllByTravelId(travelId)
                .stream()
                .map(TravelHealth::getHealth)
                .toList();
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
