package com.planb.query.health.service;


import com.planb.query.health.repository.FoodInfoQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FoodInfoQueryService {

    private final FoodInfoQueryRepository foodInfoQueryRepository;


    /*
    삭제 메소드
     */
    public void deleteAllByHealthId(Long healthId){
        foodInfoQueryRepository.deleteAllByHealthId(healthId);
    }


}
