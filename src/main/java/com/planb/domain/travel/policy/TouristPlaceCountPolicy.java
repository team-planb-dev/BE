package com.planb.domain.travel.policy;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.entity.constant.CourseType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 하루 관광 장소 개수와 초과분 정리 규칙
 */
public final class TouristPlaceCountPolicy {

    private static final int MINIMAL_WALK_COUNT = 2;

    private static final int DEFAULT_COUNT = 3;

    private TouristPlaceCountPolicy() {
    }

    /**
     * 동행인의 걷기 수준에 따른 하루 관광 장소 개수, 동행인 미선택 시 0개
     */
    public static int expectedCount(List<TravelHealthContext> healthContexts) {

        if (healthContexts == null || healthContexts.isEmpty()) {
            return 0;
        }

        boolean hasMinimalTraveler = healthContexts
                .stream()
                .anyMatch(context -> context.walkType() == WalkType.MINIMAL);

        return hasMinimalTraveler
                ? MINIMAL_WALK_COUNT
                : DEFAULT_COUNT;
    }

    /**
     * 밀도 감소 허용 여부를 반영한 하루 관광 장소 최소 개수
     */
    public static int minimumCount(
            List<TravelHealthContext> healthContexts,
            boolean densityReductionAllowed
    ) {

        int expectedCount = expectedCount(healthContexts);

        if (!densityReductionAllowed || expectedCount == 0) {
            return expectedCount;
        }

        return Math.min(
                MINIMAL_WALK_COUNT,
                expectedCount
        );
    }

    /**
     * 기준 개수를 넘는 관광 장소의 초과분 제거, 일정 미존재 시 원본 유지
     */
    public static CreatePlanAiResponse trimExcess(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        if (response == null || response.planDays() == null) {
            return response;
        }

        int expectedCount = expectedCount(healthContexts);

        if (expectedCount <= 0) {
            return response;
        }

        return new CreatePlanAiResponse(
                response
                        .planDays()
                        .stream()
                        .map(day -> trimDay(
                                day,
                                expectedCount
                        ))
                        .toList()
        );
    }

    // 하루치 관광 장소 초과분 제거
    // ponytail: 초과 관광지 제거 후 남은 travelMinutes 재계산 생략
    // 일정 시각은 유지하고 순서·시간 검증만 통과; 정확한 이동시간이 필요해지면 재계산 추가
    private static CreatePlanAiResponse.PlanDayDetail trimDay(
            CreatePlanAiResponse.PlanDayDetail day,
            int expectedCount
    ) {

        if (day == null || day.schedules() == null) {
            return day;
        }

        List<CreatePlanAiResponse.PlanScheduleDetail> schedules = day.schedules();

        List<Integer> touristIndexes = IntStream
                .range(0, schedules.size())
                .filter(index -> isTouristPlace(schedules.get(index)))
                .boxed()
                .toList();

        int excess = touristIndexes.size() - expectedCount;

        if (excess <= 0) {
            return day;
        }

        // MUST_HAVE 보존과 뒤쪽 ATTRACTION 우선 제거
        Set<Integer> removeIndexes = new HashSet<>();

        for (int cursor = touristIndexes.size() - 1;
                cursor >= 0 && removeIndexes.size() < excess;
                cursor--) {

            int index = touristIndexes.get(cursor);

            if (schedules
                    .get(index)
                    .courseType() == CourseType.MUST_HAVE) {
                continue;
            }

            removeIndexes.add(index);
        }

        if (removeIndexes.isEmpty()) {
            return day;
        }

        return new CreatePlanAiResponse.PlanDayDetail(
                day.dayNumber(),
                day.date(),
                IntStream
                        .range(0, schedules.size())
                        .filter(index -> !removeIndexes.contains(index))
                        .mapToObj(schedules::get)
                        .toList()
        );
    }

    // validateTouristPlaceCounts와 동일한 관광 장소 집계 기준
    private static boolean isTouristPlace(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return schedule != null
                && (schedule.courseType() == CourseType.ATTRACTION
                        || schedule.courseType() == CourseType.MUST_HAVE);
    }
}
