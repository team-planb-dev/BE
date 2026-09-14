package com.planb.unit.domain.travel.policy;

import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.policy.AttractionTagPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttractionTagPolicyTest {

    @Test
    @DisplayName("TourAPI 분류 대분류로 관광 태그 결정")
    void mapsMajorCategory() {

        assertThat(AttractionTagPolicy.tagsOf("HS01"))
                .containsExactly(RecommendationTag.HISTORY_CULTURE);

        assertThat(AttractionTagPolicy.tagsOf("NA02"))
                .containsExactly(RecommendationTag.NATURAL_SCENERY);

        assertThat(AttractionTagPolicy.tagsOf("EX06"))
                .containsExactly(RecommendationTag.EXPERIENCE_ACTIVITY);
    }

    @Test
    @DisplayName("걸어서 둘러보는 VE 중분류의 자연경관 태그")
    void mapsWalkableVenueCategory() {

        // VE03 공원, VE04 거리·마을. 둘 다 걸어서 둘러보는 장소다.
        assertThat(AttractionTagPolicy.tagsOf("VE03"))
                .containsExactly(RecommendationTag.NATURAL_SCENERY);

        assertThat(AttractionTagPolicy.tagsOf("VE04"))
                .containsExactly(RecommendationTag.NATURAL_SCENERY);
    }

    @Test
    @DisplayName("관광 태그로 옮길 수 없는 분류의 빈 태그")
    void leavesUnmappableCategoryEmpty() {

        // VE01 관람지, VE05 체육시설, VE12 생활시설. 화장실과 복지관이 섞여 있어
        // 관광 태그를 붙이면 틀린 정보가 된다.
        assertThat(AttractionTagPolicy.tagsOf("VE01"))
                .isEmpty();

        assertThat(AttractionTagPolicy.tagsOf("VE12"))
                .isEmpty();

        assertThat(AttractionTagPolicy.tagsOf("FD01"))
                .isEmpty();
    }

    @Test
    @DisplayName("분류를 모르는 후보의 빈 태그")
    void leavesUnknownCategoryEmpty() {

        assertThat(AttractionTagPolicy.tagsOf(null))
                .isEmpty();

        assertThat(AttractionTagPolicy.tagsOf(""))
                .isEmpty();

        assertThat(AttractionTagPolicy.tagsOf("ZZ99"))
                .isEmpty();
    }
}
