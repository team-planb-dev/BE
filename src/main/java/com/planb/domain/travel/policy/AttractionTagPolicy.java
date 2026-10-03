package com.planb.domain.travel.policy;

import com.planb.domain.travel.entity.constant.RecommendationTag;

import java.util.Set;

/**
 * TourAPI 관광 분류에 따른 추천 태그 규칙
 */
public final class AttractionTagPolicy {

    // lclsSystm2의 앞 두 글자인 TourAPI 분류 대분류
    private static final String HISTORY = "HS";

    private static final String NATURE = "NA";

    private static final String EXPERIENCE = "EX";

    /**
     * 관광 후보의 태그 판정에 사용하는 VE 중분류
     */
    private static final Set<String> NATURAL_SCENERY_VENUES = Set.of(
            "VE03",
            "VE04"
    );

    private static final Set<String> EXPERIENCE_VENUES = Set.of(
            "VE02"
    );

    private static final Set<String> HISTORY_CULTURE_VENUES = Set.of(
            "VE01",
            "VE07"
    );

    private AttractionTagPolicy() {
    }

    /**
     * TourAPI 관광 분류 코드에 대응하는 추천 태그, 미지원 분류는 빈 집합
     */
    public static Set<RecommendationTag> tagsOf(String categoryCode) {

        if (categoryCode == null || categoryCode.isBlank()) {
            return Set.of();
        }

        String code = categoryCode.strip();

        if (NATURAL_SCENERY_VENUES.contains(code)) {
            return Set.of(RecommendationTag.NATURAL_SCENERY);
        }

        if (EXPERIENCE_VENUES.contains(code)) {
            return Set.of(RecommendationTag.EXPERIENCE_ACTIVITY);
        }

        if (HISTORY_CULTURE_VENUES.contains(code)) {
            return Set.of(RecommendationTag.HISTORY_CULTURE);
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
