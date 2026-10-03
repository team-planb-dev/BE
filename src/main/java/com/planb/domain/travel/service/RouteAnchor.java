package com.planb.domain.travel.service;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;

import java.util.List;

/**
 * 부분 일정 재계산용 직전 장소와 좌표, 출발지 기준 좌표는 선택값
 */
public record RouteAnchor(

        String previousLocation,
        CreatePlanAiResponse.PlanScheduleDetail previousPlace
) {

    // 일정 전체를 계산할 때의 기준점
    // 좌표가 없는 사용자 입력 decidedLocation의 이름 검색
    // 동명 장소에 따른 잘못된 출발지 선택 가능성
    public static RouteAnchor from(String decidedLocation) {

        return new RouteAnchor(decidedLocation, null);
    }

    /**
     * 재구성 구간 직전의 마지막 장소 기준점
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
                        && !slot
                                .locationName()
                                .isBlank())
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
