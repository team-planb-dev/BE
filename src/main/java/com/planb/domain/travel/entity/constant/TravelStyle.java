package com.planb.domain.travel.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum TravelStyle implements CodeCommInterface {

    LESS_WALK("LESS_WALK","걷기 부담 적음"),
    MATCH_MEAL_TIME("MATCH_MEAL_TIME","식사 시간 맞추기"),
    LESS_TOURISM("LESS_TOURISM","관광지 줄이기");

    private final String code;
    private final String codeName;
}
