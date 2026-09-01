package com.planb.slice.query.travel.repository;

import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.user.entity.TermsAgreement;
import com.planb.domain.user.entity.User;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.travel.dto.response.TravelConditionQueryResponse;
import com.planb.query.travel.repository.TravelQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        TravelQueryRepository.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class TravelQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TravelQueryRepository travelQueryRepository;

    @Test
    @DisplayName("Travel ID를 기준으로 여행 조건 조회")
    void findTravelConditionById() {

        // given
        Travel travel = createTravel(
                "부산 여행",
                TravelStyle.LESS_WALK,
                TravelTheme.TASTE
        );

        entityManager.flush();
        entityManager.clear();

        // when
        TravelConditionQueryResponse result =
                travelQueryRepository
                        .findTravelConditionById(
                                travel.getId()
                        );

        // then
        assertThat(result).isNotNull();

        assertThat(
                result.travelStyle()
        ).isEqualTo(
                TravelStyle.LESS_WALK
        );

        assertThat(
                result.travelTheme()
        ).isEqualTo(
                TravelTheme.TASTE
        );
    }

    @Test
    @DisplayName("Travel ID에 해당하는 여행 조건만 조회")
    void findTravelConditionByIdReturnsTargetTravelCondition() {

        // given
        Travel targetTravel = createTravel(
                "부산 여행",
                TravelStyle.LESS_WALK,
                TravelTheme.TASTE
        );

        createTravel(
                "서울 여행",
                TravelStyle.MATCH_MEAL_TIME,
                TravelTheme.HISTORY
        );

        entityManager.flush();
        entityManager.clear();

        // when
        TravelConditionQueryResponse result =
                travelQueryRepository
                        .findTravelConditionById(
                                targetTravel.getId()
                        );

        // then
        assertThat(result).isNotNull();

        assertThat(
                result.travelStyle()
        ).isEqualTo(
                TravelStyle.LESS_WALK
        );

        assertThat(
                result.travelTheme()
        ).isEqualTo(
                TravelTheme.TASTE
        );
    }

    @Test
    @DisplayName("Travel 소유자가 맞으면 true")
    void existsByIdAndUserIdReturnsTrueForOwner() {

        // given
        User owner = createUser();

        Travel travel = createTravel(
                "부산 여행",
                TravelStyle.LESS_WALK,
                TravelTheme.TASTE,
                owner
        );

        entityManager.flush();
        entityManager.clear();

        // when
        boolean result =
                travelQueryRepository
                        .existsByIdAndUserId(
                                travel.getId(),
                                owner.getId()
                        );

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Travel 소유자가 다르면 false")
    void existsByIdAndUserIdReturnsFalseForNonOwner() {

        // given
        User owner = createUser();
        User stranger = createUser();

        Travel travel = createTravel(
                "부산 여행",
                TravelStyle.LESS_WALK,
                TravelTheme.TASTE,
                owner
        );

        entityManager.flush();
        entityManager.clear();

        // when
        boolean result =
                travelQueryRepository
                        .existsByIdAndUserId(
                                travel.getId(),
                                stranger.getId()
                        );

        // then
        assertThat(result).isFalse();
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

    private Travel createTravel(
            String travelName,
            TravelStyle travelStyle,
            TravelTheme travelTheme
    ) {

        return createTravel(
                travelName,
                travelStyle,
                travelTheme,
                createUser()
        );
    }

    private Travel createTravel(
            String travelName,
            TravelStyle travelStyle,
            TravelTheme travelTheme,
            User user
    ) {

        Travel travel = Travel.builder()
                .user(user)
                .travelName(travelName)
                .locationDo("부산광역시")
                .locationSigungu("해운대구")
                .startDate(
                        LocalDate.of(
                                2026,
                                9,
                                1
                        )
                )
                .endDate(
                        LocalDate.of(
                                2026,
                                9,
                                2
                        )
                )
                .dateType(
                        DateType.ONE_NIGHT_TWO_DAYS
                )
                .transportation(
                        Transportation.TRANSIT
                )
                .travelStyle(
                        travelStyle
                )
                .travelTheme(
                        travelTheme
                )
                .localFoods(List.of("돼지국밥"))
                .recommendFoods(
                        List.of("돼지국밥")
                )
                .decidedLocation("해운대")
                .build();

        entityManager.persist(travel);

        return travel;
    }
}