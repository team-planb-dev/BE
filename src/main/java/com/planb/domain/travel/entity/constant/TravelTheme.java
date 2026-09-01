package com.planb.domain.travel.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Getter
public enum TravelTheme implements CodeCommInterface {

    HISTORY("HISTORY","역사 중심"),
    NATURE("NATURE","자연 중심"),
    TASTE("TASTE","미식 중심"),
    ACTIVITY("ACTIVITY","액티비티 중심");

    private final String code;
    private final String codeName;

}
