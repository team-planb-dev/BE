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
import java.util.List;
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
                        "약1",
                        LocalTime.of(8, 0)
                );

        MedicationInfo medicationInfo2 =
                createMedicationInfo(
                        health,
                        "약2",
                        LocalTime.of(9, 0)
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
                        "약1",
                        LocalTime.of(8, 0)
                );

        MedicationInfo otherMedicationInfo =
                createMedicationInfo(
                        health2,
                        "약2",
                        LocalTime.of(9, 0)
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

    @Test
    @DisplayName("User ID를 기준으로 모든 동행인의 복약 시간 조회")
    void findMedicationTimesByUserId() {

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

        createMedicationInfo(
                health1,
                "약1",
                LocalTime.of(8, 0)
        );

        createMedicationInfo(
                health1,
                "약2",
                LocalTime.of(13, 0)
        );

        createMedicationInfo(
                health2,
                "약3",
                LocalTime.of(20, 0)
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<LocalTime> result =
                medicationInfoQueryRepository
                        .findMedicationTimesByUserId(
                                user.getId()
                        );

        // then
        assertThat(result)
                .hasSize(3)
                .containsExactlyInAnyOrder(
                        LocalTime.of(8, 0),
                        LocalTime.of(13, 0),
                        LocalTime.of(20, 0)
                );
    }

    @Test
    @DisplayName("다른 User의 복약 시간 조회 제외")
    void findMedicationTimesByUserIdExcludesOtherUser() {

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
                "동행인1"
        );

        Health user2Health = createHealth(
                user2,
                "다른 사용자 동행인"
        );

        createMedicationInfo(
                user1Health,
                "약1",
                LocalTime.of(8, 0)
        );

        createMedicationInfo(
                user2Health,
                "약2",
                LocalTime.of(20, 0)
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<LocalTime> result =
                medicationInfoQueryRepository
                        .findMedicationTimesByUserId(
                                user1.getId()
                        );

        // then
        assertThat(result)
                .containsExactly(
                        LocalTime.of(8, 0)
                );
    }

    @Test
    @DisplayName("복약 시간이 없는 복약 정보 조회 제외")
    void findMedicationTimesByUserIdExcludesNullMedicationTime() {

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

        createMedicationInfo(
                health,
                "복약 시간 있음",
                LocalTime.of(8, 0)
        );

        createMedicationInfo(
                health,
                "복약 시간 없음",
                null
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<LocalTime> result =
                medicationInfoQueryRepository
                        .findMedicationTimesByUserId(
                                user.getId()
                        );

        // then
        assertThat(result)
                .containsExactly(
                        LocalTime.of(8, 0)
                );
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
            String drugName,
            LocalTime medicationTime
    ) {

        MedicationInfo medicationInfo =
                MedicationInfo.builder()
                        .health(health)
                        .drugName(drugName)
                        .medicationBasis(
                                MedicationBasis.values()[0]
                        )
                        .medicationTime(
                                medicationTime
                        )
                        .mealMedicationRules(
                                Set.of()
                        )
                        .build();

        entityManager.persist(medicationInfo);

        return medicationInfo;
    }
}