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
    public com.planb.ai.context.AiCallBudget callBudget() {

        return context.callBudget();
    }

    @Override
    public String system() {

        return "실패한 일정 슬롯 하나의 장소만 다시 검색해 선택합니다. 일정 유형/시간은 Java가 유지합니다. "
                + "selectedCandidateId에는 반드시 이번 호출의 Tool 검색 candidateId를 반환하고 "
                + "음식점은 같은 candidateId로 getRestaurantDetail을 호출하고 "
                + "firstmenu 또는 treatmenu에 나열된 실제 메뉴 하나를 원문 그대로 선택합니다. "
                + "괄호 밖의 쉼표·슬래시·세미콜론·파이프·줄바꿈·HTML br만 메뉴 구분자로 사용하고 "
                + "괄호 내부 표현은 보존합니다. 괄호 짝이 맞지 않는 필드는 사용하지 않습니다. "
                + "교정 요청의 선택 가능 메뉴 JSON 배열에서는 문자열 요소 하나를 그대로 선택합니다. "
                + "상세의 음식점 ID나 유형이 다르거나 메뉴가 없으면 다른 후보를 선택합니다. "
                + "음식점은 실제 메뉴와 영양 평가를 restaurantDetail에 반환합니다.";
    }

    @Override
    public String user() {

        return "여행 조건: " + context.createTravelRequest() + "\n건강 조건: " + context.healthContexts() + "\n대상 슬롯: " + slot + "\n실패 이유: " + reason
                + "\n제외 장소: " + usedPlaces + "\n제외 메뉴: " + usedMenus;
    }
}
