package com.planb.ai.handler;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanDayDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.policy.AttractionTagPolicy;
import com.planb.domain.travel.policy.MealSlotPolicy;
import com.planb.domain.travel.policy.TouristPlaceCountPolicy;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 검색 후보를 이용한 관광·식사 누락 슬롯 보충
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissingSlotCompleter {

    private static final int FILLED_STAY_MINUTES = 60;

    // 관광지 보충용 최소 간격, 실제 시간은 이후 정규화에서 확정
    private static final int FILLED_GAP_MINUTES = 20;

    private static final LocalTime DEFAULT_DAY_START = LocalTime.of(9, 0);

    private final TourismTool tourismTool;

    /**
     * 이번 호출의 후보를 이용한 누락 슬롯 보충
     */
    public CreatePlanAiResponse complete(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            Set<String> usedMenus
    ) {

        return complete(
                response,
                healthContexts,
                candidates,
                usedNames,
                usedMenus,
                Set.of()
        );
    }

    public CreatePlanAiResponse complete(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            Set<String> usedMenus,
            Set<Integer> densityReductionDays
    ) {

        int totalDays = response == null || response.planDays() == null
                ? 0
                : response
                        .planDays()
                        .size();

        return complete(
                response,
                healthContexts,
                candidates,
                usedNames,
                usedMenus,
                densityReductionDays,
                totalDays
        );
    }

    public CreatePlanAiResponse complete(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            Set<String> usedMenus,
            Set<Integer> densityReductionDays,
            int totalDays
    ) {

        if (response == null || response.planDays() == null) {
            return response;
        }

        Map<PlanScheduleDetail, PlanScheduleDetail> previousPlaces =
                previousPlaces(response);

        // 출발 장소 변경 감지를 위해 원본 직전 장소 기록 후 지정 장소 교체
        response = pinPlannedPlaces(
                response,
                candidates
        );

        // 여행 전체의 장소 중복 선택 방지
        // 응답 안팎에서 사용한 장소명 통합
        Set<String> selectedNames = merged(
                usedNames(response),
                usedNames
        );

        // 메뉴도 같은 이유로 본다. 식당 이름이 달라도 대표메뉴가 겹치면
        // 중복 메뉴로 인한 동일 음식 재추천 방지
        Set<String> selectedMenus = merged(
                usedMenus(response),
                usedMenus
        );

        List<CreatePlanAiResponse.PlanDayDetail> requiredMealDays = new ArrayList<>();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            requiredMealDays.add(
                    fillDay(
                            day,
                            healthContexts,
                            candidates,
                            selectedNames,
                            selectedMenus,
                            totalDays,
                            true,
                            day != null
                                    && densityReductionDays != null
                                    && densityReductionDays.contains(day.dayNumber())
                    )
            );
        }

        List<CreatePlanAiResponse.PlanDayDetail> days = new ArrayList<>();

        for (CreatePlanAiResponse.PlanDayDetail day : requiredMealDays) {
            days.add(
                    fillDay(
                            day,
                            healthContexts,
                            candidates,
                            selectedNames,
                            selectedMenus,
                            totalDays,
                            false,
                            day != null
                                    && densityReductionDays != null
                                    && densityReductionDays.contains(day.dayNumber())
                    )
            );
        }

        return invalidateChangedTravelMinutes(
                new CreatePlanAiResponse(days),
                previousPlaces
        );
    }

    /**
     * 고정한 지정 장소가 응답에 없으면 날짜 순서대로 그날 첫 관광지를 지정 장소로 교체
     * 시각·체류시간 유지, 관광 장소 개수 불변, 이동시간은 후속 단계가 재조회
     */
    private CreatePlanAiResponse pinPlannedPlaces(
            CreatePlanAiResponse response,
            PlaceCandidateContext candidates
    ) {

        if (candidates == null || response.planDays().isEmpty()) {
            return response;
        }

        Set<String> usedIds = new HashSet<>();
        Set<String> usedNames = usedNames(response);

        response
                .planDays()
                .stream()
                .filter(Objects::nonNull)
                .filter(day -> day.schedules() != null)
                .flatMap(day -> day
                        .schedules()
                        .stream())
                .filter(Objects::nonNull)
                .map(PlanScheduleDetail::candidateId)
                .filter(Objects::nonNull)
                .forEach(usedIds::add);

        List<PlaceCandidateContext.Candidate> missing = candidates
                .pinnedCandidates()
                .stream()
                .filter(candidate -> !usedIds.contains(candidate.candidateId())
                        && !usedNames.contains(normalized(candidate.name())))
                .toList();

        if (missing.isEmpty()) {
            return response;
        }

        List<CreatePlanAiResponse.PlanDayDetail> days = new ArrayList<>(response.planDays());

        for (int index = 0; index < missing.size(); index++) {
            int dayIndex = index % days.size();
            CreatePlanAiResponse.PlanDayDetail day = days.get(dayIndex);

            if (day == null || day.schedules() == null) {
                continue;
            }

            List<PlanScheduleDetail> schedules = new ArrayList<>(day.schedules());

            for (int slotIndex = 0; slotIndex < schedules.size(); slotIndex++) {
                PlanScheduleDetail slot = schedules.get(slotIndex);

                if (slot == null || slot.courseType() != CourseType.ATTRACTION) {
                    continue;
                }

                schedules.set(
                        slotIndex,
                        plannedSlot(
                                slot,
                                missing.get(index)
                        )
                );

                log.info(
                        "[SLOT FILL] 지정 장소 교체 - day: {}, replaced: {}, planned: {}",
                        day.dayNumber(),
                        slot.locationName(),
                        missing
                                .get(index)
                                .name()
                );

                break;
            }

            days.set(
                    dayIndex,
                    new CreatePlanAiResponse.PlanDayDetail(
                            day.dayNumber(),
                            day.date(),
                            schedules
                    )
            );
        }

        return new CreatePlanAiResponse(days);
    }

    private PlanScheduleDetail plannedSlot(
            PlanScheduleDetail slot,
            PlaceCandidateContext.Candidate candidate
    ) {

        return new PlanScheduleDetail(
                slot.scheduleType(),
                CourseType.MUST_HAVE,
                slot.startTime(),
                slot.endTime(),
                candidate.name(),
                candidate.address(),
                candidate.longitude(),
                candidate.latitude(),
                candidate.imageUrl(),
                candidate.thumbnailUrl(),
                slot.stayMinutes(),
                null,
                Set.of(RecommendationTag.MUST_VISIT),
                null,
                null,
                candidate.candidateId()
        );
    }

    /**
     * 출발 장소가 바뀐 슬롯의 이동시간 무효화
     */
    private CreatePlanAiResponse invalidateChangedTravelMinutes(
            CreatePlanAiResponse response,
            Map<PlanScheduleDetail, PlanScheduleDetail> previousPlaces
    ) {

        List<PlanDayDetail> days = new ArrayList<>();
        PlanScheduleDetail previousPlace = null;

        for (PlanDayDetail day : response.planDays()) {
            if (day == null || day.schedules() == null) {
                days.add(day);

                continue;
            }

            List<PlanScheduleDetail> schedules = new ArrayList<>();

            for (PlanScheduleDetail schedule : day.schedules()) {
                PlanScheduleDetail completed = schedule;

                if (requiresPlace(schedule)) {
                    if (previousPlaces.containsKey(schedule)
                            && previousPlaces.get(schedule) != previousPlace) {
                        completed = withTravelMinutes(schedule, null);
                    }

                    previousPlace = schedule;
                }

                schedules.add(completed);
            }

            days.add(
                    new PlanDayDetail(
                            day.dayNumber(),
                            day.date(),
                            schedules
                    )
            );
        }

        return new CreatePlanAiResponse(days);
    }

    private Map<PlanScheduleDetail, PlanScheduleDetail> previousPlaces(
            CreatePlanAiResponse response
    ) {

        Map<PlanScheduleDetail, PlanScheduleDetail> previousPlaces =
                new IdentityHashMap<>();

        PlanScheduleDetail previousPlace = null;

        for (PlanDayDetail day : response.planDays()) {
            if (day == null || day.schedules() == null) {
                continue;
            }

            for (PlanScheduleDetail schedule : day.schedules()) {
                if (!requiresPlace(schedule)) {
                    continue;
                }

                previousPlaces.put(schedule, previousPlace);
                previousPlace = schedule;
            }
        }

        return previousPlaces;
    }

    private boolean requiresPlace(PlanScheduleDetail schedule) {

        return schedule != null
                && schedule.courseType() != CourseType.MEDICATION
                && schedule.courseType() != CourseType.TRANSPORTATION;
    }

    private PlanScheduleDetail withTravelMinutes(
            PlanScheduleDetail schedule,
            Integer travelMinutes
    ) {

        return new PlanScheduleDetail(
                schedule.scheduleType(),
                schedule.courseType(),
                schedule.startTime(),
                schedule.endTime(),
                schedule.locationName(),
                schedule.location(),
                schedule.longitude(),
                schedule.latitude(),
                schedule.imageUrl(),
                schedule.thumbNailImageUrl(),
                schedule.stayMinutes(),
                travelMinutes,
                schedule.tags(),
                schedule.medication(),
                schedule.restaurantDetail(),
                schedule.candidateId()
        );
    }

    private Set<String> merged(
            Set<String> fromResponse,
            Set<String> fromCaller
    ) {

        if (fromCaller != null) {
            fromCaller
                    .stream()
                    .map(MissingSlotCompleter::normalized)
                    .filter(Objects::nonNull)
                    .forEach(fromResponse::add);
        }

        return fromResponse;
    }

    private CreatePlanAiResponse.PlanDayDetail fillDay(
            CreatePlanAiResponse.PlanDayDetail day,
            List<TravelHealthContext> healthContexts,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            Set<String> usedMenus,
            int totalDays,
            boolean requiredMealPhase,
            boolean densityReductionAllowed
    ) {

        if (day == null || day.schedules() == null) {
            return day;
        }

        List<CreatePlanAiResponse.PlanScheduleDetail> schedules =
                new ArrayList<>(day.schedules());

        addTouristPlaces(
                schedules,
                healthContexts,
                candidates,
                usedNames,
                densityReductionAllowed
        );

        addMealSlots(
                day,
                schedules,
                healthContexts,
                candidates,
                usedNames,
                usedMenus,
                totalDays,
                requiredMealPhase
        );

        schedules.sort(
                Comparator.comparing(
                        CreatePlanAiResponse.PlanScheduleDetail::startTime,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
        );

        return new CreatePlanAiResponse.PlanDayDetail(
                day.dayNumber(),
                day.date(),
                schedules
        );
    }

    private void addTouristPlaces(
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules,
            List<TravelHealthContext> healthContexts,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            boolean densityReductionAllowed
    ) {

        int expectedCount = TouristPlaceCountPolicy.minimumCount(
                healthContexts,
                densityReductionAllowed
        );

        long actualCount = schedules
                .stream()
                .filter(Objects::nonNull)
                .filter(schedule -> schedule.courseType() == CourseType.ATTRACTION
                        || schedule.courseType() == CourseType.MUST_HAVE)
                .count();

        for (long missing = expectedCount - actualCount; missing > 0; missing--) {
            PlaceCandidateContext.Candidate candidate = nearestUnused(
                    candidates.attractionCandidates(),
                    lastPlace(schedules),
                    usedNames
            );

            if (candidate == null) {
                return;
            }

            LocalTime startTime = nextStartTime(schedules);

            schedules.add(
                    placeSlot(
                            ScheduleType.ACTIVITY,
                            CourseType.ATTRACTION,
                            candidate,
                            startTime,
                            null,
                            // Java가 보충한 슬롯의 태그 결정
                            AttractionTagPolicy.tagsOf(candidate.categoryCode())
                    )
            );

            usedNames.add(normalized(candidate.name()));

            log.info(
                    "[SLOT FILL] 관광 슬롯 보정 - locationName: {}, startTime: {}",
                    candidate.name(),
                    startTime
            );
        }
    }

    private void addMealSlots(
            CreatePlanAiResponse.PlanDayDetail day,
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules,
            List<TravelHealthContext> healthContexts,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            Set<String> usedMenus,
            int totalDays,
            boolean requiredMealPhase
    ) {

        List<ScheduleType> missingMeals = new ArrayList<>(
                MealSlotPolicy.missingMeals(
                        new CreatePlanAiResponse.PlanDayDetail(
                                day.dayNumber(),
                                day.date(),
                                schedules
                        ),
                        healthContexts
                )
        );

        Set<ScheduleType> requiredMeals = new HashSet<>(
                MealSlotPolicy.requiredMissingMeals(
                        new CreatePlanAiResponse.PlanDayDetail(
                                day.dayNumber(),
                                day.date(),
                                schedules
                        ),
                        healthContexts,
                        totalDays
                )
        );

        missingMeals.removeIf(mealType -> requiredMealPhase
                ? !requiredMeals.contains(mealType)
                : requiredMeals.contains(mealType));

        for (ScheduleType mealType : missingMeals) {
            LocalTime mealTime = MealSlotPolicy.configuredMealTime(mealType, healthContexts);

            if (mealTime == null) {
                continue;
            }

            CreatePlanAiResponse.PlanScheduleDetail mealSlot = mealSlot(
                    mealType,
                    mealTime,
                    candidates,
                    usedNames,
                    usedMenus
            );

            if (mealSlot == null) {

                // 후보 보충 포기 시 후속 검증 실패
                // 후보 보충 실패 사유의 로그 보존
                log.info(
                        "[SLOT FILL] 식사 슬롯 보정 실패 - scheduleType: {}, 검색 음식점 후보 수: {}",
                        mealType,
                        candidates
                                .restaurantCandidates()
                                .size()
                );

                return;
            }

            schedules.add(mealSlot);

            usedNames.add(normalized(mealSlot.locationName()));

            usedMenus.add(
                    normalized(
                            mealSlot
                                    .restaurantDetail()
                                    .menuName()
                    )
            );

            log.info(
                    "[SLOT FILL] 식사 슬롯 보정 - scheduleType: {}, locationName: {}, startTime: {}",
                    mealType,
                    mealSlot.locationName(),
                    mealTime
            );
        }
    }

    /**
     * TourAPI 대표메뉴가 확인된 후보만 이용한 식사 슬롯 생성
     */
    private CreatePlanAiResponse.PlanScheduleDetail mealSlot(
            ScheduleType mealType,
            LocalTime mealTime,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            Set<String> usedMenus
    ) {

        for (PlaceCandidateContext.Candidate candidate : candidates.restaurantCandidates()) {
            MealCandidateSelection selection = mealCandidate(
                    candidate,
                    candidates,
                    usedNames,
                    usedMenus
            );

            if (selection == null) {
                continue;
            }

            // 메뉴·영양 정보 확정 후 식사 태그 계산
            return placeSlot(
                    mealType,
                    CourseType.RESTAURANT,
                    selection.candidate(),
                    mealTime,
                    new CreatePlanAiResponse.RestaurantDetail(
                            selection.menuName(),
                            null,
                            null,
                            null,
                            null,
                            selection
                                    .candidate()
                                    .address(),
                            selection
                                    .candidate()
                                    .longitude(),
                            selection
                                    .candidate()
                                    .latitude(),
                            selection
                                    .candidate()
                                    .imageUrl()
                    ),
                    Set.of()
            );
        }

        return null;
    }

    private MealCandidateSelection mealCandidate(
            PlaceCandidateContext.Candidate candidate,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            Set<String> usedMenus
    ) {

        if (usedNames.contains(normalized(candidate.name()))) {
            return null;
        }

        String menuName;

        // 조회 성공 결과만 요청 범위 메모이즈, 일시 실패는 다음 보정에서 재시도
        try {
            menuName = candidates.representativeMenu(
                    candidate.candidateId(),
                    () -> representativeMenu(candidate)
            );
        } catch (RuntimeException failure) {
            log.info(
                    "[SLOT FILL] 음식점 상세 조회 실패로 후보 제외 - locationName: {}, 원인: {}",
                    candidate.name(),
                    failure.toString()
            );

            return null;
        }

        if (menuName == null || usedMenus.contains(normalized(menuName))) {
            return null;
        }

        return new MealCandidateSelection(candidate, menuName);
    }

    private String representativeMenu(PlaceCandidateContext.Candidate candidate) {

        Kor2RestaurantIntroResponse intro = tourismTool.getRestaurantDetail(
                PlaceCandidateContext.contentId(candidate.candidateId())
        );

        if (intro == null
                || intro.response() == null
                || intro
                        .response()
                        .body() == null
                || intro
                        .response()
                        .body()
                        .items() == null
                || intro
                        .response()
                        .body()
                        .items()
                        .item() == null) {
            return null;
        }

        return intro
                .response()
                .body()
                .items()
                .item()
                .stream()
                .filter(Objects::nonNull)
                .map(Kor2RestaurantIntroResponse.Item::firstmenu)
                .filter(menu -> menu != null && !menu.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * 직전 장소 기준의 가장 가까운 미사용 후보 선택
     * 출발 좌표가 없으면 첫 번째 미사용 후보 선택
     */
    private PlaceCandidateContext.Candidate nearestUnused(
            List<PlaceCandidateContext.Candidate> pool,
            CreatePlanAiResponse.PlanScheduleDetail lastPlace,
            Set<String> usedNames
    ) {

        List<PlaceCandidateContext.Candidate> unused = pool
                .stream()
                .filter(candidate -> candidate.name() != null)
                .filter(candidate -> !usedNames.contains(normalized(candidate.name())))
                .toList();

        if (unused.isEmpty()) {
            return null;
        }

        Double originX = number(lastPlace == null ? null : lastPlace.longitude());
        Double originY = number(lastPlace == null ? null : lastPlace.latitude());

        if (originX == null || originY == null) {
            return unused.getFirst();
        }

        return unused
                .stream()
                .min(Comparator.comparingDouble(candidate -> squaredDistance(
                                candidate,
                                originX,
                                originY
                        )))
                .orElse(null);
    }

    // 거리 순서 비교를 위한 제곱거리 사용
    private double squaredDistance(
            PlaceCandidateContext.Candidate candidate,
            double originX,
            double originY
    ) {

        Double x = number(candidate.longitude());
        Double y = number(candidate.latitude());

        if (x == null || y == null) {
            return Double.MAX_VALUE;
        }

        return Math.pow(x - originX, 2) + Math.pow(y - originY, 2);
    }

    private CreatePlanAiResponse.PlanScheduleDetail placeSlot(
            ScheduleType scheduleType,
            CourseType courseType,
            PlaceCandidateContext.Candidate candidate,
            LocalTime startTime,
            CreatePlanAiResponse.RestaurantDetail restaurantDetail,
            Set<RecommendationTag> tags
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                scheduleType,
                courseType,
                startTime,
                startTime.plusMinutes(FILLED_STAY_MINUTES),
                candidate.name(),
                candidate.address(),
                candidate.longitude(),
                candidate.latitude(),
                candidate.imageUrl(),
                candidate.thumbnailUrl(),
                FILLED_STAY_MINUTES,
                // 확정 좌표 기반 이동시간 후속 조회
                null,
                tags,
                null,
                restaurantDetail,
                candidate.candidateId()
        );
    }

    /**
     * 하루의 빈 시간대에 들어갈 슬롯 시작시각
     */
    private LocalTime nextStartTime(
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules
    ) {

        List<CreatePlanAiResponse.PlanScheduleDetail> ordered = schedules
                .stream()
                .filter(Objects::nonNull)
                .filter(schedule -> schedule.startTime() != null)
                .sorted(Comparator.comparing(CreatePlanAiResponse.PlanScheduleDetail::startTime))
                .toList();

        if (ordered.isEmpty()) {
            return DEFAULT_DAY_START;
        }

        int neededMinutes = FILLED_STAY_MINUTES + FILLED_GAP_MINUTES * 2;

        for (int index = 0; index < ordered.size() - 1; index++) {
            LocalTime previousEnd = endOf(ordered.get(index));

            LocalTime nextStart = ordered
                    .get(index + 1)
                    .startTime();

            if (Duration
                    .between(previousEnd, nextStart)
                    .toMinutes() >= neededMinutes) {
                return previousEnd.plusMinutes(FILLED_GAP_MINUTES);
            }
        }

        return endOf(ordered.getLast())
                .plusMinutes(FILLED_GAP_MINUTES);
    }

    private LocalTime endOf(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return schedule.endTime() == null
                ? schedule.startTime()
                : schedule.endTime();
    }

    private CreatePlanAiResponse.PlanScheduleDetail lastPlace(
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules
    ) {

        return schedules
                .stream()
                .filter(Objects::nonNull)
                .filter(schedule -> schedule.longitude() != null
                        && schedule.latitude() != null)
                .filter(schedule -> schedule.startTime() != null)
                .max(Comparator.comparing(CreatePlanAiResponse.PlanScheduleDetail::startTime))
                .orElse(null);
    }

    // PlanPlaceResolver와 동일한 장소명 strip 비교 기준
    private static String normalized(String name) {

        if (name == null) {
            return null;
        }

        String stripped = name.strip();

        return stripped.isEmpty() ? null : stripped;
    }

    private Set<String> usedMenus(CreatePlanAiResponse response) {

        Set<String> menus = new HashSet<>();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            if (day == null || day.schedules() == null) {
                continue;
            }

            day
                    .schedules()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(CreatePlanAiResponse.PlanScheduleDetail::restaurantDetail)
                    .filter(Objects::nonNull)
                    .map(CreatePlanAiResponse.RestaurantDetail::menuName)
                    .map(MissingSlotCompleter::normalized)
                    .filter(Objects::nonNull)
                    .forEach(menus::add);
        }

        return menus;
    }

    private Set<String> usedNames(CreatePlanAiResponse response) {

        Set<String> names = new HashSet<>();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            if (day == null || day.schedules() == null) {
                continue;
            }

            day
                    .schedules()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                    .map(MissingSlotCompleter::normalized)
                    .filter(Objects::nonNull)
                    .forEach(names::add);
        }

        return names;
    }

    private Double number(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private record MealCandidateSelection(
            PlaceCandidateContext.Candidate candidate,
            String menuName
    ) { }
}
