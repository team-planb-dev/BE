package com.planb.domain.health.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "복약 시간 기준입니다. INDEPENDENT는 고정 시각, WITH_MEAL은 식사 기준, UNKNOWN은 기준 미확정을 뜻합니다.")
public enum MedicationBasis implements CodeCommInterface {


    INDEPENDENT("INDEPENDENT", "특정 시간대에 먹어요"),
    WITH_MEAL("WITH_MEAL", "식사를 기준으로 기억해요"),
    UNKNOWN("UNKNOWN", "잘 모르겠어요");

    private final String code;
    private final String codeName;

}
