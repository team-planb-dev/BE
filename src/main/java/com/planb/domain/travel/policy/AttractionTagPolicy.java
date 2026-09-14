package com.planb.domain.travel.policy;

import com.planb.domain.travel.entity.constant.RecommendationTag;

import java.util.Set;

/**
 * TourAPI 분류 코드로 관광 슬롯의 추천 태그를 정하는 규칙.
 *
 * Java가 채워 넣은 관광 슬롯은 AI가 태그를 달아주지 않으므로 여기서 결정한다.
 * 붙일 수 있는 태그는 TravelPlanPrompt가 ATTRACTION에 허용한 것으로 한정한다.
 * 같은 응답 안에서 AI와 Java가 서로 다른 규칙을 쓰면 안 되기 때문이다.
 */
public final class AttractionTagPolicy {

    // TourAPI 분류 대분류. lclsSystm2 값의 앞 두 글자다.
    private static final String HISTORY = "HS";

    private static final String NATURE = "NA";

    private static final String EXPERIENCE = "EX";

    /**
     * 걸어서 둘러보는 VE 중분류.
     *
     * VE는 공원부터 화장실까지 섞인 분류라 대분류만으로는 태그를 정할 수 없다.
     * 공원(VE03)과 거리·마을(VE04)만 자연경관으로 본다.
     */
    private static final Set<String> WALKABLE_VENUES = Set.of("VE03", "VE04");

    private AttractionTagPolicy() {
    }

    /**
     * 분류 코드에 대응하는 관광 태그.
     *
     * @param categoryCode TourAPI lclsSystm2 (예: HS01, NA02, VE03)
     * @return 대응하는 태그, 옮길 수 없는 분류면 빈 집합
     */
    public static Set<RecommendationTag> tagsOf(String categoryCode) {

        if (categoryCode == null || categoryCode.isBlank()) {
            return Set.of();
        }

        String code = categoryCode.strip();

        if (WALKABLE_VENUES.contains(code)) {
            return Set.of(RecommendationTag.NATURAL_SCENERY);
        }

        if (code.length() < 2) {
            return Set.of();
        }

        return switch (code.substring(0, 2)) {
            case HISTORY -> Set.of(RecommendationTag.HISTORY_CULTURE);

            case NATURE -> Set.of(RecommendationTag.NATURAL_SCENERY);

            case EXPERIENCE -> Set.of(RecommendationTag.EXPERIENCE_ACTIVITY);

            default -> Set.of();
        };
    }
}
