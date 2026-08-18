package com.planb.slice.query.health.repository;

import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.health.entity.FoodInfo;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.health.entity.vo.HealthInfo;
import com.planb.domain.health.entity.vo.MealInfo;
import com.planb.domain.user.entity.User;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.health.repository.HealthQueryRepository;
import com.planb.slice.query.chat.repository.helper.ChatDomainRepositoryTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        HealthQueryRepository.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class HealthQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HealthQueryRepository healthQueryRepository;

    private ChatDomainRepositoryTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new ChatDomainRepositoryTestHelper(entityManager);
    }

    @Test
    @DisplayName("User ID를 기준으로 동행인 건강 요약 정보 조회")
    void findHealthSummaryList() {

        // given
        User user = helper.createUser(
                "user1@example.com",
                "test1234!",
                "ROLE_USER",
                "nickname1",
                false
        );

        Health healthWithAllergy = createHealth(
                user,
                "동행인1",
                true,
                DiseaseType.DIABETES
        );

        Health healthWithoutAllergy = createHealth(
                user,
                "동행인2",
                false,
                DiseaseType.HIGH_BLOOD_PRESSURE
        );

        createFoodInfo(
                healthWithAllergy,
                "땅콩",
                FoodType.ALLERGY
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<HealthSummaryQueryResponse> result =
                healthQueryRepository
                        .findHealthSummaryList(
                                user.getId()
                        );

        // then
        assertThat(result)
                .hasSize(2)
                .extracting(
                        HealthSummaryQueryResponse::healthId,
                        HealthSummaryQueryResponse::travelerName,
                        HealthSummaryQueryResponse::hasMedication,
                        HealthSummaryQueryResponse::diseaseType,
                        HealthSummaryQueryResponse::hasAllergy
                )
                .containsExactlyInAnyOrder(
                        tuple(
                                healthWithAllergy.getId(),
                                "동행인1",
                                true,
                                DiseaseType.DIABETES,
                                true
                        ),
                        tuple(
                                healthWithoutAllergy.getId(),
                                "동행인2",
                                false,
                                DiseaseType.HIGH_BLOOD_PRESSURE,
                                false
                        )
                );
    }

    @Test
    @DisplayName("다른 User의 동행인 건강 요약 정보 조회 제외")
    void findHealthSummaryListExcludesOtherUserHealth() {

        // given
        User user1 = helper.createUser(
                "user1@example.com",
                "test1234!",
                "ROLE_USER",
                "nickname1",
                false
        );

        User user2 = helper.createUser(
                "user2@example.com",
                "test1234!",
                "ROLE_USER",
                "nickname2",
                false
        );

        Health user1Health = createHealth(
                user1,
                "동행인1",
                false,
                DiseaseType.DIABETES
        );

        createHealth(
                user2,
                "다른 사용자 동행인",
                false,
                DiseaseType.DYSLIPIDEMIA
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<HealthSummaryQueryResponse> result =
                healthQueryRepository
                        .findHealthSummaryList(
                                user1.getId()
                        );

        // then
        assertThat(result).hasSize(1);

        assertThat(
                result.get(0).healthId()
        ).isEqualTo(
                user1Health.getId()
        );

        assertThat(
                result.get(0).travelerName()
        ).isEqualTo(
                "동행인1"
        );
    }

    @Test
    @DisplayName("Health와 User 소유 관계 존재 확인")
    void existsByHealthIdAndUserId() {

        // given
        User user = helper.createUser(
                "user1@example.com",
                "test1234!",
                "ROLE_USER",
                "nickname1",
                false
        );

        Health health = createHealth(
                user,
                "동행인1",
                false,
                DiseaseType.DIABETES
        );

        entityManager.flush();
        entityManager.clear();

        // when
        boolean result =
                healthQueryRepository
                        .existsByHealthIdAndUserId(
                                health.getId(),
                                user.getId()
                        );

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Health와 User 소유 관계 미존재 확인")
    void existsByHealthIdAndUserIdReturnsFalseForOtherUser() {

        // given
        User owner = helper.createUser(
                "owner@example.com",
                "test1234!",
                "ROLE_USER",
                "owner",
                false
        );

        User otherUser = helper.createUser(
                "other@example.com",
                "test1234!",
                "ROLE_USER",
                "other",
                false
        );

        Health health = createHealth(
                owner,
                "동행인1",
                false,
                DiseaseType.DIABETES
        );

        entityManager.flush();
        entityManager.clear();

        // when
        boolean result =
                healthQueryRepository
                        .existsByHealthIdAndUserId(
                                health.getId(),
                                otherUser.getId()
                        );

        // then
        assertThat(result).isFalse();
    }

    private Health createHealth(
            User user,
            String travelerName,
            boolean hasMedication,
            DiseaseType diseaseType
    ) {

        Health health = Health.builder()
                .travelerName(travelerName)
                .sensitiveAgree(true)
                .hasMedication(hasMedication)
                .healthInfo(new HealthInfo(
                        diseaseType,
                        WalkType.values()[0]
                ))
                .mealInfo(new MealInfo(
                        false,
                        false,
                        null,
                        false,
                        null,
                        false,
                        null
                ))
                .user(user)
                .build();

        entityManager.persist(health);

        return health;
    }

    private FoodInfo createFoodInfo(
            Health health,
            String foodName,
            FoodType foodType
    ) {

        FoodInfo foodInfo = FoodInfo.builder()
                .health(health)
                .foodName(foodName)
                .foodType(foodType)
                .build();

        entityManager.persist(foodInfo);

        return foodInfo;
    }
}