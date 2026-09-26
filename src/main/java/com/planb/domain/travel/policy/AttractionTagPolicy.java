package com.planb.domain.travel.policy;

import com.planb.domain.travel.entity.constant.RecommendationTag;

import java.util.Set;

/**
 * TourAPI 분류 코드로 관광 슬롯의 추천 태그를 정하는 규칙.
 *
 * Java가 채워 넣은 관광 슬롯은 AI가 태그를 달아주지 않으므로 여기서 결정한다.
 * 최종 허용 범위는 {@link RecommendationTag#candidates}가 결정한다.
 * Prompt의 태그 안내는 이 Java 정책을 따르며 최종 승인 근거로 사용하지 않는다.
 */
public final class AttractionTagPolicy {

    // TourAPI 분류 대분류. lclsSystm2 값의 앞 두 글자다.
    private static final String HISTORY = "HS";

    private static final String NATURE = "NA";

    private static final String EXPERIENCE = "EX";

    /**
     * TourismTool의 관광 분류 허용 목록을 통과한 VE 중분류.
     *
     * 이 정책은 후보를 다시 검증하지 않고, 이미 검증된 후보의 추천 태그만 결정한다.
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
