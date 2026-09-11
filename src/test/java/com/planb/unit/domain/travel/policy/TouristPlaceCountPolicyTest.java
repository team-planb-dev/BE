package com.planb.unit.domain.travel.policy;

import com.planb.ai.context.TravelHealthContext;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.policy.TouristPlaceCountPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TouristPlaceCountPolicyTest {

    @Test
    @DisplayName("동행인이 없는 여행의 관광지 개수 규칙 미적용")
    void noCompanionMeansNoRule() {

        assertThat(TouristPlaceCountPolicy.expectedCount(List.of()))
                .isZero();

        assertThat(TouristPlaceCountPolicy.expectedCount(null))
                .isZero();
    }

    @Test
    @DisplayName("MINIMAL 여행자가 없는 경우 하루 관광지 3개")
    void withoutMinimalTravelerExpectsThree() {

        assertThat(TouristPlaceCountPolicy.expectedCount(List.of(
                healthContext(WalkType.MODERATE),
                healthContext(WalkType.ACTIVE))))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("MINIMAL 여행자가 한 명이라도 있는 경우 하루 관광지 2개")
    void anyMinimalTravelerExpectsTwo() {

        assertThat(TouristPlaceCountPolicy.expectedCount(List.of(
                healthContext(WalkType.ACTIVE),
                healthContext(WalkType.MINIMAL))))
                .isEqualTo(2);
    }

    private TravelHealthContext healthContext(WalkType walkType) {

        return new TravelHealthContext(
                "동행인",
                DiseaseType.DIABETES,
                walkType,
                new TravelHealthContext.MealInfoContext(
                        LocalTime.of(8, 0),
                        LocalTime.of(12, 0),
                        LocalTime.of(18, 0)
                ),
                List.of(),
                List.of()
        );
    }
}
