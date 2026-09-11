package com.planb.ai.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;

import com.planb.global.config.exception.AiFailure;
import com.planb.global.config.exception.domain.AiOrchestrationException;

public record RebuildPlanDayPrompt(
        PlanEditContext context,
        CreatePlanAiResponse current,
        Integer dayNumber,
        String reason,
        ObjectMapper objectMapper
) implements AiPrompt {

    @Override
    public String system() {

        return """
                지정한 날짜 하나의 여행 장소 구성을 재생성합니다.
                입력의 다른 날짜는 중복 방지용 참고 자료이며 출력 대상이 아닙니다.
                기존에 관광지가 있다는 이유나 최소 변경 원칙으로 재구성을 생략하지 않습니다.

                [검색]
                관광지는 searchAttractionsByRegion(locationDo, locationSigungu)이 반환한 후보 중에서만 선택하고,
                선택한 candidateId를 그대로 반환합니다. 관광지명이나 후보를 직접 생성하지 않습니다.
                음식점은 searchRestaurantsByLocation(keyword, locationDo, locationSigungu)으로 검색합니다.
                음식점 keyword에는 구체적인 음식명만 넣고 '맛집' 같은 일반 검색어는 사용하지 않습니다.
                plannedPlaces가 지역 관광지 후보에 없을 때만 입력 장소명으로 findPlaceWithRoute를 사용합니다.
                previousLocation에는 직전 장소명을 넣고, excludeNames에는 제외할 장소명만 넣습니다.
                candidateId는 제외 장소명 배열에 넣지 않습니다.

                [장소·일정]
                다른 날짜의 장소·메뉴는 사용하지 않습니다. 건강·알레르기·필수 방문 조건을 준수합니다.
                기존 대상 날짜와 다른 실제 장소를 하나 이상 포함합니다.
                이름 표기나 순서만 바꾸는 것은 새 구성이 아닙니다.
                음식점은 실제 메뉴를 확인하고 영양 근거가 필요하면 영양 평가 Tool을 사용합니다.
                RESTAURANT/LOCAL_FOOD는 BREAKFAST/LUNCH/DINNER,
                나머지 courseType은 ACTIVITY를 사용합니다.
                날짜는 YYYY-MM-DD, 시간은 HH:mm:ss입니다. startTime/endTime과 stayMinutes를 일치시킵니다.
                검색 후보 사이에는 getRoute(originCandidateId, destinationCandidateId, transportation)로
                구간 이동시간을 확인합니다. 장소명 대신 검색 Tool의 candidateId를 전달하고,
                도착 전에 다음 일정이 시작되지 않도록 배치합니다.
                MEDICATION 슬롯을 생성하지 않습니다. Java가 최종 일정에서 다시 생성합니다.
                태그는 근거가 있을 때만 사용하며 없으면 []입니다.

                [출력]
                성공하면 rebuilt=true, failureReason="", planDays=[대상 날짜 하나]입니다.
                dayNumber와 date는 입력 targetDay의 값을 정확히 사용합니다.
                다른 날짜를 출력하거나 planName/changes/processable을 반환하지 않습니다.
                유효한 후보가 없거나 조건을 충족할 수 없으면 rebuilt=false,
                failureReason에 검색 실패 또는 충족하지 못한 조건을 구체적으로 적고 planDays=[]를 반환합니다.
                실패를 숨기려고 기존 일정을 복사하거나 성공 표시와 빈 일정을 함께 반환하지 않습니다.
                """;
    }

    @Override
    public String user() {

        CreatePlanAiResponse.PlanDayDetail target = current
                .planDays()
                .stream()
                .filter(day -> dayNumber.equals(day.dayNumber()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("재구성 대상 날짜 누락"));

        try {
            return objectMapper
                    .writeValueAsString(
                            new RebuildInput(
                                    context,
                                    target,
                                    current,
                                    reason));
        } catch (JsonProcessingException exception) {
            throw new AiOrchestrationException(AiFailure.CONTEXT_SERIALIZATION_FAILED, exception);
        }
    }

    private record RebuildInput(
            PlanEditContext request,
            CreatePlanAiResponse.PlanDayDetail targetDay,
            CreatePlanAiResponse currentPlan,
            String validationFailure
    ) {
    }
}
