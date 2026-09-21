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
 * 하루에 배치할 관광 장소 개수 규칙.
 *
 * AI에게 요구하는 개수와 최종 검증이 쓰는 개수가 갈라지지 않도록 한 곳에서 계산한다.
 * 초과분을 잘라내는 규칙도 여기 둔다. 개수를 정하는 곳과 강제하는 곳이 갈라지면
 * 한쪽만 고쳐진 채로 남는다. 부족분을 어떻게 메울지는 호출부가 판단한다.
 */
public final class TouristPlaceCountPolicy {

    private static final int MINIMAL_WALK_COUNT = 2;

    private static final int DEFAULT_COUNT = 3;

    private TouristPlaceCountPolicy() {
    }

    /**
     * 동행인의 걷기 수준으로 하루 관광 장소 개수를 계산한다.
     *
     * @param healthContexts 이번 여행에 선택된 동행인, 없으면 규칙을 적용하지 않는다
     * @return 하루 관광 장소 개수, 동행인이 없으면 0
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
     * 편집 의도까지 반영한 하루 관광 장소 최소 개수를 계산한다.
     *
     * @param healthContexts          이번 여행에 선택된 동행인
     * @param densityReductionAllowed 밀도 감소가 허용된 날짜인지 여부
     * @return 허용되는 최소 관광 장소 개수
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
     * 하루 관광 장소가 기준 개수를 넘으면 초과분을 제거한다.
     *
     * AI 재시도를 쓰지 않는 이유는 초과가 정답을 모르는 문제가 아니기 때문이다.
     * 어느 슬롯을 빼도 기준을 만족하므로 Java가 정하는 편이 확실하고 재시도 예산을 아낀다.
     *
     * @param response       검사할 일정, planDays가 없으면 그대로 돌려준다
     * @param healthContexts 이번 여행에 선택된 동행인, 없으면 규칙을 적용하지 않는다
     * @return 초과분을 제거한 일정
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
    // ponytail: 제거 이후 남은 슬롯의 travelMinutes는 이전 장소 기준 그대로 둔다.
    // 일정 시간이 앞당겨지지 않을 뿐 순서와 시간 검증은 통과하며, 정확한 이동시간이 필요해지면 재계산을 붙인다.
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

        // 사용자가 지정한 MUST_HAVE는 남기고 뒤쪽 ATTRACTION부터 뺀다.
        Set<Integer> removeIndexes = new HashSet<>();

        for (int cursor = touristIndexes.size() - 1;
                cursor >= 0 && removeIndexes.size() < excess;
                cursor--) {

            int index = touristIndexes.get(cursor);

            if (schedules.get(index).courseType() == CourseType.MUST_HAVE) {
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

    // 관광 장소로 세는 슬롯. 검증(validateTouristPlaceCounts)과 같은 기준이어야 한다.
    private static boolean isTouristPlace(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return schedule != null
                && (schedule.courseType() == CourseType.ATTRACTION
                        || schedule.courseType() == CourseType.MUST_HAVE);
    }
}
