package com.planb.ai.prompt;

import com.planb.ai.context.PlanEditContext;

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
                """;
    }

    @Override
    public String user() {

        return "현재 일차/날짜: " + context.currentPlan().planDays().stream()
                .map(day -> day.dayNumber() + "=" + day.date()).toList()
                + "\n사용자 수정 요청: " + context.editRequest();
    }
}
