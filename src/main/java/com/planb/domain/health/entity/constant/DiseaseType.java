package com.planb.domain.health.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiseaseType implements CodeCommInterface {

    DIABETES("DIABETES","당뇨"),
    HIGH_BLOOD_PRESSURE("HIGH_BLOOD_PRESSURE","고혈압"),
    DYSLIPIDEMIA("DYSLIPIDEMIA","이상지질혈증");

    private final String code;
    private final String codeName;
}
