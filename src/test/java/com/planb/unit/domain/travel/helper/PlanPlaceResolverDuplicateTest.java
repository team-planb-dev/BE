package com.planb.unit.domain.travel.helper;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.RestaurantDetail;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.helper.PlanPlaceResolver.Validation;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlanPlaceResolverDuplicateTest {

    private final PlanPlaceResolver planPlaceResolver = new PlanPlaceResolver();

    private final Set<String> places = new HashSet<>();

    private final Set<String> menus = new HashSet<>();

    @Test
    @DisplayName("같은 candidateId 재사용의 중복 거부")
    void rejectsSameCandidateId() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(tourAttraction(
                "1",
                "첨성대",
                "경상북도 경주시 인왕동"
        ));

        accept(
                attraction("tour:1", 9),
                candidates
        );

        // 다른 날짜·시간의 슬롯도 여행 전체 사용 집합 기준
        Validation second = planPlaceResolver.validate(
                attraction("tour:1", 15),
                candidates,
                places,
                menus
        );

        assertThat(second.valid())
                .isFalse();

        assertThat(second.reason())
                .startsWith("이미 사용한 장소");
    }

    @Test
    @DisplayName("candidateId가 달라도 같은 장소명의 중복 거부")
    void rejectsSameNameWithDifferentCandidateId() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(tourAttraction(
                "1",
                "첨성대",
                "경상북도 경주시 인왕동"
        ));

        candidates.record(tourAttraction(
                "2",
                " 첨성대 ",
                "경상북도 경주시 인왕동"
        ));

        accept(
                attraction("tour:1", 9),
                candidates
        );

        assertThat(planPlaceResolver
                        .validate(
                                attraction("tour:2", 11),
                                candidates,
                                places,
                                menus)
                        .reason())
                .startsWith("이미 사용한 장소");
    }

    @Test
    @DisplayName("주소가 다른 동명 지점의 이름 기준 거부, 과잉 거부 한계")
    void rejectsSameNameAtDifferentAddress() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(tourAttraction(
                "1",
                "황리단길",
                "경상북도 경주시 황남동"
        ));

        candidates.record(tourAttraction(
                "2",
                "황리단길",
                "경상북도 경주시 사정동"
        ));

        accept(
                attraction("tour:1", 9),
                candidates
        );

        // 주소 기반 지점 구분 정책 부재, 실제 다른 지점도 중복 처리
        assertThat(planPlaceResolver
                        .validate(
                                attraction("tour:2", 11),
                                candidates,
                                places,
                                menus)
                        .reason())
                .startsWith("이미 사용한 장소");
    }

    @Test
    @DisplayName("Kakao·TourAPI 출처가 달라도 같은 장소명의 중복 거부")
    void rejectsSameNameAcrossProviders() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(tourAttraction(
                "1",
                "첨성대",
                "경상북도 경주시 인왕동"
        ));

        candidates.record(kakaoPlace(
                "kakao:1",
                "AT4",
                "첨성대"
        ));

        accept(
                attraction("tour:1", 9),
                candidates
        );

        assertThat(planPlaceResolver
                        .validate(
                                attraction("kakao:1", 11),
                                candidates,
                                places,
                                menus)
                        .reason())
                .startsWith("이미 사용한 장소");
    }

    @Test
    @DisplayName("출처별 표기가 다른 같은 장소의 통과, 별칭 미탐 한계")
    void acceptsAliasAcrossProviders() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(tourAttraction(
                "1",
                "첨성대",
                "경상북도 경주시 인왕동"
        ));

        candidates.record(kakaoPlace(
                "kakao:1",
                "AT4",
                "경주 첨성대"
        ));

        accept(
                attraction("tour:1", 9),
                candidates
        );

        // 공백·접두 표기 차이의 동일 장소 판별 불가
        assertThat(planPlaceResolver
                        .validate(
                                attraction("kakao:1", 11),
                                candidates,
                                places,
                                menus)
                        .valid())
                .isTrue();
    }

    @Test
    @DisplayName("같은 식당의 다른 메뉴는 장소 중복, 다른 식당의 같은 메뉴는 메뉴 중복")
    void distinguishesPlaceDuplicateFromMenuDuplicate() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(kakaoPlace(
                "kakao:10",
                "FD6",
                "백제삼계탕"
        ));

        candidates.record(kakaoPlace(
                "kakao:20",
                "FD6",
                "하니칼국수"
        ));

        accept(
                meal("kakao:10", "삼계탕", 12),
                candidates
        );

        Validation samePlace = planPlaceResolver.validate(
                meal("kakao:10", "칼국수", 18),
                candidates,
                places,
                menus
        );

        Validation sameMenu = planPlaceResolver.validate(
                meal("kakao:20", "삼계탕", 18),
                candidates,
                places,
                menus
        );

        assertThat(samePlace.reason())
                .startsWith("이미 사용한 장소");

        assertThat(sameMenu.reason())
                .isEqualTo("음식점 메뉴 누락 또는 중복");
    }

    @Test
    @DisplayName("재선택 장소가 차지한 이름의 원본 복원 거부")
    void rejectsRestoringOriginalAlreadyTakenByReselection() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(tourAttraction(
                "1",
                "첨성대",
                "경상북도 경주시 인왕동"
        ));

        // 다른 슬롯 재선택 결과로 확정된 장소
        accept(
                attraction("tour:1", 9),
                candidates
        );

        // 같은 장소였던 저장 원본의 verifiedFallback 복원 경로
        Validation restore = planPlaceResolver.verifyExisting(
                saved("첨성대"),
                places,
                menus
        );

        assertThat(restore.valid())
                .isFalse();

        assertThat(restore.reason())
                .startsWith("이미 사용한 장소");
    }

    @Test
    @DisplayName("저장 일정 기존 중복의 검출과 원본 슬롯 변환 유지")
    void reportsPersistedDuplicateWithoutDroppingIt() {

        GetAiPlanResponse.PlanScheduleDetail first = saved("첨성대");

        GetAiPlanResponse.PlanScheduleDetail second = saved("첨성대");

        planPlaceResolver.track(
                planPlaceResolver
                        .verifyExisting(
                                first,
                                places,
                                menus)
                        .schedule(),
                places,
                menus
        );

        Set<String> before = Set.copyOf(places);

        Validation duplicate = planPlaceResolver.verifyExisting(
                second,
                places,
                menus
        );

        assertThat(duplicate.valid())
                .isFalse();

        assertThat(places)
                .isEqualTo(before);

        // 검출 후 원본 슬롯 변환 유지, PlanService 보존 날짜 경로 자체는 범위 밖
        assertThat(planPlaceResolver
                        .fromExisting(second)
                        .locationName())
                .isEqualTo("첨성대");
    }

    @Test
    @DisplayName("저장 일정의 다른 식당 같은 메뉴 중복 검출")
    void reportsPersistedMenuDuplicate() {

        planPlaceResolver.track(
                planPlaceResolver
                        .verifyExisting(
                                savedRestaurant(
                                        "백제삼계탕",
                                        "삼계탕"
                                ),
                                places,
                                menus
                        )
                        .schedule(),
                places,
                menus
        );

        Validation duplicate = planPlaceResolver.verifyExisting(
                savedRestaurant(
                        "황남삼계탕",
                        "삼계탕"
                ),
                places,
                menus
        );

        assertThat(duplicate.reason())
                .isEqualTo("음식점 메뉴 누락 또는 중복");
    }

    private void accept(
            PlanScheduleDetail slot,
            PlaceCandidateContext candidates
    ) {

        Validation validation = planPlaceResolver.validate(
                slot,
                candidates,
                places,
                menus
        );

        assertThat(validation.valid())
                .isTrue();

        planPlaceResolver.track(
                validation.schedule(),
                places,
                menus
        );
    }

    private PlanScheduleDetail attraction(
            String candidateId,
            int hour
    ) {

        return new PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(hour, 0),
                LocalTime.of(hour + 1, 0),
                null,
                null,
                null,
                null,
                null,
                null,
                60,
                10,
                Set.of(),
                null,
                null,
                candidateId
        );
    }

    private PlanScheduleDetail meal(
            String candidateId,
            String menuName,
            int hour
    ) {

        return new PlanScheduleDetail(
                hour < 16 ? ScheduleType.LUNCH : ScheduleType.DINNER,
                CourseType.RESTAURANT,
                LocalTime.of(hour, 0),
                LocalTime.of(hour + 1, 0),
                null,
                null,
                null,
                null,
                null,
                null,
                60,
                10,
                Set.of(),
                null,
                new RestaurantDetail(
                        menuName,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                candidateId
        );
    }

    private GetAiPlanResponse.PlanScheduleDetail saved(String locationName) {

        return new GetAiPlanResponse.PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                locationName,
                "경상북도 경주시 인왕동",
                "129.22",
                "35.83",
                null,
                null,
                60,
                0,
                Set.of(),
                null,
                null
        );
    }

    private GetAiPlanResponse.PlanScheduleDetail savedRestaurant(
            String locationName,
            String menuName
    ) {

        return new GetAiPlanResponse.PlanScheduleDetail(
                ScheduleType.LUNCH,
                CourseType.RESTAURANT,
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                locationName,
                "경상북도 경주시 황남동",
                "129.21",
                "35.83",
                null,
                null,
                60,
                0,
                Set.of(),
                null,
                new GetAiPlanResponse.RestaurantDetail(
                        menuName,
                        0.0,
                        0.0,
                        0.0,
                        "",
                        "경상북도 경주시 황남동",
                        "129.21",
                        "35.83",
                        null
                )
        );
    }

    private PlaceWithRouteResult kakaoPlace(
            String candidateId,
            String categoryCode,
            String placeName
    ) {

        return new PlaceWithRouteResult(
                true,
                placeName,
                "경상북도 경주시 인왕동",
                "129.22",
                "35.83",
                10,
                candidateId,
                categoryCode,
                null
        );
    }

    private Kor2KeywordSearchResponse.Item tourAttraction(
            String contentId,
            String title,
            String address
    ) {

        return new Kor2KeywordSearchResponse.Item(
                address,
                "",
                null,
                contentId,
                "12",
                null,
                null,
                null,
                null,
                "129.22",
                "35.83",
                null,
                null,
                null,
                title,
                null,
                null,
                null,
                "HS01",
                null
        );
    }
}
