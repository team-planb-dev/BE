package com.planb.domain.travel.dto.request;

import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record CreateTravelRequest(
        @Schema(description = "여행 이름", example = "경주 건강 여행")
        String travelName,

        @Schema(description = "지역 규격에 맞는 시·도 이름", example = "경상북도")
        String locationDo,

        @Schema(description = "여행할 시·군·구 이름", example = "경주시")
        String locationSigungu,

        @Schema(description = "여행 시작일", example = "2026-09-16")
        LocalDate startDate,

        @Schema(description = "여행 기간 코드", example = "ONE_NIGHT_TWO_DAYS")
        DateType dateType,

        @Schema(description = "주요 이동 수단", example = "TRANSIT")
        Transportation transportation,

        @Schema(description = "사용자가 정한 대표 여행 장소 또는 지역", example = "경주")
        String decidedLocation,

        @Schema(description = "사용자가 일정에 포함하기를 원하는 장소 목록")
        List<PlannedPlaceDetail> plannedPlaces,

        @Schema(description = "일정 구성에 적용할 여행 스타일", example = "MATCH_MEAL_TIME")
        TravelStyle travelStyle,

        @Schema(description = "선호 여행 테마", example = "HISTORY")
        TravelTheme travelTheme,

        @Schema(description = "사용자가 직접 선택한 지역 음식", example = "[\"황남빵\"]")
        List<String> localFoods,

        @Schema(description = "지역 음식 추천 API에서 선택한 음식", example = "[\"쌈밥\", \"연잎밥\"]")
        List<String> recommendFoods,

        @Schema(description = "이번 여행에 참여할 동행인 ID 목록입니다. 한 명 이상 필요합니다.", example = "[1, 2]")
        List<Long> healthIds
) {

    // healthIds가 필요 없는 AI 프롬프트 컨텍스트 재구성 등에서 사용하는 축약 생성자
    public CreateTravelRequest(String travelName,
                               String locationDo,
                               String locationSigungu,
                               LocalDate startDate,
                               DateType dateType,
                               Transportation transportation,
                               String decidedLocation,
                               List<PlannedPlaceDetail> plannedPlaces,
                               TravelStyle travelStyle,
                               TravelTheme travelTheme,
                               List<String> localFoods,
                               List<String> recommendFoods){

        this(travelName,
                locationDo,
                locationSigungu,
                startDate,
                dateType,
                transportation,
                decidedLocation,
                plannedPlaces,
                travelStyle,
                travelTheme,
                localFoods,
                recommendFoods,
                List.of());
    }

    // Travel 엔티티와 PlannedPlace 목록으로 원본 CreateTravelRequest 복원 (AI 편집 컨텍스트 재구성용)
    // healthIds는 travel_health에 저장된 값을 그대로 전달해 여행 당시 선택한 구성원을 유지한다.
    public static CreateTravelRequest from(
            Travel travel,
            List<PlannedPlaceDetail> plannedPlaceDetails,
            List<Long> healthIds
    ) {

        return new CreateTravelRequest(
                travel.getTravelName(),
                travel.getLocationDo(),
                travel.getLocationSigungu(),
                travel.getStartDate(),
                travel.getDateType(),
                travel.getTransportation(),
                travel.getDecidedLocation(),
                plannedPlaceDetails,
                travel.getTravelStyle(),
                travel.getTravelTheme(),
                travel.getLocalFoods(),
                travel.getRecommendFoods(),
                healthIds
        );
    }

    public record PlannedPlaceDetail(
            @Schema(description = "장소 이름", example = "불국사")
            String locationName,

            @Schema(description = "장소 주소", example = "경상북도 경주시 불국로 385")
            String location
    ) {

    }
}
