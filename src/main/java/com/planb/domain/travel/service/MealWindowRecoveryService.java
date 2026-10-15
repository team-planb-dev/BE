package com.planb.domain.travel.service;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.policy.AllergenPolicy;
import com.planb.domain.travel.policy.MealSlotPolicy;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 등록 점심 창을 위한 미사용 식당 후보의 제한 재선택
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealWindowRecoveryService {

    private final PlanPlaceResolver planPlaceResolver;
    private final ScheduleNormalizer scheduleNormalizer;
    private final TravelMinutesResolver travelMinutesResolver;

    public CreatePlanAiResponse recover(
            CreatePlanAiResponse response,
            TravelPlanContext context,
            PlaceCandidateContext candidates
    ) {

        LocalTime desired = MealSlotPolicy.configuredMealTime(ScheduleType.LUNCH, context.healthContexts());

        if (desired == null) {
            return response;
        }

        Set<String> usedPlaces = new HashSet<>();
        Set<String> usedMenus = new HashSet<>();
        for (var day : response.planDays()) {
            for (PlanScheduleDetail slot : day.schedules()) {
                planPlaceResolver.track(slot, usedPlaces, usedMenus);
            }
        }

        List<CreatePlanAiResponse.PlanDayDetail> days = new ArrayList<>();

        for (var day : response.planDays()) {
            List<PlanScheduleDetail> schedules = new ArrayList<>(day.schedules());

            for (int index = 0; index < schedules.size(); index++) {
                PlanScheduleDetail meal = schedules.get(index);

                if (meal.scheduleType() != ScheduleType.LUNCH
                        || scheduleNormalizer.mealTimeSatisfied(meal, context.healthContexts())) {
                    continue;
                }

                usedPlaces.remove(meal.candidateId());
                usedPlaces.remove(meal.locationName().strip());
                usedMenus.remove(meal.restaurantDetail().menuName().strip());

                PlanScheduleDetail previous = previousPlace(schedules, index);
                int nextIndex = nextPlace(schedules, index);
                PlanScheduleDetail next = nextIndex < 0 ? null : schedules.get(nextIndex);
                Replacement replacement = replacement(
                        meal,
                        previous,
                        next,
                        desired,
                        context,
                        candidates,
                        usedPlaces,
                        usedMenus
                ).orElseThrow(() -> new BaseException(
                        PlanEditExceptionEnum.MEAL_TIME_UNFEASIBLE,
                        new Object[]{"day=" + day.dayNumber() + ", slot=LUNCH"}
                ));
                schedules.set(index, replacement.meal());
                planPlaceResolver.track(replacement.meal(), usedPlaces, usedMenus);

                if (nextIndex >= 0) {
                    schedules.set(nextIndex, copy(
                            next,
                            next.startTime(),
                            next.endTime(),
                            replacement.outgoing(),
                            next.restaurantDetail()
                    ));
                }
                log.info(
                        "[MEAL WINDOW RECOVERY] day={}, result=restored, candidateId={}",
                        day.dayNumber(),
                        replacement.meal().candidateId()
                );
            }

            days.add(new CreatePlanAiResponse.PlanDayDetail(
                    day.dayNumber(),
                    day.date(),
                    schedules
            ));
        }

        return new CreatePlanAiResponse(days);
    }

    private Optional<Replacement> replacement(
            PlanScheduleDetail meal,
            PlanScheduleDetail previous,
            PlanScheduleDetail next,
            LocalTime desired,
            TravelPlanContext context,
            PlaceCandidateContext candidates,
            Set<String> usedPlaces,
            Set<String> usedMenus
    ) {

        if (previous == null) {
            return Optional.empty();
        }

        int attempts = 0;
        boolean routeUnavailable = false;

        for (var candidate : candidates.restaurantCandidates()) {
            if (usedPlaces.contains(candidate.candidateId()) || usedPlaces.contains(candidate.name())) {
                continue;
            }
            if (++attempts > 3) {
                break;
            }

            for (String menu : candidates.recordedRestaurantMenus(candidate.candidateId())) {
                if (AllergenPolicy.violation(context.healthContexts(), candidate.name(), menu).isPresent()) {
                    continue;
                }
                PlanScheduleDetail selected = new PlanScheduleDetail(
                        meal.scheduleType(),
                        meal.courseType(),
                        meal.startTime(),
                        meal.endTime(),
                        candidate.name(),
                        candidate.address(),
                        candidate.longitude(),
                        candidate.latitude(),
                        candidate.imageUrl(),
                        candidate.thumbnailUrl(),
                        meal.stayMinutes(),
                        null,
                        meal.tags(),
                        null,
                        new CreatePlanAiResponse.RestaurantDetail(
                                menu,
                                null,
                                null,
                                null,
                                null,
                                candidate.address(),
                                candidate.longitude(),
                                candidate.latitude(),
                                candidate.imageUrl()
                        ),
                        candidate.candidateId()
                );
                var validation = planPlaceResolver.validate(
                        selected,
                        candidates,
                        usedPlaces,
                        usedMenus
                );
                if (!validation.valid()) {
                    continue;
                }
                selected = validation.schedule();
                var inbound = travelMinutesResolver.lookup(
                        previous.locationName(),
                        previous,
                        selected,
                        context.createTravelRequest().transportation()
                );
                var outbound = next == null ? new TravelMinutesResolver.SegmentMinutes(Optional.of(0), null)
                        : travelMinutesResolver.lookup(
                                selected.locationName(),
                                selected,
                                next,
                                context.createTravelRequest().transportation()
                        );
                if (inbound.minutes().isEmpty() || outbound.minutes().isEmpty()) {
                    routeUnavailable = true;
                    continue;
                }

                LocalTime arrival = previous.endTime().plusMinutes(inbound.minutes().get());
                if (arrival.isBefore(previous.endTime())) {
                    continue;
                }
                for (int minute = 0; minute <= 60; minute++) {
                    // 등록 시각과 최소 차이, 동률은 늦은 시각 우선
                    int offset = minute == 0 ? 0 : (minute + 1) / 2 * (minute % 2 == 1 ? 1 : -1);
                    LocalTime start = desired.plusMinutes(offset);
                    LocalTime end = start.plusMinutes(meal.stayMinutes());
                    PlanScheduleDetail timed = copy(
                            selected,
                            start,
                            end,
                            inbound.minutes().get(),
                            selected.restaurantDetail()
                    );
                    if (start.isBefore(arrival) || !end.isAfter(start)
                            || !scheduleNormalizer.mealTimeSatisfied(timed, context.healthContexts())) {
                        continue;
                    }
                    LocalTime nextArrival = end.plusMinutes(outbound.minutes().get());
                    if (next != null && (nextArrival.isBefore(end) || nextArrival.isAfter(next.startTime()))) {
                        continue;
                    }
                    return Optional.of(new Replacement(timed, outbound.minutes().get()));
                }
                break;
            }
        }

        if (routeUnavailable) {
            throw new BaseException(PlanEditExceptionEnum.ROUTE_LOOKUP_FAILED, new Object[]{"점심 재선택 후보의 이동시간 확인 불가"});
        }

        return Optional.empty();
    }

    private PlanScheduleDetail previousPlace(List<PlanScheduleDetail> schedules, int index) {

        for (int previous = index - 1; previous >= 0; previous--) {
            if (planPlaceResolver.requiresPlace(schedules.get(previous))) {
                return schedules.get(previous);
            }
        }
        return null;
    }

    private int nextPlace(List<PlanScheduleDetail> schedules, int index) {

        for (int next = index + 1; next < schedules.size(); next++) {
            if (planPlaceResolver.requiresPlace(schedules.get(next))) {
                return next;
            }
        }
        return -1;
    }

    private PlanScheduleDetail copy(
            PlanScheduleDetail slot,
            LocalTime start,
            LocalTime end,
            Integer travel,
            CreatePlanAiResponse.RestaurantDetail restaurant
    ) {

        return new PlanScheduleDetail(
                slot.scheduleType(),
                slot.courseType(),
                start,
                end,
                slot.locationName(),
                slot.location(),
                slot.longitude(),
                slot.latitude(),
                slot.imageUrl(),
                slot.thumbNailImageUrl(),
                slot.stayMinutes(),
                travel,
                slot.tags(),
                slot.medication(),
                restaurant,
                slot.candidateId()
        );
    }

    private record Replacement(PlanScheduleDetail meal, Integer outgoing) { }
}
