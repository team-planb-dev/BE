package com.planb.domain.health.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


@Getter
@RequiredArgsConstructor
public enum WalkType implements CodeCommInterface {

    ACTIVE("ACTIVE", "많이 걸어도 좋아요"),
    MODERATE("MODERATE", "보통 정도가 좋아요"),
    MINIMAL("MINIMAL", "걷는 시간을 가능한 줄이고 싶어요");

    private final String code;
    private final String codeName;
}
