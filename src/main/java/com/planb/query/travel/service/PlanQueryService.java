package com.planb.query.travel.service;

import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.query.travel.dto.response.PlanDayQueryResponse;
import com.planb.query.travel.dto.response.RestaurantDetailQueryResponse;
import com.planb.query.travel.dto.response.TravelConditionQueryResponse;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.repository.PlanRepository;
import com.planb.query.travel.dto.response.PlanBasicQueryResponse;
import com.planb.query.travel.dto.response.PlanQueryResponse;
import com.planb.query.travel.repository.PlanQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlanQueryService {

    private final PlanQueryRepository planQueryRepository;
    private final PlanRepository planRepository;
    private final TravelQueryService travelQueryService;
    private final PlanDayQueryService planDayQueryService;
    private final PlanScheduleQueryService planScheduleQueryService;
    private final RestaurantDetailQueryService restaurantDetailQueryService;

    public GetAiPlanResponse getPlanDetailResponse(
            Long travelId,
            List<HealthSummaryQueryResponse> healthSummaries,
            List<LocalTime> medicationTimes
    ) {

        TravelConditionQueryResponse travelCondition =
                travelQueryService.getTravelConditionQueryResponse(travelId);
        PlanQueryResponse plan = getPlanByTravelId(travelId);
        List<PlanDayQueryResponse> days = planDayQueryService.getPlanDaysByPlanId(plan.planId());

        List<Long> planDayIds = days
                .stream()
                .map(PlanDayQueryResponse::planDayId)
                .toList();

        List<PlanSchedule> schedules = planScheduleQueryService.getPlanSchedulesByPlanDayIds(
                planDayIds
        );

        List<Long> scheduleIds = schedules
                .stream()
                .map(PlanSchedule::getId)
                .toList();

        List<RestaurantDetailQueryResponse> restaurants =
                restaurantDetailQueryService.getRestaurantDetailsByPlanScheduleIds(
                        scheduleIds
                );

        return GetAiPlanResponse.from(
                plan,
                travelCondition,
                healthSummaries,
                medicationTimes,
                days,
                schedules,
                restaurants
        );
    }

    public GetAiPlanResponse getSharedPlanDetailResponse(Long travelId) {

        return getPlanDetailResponse(travelId, List.of(), List.of());
    }

    // travelId로 Plan객체 조회하기 (RecommendationTag 포함)
    public PlanQueryResponse getPlanByTravelId(Long travelId){

        PlanBasicQueryResponse basic =
                planQueryRepository.findPlanBasicByTravelId(travelId);

        // tags는 @ElementCollection이라 QueryDSL 단일 프로젝션에 담기 애매해서
        // Plan 엔티티를 직접 조회해 꺼내온다 (같은 트랜잭션 내부라 지연로딩 가능)
        // new HashSet<>(...)으로 즉시 복사해서 초기화해야 한다 - 트랜잭션(세션)이 끝난 뒤
        // 응답 직렬화 시점에 지연 컬렉션 접근 시 LazyInitializationException 발생
        // (open-in-view: false 환경이라 세션이 컨트롤러 응답 시점까지 열려있지 않음)
        Set<RecommendationTag> tags =
                planRepository.findById(basic.planId())
                        .map(Plan::getTags)
                        .map(HashSet::new)
                        .orElse(new HashSet<>());

        return new PlanQueryResponse(
                basic.planId(),
                basic.planName(),
                tags
        );
    }
}
