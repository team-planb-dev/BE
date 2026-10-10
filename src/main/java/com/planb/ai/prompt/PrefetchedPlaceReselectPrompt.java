package com.planb.ai.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.context.PlaceCandidateContext.Candidate;
import com.planb.global.config.exception.AiFailure;
import com.planb.global.config.exception.domain.AiOrchestrationException;

import java.util.List;

/**
 * 확정 후보 안에서 실패 슬롯 하나의 재선택 요청
 */
public record PrefetchedPlaceReselectPrompt(
        PlaceReselectPrompt request,
        ObjectMapper objectMapper,
        List<Candidate> candidates
) implements AiPrompt {

    @Override
    public String system() {

        return "실패한 일정 슬롯 하나의 장소를 제공된 후보 목록에서 다시 선택합니다. "
                + "일정 유형과 시간은 Java가 유지합니다. selectedCandidateId는 후보 목록 또는 "
                + "findPlaceWithRoute로 실제 확인한 ID만 반환합니다. "
                + "지역 관광지·음식점 keyword 검색 Tool은 없습니다. 카페·대체 관광지는 findPlaceWithRoute로 확인합니다. "
                + "음식점은 getRestaurantDetail로 실제 메뉴를 확인하고 evaluateFoodNutrition으로 평가합니다. "
                + "제외 장소와 메뉴를 사용하지 않습니다.";
    }

    @Override
    public String user() {

        try {
            String candidateJson = objectMapper.writeValueAsString(candidates);

            return request.user() + "\n후보 목록: " + candidateJson;
        } catch (JsonProcessingException failure) {
            throw new AiOrchestrationException(
                    AiFailure.CONTEXT_SERIALIZATION_FAILED,
                    failure
            );
        }
    }
}
