package com.planb.slice.query.health.repository;

import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.MedicationInfo;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.vo.HealthInfo;
import com.planb.domain.health.entity.vo.MealInfo;
import com.planb.domain.user.entity.User;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.health.repository.MedicationInfoQueryRepository;
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

import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        MedicationInfoQueryRepository.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class MedicationInfoQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MedicationInfoQueryRepository medicationInfoQueryRepository;

    private ChatDomainRepositoryTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new ChatDomainRepositoryTestHelper(entityManager);
    }

    @Test
    @DisplayName("Health ID를 기준으로 모든 복약 정보 삭제")
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

        MedicationInfo medicationInfo1 =
                createMedicationInfo(
                        health,
                        "약1"
                );

        MedicationInfo medicationInfo2 =
                createMedicationInfo(
                        health,
                        "약2"
                );

        entityManager.flush();
        entityManager.clear();

        // when
        long deleteCount =
                medicationInfoQueryRepository
                        .deleteAllByHealthId(
                                health.getId()
                        );

        entityManager.clear();

        // then
        MedicationInfo deletedMedicationInfo1 =
                entityManager.find(
                        MedicationInfo.class,
                        medicationInfo1.getId()
                );

        MedicationInfo deletedMedicationInfo2 =
                entityManager.find(
                        MedicationInfo.class,
                        medicationInfo2.getId()
                );

        assertThat(deleteCount).isEqualTo(2);
        assertThat(deletedMedicationInfo1).isNull();
        assertThat(deletedMedicationInfo2).isNull();
    }

    @Test
    @DisplayName("다른 Health의 복약 정보 삭제 제외")
    void deleteAllByHealthIdDoesNotDeleteOtherHealthMedicationInfo() {

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

        MedicationInfo targetMedicationInfo =
                createMedicationInfo(
                        health1,
                        "약1"
                );

        MedicationInfo otherMedicationInfo =
                createMedicationInfo(
                        health2,
                        "약2"
                );

        entityManager.flush();
        entityManager.clear();

        // when
        long deleteCount =
                medicationInfoQueryRepository
                        .deleteAllByHealthId(
                                health1.getId()
                        );

        entityManager.clear();

        // then
        MedicationInfo deletedMedicationInfo =
                entityManager.find(
                        MedicationInfo.class,
                        targetMedicationInfo.getId()
                );

        MedicationInfo remainedMedicationInfo =
                entityManager.find(
                        MedicationInfo.class,
                        otherMedicationInfo.getId()
                );

        assertThat(deleteCount).isEqualTo(1);
        assertThat(deletedMedicationInfo).isNull();
        assertThat(remainedMedicationInfo).isNotNull();
    }

    private Health createHealth(
            User user,
            String travelerName
    ) {

        Health health = Health.builder()
                .travelerName(travelerName)
                .sensitiveAgree(true)
                .hasMedication(true)
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

    private MedicationInfo createMedicationInfo(
            Health health,
            String drugName
    ) {

        MedicationInfo medicationInfo =
                MedicationInfo.builder()
                        .health(health)
                        .drugName(drugName)
                        .medicationBasis(
                                MedicationBasis.values()[0]
                        )
                        .medicationTime(
                                LocalTime.of(8, 0)
                        )
                        .mealMedicationRules(
                                Set.of()
                        )
                        .build();

        entityManager.persist(medicationInfo);

        return medicationInfo;
    }
}