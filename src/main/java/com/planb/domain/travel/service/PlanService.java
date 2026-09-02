package com.planb.domain.travel.service;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.dto.response.RestaurantRecommendResult;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.prompt.AttractionRecommendPrompt;
import com.planb.ai.prompt.CafeRecommendPrompt;
import com.planb.ai.prompt.RestaurantRecommendPrompt;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.entity.constant.NutritionLevel;
import com.planb.domain.travel.entity.constant.NutritionType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PlanService {

    // 실제 근거가 있는 NutritionType만 RecommendationTag로 매핑 (평가는 되지만 대응 태그가 없는 타입 제외)
    private static final Map<NutritionType, RecommendationTag> NUTRITION_REFERENCE_TAGS = Map.of(
            NutritionType.CARBOHYDRATE, RecommendationTag.CARBOHYDRATE_REFERENCE,
            NutritionType.SODIUM, RecommendationTag.SODIUM_REFERENCE,
            NutritionType.SATURATED_FAT, RecommendationTag.SATURATED_FAT_REFERENCE
    );

    /*
    Repository
     */
    private final PlanRepository planRepository;

    /*
    Handler
     */
    private final TravelRecommendHandler travelRecommendHandler;

    /*
    Tool 호출 결과 수집기
     */
    private final NutritionEvaluationCollector nutritionEvaluationCollector;

    public Plan createPlan(CreatePlanRequest createPlanRequest){

        return Plan
                .builder()
                .planName(createPlanRequest
                        .planName())
                .travel(createPlanRequest
                        .travel())
                .build();
    }

    // AI로 사용자 입력 및 정보기반 일정 생성하기
    public CreatePlanAiResponse makePlanByAi(TravelPlanContext travelPlanContext){

        nutritionEvaluationCollector.start();

        CreatePlanAiResponse response =
                travelRecommendHandler.createPlanByAi(travelPlanContext);

        // RESTAURANT 재추천 시 evaluateFoodNutrition이 다시 호출될 수 있으므로
        // nutritionEvaluationCollector.finish()보다 먼저 수행
        CreatePlanAiResponse uniqueResponse =
                ensureUniquePlaces(
                        response,
                        travelPlanContext.createTravelRequest(),
                        travelPlanContext.healthContexts()
                );

        List<NutritionEvaluationCollector.FoodNutritionEvaluation> nutritionEvaluations =
                nutritionEvaluationCollector.finish();

        return applyDeterministicTags(
                uniqueResponse,
                travelPlanContext,
                nutritionEvaluations
        );
    }

    // planDays에 붙은 모든 RecommendationTag를 모아 Plan 전체 태그로 집계하기
    // CreatePlanAiResponse.PlanDayDetail을 EditPlanAiResponse도 재사용하므로 Create/Edit 공통 사용
    public Set<RecommendationTag> aggregateTags(
            List<CreatePlanAiResponse.PlanDayDetail> planDays
    ) {

        return planDays.stream()
                .flatMap(planDay -> planDay.schedules().stream())
                .flatMap(schedule -> nullSafeTags(schedule).stream())
                .collect(Collectors.toSet());
    }

    // 여행 전체 기간 CAFE_REST/RESTAURANT 중복 배치 보정
    // 메인 플랜 생성 프롬프트(STEP 9) 자체검증 대신 Java 레벨 강제 검증
    // 중복 슬롯은 recommendCafe/recommendRestaurant로 재확정, 대체 후보 없으면 슬롯 제거
    private CreatePlanAiResponse ensureUniquePlaces(
            CreatePlanAiResponse response,
            CreateTravelRequest createTravelRequest,
            List<TravelHealthContext> healthContexts
    ) {

        Set<String> usedLocationNames = new HashSet<>();
        Set<String> usedMenuNames = new HashSet<>();

        List<CreatePlanAiResponse.PlanDayDetail> fixedPlanDays =
                response.planDays()
                        .stream()
                        .map(planDay ->
                                fixPlanDay(
                                        planDay,
                                        usedLocationNames,
                                        usedMenuNames,
                                        createTravelRequest,
                                        healthContexts
                                )
                        )
                        .toList();

        return new CreatePlanAiResponse(
                fixedPlanDays
        );
    }

    // 하루치 일정 순회, CAFE_REST/RESTAURANT 중복 보정
    // previousLocation(직전 확정 장소) 순서 의존성 때문에 reduce로 순차 누적
    // 분기는 nested if 없이 작은 메서드로 분리
    private CreatePlanAiResponse.PlanDayDetail fixPlanDay(
            CreatePlanAiResponse.PlanDayDetail planDay,
            Set<String> usedLocationNames,
            Set<String> usedMenuNames,
            CreateTravelRequest createTravelRequest,
            List<TravelHealthContext> healthContexts
    ) {

        DayFixAccumulator result =
                planDay.schedules()
                        .stream()
                        .reduce(
                                DayFixAccumulator.empty(),
                                (accumulator, schedule) ->
                                        accumulator.append(
                                                resolveSchedule(
                                                        schedule,
                                                        accumulator.previousLocation(),
                                                        usedLocationNames,
                                                        usedMenuNames,
                                                        createTravelRequest,
                                                        healthContexts
                                                )
                                        ),
                                // 순차 스트림 전용 reduce, 병렬 병합 로직 미사용
                                (a, b) -> a
                        );

        return new CreatePlanAiResponse.PlanDayDetail(
                planDay.dayNumber(),
                planDay.date(),
                result.schedules()
        );
    }

    // 한 슬롯 확정: CourseType별로 중복 보정 대상인지 분기
    private Optional<CreatePlanAiResponse.PlanScheduleDetail> resolveSchedule(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            String previousLocation,
            Set<String> usedLocationNames,
            Set<String> usedMenuNames,
            CreateTravelRequest createTravelRequest,
            List<TravelHealthContext> healthContexts
    ) {

        if (schedule.courseType() == CourseType.CAFE_REST) {
            return resolveCafe(schedule, previousLocation, usedLocationNames, createTravelRequest);
        }

        if (schedule.courseType() == CourseType.RESTAURANT) {
            return resolveRestaurant(schedule, previousLocation, usedMenuNames, createTravelRequest, healthContexts);
        }

        if (schedule.courseType() == CourseType.ATTRACTION) {
            return resolveAttraction(schedule, previousLocation, usedLocationNames, createTravelRequest);
        }

        return Optional.of(keepSchedule(schedule, usedLocationNames));
    }

    // CAFE_REST 슬롯: 장소명이 이미 사용됐으면 재추천으로 대체
    private Optional<CreatePlanAiResponse.PlanScheduleDetail> resolveCafe(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            String previousLocation,
            Set<String> usedLocationNames,
            CreateTravelRequest createTravelRequest
    ) {

        boolean isDuplicateCafe =
                isBlank(schedule.locationName())
                        || usedLocationNames.contains(schedule.locationName());

        return isDuplicateCafe
                ? replaceCafe(schedule, previousLocation, usedLocationNames, createTravelRequest)
                : Optional.of(keepSchedule(schedule, usedLocationNames));
    }

    // RESTAURANT 슬롯: 메뉴명이 이미 사용됐으면 재추천으로 대체
    private Optional<CreatePlanAiResponse.PlanScheduleDetail> resolveRestaurant(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            String previousLocation,
            Set<String> usedMenuNames,
            CreateTravelRequest createTravelRequest,
            List<TravelHealthContext> healthContexts
    ) {

        String menuName = schedule.restaurantDetail() == null
                ? null
                : schedule.restaurantDetail().menuName();

        boolean isDuplicateMenu = isBlank(menuName) || usedMenuNames.contains(menuName);

        if (!isDuplicateMenu) {
            usedMenuNames.add(menuName);
            return Optional.of(schedule);
        }

        return replaceRestaurant(schedule, previousLocation, usedMenuNames, createTravelRequest, healthContexts);
    }

    // 중복된 RESTAURANT 슬롯을 재추천으로 대체 (대체 후보 없으면 슬롯 미생성)
    private Optional<CreatePlanAiResponse.PlanScheduleDetail> replaceRestaurant(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            String previousLocation,
            Set<String> usedMenuNames,
            CreateTravelRequest createTravelRequest,
            List<TravelHealthContext> healthContexts
    ) {

        List<DiseaseType> diseaseTypes =
                healthContexts.stream()
                        .map(TravelHealthContext::diseaseType)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        RestaurantRecommendResult replacement =
                travelRecommendHandler.recommendRestaurant(
                        new RestaurantRecommendPrompt(
                                createTravelRequest.locationDo(),
                                createTravelRequest.locationSigungu(),
                                previousLocation,
                                createTravelRequest.transportation(),
                                createTravelRequest.localFoods(),
                                createTravelRequest.recommendFoods(),
                                diseaseTypes,
                                List.copyOf(usedMenuNames)
                        )
                );

        if (!replacement.found()) {
            // 대체 음식점 없음, 슬롯 미생성 (기존 CAFE_REST 정책과 동일)
            return Optional.empty();
        }

        String newMenuName = replacement.restaurantDetail() == null
                ? null
                : replacement.restaurantDetail().menuName();

        if (!isBlank(newMenuName)) {
            usedMenuNames.add(newMenuName);
        }

        return Optional.of(
                new CreatePlanAiResponse.PlanScheduleDetail(
                        schedule.scheduleType(),
                        schedule.courseType(),
                        schedule.startTime(),
                        schedule.endTime(),
                        replacement.locationName(),
                        replacement.location(),
                        null,
                        null,
                        null,
                        null,
                        schedule.stayMinutes(),
                        replacement.travelMinutes(),
                        schedule.tags(),
                        schedule.medication(),
                        toRestaurantDetail(replacement.restaurantDetail())
                )
        );
    }

    private CreatePlanAiResponse.RestaurantDetail toRestaurantDetail(
            RestaurantRecommendResult.RestaurantDetailResult result
    ) {

        if (result == null) {
            return null;
        }

        return new CreatePlanAiResponse.RestaurantDetail(
                result.menuName(),
                result.carbohydrate(),
                result.sodium(),
                result.fat(),
                result.openTime(),
                result.address(),
                result.longitude(),
                result.latitude(),
                result.imageUrl()
        );
    }

    // 중복된 CAFE_REST 슬롯을 재추천으로 대체 (대체 후보 없으면 슬롯 미생성)
    private Optional<CreatePlanAiResponse.PlanScheduleDetail> replaceCafe(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            String previousLocation,
            Set<String> usedNames,
            CreateTravelRequest createTravelRequest
    ) {

        PlaceWithRouteResult replacement =
                travelRecommendHandler.recommendCafe(
                        new CafeRecommendPrompt(
                                createTravelRequest.locationDo(),
                                createTravelRequest.locationSigungu(),
                                createTravelRequest.decidedLocation(),
                                previousLocation,
                                createTravelRequest.transportation(),
                                List.copyOf(usedNames)
                        )
                );

        if (!replacement.found()) {
            // 대체 카페 없음, 슬롯 미생성 (기존 프롬프트 정책과 동일)
            return Optional.empty();
        }

        usedNames.add(replacement.placeName());

        return Optional.of(
                new CreatePlanAiResponse.PlanScheduleDetail(
                        schedule.scheduleType(),
                        schedule.courseType(),
                        schedule.startTime(),
                        schedule.endTime(),
                        replacement.placeName(),
                        replacement.address(),
                        replacement.longitude(),
                        replacement.latitude(),
                        null,
                        null,
                        schedule.stayMinutes(),
                        replacement.travelMinutes(),
                        schedule.tags(),
                        schedule.medication(),
                        null
                )
        );
    }

    // ATTRACTION 슬롯: 장소명이 이미 사용됐으면 재추천으로 대체
    private Optional<CreatePlanAiResponse.PlanScheduleDetail> resolveAttraction(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            String previousLocation,
            Set<String> usedLocationNames,
            CreateTravelRequest createTravelRequest
    ) {

        boolean isDuplicateAttraction =
                isBlank(schedule.locationName())
                        || usedLocationNames.contains(schedule.locationName());

        return isDuplicateAttraction
                ? replaceAttraction(schedule, previousLocation, usedLocationNames, createTravelRequest)
                : Optional.of(keepSchedule(schedule, usedLocationNames));
    }

    // 중복된 ATTRACTION 슬롯을 재추천으로 대체 (대체 후보 없으면 슬롯 미생성)
    private Optional<CreatePlanAiResponse.PlanScheduleDetail> replaceAttraction(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            String previousLocation,
            Set<String> usedLocationNames,
            CreateTravelRequest createTravelRequest
    ) {

        PlaceWithRouteResult replacement =
                travelRecommendHandler.recommendAttraction(
                        new AttractionRecommendPrompt(
                                createTravelRequest.locationDo(),
                                createTravelRequest.locationSigungu(),
                                createTravelRequest.decidedLocation(),
                                previousLocation,
                                createTravelRequest.transportation(),
                                List.copyOf(usedLocationNames)
                        )
                );

        if (!replacement.found()) {
            // 대체 관광지 없음, 슬롯 미생성 (기존 CAFE_REST 정책과 동일)
            return Optional.empty();
        }

        usedLocationNames.add(replacement.placeName());

        return Optional.of(
                new CreatePlanAiResponse.PlanScheduleDetail(
                        schedule.scheduleType(),
                        schedule.courseType(),
                        schedule.startTime(),
                        schedule.endTime(),
                        replacement.placeName(),
                        replacement.address(),
                        replacement.longitude(),
                        replacement.latitude(),
                        null,
                        null,
                        schedule.stayMinutes(),
                        replacement.travelMinutes(),
                        schedule.tags(),
                        schedule.medication(),
                        null
                )
        );
    }

    // 중복 아닌 슬롯은 그대로 사용, ATTRACTION/CAFE_REST면 usedNames에 등록
    private CreatePlanAiResponse.PlanScheduleDetail keepSchedule(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            Set<String> usedNames
    ) {

        boolean tracksLocationName =
                schedule.courseType() == CourseType.ATTRACTION
                        || schedule.courseType() == CourseType.CAFE_REST;

        if (tracksLocationName) {
            addIfPresent(usedNames, schedule.locationName());
        }

        return schedule;
    }

    // STEP 6/STEP 9에서 AI 판단 없이 결정 가능한 RecommendationTag를 Java에서 보강
    // 기존 AI 판단 태그는 유지하고, 결정 가능한 태그만 합집합으로 추가
    private CreatePlanAiResponse applyDeterministicTags(
            CreatePlanAiResponse response,
            TravelPlanContext travelPlanContext,
            List<NutritionEvaluationCollector.FoodNutritionEvaluation> nutritionEvaluations
    ) {

        CreateTravelRequest createTravelRequest = travelPlanContext.createTravelRequest();

        Map<String, List<NutritionEvaluationResult>> resultsByFoodName =
                nutritionEvaluationsByFoodName(nutritionEvaluations);

        boolean hasAllergyOrAvoidFood =
                hasAllergyOrAvoidFood(travelPlanContext.healthContexts());

        List<CreatePlanAiResponse.PlanDayDetail> taggedPlanDays =
                response.planDays()
                        .stream()
                        .map(planDay ->
                                new CreatePlanAiResponse.PlanDayDetail(
                                        planDay.dayNumber(),
                                        planDay.date(),
                                        planDay.schedules()
                                                .stream()
                                                .map(schedule ->
                                                        addDeterministicTags(
                                                                schedule,
                                                                createTravelRequest,
                                                                resultsByFoodName,
                                                                hasAllergyOrAvoidFood
                                                        )
                                                )
                                                .toList()
                                )
                        )
                        .toList();

        return new CreatePlanAiResponse(taggedPlanDays);
    }

    // 한 슬롯에 결정 가능한 태그를 계산해 기존 태그와 합집합으로 병합
    private CreatePlanAiResponse.PlanScheduleDetail addDeterministicTags(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            CreateTravelRequest createTravelRequest,
            Map<String, List<NutritionEvaluationResult>> resultsByFoodName,
            boolean hasAllergyOrAvoidFood
    ) {

        Set<RecommendationTag> deterministicTags =
                deterministicTagsFor(
                        schedule,
                        createTravelRequest,
                        resultsByFoodName,
                        hasAllergyOrAvoidFood
                );

        if (deterministicTags.isEmpty()) {
            return schedule;
        }

        Set<RecommendationTag> mergedTags = new HashSet<>(nullSafeTags(schedule));
        mergedTags.addAll(deterministicTags);

        return new CreatePlanAiResponse.PlanScheduleDetail(
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
                schedule.travelMinutes(),
                mergedTags,
                schedule.medication(),
                schedule.restaurantDetail()
        );
    }

    // CourseType별로 결정 가능한 태그 계산을 분기 (그 외 CourseType은 AI 판단 영역이라 빈 집합)
    private Set<RecommendationTag> deterministicTagsFor(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            CreateTravelRequest createTravelRequest,
            Map<String, List<NutritionEvaluationResult>> resultsByFoodName,
            boolean hasAllergyOrAvoidFood
    ) {

        return switch (schedule.courseType()) {
            case MEDICATION -> Set.of(RecommendationTag.MEDICATION_SCHEDULE);
            case TRANSPORTATION -> transportationTag(createTravelRequest.transportation());
            case RESTAURANT -> restaurantTags(
                    schedule.restaurantDetail(),
                    createTravelRequest,
                    resultsByFoodName,
                    hasAllergyOrAvoidFood
            );
            default -> Set.of();
        };
    }

    // 이동수단 태그: WALKING은 Transportation enum에 대응값이 없어 AI 판단 영역으로 남김
    private Set<RecommendationTag> transportationTag(Transportation transportation) {

        return switch (transportation) {
            case CAR -> Set.of(RecommendationTag.CAR);
            case TRANSIT -> Set.of(RecommendationTag.TRANSIT);
        };
    }

    // RESTAURANT 슬롯 전용: 지역음식/영양참고/알레르기확인 태그 결정
    // restaurantDetail이 없는 슬롯(Tool 확정 실패)은 판단 근거가 없어 빈 집합
    private Set<RecommendationTag> restaurantTags(
            CreatePlanAiResponse.RestaurantDetail restaurantDetail,
            CreateTravelRequest createTravelRequest,
            Map<String, List<NutritionEvaluationResult>> resultsByFoodName,
            boolean hasAllergyOrAvoidFood
    ) {

        if (restaurantDetail == null || isBlank(restaurantDetail.menuName())) {
            return Set.of();
        }

        Set<RecommendationTag> tags = new HashSet<>();

        if (matchesLocalFood(restaurantDetail.menuName(), createTravelRequest)) {
            tags.add(RecommendationTag.LOCAL_FOOD);
        }

        tags.addAll(
                nutritionReferenceTags(
                        restaurantDetail.menuName(),
                        resultsByFoodName
                )
        );

        if (hasAllergyOrAvoidFood) {
            tags.add(RecommendationTag.ALLERGY_CHECK);
        }

        return tags;
    }

    // 여행 요청의 지역음식 후보(localFoods, recommendFoods)와 실제 메뉴명 대조
    private boolean matchesLocalFood(String menuName, CreateTravelRequest createTravelRequest) {

        return Stream.concat(
                        createTravelRequest.localFoods().stream(),
                        createTravelRequest.recommendFoods().stream()
                )
                .filter(food -> !isBlank(food))
                .anyMatch(food -> menuName.contains(food) || food.contains(menuName));
    }

    // 실제 메뉴 기준 영양평가 결과(CHECK/HIGH)를 참고 태그로 변환
    // 같은 메뉴를 여러 여행자 질환 기준으로 평가했다면 결과를 모두 반영
    private Set<RecommendationTag> nutritionReferenceTags(
            String menuName,
            Map<String, List<NutritionEvaluationResult>> resultsByFoodName
    ) {

        return resultsByFoodName
                .getOrDefault(menuName, List.of())
                .stream()
                .filter(result -> result.status() == NutritionEvaluationStatus.AVAILABLE)
                .flatMap(result -> result.evaluations().stream())
                .filter(detail -> detail.nutritionLevel() != NutritionLevel.LOW)
                .map(detail -> NUTRITION_REFERENCE_TAGS.get(detail.nutritionType()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // 여행자 중 알레르기/기피 음식이 등록된 사람이 있는지 확인
    private boolean hasAllergyOrAvoidFood(List<TravelHealthContext> healthContexts) {

        return healthContexts.stream()
                .flatMap(healthContext -> healthContext.foodInfos().stream())
                .anyMatch(foodInfo ->
                        foodInfo.foodType() == FoodType.ALLERGY
                                || foodInfo.foodType() == FoodType.AVOID
                );
    }

    // evaluateFoodNutrition Tool 호출 기록을 메뉴명 기준으로 재구성
    private Map<String, List<NutritionEvaluationResult>> nutritionEvaluationsByFoodName(
            List<NutritionEvaluationCollector.FoodNutritionEvaluation> nutritionEvaluations
    ) {

        return nutritionEvaluations.stream()
                .collect(
                        Collectors.groupingBy(
                                NutritionEvaluationCollector.FoodNutritionEvaluation::foodName,
                                Collectors.mapping(
                                        NutritionEvaluationCollector.FoodNutritionEvaluation::result,
                                        Collectors.toList()
                                )
                        )
                );
    }

    private static void addIfPresent(Set<String> usedNames, String name) {
        if (!isBlank(name)) {
            usedNames.add(name);
        }
    }

    // AI 응답의 tags가 null인 경우(빈 배열 대신 null을 반환한 경우)를 대비한 안전 접근
    private static Set<RecommendationTag> nullSafeTags(
            CreatePlanAiResponse.PlanScheduleDetail schedule
    ) {
        return schedule.tags() == null ? Set.of() : schedule.tags();
    }

    private static String firstNonBlank(String candidate, String fallback) {
        return isBlank(candidate) ? fallback : candidate;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // fixPlanDay의 reduce 누적값: 확정된 슬롯 목록 + 직전 확정 장소
    // schedules는 append마다 복사하지 않고 같은 리스트에 이어붙이는 방식
    private record DayFixAccumulator(
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules,
            String previousLocation
    ) {

        static DayFixAccumulator empty() {
            return new DayFixAccumulator(new ArrayList<>(), "");
        }

        DayFixAccumulator append(
                Optional<CreatePlanAiResponse.PlanScheduleDetail> resolved
        ) {

            if (resolved.isEmpty()) {
                return this;
            }

            CreatePlanAiResponse.PlanScheduleDetail schedule = resolved.get();
            schedules.add(schedule);

            // MEDICATION은 실제 이동 지점 아님, previousLocation 갱신 제외
            String nextPreviousLocation =
                    schedule.courseType() == CourseType.MEDICATION
                            ? previousLocation
                            : firstNonBlank(schedule.locationName(), previousLocation);

            return new DayFixAccumulator(schedules, nextPreviousLocation);
        }
    }

    /*
    기본 CRUD 모음
     */
    public void savePlan(Plan plan){
        planRepository.save(plan);
    }
}
