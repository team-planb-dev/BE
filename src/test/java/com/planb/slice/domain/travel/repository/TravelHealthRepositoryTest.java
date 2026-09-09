package com.planb.slice.domain.travel.repository;

import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.health.entity.vo.HealthInfo;
import com.planb.domain.health.entity.vo.MealInfo;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.TravelHealth;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.repository.TravelHealthRepository;
import com.planb.domain.user.entity.AccountRecovery;
import com.planb.domain.user.entity.TermsAgreement;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.entity.constant.RecoveryQuestion;
import com.planb.slice.support.MySqlRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class TravelHealthRepositoryTest
        extends MySqlRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TravelHealthRepository travelHealthRepository;

    @Test
    @DisplayName("여행에 연결한 구성원만 조회")
    void findAllByTravelIdReturnsSelectedCompanions() {

        // given
        User owner = createUser();

        Travel travel = createTravel(owner);
        Travel otherTravel = createTravel(owner);

        Health selected = createHealth(owner, "선택 동행인");
        Health unselected = createHealth(owner, "미선택 동행인");

        travelHealthRepository.save(TravelHealth
                .builder()
                .travel(travel)
                .health(selected)
                .build());

        travelHealthRepository.save(TravelHealth
                .builder()
                .travel(otherTravel)
                .health(unselected)
                .build());

        entityManager.flush();
        entityManager.clear();

        // when
        List<TravelHealth> result =
                travelHealthRepository.findAllByTravelId(travel.getId());

        // then
        assertThat(result)
                .extracting(travelHealth -> travelHealth
                        .getHealth()
                        .getTravelerName())
                .containsExactly("선택 동행인");
    }

    @Test
    @DisplayName("같은 여행과 구성원 관계는 중복 저장 불가")
    void rejectsDuplicateTravelHealth() {

        // given
        User owner = createUser();
        Travel travel = createTravel(owner);
        Health health = createHealth(owner, "동행인");

        travelHealthRepository.save(TravelHealth
                .builder()
                .travel(travel)
                .health(health)
                .build());

        // when & then
        assertThatThrownBy(() -> travelHealthRepository.save(TravelHealth
                .builder()
                .travel(travel)
                .health(health)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User createUser() {

        User user = User.builder()
                .username("travel-health" + System.nanoTime() + "@example.com")
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
                .accountRecovery(
                        AccountRecovery.of(
                                RecoveryQuestion.FIRST_PET,
                                "콩이"
                        )
                )
                .build();

        entityManager.persist(user);

        return user;
    }

    private Travel createTravel(User user) {

        Travel travel = Travel.builder()
                .user(user)
                .travelName("부산 여행")
                .locationDo("부산")
                .locationSigungu("해운대구")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 2))
                .dateType(DateType.ONE_NIGHT_TWO_DAYS)
                .transportation(Transportation.TRANSIT)
                .travelStyle(TravelStyle.LESS_WALK)
                .travelTheme(TravelTheme.TASTE)
                .localFoods(List.of("돼지국밥"))
                .recommendFoods(List.of("돼지국밥"))
                .decidedLocation("해운대")
                .build();

        entityManager.persist(travel);

        return travel;
    }

    private Health createHealth(
            User user,
            String travelerName
    ) {

        Health health = Health.builder()
                .user(user)
                .travelerName(travelerName)
                .sensitiveAgree(true)
                .hasMedication(false)
                .healthInfo(
                        new HealthInfo(
                                DiseaseType.DIABETES,
                                WalkType.MODERATE
                        )
                )
                .mealInfo(
                        new MealInfo(
                                true,
                                true,
                                LocalTime.of(8, 0),
                                true,
                                LocalTime.of(12, 0),
                                true,
                                LocalTime.of(18, 0)
                        )
                )
                .build();

        entityManager.persist(health);

        return health;
    }
}
