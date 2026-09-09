package com.planb.domain.travel.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
@Schema(description = "여행 스타일입니다. LESS_WALK는 걷기 부담 완화, MATCH_MEAL_TIME은 식사시간 반영, LESS_TOURISM은 관광지 수 축소를 뜻합니다.")
public enum TravelStyle implements CodeCommInterface {

    LESS_WALK("LESS_WALK", "걷기 부담 적음"),
    MATCH_MEAL_TIME("MATCH_MEAL_TIME", "식사 시간 맞추기"),
    LESS_TOURISM("LESS_TOURISM", "관광지 줄이기");

    private final String code;
    private final String codeName;
}
