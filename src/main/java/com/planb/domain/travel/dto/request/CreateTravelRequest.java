package com.planb.domain.travel.dto.request;

import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;

import java.time.LocalDate;
import java.util.List;

public record CreateTravelRequest(String travelName, // 여행이름
                                  String locationDo, // 여행 위치 (도,특별시,광역시)
                                  String locationSigungu, // 여행 위치 (
                                  LocalDate startDate, // 시작일
                                  DateType dateType, // 날짜 타입
                                  Transportation transportation, // 교통수단
                                  String decidedLocation, // 정해진 위치
                                  List<PlannedPlaceDetail> plannedPlaces, // 사용자가 미리 선택한 장소
                                  TravelStyle travelStyle, // 여행 스타일
                                  TravelTheme travelTheme, // 여행 테마
                                  List<String> localFoods, // 기입받은 지역 음식
                                  List<String> recommendFoods){ // AI 추천 지역음식

    public record PlannedPlaceDetail
            (String locationName,
             String location) {

    }
}
