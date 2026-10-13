package com.planb.ai.prompt;

import com.planb.ai.context.PlanEditContext;

import java.util.Objects;

public record PlanEditScopePrompt(PlanEditContext context) implements AiPrompt {
    @Override
    public String system() {

        return """
                일정 수정 요청의 범위만 해석합니다. 일정 생성은 하지 않습니다.
                rebuildDayNumbers에는 사용자가 날짜 전체의 장소 구성을 다시 짜도록 명시한 일차만 넣습니다.
                '1일차를 관광지 위주로 통째로 다시 짜주세요'는 [1]입니다.
                '1일차 점심을 다시 골라주세요', '시간만 조정', '덜 걷게', '기존 일정을 유지'는 []입니다.
                단어 하나만으로 전체 재구성으로 판단하지 말고 부정 표현과 대상 범위를 함께 해석합니다.
                날짜 전체 재구성을 명시했으나 대상을 특정할 수 없으면 [0]으로 표시합니다.
                preserveOtherDays는 전체 날짜 재구성 외의 변경 요청이 다른 날짜에 없을 때만 true입니다.
                예: '1일차 통째로 다시, 2일차 점심도 변경'은 rebuildDayNumbers=[1], preserveOtherDays=false입니다.
                여행 전체를 통째로 재구성하라고 명시하면 현재 일정의 모든 일차를 반환합니다.

                densityReductionDayNumbers에는 사용자가 걷는 양, 관광지 개수 또는 일정 밀도를
                줄여 달라고 명시한 일차만 넣습니다.
                특정 일차를 지정하지 않은 여행 전체 감소 요청이면 현재 일정의 모든 일차를 넣습니다.
                장소 교체, 시간 조정, 식사 변경, 관광지 증가 요청 또는 모호한 요청이면 []입니다.

                editTargets에는 날짜 전체 재구성이 아닌 슬롯 단위 변경 대상만 넣습니다.
                slot은 아침 BREAKFAST, 점심 LUNCH, 저녁 DINNER, 관광지 ATTRACTION, 카페 CAFE입니다.
                dayNumber는 요청에 명시된 일차이며, 일차를 특정할 수 없으면 0입니다.
                placeName은 특정 장소를 지목한 경우 그 장소명이고, 지목하지 않았으면 빈 문자열입니다.
                menuOnly는 같은 식사의 메뉴만 바꾸라는 요청이면 true, 식당이나 음식점을 바꾸라는 요청이면 false입니다.
                예: '2일차 점심 메뉴를 바꿔줘'는 rebuildDayNumbers=[],
                editTargets=[{dayNumber:2, slot:LUNCH, placeName:"", menuOnly:true}]입니다.
                '2일차 관광지를 전부 다른 곳으로 바꿔줘'는 날짜 전체 재구성이 아니며
                rebuildDayNumbers=[], editTargets=[{dayNumber:2, slot:ATTRACTION, placeName:"", menuOnly:false}]입니다.
                '1일차 경복궁 대신 다른 곳'은 editTargets=[{dayNumber:1, slot:ATTRACTION, placeName:"경복궁", menuOnly:false}]입니다.
                날짜 전체 재구성, 밀도 감소, 시간 조정처럼 슬롯 단위로 표현되지 않는 요청은 editTargets=[]입니다.
                '점심 메뉴 바꿔줘'처럼 일차가 없는 슬롯 단위 요청은 editTargets의 dayNumber를 0으로 둡니다.
                placeName은 아래 현재 장소 목록에 있는 이름을 그대로 씁니다.
                """;
    }

    @Override
    public String user() {

        return "현재 일차/날짜: " + context
                .currentPlan()
                .planDays()
                .stream()
                .map(day -> day.dayNumber() + "=" + day.date())
                .toList()
                + "\n현재 장소: " + context
                        .currentPlan()
                        .planDays()
                        .stream()
                        .map(day -> day.dayNumber() + "일차=" + day
                                .schedules()
                                .stream()
                                .map(slot -> slot.locationName())
                                .filter(Objects::nonNull)
                                .toList())
                        .toList()
                + "\n사용자 수정 요청: " + context.editRequest();
    }
}
