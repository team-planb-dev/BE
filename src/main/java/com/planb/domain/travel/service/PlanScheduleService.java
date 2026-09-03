package com.planb.domain.travel.service;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.repository.PlanScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanScheduleService {

    private final PlanScheduleRepository planScheduleRepository;

    // AI 응답을 기반으로 PlanSchedule 객체 리스트 생성
    public List<PlanSchedule> makePlanScheduleList(
            PlanDay planDay,
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules
    ) {

        return schedules.stream()
                .map(schedule ->
                        PlanSchedule.builder()
                                .planDay(planDay)
                                .scheduleType(
                                        schedule.scheduleType()
                                )
                                .courseType(
                                        schedule.courseType()
                                )
                                .startTime(
                                        schedule.startTime()
                                )
                                .endTime(
                                        schedule.endTime()
                                )
                                .locationName(
                                        schedule.locationName()
                                )
                                .imageUrl(
                                        schedule.imageUrl()
                                )
                                .thumbNailImageUrl(
                                        schedule.thumbNailImageUrl()
                                )
                                .location(
                                        schedule.location()
                                )
                                .longitude(
                                        schedule.longitude()
                                )
                                .latitude(
                                        schedule.latitude()
                                )
                                .stayMinutes(
                                        schedule.stayMinutes()
                                )
                                .travelMinutes(
                                        schedule.travelMinutes()
                                )
                                .tags(
                                        schedule.tags()
                                )
                                .medicationIntervalMinutes(
                                        schedule.medication() != null
                                                ? schedule.medication()
                                                .intervalMinutes()
                                                : null
                                )
                                .medicationDescription(
                                        schedule.medication() != null
                                                ? schedule.medication()
                                                .description()
                                                : null
                                )
                                .build()
                )
                .toList();
    }

    /*
    기본 CRUD 모음
     */

    // PlanSchedule 객체 리스트 일괄 저장
    public void savePlanScheduleAll(
            List<PlanSchedule> planScheduleList
    ) {

        planScheduleRepository.saveAll(planScheduleList);
    }

    // 특정 PlanDay 목록에 속한 PlanSchedule 리스트 조회하기
    public List<PlanSchedule> findAllByPlanDayIn(List<PlanDay> planDays){

        return planScheduleRepository.findAllByPlanDayIn(planDays);
    }

    // 특정 PlanDay 목록에 속한 PlanSchedule 리스트 일괄 삭제하기
    public void deleteAllByPlanDayIn(List<PlanDay> planDays){

        planScheduleRepository.deleteAllByPlanDayIn(planDays);
    }
}
