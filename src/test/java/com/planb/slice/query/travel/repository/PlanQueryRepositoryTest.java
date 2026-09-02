package com.planb.slice.query.travel.repository;

import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.user.entity.TermsAgreement;
import com.planb.domain.user.entity.User;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.travel.dto.response.PlanBasicQueryResponse;
import com.planb.query.travel.repository.PlanQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        PlanQueryRepository.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class PlanQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlanQueryRepository planQueryRepository;

    @Test
    @DisplayName("Travel ID를 기준으로 Plan 조회")
    void findPlanBasicByTravelId() {

        // given
        Travel travel = createTravel(
                "부산 여행"
        );

        Plan plan = createPlan(
                travel,
                "부산 AI 여행 일정"
        );

        entityManager.flush();
        entityManager.clear();

        // when
        PlanBasicQueryResponse result =
                planQueryRepository.findPlanBasicByTravelId(
                        travel.getId()
                );

        // then
        assertThat(result).isNotNull();

        assertThat(
                result.planId()
        ).isEqualTo(
                plan.getId()
        );

        assertThat(
                result.planName()
        ).isEqualTo(
                "부산 AI 여행 일정"
        );
    }

    @Test
    @DisplayName("다른 Travel의 Plan 조회 제외")
    void findPlanBasicByTravelIdExcludesOtherTravel() {

        // given
        Travel travel1 = createTravel(
                "부산 여행"
        );

        Travel travel2 = createTravel(
                "서울 여행"
        );

        Plan plan1 = createPlan(
                travel1,
                "부산 AI 여행 일정"
        );

        createPlan(
                travel2,
                "서울 AI 여행 일정"
        );

        entityManager.flush();
        entityManager.clear();

        // when
        PlanBasicQueryResponse result =
                planQueryRepository.findPlanBasicByTravelId(
                        travel1.getId()
                );

        // then
        assertThat(result).isNotNull();

        assertThat(
                result.planId()
        ).isEqualTo(
                plan1.getId()
        );

        assertThat(
                result.planName()
        ).isEqualTo(
                "부산 AI 여행 일정"
        );
    }

    private User createUser() {

        User user = User.builder()
                .username("test" + System.nanoTime() + "@example.com")
                .password("password")
                .role("ROLE_USER")
                .nickname("테스트유저")
                .termsAgreement(
                        new TermsAgreement(
                                true,
                                true,
                                true
                        )
                )
                .build();

        entityManager.persist(user);

        return user;
    }

    private Travel createTravel(String travelName) {

        Travel travel = Travel.builder()
                .user(createUser())
                .travelName(travelName)
                .build();

        entityManager.persist(travel);

        return travel;
    }

    private Plan createPlan(
            Travel travel,
            String planName
    ) {

        Plan plan = Plan.builder()
                .travel(travel)
                .planName(planName)
                .build();

        entityManager.persist(plan);

        return plan;
    }
}