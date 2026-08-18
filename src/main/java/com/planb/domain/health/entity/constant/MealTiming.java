package com.planb.domain.health.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MealTiming implements CodeCommInterface {

    BEFORE_MEAL("BEFORE_MEAL", "식전"),
    DURING_MEAL("DURING_MEAL", "식사 중"),
    AFTER_MEAL("AFTER_MEAL", "식후"),
    REGARDLESS_OF_MEAL("REGARDLESS_OF_MEAL", "식사 무관");


    private final String code;
    private final String codeName;
}
