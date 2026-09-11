package com.planb.domain.travel.service;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;

import java.util.List;

/**
 * 이동시간 계산의 출발 기준점.
 *
 * travelMinutes는 직전 확정 장소에서 현재 슬롯까지의 시간이므로,
 * 일정의 일부만 다시 계산할 때는 잘려나간 앞 구간의 마지막 장소를 따로 전달해야 한다.
 *
 * @param previousLocation 직전 장소 이름, 일정 첫 슬롯에서는 여행 출발지
 * @param previousPlace    직전 장소의 좌표를 가진 슬롯, 출발지에서 시작할 때는 null
 */
public record RouteAnchor(

        String previousLocation,
        CreatePlanAiResponse.PlanScheduleDetail previousPlace
) {

    // 일정 전체를 계산할 때의 기준점
    public static RouteAnchor from(String decidedLocation) {

        return new RouteAnchor(decidedLocation, null);
    }

    /**
     * 재구성 구간 직전 날짜의 마지막 장소를 기준점으로 삼는다.
     *
     * 장소가 없는 날짜이거나 직전 날짜 자체가 없으면 출발지 기준으로 되돌린다.
     */
    public static RouteAnchor after(
            GetAiPlanResponse.PlanDayDetail previousDay,
            String decidedLocation
    ) {

        if (previousDay == null || previousDay.schedules() == null) {
            return from(decidedLocation);
        }

        List<GetAiPlanResponse.PlanScheduleDetail> places = previousDay
                .schedules()
                .stream()
                .filter(slot -> slot.locationName() != null
                        && !slot.locationName().isBlank())
                .toList();

        if (places.isEmpty()) {
            return from(decidedLocation);
        }

        GetAiPlanResponse.PlanScheduleDetail last = places
                .getLast();

        return new RouteAnchor(
                last.locationName(),
                new CreatePlanAiResponse.PlanScheduleDetail(
                        last.scheduleType(),
                        last.courseType(),
                        last.startTime(),
                        last.endTime(),
                        last.locationName(),
                        last.location(),
                        last.longitude(),
                        last.latitude(),
                        null,
                        null,
                        last.stayMinutes(),
                        last.travelMinutes(),
                        null,
                        null,
                        null,
                        null
                )
        );
    }
}
