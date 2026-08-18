package com.planb.domain.health.dto.request;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;


import java.time.LocalTime;

public record CreateHealthRequest(String travelerName, // 동행자 이름
                                  boolean sensitiveAgree, // 민감정보 동의 여부
                                  boolean hasMedication, // 복용약 여부
                                  HealthInfo healthInfo,
                                  MealInfo mealInfo) {

    public record HealthInfo(DiseaseType diseaseType, // 질환 종류
                             WalkType walkType){ // 걷기 수준

    }

    public record MealInfo(boolean applied, // 식사정보 적용 여부
                           boolean breakfastApplied, // 아침 적용 여부
                           LocalTime breakfastTime, // 아침 식사 시간
                           boolean lunchApplied, // 점심 적용 여부
                           LocalTime lunchTime, // 점심 식사 시간
                           boolean dinnerApplied, // 저녁 적용 여부
                           LocalTime dinnerTime){ // 저녁 식사 시간
    }

}
