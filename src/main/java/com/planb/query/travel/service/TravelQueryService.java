package com.planb.query.travel.service;

import com.planb.query.travel.dto.response.TravelConditionQueryResponse;
import com.planb.query.travel.repository.TravelQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TravelQueryService {

    private final TravelQueryRepository travelQueryRepository;

    // travelId로 여행조건 데이터 조회
    public TravelConditionQueryResponse getTravelConditionQueryResponse(Long travelId){

        return travelQueryRepository.findTravelConditionById(travelId);
    }
}
