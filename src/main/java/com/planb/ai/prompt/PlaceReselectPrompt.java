package com.planb.ai.prompt;

import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;

import java.util.Set;

public record PlaceReselectPrompt(
        TravelPlanContext context,
        PlanScheduleDetail slot,
        String reason,
        Set<String> usedPlaces,
        Set<String> usedMenus
) implements AiPrompt {
    @Override
    public String system() {

        return "실패한 일정 슬롯 하나의 장소만 다시 검색해 선택합니다. 일정 유형/시간은 Java가 유지합니다. "
                + "selectedCandidateId에는 반드시 이번 호출의 Tool 검색 candidateId를 반환하고 "
                + "음식점은 실제 메뉴와 영양 평가를 restaurantDetail에 반환합니다.";
    }

    @Override
    public String user() {

        return "여행/건강 조건: " + context + "\n대상 슬롯: " + slot + "\n실패 이유: " + reason
                + "\n제외 장소: " + usedPlaces + "\n제외 메뉴: " + usedMenus;
    }
}
