package com.planb.ai.handler;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlaceCandidateContext.Candidate;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.CreatePlanSelection;
import com.planb.domain.travel.entity.constant.CourseType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlanGenerationSelectionMapper {

    public CreatePlanAiResponse toResponse(
            CreatePlanSelection selection,
            PlaceCandidateContext candidates
    ) {

        if (selection == null || selection.planDays() == null) {
            return null;
        }

        candidates.clearStandardFoodNames();

        List<CreatePlanAiResponse.PlanDayDetail> days = selection
                .planDays()
                .stream()
                .map(day -> toDay(day, candidates))
                .toList();

        return new CreatePlanAiResponse(days);
    }

    private CreatePlanAiResponse.PlanDayDetail toDay(
            CreatePlanSelection.PlanDaySelection day,
            PlaceCandidateContext candidates
    ) {

        if (day == null) {
            return null;
        }

        return new CreatePlanAiResponse.PlanDayDetail(
                day.dayNumber(),
                day.date(),
                day.schedules() == null
                        ? null
                        : day
                                .schedules()
                                .stream()
                                .map(slot -> toSlot(slot, candidates))
                                .toList()
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail toSlot(
            CreatePlanSelection.ScheduleSelection slot,
            PlaceCandidateContext candidates
    ) {

        if (slot == null) {
            return null;
        }

        Candidate candidate = candidates.find(slot.candidateId());
        if (candidate != null && slot.restaurantDetail() != null
                && (slot.courseType() == CourseType.RESTAURANT || slot.courseType() == CourseType.LOCAL_FOOD)) {
            candidates.recordStandardFoodName(
                    slot.candidateId(),
                    slot.restaurantDetail().menuName(),
                    slot.restaurantDetail().standardFoodName()
            );
        }

        return new CreatePlanAiResponse.PlanScheduleDetail(
                slot.scheduleType(),
                slot.courseType(),
                slot.startTime(),
                slot.endTime(),
                candidate == null ? null : candidate.name(),
                candidate == null ? null : candidate.address(),
                candidate == null ? null : candidate.longitude(),
                candidate == null ? null : candidate.latitude(),
                candidate == null ? null : candidate.imageUrl(),
                candidate == null ? null : candidate.thumbnailUrl(),
                slot.stayMinutes(),
                null,
                slot.tags(),
                null,
                toRestaurant(slot.restaurantDetail(), candidate, candidates),
                slot.candidateId()
        );
    }

    private CreatePlanAiResponse.RestaurantDetail toRestaurant(
            CreatePlanSelection.RestaurantSelection restaurant,
            Candidate candidate,
            PlaceCandidateContext candidates
    ) {

        if (restaurant == null) {
            return null;
        }

        return new CreatePlanAiResponse.RestaurantDetail(
                restaurant.menuName(),
                null,
                null,
                null,
                candidates.generationRestaurants() == null || candidate == null
                        ? null
                        : candidates
                                .generationRestaurants()
                                .openingHours(candidate.candidateId()),
                candidate == null ? null : candidate.address(),
                candidate == null ? null : candidate.longitude(),
                candidate == null ? null : candidate.latitude(),
                candidate == null ? null : candidate.imageUrl()
        );
    }
}
