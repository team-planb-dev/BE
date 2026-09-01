package com.planb.domain.travel.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScheduleType implements CodeCommInterface {

    BREAKFAST("BREAKFAST", "아침식사"),
    LUNCH("LUNCH", "점심식사"),
    DINNER("DINNER", "저녁식사"),
    ACTIVITY("ACTIVITY", "일정"),
    CHECK_IN("CHECK_IN", "체크인"),
    CHECK_OUT("CHECK_OUT", "체크아웃");

    private final String code;
    private final String codeName;
}