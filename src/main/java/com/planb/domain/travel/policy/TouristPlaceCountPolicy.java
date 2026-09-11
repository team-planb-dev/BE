package com.planb.domain.travel.policy;

import com.planb.ai.context.TravelHealthContext;
import com.planb.domain.health.entity.constant.WalkType;

import java.util.List;

/**
 * 하루에 배치할 관광 장소 개수 규칙.
 *
 * AI에게 요구하는 개수와 최종 검증이 쓰는 개수가 갈라지지 않도록 한 곳에서 계산한다.
 * 같은 값을 두 계층이 쓰되, 부족분을 재시도할지 정확히 맞출지는 각 호출부가 판단한다.
 */
public final class TouristPlaceCountPolicy {

    private static final int MINIMAL_WALK_COUNT = 2;

    private static final int DEFAULT_COUNT = 3;

    private TouristPlaceCountPolicy() {
    }

    /**
     * 동행인의 걷기 수준으로 하루 관광 장소 개수를 계산한다.
     *
     * @param healthContexts 이번 여행에 선택된 동행인, 없으면 규칙을 적용하지 않는다
     * @return 하루 관광 장소 개수, 동행인이 없으면 0
     */
    public static int expectedCount(List<TravelHealthContext> healthContexts) {

        if (healthContexts == null || healthContexts.isEmpty()) {
            return 0;
        }

        boolean hasMinimalTraveler = healthContexts
                .stream()
                .anyMatch(context -> context.walkType() == WalkType.MINIMAL);

        return hasMinimalTraveler
                ? MINIMAL_WALK_COUNT
                : DEFAULT_COUNT;
    }
}
