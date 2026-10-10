package com.planb.domain.travel.helper;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlaceCandidateContext.Candidate;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.RestaurantDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.MedicationSchedule;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PlanPlaceResolver {

    public Validation validate(
            PlanScheduleDetail slot,
            PlaceCandidateContext candidates,
            Set<String> usedPlaces,
            Set<String> usedMenus
    ) {

        if (slot == null) {
            return Validation.failure("일정 슬롯 없음");
        }

        if (slot.courseType() == null) {
            return Validation.failure(invalidCombinationReason(slot));
        }

        slot = alignCombination(slot);

        if (!validCombination(slot)) {
            return Validation.failure(invalidCombinationReason(slot));
        }

        if (slot.startTime() == null || slot.endTime() == null || slot.stayMinutes() == null
                || slot.stayMinutes() < 0 || requiresPlace(slot) && slot.stayMinutes() == 0 || !slot
                        .endTime()
                        .isAfter(slot.startTime())) {
            return Validation.failure("유효하지 않은 일정 시간");
        }

        if (requiresPlace(slot) && Duration
                .between(
                        slot.startTime(),
                        slot.endTime())
                .toMinutes() != slot.stayMinutes()) {
            return Validation.failure("일정 시간과 체류시간 불일치");
        }

        if (!requiresPlace(slot)) {
            boolean validMedication = slot.courseType() == CourseType.MEDICATION
                    ? slot.medication() != null
                    : slot.medication() == null;

            return blank(slot.locationName()) && blank(slot.location()) && blank(slot.longitude())
                    && blank(slot.latitude()) && slot.restaurantDetail() == null && slot.candidateId() == null
                    && validMedication
                    ? new Validation(slot, null) : Validation.failure("비장소 슬롯의 장소 정보");
        }

        Candidate candidate = candidates.find(slot.candidateId());

        if (candidate == null) {
            return Validation.failure("이번 호출에서 검색하지 않은 candidateId: " + slot.candidateId());
        }

        if (!allowed(slot.courseType(), candidate)) {
            return Validation.failure("장소 유형 불일치: " + candidate.candidateId() + " 원본 유형="
                    + candidate.type() + ", 상세 분류=" + candidate.categoryName() + ", 요청 유형=" + slot.courseType());
        }

        if (blank(candidate.name()) || blank(candidate.address())) {
            return Validation.failure("검색 원본의 장소명/주소 부족");
        }

        boolean coordinatesPresent = !blank(candidate.longitude()) || !blank(candidate.latitude());

        if ((isMeal(slot.courseType()) || coordinatesPresent) && !validCoordinates(candidate)) {
            return Validation.failure("검색 원본의 좌표 누락 또는 유효하지 않은 좌표");
        }

        if (usedPlaces.contains(candidate.candidateId()) || usedPlaces.contains(candidate
                        .name()
                        .strip())) {
            return Validation.failure("이미 사용한 장소: " + candidate.name());
        }

        RestaurantDetail restaurant = slot.restaurantDetail();

        if (isMeal(slot.courseType()) && (restaurant == null || blank(restaurant.menuName())
                || usedMenus.contains(restaurant
                                .menuName()
                                .strip()))) {
            return Validation.failure("음식점 메뉴 누락 또는 중복");
        }

        if (isMeal(slot.courseType()) && candidate.candidateId().startsWith("tour:")) {
            String menuFailure = candidates.restaurantMenuFailure(
                    candidate.candidateId(),
                    restaurant.menuName()
            );

            if (menuFailure != null) {
                return Validation.failure(menuFailure);
            }
        }

        RestaurantDetail canonicalRestaurant = !isMeal(slot.courseType()) ? null : new RestaurantDetail(
                restaurant
                        .menuName()
                        .strip(),
                restaurant.carbohydrate(),
                restaurant.sodium(),
                restaurant.fat(),
                restaurant.openTime(),
                candidate.address(),
                candidate.longitude(),
                candidate.latitude(),
                candidate.imageUrl()
        );

        return new Validation(new PlanScheduleDetail(
                slot.scheduleType(),
                slot.courseType(),
                slot.startTime(),
                slot.endTime(),
                candidate.name(),
                candidate.address(),
                candidate.longitude(),
                candidate.latitude(),
                candidate.imageUrl(),
                candidate.thumbnailUrl(),
                slot.stayMinutes(),
                slot.travelMinutes(),
                slot.tags(),
                null,
                canonicalRestaurant,
                candidate.candidateId()
        ), null);
    }

    public boolean requiresPlace(PlanScheduleDetail slot) {

        return slot.courseType() != CourseType.MEDICATION && slot.courseType() != CourseType.TRANSPORTATION;
    }

    public void track(
            PlanScheduleDetail slot,
            Set<String> places,
            Set<String> menus
    ) {

        if (!requiresPlace(slot)) {
            return;
        }

        if (slot.candidateId() != null) {
            places.add(slot.candidateId());
        }

        places.add(slot
                        .locationName()
                        .strip());

        if (slot.restaurantDetail() != null) {
            menus.add(slot
                            .restaurantDetail()
                            .menuName()
                            .strip());
        }
    }

    /**
     * 저장된 장소의 구조 정합성과 중복 사용 확인
     */
    public Validation verifyExisting(
            GetAiPlanResponse.PlanScheduleDetail old,
            Set<String> places,
            Set<String> menus
    ) {

        PlanScheduleDetail slot = fromExisting(old);

        if (!requiresPlace(slot)) {
            return validate(
                    slot,
                    new PlaceCandidateContext(),
                    places,
                    menus
            );
        }

        if (!validCombination(slot)) {
            return Validation.failure(invalidCombinationReason(slot));
        }

        if (blank(slot.locationName()) || blank(slot.location())) {
            return Validation.failure("기존 장소명 누락");
        }

        if (slot.startTime() == null || slot.endTime() == null || slot.stayMinutes() == null
                || slot.stayMinutes() <= 0 || !slot
                        .endTime()
                        .isAfter(slot.startTime())) {
            return Validation.failure("유효하지 않은 일정 시간");
        }

        if (Duration
                .between(
                        slot.startTime(),
                        slot.endTime())
                .toMinutes() != slot.stayMinutes()) {
            return Validation.failure("일정 시간과 체류시간 불일치");
        }

        if (places.contains(slot
                        .locationName()
                        .strip())) {
            return Validation.failure("이미 사용한 장소: " + slot.locationName());
        }

        RestaurantDetail restaurant = slot.restaurantDetail();

        if (isMeal(slot.courseType()) && (restaurant == null || blank(restaurant.menuName())
                || menus.contains(restaurant
                                .menuName()
                                .strip()))) {
            return Validation.failure("음식점 메뉴 누락 또는 중복");
        }

        return new Validation(slot, null);
    }

    public PlanScheduleDetail select(PlanScheduleDetail original, PlanScheduleDetail choice) {

        if (choice == null) {
            return null;
        }

        return new PlanScheduleDetail(
                original.scheduleType(),
                original.courseType(),
                original.startTime(),
                original.endTime(),
                choice.locationName(),
                choice.location(),
                choice.longitude(),
                choice.latitude(),
                choice.imageUrl(),
                choice.thumbNailImageUrl(),
                original.stayMinutes(),
                null,
                original.tags(),
                original.medication(),
                choice.restaurantDetail(),
                choice.candidateId()
        );
    }

    // 값 변경 없는 저장 일정 슬롯의 AI 응답 형태 변환
    public PlanScheduleDetail fromExisting(GetAiPlanResponse.PlanScheduleDetail old) {

        GetAiPlanResponse.RestaurantDetail r = old.restaurantDetail();

        RestaurantDetail restaurant = r == null ? null : new RestaurantDetail(
                r.menuName(),
                r.carbohydrate(),
                r.sodium(),
                r.fat(),
                r.openTime(),
                r.address(),
                r.longitude(),
                r.latitude(),
                r.imageUrl()
        );

        MedicationSchedule medication = old.medication() == null ? null : new MedicationSchedule(
                old
                        .medication()
                        .intervalMinutes(), old
                        .medication()
                        .description());

        return new PlanScheduleDetail(
                old.scheduleType(),
                old.courseType(),
                old.startTime(),
                old.endTime(),
                old.locationName(),
                old.location(),
                old.longitude(),
                old.latitude(),
                old.imageUrl(),
                old.thumbNailImageUrl(),
                old.stayMinutes(),
                old.travelMinutes(),
                old.tags(),
                medication,
                restaurant
        );
    }

    /**
     * courseType 기준의 일정·장소 유형 정합성 보정
     */
    private PlanScheduleDetail alignCombination(PlanScheduleDetail slot) {

        if (validCombination(slot)) {
            return slot;
        }

        ScheduleType aligned = switch (slot.courseType()) {
            case MEDICATION -> ScheduleType.CHECK_IN;

            case RESTAURANT, LOCAL_FOOD -> mealTypeAt(slot.startTime());

            default -> ScheduleType.ACTIVITY;
        };

        return aligned == null ? slot : withScheduleType(slot, aligned);
    }

    // 시각 기준 식사 구분, 시각이 없으면 교정 제외
    private ScheduleType mealTypeAt(LocalTime startTime) {

        if (startTime == null) {
            return null;
        }

        if (startTime.isBefore(LocalTime.of(11, 0))) {
            return ScheduleType.BREAKFAST;
        }

        return startTime.isBefore(LocalTime.of(16, 0))
                ? ScheduleType.LUNCH
                : ScheduleType.DINNER;
    }

    private PlanScheduleDetail withScheduleType(
            PlanScheduleDetail slot,
            ScheduleType scheduleType
    ) {

        return new PlanScheduleDetail(
                scheduleType,
                slot.courseType(),
                slot.startTime(),
                slot.endTime(),
                slot.locationName(),
                slot.location(),
                slot.longitude(),
                slot.latitude(),
                slot.imageUrl(),
                slot.thumbNailImageUrl(),
                slot.stayMinutes(),
                slot.travelMinutes(),
                slot.tags(),
                slot.medication(),
                slot.restaurantDetail(),
                slot.candidateId()
        );
    }

    // 로그에서 어긋난 조합을 식별하기 위한 실패 사유 기록
    private String invalidCombinationReason(PlanScheduleDetail slot) {

        return "허용되지 않은 scheduleType/courseType 조합: "
                + slot.scheduleType()
                + "/"
                + slot.courseType();
    }

    private boolean validCombination(PlanScheduleDetail slot) {

        return switch (slot.courseType()) {
            case RESTAURANT, LOCAL_FOOD -> slot.scheduleType() == ScheduleType.BREAKFAST
                    || slot.scheduleType() == ScheduleType.LUNCH || slot.scheduleType() == ScheduleType.DINNER;

            case MEDICATION -> slot.scheduleType() == ScheduleType.CHECK_IN;

            default -> slot.scheduleType() == ScheduleType.ACTIVITY;
        };
    }

    private boolean allowed(CourseType type, Candidate candidate) {

        boolean tour = candidate
                .candidateId()
                .startsWith("tour:");

        return switch (type) {
            case ATTRACTION, MUST_HAVE -> Objects.equals(candidate.type(), tour ? "12" : "AT4")
                    || !tour && blank(candidate.type()) && verifiedAttractionCategory(candidate.categoryName());

            case PARK_WALK -> Objects.equals(candidate.type(), tour ? "12" : "AT4")
                    && candidate.categoryName() != null && candidate
                            .categoryName()
                            .contains("공원");

            case CAFE_REST -> !tour && "CE7".equals(candidate.type());

            case RESTAURANT, LOCAL_FOOD -> Objects.equals(candidate.type(), tour ? "39" : "FD6");

            default -> false;
        };
    }

    // 그룹 코드 누락 시 검색 원본의 명시적 관광·문화유적 분류만 허용
    private boolean verifiedAttractionCategory(String categoryName) {

        if (blank(categoryName)) {
            return false;
        }

        Set<String> allowedCategories = Set.of(
                "관광명소",
                "관광,명소",
                "문화유적",
                "고궁,궁",
                "성,성곽",
                "절,사찰"
        );

        return java.util.Arrays
                .stream(categoryName.split(">"))
                .map(String::strip)
                .anyMatch(allowedCategories::contains);
    }

    private boolean isMeal(CourseType type) {

        return type == CourseType.RESTAURANT || type == CourseType.LOCAL_FOOD;
    }

    private boolean validCoordinates(Candidate candidate) {

        try {
            double x = Double.parseDouble(candidate.longitude());

            double y = Double.parseDouble(candidate.latitude());

            return Double.isFinite(x) && Double.isFinite(y) && Math.abs(x) <= 180 && Math.abs(y) <= 90
                    && x != 0 && y != 0;
        } catch (NullPointerException | NumberFormatException exception) {
            return false;
        }
    }

    private static boolean blank(String value) {

        return value == null || value.isBlank();
    }

    public record Validation(PlanScheduleDetail schedule, String reason) {

        public boolean valid() {
            return schedule != null;
        }

        public static Validation failure(String reason) {
            return new Validation(null, reason);
        }
    }
}
