package com.planb.domain.travel.entity.constant;


import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum NutritionLevel implements CodeCommInterface {

    LOW("LOW", "낮은 편"),
    CHECK("CHECK", "확인 필요"),
    HIGH("HIGH", "높은 편");

    private final String code;
    private final String codeName;
}
