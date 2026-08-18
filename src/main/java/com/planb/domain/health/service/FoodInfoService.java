package com.planb.domain.health.service;

import com.planb.domain.health.dto.request.CreateFoodInfoRequest;
import com.planb.domain.health.entity.FoodInfo;
import com.planb.domain.health.repository.FoodInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodInfoService {

    private final FoodInfoRepository foodInfoRepository;

    public List<FoodInfo> makeFoodInfoList(CreateFoodInfoRequest request){

        return request
                .data()
                .stream()
                .map(food ->
                        FoodInfo
                                .builder()
                                .health(request
                                        .health())
                                .foodName(food.foodName())
                                .foodType(food.foodType())
                                .build())
                .toList();
    }

    /*
    기본 CRUD 모음
     */

    // FoodInfo 객체 모음 저장
    public void saveFoodInfoAll(List<FoodInfo> foodInfos){
        foodInfoRepository.saveAll(foodInfos);
    }





}
