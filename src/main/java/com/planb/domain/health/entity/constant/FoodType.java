package com.planb.domain.health.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FoodType implements CodeCommInterface {

    ALLERGY("ALLERGY","알러지 음식"),
    AVOID("AVOID","피하는 음식");

    private final String code;
    private final String codeName;


}
