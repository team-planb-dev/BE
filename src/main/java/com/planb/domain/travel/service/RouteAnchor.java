package com.planb.domain.travel.service;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;

import java.util.List;

/**
 * 부분 일정 재계산에 필요한 앞 구간의 출발 장소와 좌표
 * @param previousLocation 직전 장소 이름, 일정 첫 슬롯에서는 여행 출발지
 * @param previousPlace 직전 장소의 좌표를 가진 슬롯, 출발지 기준이면 null 허용
 */
public record RouteAnchor(

        String previousLocation,
        CreatePlanAiResponse.PlanScheduleDetail previousPlace
) {

    // 일정 전체를 계산할 때의 기준점.
    // decidedLocation은 사용자 입력 문자열이라 좌표를 모르고, 경로 조회에서 이름으로 검색된다.
    // 동명 장소가 여러 곳이면 엉뚱한 출발지가 잡힐 수 있다.
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
