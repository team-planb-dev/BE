package com.planb.slice.query.health.repository;

import com.planb.domain.health.entity.FoodInfo;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.vo.HealthInfo;
import com.planb.domain.health.entity.vo.MealInfo;
import com.planb.domain.user.entity.User;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.health.repository.FoodInfoQueryRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        FoodInfoQueryRepository.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class FoodInfoQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FoodInfoQueryRepository foodInfoQueryRepository;

    private ChatDomainRepositoryTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new ChatDomainRepositoryTestHelper(entityManager);
    }

    @Test
    @DisplayName("Health ID를 기준으로 모든 음식 정보 삭제")
    void deleteAllByHealthId() {

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
                "동행인1"
        );

        FoodInfo foodInfo1 = createFoodInfo(
                health,
                "땅콩",
                FoodType.ALLERGY
        );

        FoodInfo foodInfo2 = createFoodInfo(
                health,
                "우유",
                FoodType.ALLERGY
        );

        entityManager.flush();
        entityManager.clear();

        // when
        long deleteCount = foodInfoQueryRepository
                .deleteAllByHealthId(
                        health.getId()
                );

        entityManager.clear();

        // then
        FoodInfo deletedFoodInfo1 = entityManager.find(
                FoodInfo.class,
                foodInfo1.getId()
        );

        FoodInfo deletedFoodInfo2 = entityManager.find(
                FoodInfo.class,
                foodInfo2.getId()
        );

        assertThat(deleteCount).isEqualTo(2);
        assertThat(deletedFoodInfo1).isNull();
        assertThat(deletedFoodInfo2).isNull();
    }

    @Test
    @DisplayName("다른 Health의 음식 정보 삭제 제외")
    void deleteAllByHealthIdDoesNotDeleteOtherHealthFoodInfo() {

        // given
        User user = helper.createUser(
                "user1@example.com",
                "test1234!",
                "ROLE_USER",
                "nickname1",
                false
        );

        Health health1 = createHealth(
                user,
                "동행인1"
        );

        Health health2 = createHealth(
                user,
                "동행인2"
        );

        FoodInfo targetFoodInfo = createFoodInfo(
                health1,
                "땅콩",
                FoodType.ALLERGY
        );

        FoodInfo otherFoodInfo = createFoodInfo(
                health2,
                "우유",
                FoodType.ALLERGY
        );

        entityManager.flush();
        entityManager.clear();

        // when
        long deleteCount = foodInfoQueryRepository
                .deleteAllByHealthId(
                        health1.getId()
                );

        entityManager.clear();

        // then
        FoodInfo deletedFoodInfo = entityManager.find(
                FoodInfo.class,
                targetFoodInfo.getId()
        );

        FoodInfo remainedFoodInfo = entityManager.find(
                FoodInfo.class,
                otherFoodInfo.getId()
        );

        assertThat(deleteCount).isEqualTo(1);
        assertThat(deletedFoodInfo).isNull();
        assertThat(remainedFoodInfo).isNotNull();
    }

    private Health createHealth(
            User user,
            String travelerName
    ) {

        Health health = Health.builder()
                .travelerName(travelerName)
                .sensitiveAgree(true)
                .hasMedication(false)
                .healthInfo(new HealthInfo(
                        null,
                        null
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