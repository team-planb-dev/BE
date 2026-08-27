package com.planb.domain.travel.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum NutritionType implements CodeCommInterface {

    CARBOHYDRATE("CARBOHYDRATE", "탄수화물"),
    SUGAR("SUGAR", "당류"),
    DIETARY_FIBER("DIETARY_FIBER", "식이섬유"),
    SODIUM("SODIUM", "나트륨"),
    SATURATED_FAT("SATURATED_FAT", "포화지방"),
    TRANS_FAT("TRANS_FAT", "트랜스지방"),
    CHOLESTEROL("CHOLESTEROL", "콜레스테롤");

    private final String code;
    private final String codeName;
}
