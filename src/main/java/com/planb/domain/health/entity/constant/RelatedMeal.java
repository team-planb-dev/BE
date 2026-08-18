package com.planb.domain.health.entity.constant;


import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RelatedMeal implements CodeCommInterface {

    BREAKFAST("BREAKFAST", "아침"),
    LUNCH("LUNCH", "점심"),
    DINNER("DINNER", "저녁");

    private final String code;
    private final String codeName;
}
