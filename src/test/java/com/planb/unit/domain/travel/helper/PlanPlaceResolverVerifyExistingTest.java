package com.planb.unit.domain.travel.helper;

import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanPlaceResolverVerifyExistingTest {

    private final PlanPlaceResolver planPlaceResolver = new PlanPlaceResolver();

    @Test
    @DisplayName("외부 검색과 이름·좌표가 달라도 확정 저장된 슬롯의 통과와 원본 보존")
    void keepsPersistedSlotWithoutExternalLookup() {

        // TourAPI에서 확정해 저장한 음식점. 같은 이름으로 Kakao를 검색하면
        // 지점명 표기와 좌표가 달라 예전에는 재검증이 실패했다.
        GetAiPlanResponse.PlanScheduleDetail stored = mealSlot(
                "까치장칼국수",
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                60
        );

        PlanPlaceResolver.Validation validation = planPlaceResolver.verifyExisting(
                stored,
                new HashSet<>(),
                new HashSet<>()
        );

        assertTrue(validation.valid());

        assertEquals("까치장칼국수", validation.schedule().locationName());

        assertEquals("128.9052237686", validation.schedule().longitude());

        // 이번 호출의 검색 후보가 아니므로 candidateId는 없다
        assertNull(validation.schedule().candidateId());
    }

    @Test
    @DisplayName("확정 저장된 슬롯의 시간 정합 위반 검출")
    void reportsBrokenPersistedSlot() {

        // stayMinutes와 실제 일정 길이가 어긋난 손상 데이터
        GetAiPlanResponse.PlanScheduleDetail broken = mealSlot(
                "까치장칼국수",
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                30
        );

        PlanPlaceResolver.Validation validation = planPlaceResolver.verifyExisting(
                broken,
                new HashSet<>(),
                new HashSet<>()
        );

        assertFalse(validation.valid());
    }

    @Test
    @DisplayName("같은 장소를 두 번 예약한 경우의 중복 검출")
    void reportsDuplicatePersistedPlace() {

        GetAiPlanResponse.PlanScheduleDetail stored = mealSlot(
                "까치장칼국수",
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                60
        );

        Set<String> places = new HashSet<>();

        Set<String> menus = new HashSet<>();

        PlanPlaceResolver.Validation first = planPlaceResolver.verifyExisting(stored, places, menus);

        planPlaceResolver.track(first.schedule(), places, menus);

        PlanPlaceResolver.Validation second = planPlaceResolver.verifyExisting(stored, places, menus);

        assertFalse(second.valid());
    }

    private GetAiPlanResponse.PlanScheduleDetail mealSlot(
            String locationName,
            LocalTime startTime,
            LocalTime endTime,
            int stayMinutes
    ) {

        return new GetAiPlanResponse.PlanScheduleDetail(
                ScheduleType.BREAKFAST,
                CourseType.RESTAURANT,
                startTime,
                endTime,
                locationName,
                "강원특별자치도 강릉시 강릉대로313번길 62",
                "128.9052237686",
                "37.7686363338",
                "image-url",
                "thumbnail-url",
                stayMinutes,
                0,
                Set.of(RecommendationTag.LOCAL_FOOD),
                null,
                new GetAiPlanResponse.RestaurantDetail(
                        "검은콩 장칼국수",
                        0.0,
                        0.0,
                        0.0,
                        "",
                        "강원특별자치도 강릉시 강릉대로313번길 62",
                        "128.9052237686",
                        "37.7686363338",
                        "image-url"
                )
        );
    }
}
