package com.planb.query.travel.service;

import com.planb.query.travel.dto.response.RestaurantDetailQueryResponse;
import com.planb.query.travel.repository.RestaurantDetailQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RestaurantDetailQueryService {

    private final RestaurantDetailQueryRepository restaurantDetailQueryRepository;

    // planScheduleId 리스트로 RestaurantDetail객체 조회하기
    public List<RestaurantDetailQueryResponse> getRestaurantDetailsByPlanScheduleIds
            (List<Long> planScheduleIds){

        return restaurantDetailQueryRepository
                .findRestaurantDetailsByPlanScheduleIds(planScheduleIds);
    }
}
