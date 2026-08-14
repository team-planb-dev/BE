package com.planb.domain.health.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MedicationBasis implements CodeCommInterface {


    INDEPENDENT("INDEPENDENT", "특정 시간대에 먹어요"),
    WITH_MEAL("WITH_MEAL", "식사를 기준으로 기억해요"),
    UNKNOWN("UNKNOWN", "잘 모르겠어요");

    private final String code;
    private final String codeName;

}