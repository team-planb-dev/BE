package com.planb.slice.query.travel.repository;

import com.planb.domain.user.entity.AccountRecovery;
import com.planb.domain.user.entity.constant.RecoveryQuestion;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.TravelListFilter;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.user.entity.TermsAgreement;
import com.planb.domain.user.entity.User;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.travel.dto.response.TravelConditionQueryResponse;
import com.planb.query.travel.dto.response.TravelListItemQueryResponse;
import com.planb.query.travel.repository.TravelQueryRepository;
import com.planb.slice.support.MySqlRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

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
class TravelQueryRepositoryTest
        extends MySqlRepositoryTest {

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

    @Test
    @DisplayName("UPCOMING 탭은 오늘 이후에 끝나는 여행만 시작일 순으로 조회")
    void findAllByUserIdReturnsUpcomingTravels() {

        // given
        LocalDate today = LocalDate.of(2026, 9, 10);

        User owner = createUser();

        createTravel("지난 여행",
                owner,
                today.minusDays(5),
                today.minusDays(3));

        Travel ongoing = createTravel("여행 중",
                owner,
                today.minusDays(1),
                today.plusDays(1));

        Travel upcoming = createTravel("예정 여행",
                owner,
                today.plusDays(7),
                today.plusDays(8));

        entityManager.flush();
        entityManager.clear();

        // when
        List<TravelListItemQueryResponse> result =
                travelQueryRepository
                        .findAllByUserId(
                                owner.getId(),
                                TravelListFilter.UPCOMING,
                                today
                        );

        // then
        assertThat(result)
                .extracting(TravelListItemQueryResponse::travelId)
                .containsExactly(
                        ongoing.getId(),
                        upcoming.getId()
                );
    }

    @Test
    @DisplayName("PAST 탭은 이미 끝난 여행만 최근 순으로 조회")
    void findAllByUserIdReturnsPastTravels() {

        // given
        LocalDate today = LocalDate.of(2026, 9, 10);

        User owner = createUser();

        Travel older = createTravel("오래된 여행",
                owner,
                today.minusDays(20),
                today.minusDays(19));

        Travel recent = createTravel("최근 여행",
                owner,
                today.minusDays(3),
                today.minusDays(2));

        createTravel("예정 여행",
                owner,
                today.plusDays(1),
                today.plusDays(2));

        entityManager.flush();
        entityManager.clear();

        // when
        List<TravelListItemQueryResponse> result =
                travelQueryRepository
                        .findAllByUserId(
                                owner.getId(),
                                TravelListFilter.PAST,
                                today
                        );

        // then
        assertThat(result)
                .extracting(TravelListItemQueryResponse::travelId)
                .containsExactly(
                        recent.getId(),
                        older.getId()
                );
    }

    @Test
    @DisplayName("다른 사용자의 여행은 목록에 포함되지 않음")
    void findAllByUserIdExcludesOtherUsersTravels() {

        // given
        LocalDate today = LocalDate.of(2026, 9, 10);

        User owner = createUser();
        User stranger = createUser();

        Travel ownerTravel = createTravel("내 여행",
                owner,
                today.plusDays(1),
                today.plusDays(2));

        createTravel("남의 여행",
                stranger,
                today.plusDays(1),
                today.plusDays(2));

        entityManager.flush();
        entityManager.clear();

        // when
        List<TravelListItemQueryResponse> result =
                travelQueryRepository
                        .findAllByUserId(
                                owner.getId(),
                                TravelListFilter.UPCOMING,
                                today
                        );

        // then
        assertThat(result)
                .extracting(TravelListItemQueryResponse::travelId)
                .containsExactly(ownerTravel.getId());
    }

    @Test
    @DisplayName("썸네일은 여행별 첫 일정의 이미지를 사용")
    void findThumbnailUrlsByTravelIdsReturnsFirstImage() {

        // given
        User owner = createUser();

        Travel travel = createTravel("부산 여행",
                owner,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2));

        Plan plan = createPlan(travel);

        PlanDay firstDay = createPlanDay(plan, 1, LocalDate.of(2026, 9, 1));
        PlanDay secondDay = createPlanDay(plan, 2, LocalDate.of(2026, 9, 2));

        createPlanSchedule(secondDay, LocalTime.of(9, 0), "second.jpg");
        createPlanSchedule(firstDay, LocalTime.of(13, 0), "first-afternoon.jpg");
        createPlanSchedule(firstDay, LocalTime.of(10, 0), "first-morning.jpg");

        entityManager.flush();
        entityManager.clear();

        // when
        Map<Long, String> result =
                travelQueryRepository
                        .findThumbnailUrlsByTravelIds(
                                List.of(travel.getId())
                        );

        // then
        assertThat(result)
                .containsEntry(travel.getId(), "first-morning.jpg");
    }

    @Test
    @DisplayName("이미지가 없는 여행은 썸네일 결과에 포함되지 않음")
    void findThumbnailUrlsByTravelIdsSkipsTravelsWithoutImage() {

        // given
        User owner = createUser();

        Travel travel = createTravel("부산 여행",
                owner,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2));

        PlanDay planDay = createPlanDay(
                createPlan(travel),
                1,
                LocalDate.of(2026, 9, 1)
        );

        createPlanSchedule(planDay, LocalTime.of(10, 0), null);

        entityManager.flush();
        entityManager.clear();

        // when
        Map<Long, String> result =
                travelQueryRepository
                        .findThumbnailUrlsByTravelIds(
                                List.of(travel.getId())
                        );

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("공유 토큰으로 여행 id 조회")
    void findTravelIdByShareToken() {

        // given
        Travel travel = createTravel("부산 여행",
                createUser(),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2));

        travel.issueShareToken("share-token-1");

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThat(travelQueryRepository
                .findTravelIdByShareToken("share-token-1"))
                .contains(travel.getId());

        assertThat(travelQueryRepository
                .findTravelIdByShareToken("없는-토큰"))
                .isEmpty();
    }

    @Test
    @DisplayName("공유 토큰은 재발급해도 기존 토큰을 유지")
    void issueShareTokenKeepsExistingToken() {

        // given
        Travel travel = createTravel("부산 여행",
                createUser(),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2));

        travel.issueShareToken("share-token-1");

        // when
        travel.issueShareToken("share-token-2");

        // then
        assertThat(travel
                .getShareToken())
                .isEqualTo("share-token-1");
    }

    private Travel createTravel(
            String travelName,
            User user,
            LocalDate startDate,
            LocalDate endDate
    ) {

        Travel travel = Travel.builder()
                .user(user)
                .travelName(travelName)
                .locationDo("부산")
                .locationSigungu("해운대구")
                .startDate(startDate)
                .endDate(endDate)
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

    private Plan createPlan(Travel travel) {

        Plan plan = Plan.builder()
                .travel(travel)
                .planName("부산 일정")
                .build();

        entityManager.persist(plan);

        return plan;
    }

    private PlanDay createPlanDay(
            Plan plan,
            int dayNumber,
            LocalDate planDate
    ) {

        PlanDay planDay = PlanDay.builder()
                .plan(plan)
                .dayNumber(dayNumber)
                .planDate(planDate)
                .build();

        entityManager.persist(planDay);

        return planDay;
    }

    private PlanSchedule createPlanSchedule(
            PlanDay planDay,
            LocalTime startTime,
            String imageUrl
    ) {

        PlanSchedule planSchedule = PlanSchedule.builder()
                .planDay(planDay)
                .scheduleType(ScheduleType.ACTIVITY)
                .courseType(CourseType.ATTRACTION)
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .locationName("장소")
                .imageUrl(imageUrl)
                .location("부산광역시")
                .stayMinutes(60)
                .travelMinutes(20)
                .build();

        entityManager.persist(planSchedule);

        return planSchedule;
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
                .locationDo("부산")
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
