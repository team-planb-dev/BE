package com.planb.domain.travel.service;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.dto.request.CreateRestaurantDetailRequest;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.RestaurantDetail;
import com.planb.domain.travel.repository.RestaurantDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RestaurantDetailService {

    private final RestaurantDetailRepository restaurantDetailRepository;

    public RestaurantDetail createRestaurantDetail
            (PlanSchedule planSchedule,
             CreateRestaurantDetailRequest createRestaurantDetailRequest){

        return RestaurantDetail
                .builder()
                .planSchedule(planSchedule)
                .menuName(
                        createRestaurantDetailRequest
                                .menuName())
                .carbohydrate(
                        createRestaurantDetailRequest
                                .carbohydrate())
                .sodium(
                        createRestaurantDetailRequest
                                .sodium())
                .fat(
                        createRestaurantDetailRequest
                                .fat())
                .openTime(
                        createRestaurantDetailRequest
                                .openTime())
                .address(
                        createRestaurantDetailRequest
                                .address())
                .longitude(
                        createRestaurantDetailRequest
                                .longitude())
                .latitude(
                        createRestaurantDetailRequest
                                .latitude())
                .imageUrl(
                        createRestaurantDetailRequest
                                .imageUrl())
                .build();
    }

    public List<RestaurantDetail> makeRestaurantDetailList
            (List<PlanSchedule> planSchedules,
             List<CreatePlanAiResponse.PlanScheduleDetail> scheduleDetails) {

        return IntStream.range(
                        0,
                        scheduleDetails.size()
                )
                .filter(index ->
                        scheduleDetails
                                .get(index)
                                .restaurantDetail() != null
                )
                .mapToObj(index ->
                        createRestaurantDetail(
                                planSchedules.get(index),
                                CreateRestaurantDetailRequest.from(
                                        scheduleDetails
                                                .get(index)
                                                .restaurantDetail()
                                )
                        )
                )
                .toList();
    }

    /*
    기본 CRUD 모음
     */

    // RestaurantDetail 객체 저장하기
    public void saveRestaurantDetailAll(List<RestaurantDetail> restaurantDetails){
        restaurantDetailRepository.saveAll(restaurantDetails);
    }

    // 특정 PlanSchedule 목록에 속한 RestaurantDetail 리스트 일괄 삭제하기
    public void deleteAllByPlanScheduleIn(List<PlanSchedule> planSchedules){
        restaurantDetailRepository.deleteAllByPlanScheduleIn(planSchedules);
    }
}
