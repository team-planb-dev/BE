package com.planb.domain.travel.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(description = "일정의 세부 장소 또는 활동 유형입니다.")
public enum CourseType implements CodeCommInterface {

    RESTAURANT(
            "RESTAURANT",
            "식당"
    ),

    ATTRACTION(
            "ATTRACTION",
            "관광지"
    ),

    PARK_WALK(
            "PARK_WALK",
            "공원·산책"
    ),

    CAFE_REST(
            "CAFE_REST",
            "카페·휴식"
    ),

    MEDICATION(
            "MEDICATION",
            "복약"
    ),

    TRANSPORTATION(
            "TRANSPORTATION",
            "이동"
    ),

    MUST_HAVE(
            "MUST_HAVE",
            "Must-have"
    ),

    LOCAL_FOOD(
            "LOCAL_FOOD",
            "지역음식"
    );

    private final String code;
    private final String codeName;
}
