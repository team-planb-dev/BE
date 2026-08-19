package com.planb.domain.travel.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Transportation implements CodeCommInterface {

    CAR("CAR","자가용"),
    TRANSIT("TRANSIT","대중 교통");

    private final String code;
    private final String codeName;
}
