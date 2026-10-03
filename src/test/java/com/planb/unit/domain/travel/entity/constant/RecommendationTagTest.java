package com.planb.unit.domain.travel.entity.constant;

import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationTagTest {

    @ParameterizedTest
    @EnumSource(CourseType.class)
    @DisplayName("모든 장소 유형에 허용 태그 목록이 정의됨")
    void everyCourseTypeHasCandidates(CourseType courseType) {

        assertThat(RecommendationTag.candidates(courseType))
                .isNotNull();
    }

    @Test
    @DisplayName("지역음식은 음식점과 같은 태그를 허용")
    void localFoodAllowsEveryRestaurantTag() {

        // PlanService.deterministicTagsFor의 RESTAURANT·LOCAL_FOOD 공통 분기
        // 허용 목록 불일치에 따른 확정 태그 유실 방지
        assertThat(RecommendationTag.candidates(CourseType.LOCAL_FOOD))
                .containsAll(RecommendationTag.candidates(CourseType.RESTAURANT));
    }

    @Test
    @DisplayName("식사 슬롯 태그는 식사 가능한 장소 유형에만 허용")
    void mealTimeAppliedAllowedOnMealCourseTypes() {

        assertThat(RecommendationTag.candidates(CourseType.RESTAURANT))
                .contains(RecommendationTag.MEAL_TIME_APPLIED);

        assertThat(RecommendationTag.candidates(CourseType.LOCAL_FOOD))
                .contains(RecommendationTag.MEAL_TIME_APPLIED);

        assertThat(RecommendationTag.candidates(CourseType.ATTRACTION))
                .doesNotContain(RecommendationTag.MEAL_TIME_APPLIED);
    }
}
