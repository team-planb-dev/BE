package com.planb.slice.query.travel.repository;

import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.RestaurantDetail;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.user.entity.TermsAgreement;
import com.planb.domain.user.entity.User;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.travel.dto.response.RestaurantDetailQueryResponse;
import com.planb.query.travel.repository.RestaurantDetailQueryRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        RestaurantDetailQueryRepository.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class RestaurantDetailQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RestaurantDetailQueryRepository restaurantDetailQueryRepository;

    @Test
    @DisplayName("PlanSchedule ID 목록을 기준으로 RestaurantDetail 목록 조회")
    void findRestaurantDetailsByPlanScheduleIds() {

        // given
        Travel travel = createTravel(
                "부산 여행"
        );

        Plan plan = createPlan(
                travel,
                "부산 AI 여행 일정"
        );

        PlanDay planDay = createPlanDay(
                plan,
                1,
                LocalDate.of(2026, 9, 1)
        );

        PlanSchedule lunchSchedule =
                createPlanSchedule(
                        planDay,
                        ScheduleType.LUNCH,
                        "점심 식당",
                        LocalTime.of(12, 0)
                );

        PlanSchedule dinnerSchedule =
                createPlanSchedule(
                        planDay,
                        ScheduleType.DINNER,
                        "저녁 식당",
                        LocalTime.of(18, 0)
                );

        createRestaurantDetail(
                lunchSchedule,
                "돼지국밥",
                55.0,
                850.0,
                15.0,
                "09:00 ~ 21:00",
                "부산광역시 중구",
                "129.0001",
                "35.0001",
                "lunch-image"
        );

        createRestaurantDetail(
                dinnerSchedule,
                "밀면",
                70.0,
                720.0,
                8.0,
                "10:00 ~ 22:00",
                "부산광역시 해운대구",
                "129.0002",
                "35.0002",
                "dinner-image"
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<RestaurantDetailQueryResponse> result =
                restaurantDetailQueryRepository
                        .findRestaurantDetailsByPlanScheduleIds(
                                List.of(
                                        lunchSchedule.getId(),
                                        dinnerSchedule.getId()
                                )
                        );

        // then
        assertThat(result)
                .hasSize(2)
                .extracting(
                        RestaurantDetailQueryResponse::planScheduleId,
                        RestaurantDetailQueryResponse::menuName,
                        RestaurantDetailQueryResponse::carbohydrate,
                        RestaurantDetailQueryResponse::sodium,
                        RestaurantDetailQueryResponse::fat
                )
                .containsExactlyInAnyOrder(
                        tuple(
                                lunchSchedule.getId(),
                                "돼지국밥",
                                55.0,
                                850.0,
                                15.0
                        ),
                        tuple(
                                dinnerSchedule.getId(),
                                "밀면",
                                70.0,
                                720.0,
                                8.0
                        )
                );
    }

    @Test
    @DisplayName("RestaurantDetail 전체 상세 정보 조회")
    void findRestaurantDetailsByPlanScheduleIdsReturnsDetail() {

        // given
        Travel travel = createTravel(
                "부산 여행"
        );

        Plan plan = createPlan(
                travel,
                "부산 AI 여행 일정"
        );

        PlanDay planDay = createPlanDay(
                plan,
                1,
                LocalDate.of(2026, 9, 1)
        );

        PlanSchedule planSchedule =
                createPlanSchedule(
                        planDay,
                        ScheduleType.LUNCH,
                        "부산 식당",
                        LocalTime.of(12, 0)
                );

        createRestaurantDetail(
                planSchedule,
                "돼지국밥",
                55.0,
                850.0,
                15.0,
                "09:00 ~ 21:00",
                "부산광역시 중구",
                "129.1234",
                "35.1234",
                "restaurant-image"
        );

        entityManager.flush();
        entityManager.clear();

        // when
        RestaurantDetailQueryResponse result =
                restaurantDetailQueryRepository
                        .findRestaurantDetailsByPlanScheduleIds(
                                List.of(
                                        planSchedule.getId()
                                )
                        )
                        .get(0);

        // then
        assertThat(
                result.planScheduleId()
        ).isEqualTo(
                planSchedule.getId()
        );

        assertThat(
                result.menuName()
        ).isEqualTo(
                "돼지국밥"
        );

        assertThat(
                result.carbohydrate()
        ).isEqualTo(
                55.0
        );

        assertThat(
                result.sodium()
        ).isEqualTo(
                850.0
        );

        assertThat(
                result.fat()
        ).isEqualTo(
                15.0
        );

        assertThat(
                result.openTime()
        ).isEqualTo(
                "09:00 ~ 21:00"
        );

        assertThat(
                result.address()
        ).isEqualTo(
                "부산광역시 중구"
        );

        assertThat(
                result.longitude()
        ).isEqualTo(
                "129.1234"
        );

        assertThat(
                result.latitude()
        ).isEqualTo(
                "35.1234"
        );

        assertThat(
                result.imageUrl()
        ).isEqualTo(
                "restaurant-image"
        );
    }

    @Test
    @DisplayName("조회 대상에 없는 PlanSchedule의 RestaurantDetail 제외")
    void findRestaurantDetailsByPlanScheduleIdsExcludesOtherSchedule() {

        // given
        Travel travel = createTravel(
                "부산 여행"
        );

        Plan plan = createPlan(
                travel,
                "부산 AI 여행 일정"
        );

        PlanDay planDay = createPlanDay(
                plan,
                1,
                LocalDate.of(2026, 9, 1)
        );

        PlanSchedule targetSchedule =
                createPlanSchedule(
                        planDay,
                        ScheduleType.LUNCH,
                        "조회 대상 식당",
                        LocalTime.of(12, 0)
                );

        PlanSchedule otherSchedule =
                createPlanSchedule(
                        planDay,
                        ScheduleType.DINNER,
                        "조회 제외 식당",
                        LocalTime.of(18, 0)
                );

        createRestaurantDetail(
                targetSchedule,
                "돼지국밥",
                55.0,
                850.0,
                15.0,
                "09:00 ~ 21:00",
                "부산광역시 중구",
                "129.0001",
                "35.0001",
                "target-image"
        );

        createRestaurantDetail(
                otherSchedule,
                "밀면",
                70.0,
                720.0,
                8.0,
                "10:00 ~ 22:00",
                "부산광역시 해운대구",
                "129.0002",
                "35.0002",
                "other-image"
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<RestaurantDetailQueryResponse> result =
                restaurantDetailQueryRepository
                        .findRestaurantDetailsByPlanScheduleIds(
                                List.of(
                                        targetSchedule.getId()
                                )
                        );

        // then
        assertThat(result).hasSize(1);

        assertThat(
                result.get(0).planScheduleId()
        ).isEqualTo(
                targetSchedule.getId()
        );

        assertThat(
                result.get(0).menuName()
        ).isEqualTo(
                "돼지국밥"
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

    private PlanDay createPlanDay(
            Plan plan,
            Integer dayNumber,
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
            ScheduleType scheduleType,
            String locationName,
            LocalTime startTime
    ) {

        PlanSchedule planSchedule =
                PlanSchedule.builder()
                        .planDay(planDay)
                        .scheduleType(scheduleType)
                        .courseType(
                                CourseType.RESTAURANT
                        )
                        .startTime(startTime)
                        .endTime(
                                startTime.plusHours(1)
                        )
                        .locationName(locationName)
                        .build();

        entityManager.persist(planSchedule);

        return planSchedule;
    }

    private RestaurantDetail createRestaurantDetail(
            PlanSchedule planSchedule,
            String menuName,
            Double carbohydrate,
            Double sodium,
            Double fat,
            String openTime,
            String address,
            String longitude,
            String latitude,
            String imageUrl
    ) {

        RestaurantDetail restaurantDetail =
                RestaurantDetail.builder()
                        .planSchedule(planSchedule)
                        .menuName(menuName)
                        .carbohydrate(carbohydrate)
                        .sodium(sodium)
                        .fat(fat)
                        .openTime(openTime)
                        .address(address)
                        .longitude(longitude)
                        .latitude(latitude)
                        .imageUrl(imageUrl)
                        .build();

        entityManager.persist(
                restaurantDetail
        );

        return restaurantDetail;
    }
}