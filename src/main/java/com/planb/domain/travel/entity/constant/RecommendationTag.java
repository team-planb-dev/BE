package com.planb.domain.travel.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum RecommendationTag implements CodeCommInterface {

    MEAL_TIME_APPLIED(
            "MEAL_TIME_APPLIED",
            "식사시간반영"
    ),

    LOCAL_FOOD(
            "LOCAL_FOOD",
            "지역음식"
    ),

    FOOD_PREFERENCE(
            "FOOD_PREFERENCE",
            "미식취향"
    ),

    MEDICATION_SCHEDULE(
            "MEDICATION_SCHEDULE",
            "복약일정"
    ),

    CARBOHYDRATE_REFERENCE(
            "CARBOHYDRATE_REFERENCE",
            "탄수화물참고"
    ),

    SODIUM_REFERENCE(
            "SODIUM_REFERENCE",
            "나트륨참고"
    ),

    SATURATED_FAT_REFERENCE(
            "SATURATED_FAT_REFERENCE",
            "포화지방참고"
    ),

    ALLERGY_CHECK(
            "ALLERGY_CHECK",
            "알레르기확인"
    ),

    HISTORY_CULTURE(
            "HISTORY_CULTURE",
            "역사문화"
    ),

    NATURAL_SCENERY(
            "NATURAL_SCENERY",
            "자연경관"
    ),

    EXPERIENCE_ACTIVITY(
            "EXPERIENCE_ACTIVITY",
            "체험액티비티"
    ),

    LIGHT_WALK(
            "LIGHT_WALK",
            "가벼운산책"
    ),

    REST_POINT(
            "REST_POINT",
            "휴식포인트"
    ),

    WALKING(
            "WALKING",
            "도보이동"
    ),

    CAR(
            "CAR",
            "차량이동"
    ),

    TRANSIT(
            "TRANSIT",
            "대중교통"
    ),

    MUST_VISIT(
            "MUST_VISIT",
            "꼭가고싶은곳"
    );

    private final String code;
    private final String codeName;

    // 각 장소태그(CourseType)에 맞는 태그만 선택
    public static Set<RecommendationTag> candidates(
            CourseType courseType
    ) {

        return switch (courseType) {

            case RESTAURANT -> Set.of(
                    MEAL_TIME_APPLIED,
                    LOCAL_FOOD,
                    FOOD_PREFERENCE,
                    CARBOHYDRATE_REFERENCE,
                    SODIUM_REFERENCE,
                    SATURATED_FAT_REFERENCE,
                    ALLERGY_CHECK
            );

            case ATTRACTION -> Set.of(
                    HISTORY_CULTURE,
                    NATURAL_SCENERY,
                    EXPERIENCE_ACTIVITY,
                    MUST_VISIT
            );

            case PARK_WALK -> Set.of(
                    LIGHT_WALK,
                    NATURAL_SCENERY
            );

            case CAFE_REST -> Set.of(
                    REST_POINT,
                    FOOD_PREFERENCE,
                    MEAL_TIME_APPLIED
            );

            case MEDICATION -> Set.of(
                    MEDICATION_SCHEDULE
            );

            case TRANSPORTATION -> Set.of(
                    WALKING,
                    CAR,
                    TRANSIT
            );

            case MUST_HAVE -> Set.of(
                    MUST_VISIT
            );

            case LOCAL_FOOD -> Set.of(
                    LOCAL_FOOD,
                    FOOD_PREFERENCE
            );
        };
    }
}