package com.planb.unit.query.travel.service;

import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.repository.PlanRepository;
import com.planb.query.travel.dto.response.PlanBasicQueryResponse;
import com.planb.query.travel.dto.response.PlanQueryResponse;
import com.planb.query.travel.repository.PlanQueryRepository;
import com.planb.query.travel.service.PlanQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanQueryServiceTest {

    @Mock
    private PlanQueryRepository planQueryRepository;

    @Mock
    private PlanRepository planRepository;

    private PlanQueryService planQueryService;

    @BeforeEach
    void setUp() {
        planQueryService =
                new PlanQueryService(
                        planQueryRepository,
                        planRepository
                );
    }

    @Test
    @DisplayName("Travel ID를 기준으로 Plan 조회 (RecommendationTag 포함)")
    void getPlanByTravelId() {

        // given
        Long travelId = 1L;
        Long planId = 10L;

        PlanBasicQueryResponse basic =
                new PlanBasicQueryResponse(
                        planId,
                        "부산 AI 여행 일정"
                );

        Set<RecommendationTag> tags =
                Set.of(
                        RecommendationTag.LOCAL_FOOD,
                        RecommendationTag.NATURAL_SCENERY
                );

        Plan planEntity =
                Plan.builder()
                        .id(planId)
                        .planName("부산 AI 여행 일정")
                        .tags(tags)
                        .build();

        when(
                planQueryRepository
                        .findPlanBasicByTravelId(
                                travelId
                        )
        ).thenReturn(
                basic
        );

        when(
                planRepository
                        .findById(
                                planId
                        )
        ).thenReturn(
                Optional.of(planEntity)
        );

        // when
        PlanQueryResponse result =
                planQueryService
                        .getPlanByTravelId(
                                travelId
                        );

        // then
        assertThat(
                result
        ).isEqualTo(
                new PlanQueryResponse(
                        planId,
                        "부산 AI 여행 일정",
                        tags
                )
        );

        verify(
                planQueryRepository
        ).findPlanBasicByTravelId(
                travelId
        );

        verify(
                planRepository
        ).findById(
                planId
        );
    }

    @Test
    @DisplayName("Plan 엔티티 조회 실패 시 빈 태그 목록 반환")
    void getPlanByTravelIdWithoutTags() {

        // given
        Long travelId = 1L;
        Long planId = 10L;

        PlanBasicQueryResponse basic =
                new PlanBasicQueryResponse(
                        planId,
                        "부산 AI 여행 일정"
                );

        when(
                planQueryRepository
                        .findPlanBasicByTravelId(
                                travelId
                        )
        ).thenReturn(
                basic
        );

        when(
                planRepository
                        .findById(
                                planId
                        )
        ).thenReturn(
                Optional.empty()
        );

        // when
        PlanQueryResponse result =
                planQueryService
                        .getPlanByTravelId(
                                travelId
                        );

        // then
        assertThat(
                result.tags()
        ).isEmpty();
    }
}