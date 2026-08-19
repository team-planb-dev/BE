package com.planb.domain.travel.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum DateType implements CodeCommInterface {

    DAY_TRIP(
            "DAY_TRIP",
            "당일치기",
            0
    ),

    ONE_NIGHT_TWO_DAYS(
            "ONE_NIGHT_TWO_DAYS",
            "1박 2일",
            1
    ),

    TWO_NIGHTS_THREE_DAYS(
            "TWO_NIGHTS_THREE_DAYS",
            "2박 3일",
            2
    );

    private final String code;
    private final String codeName;

    // 종료일 계산에 사용할 일수
    private final Integer plusDays;

}
