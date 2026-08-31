package com.planb.domain.travel.entity;

import com.planb.domain.travel.converter.DateTypeConverter;
import com.planb.domain.travel.converter.TransportationConverter;
import com.planb.domain.travel.converter.TravelStyleConverter;
import com.planb.domain.travel.converter.TravelThemeConverter;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "travel")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Travel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "travel_id")
    private Long id;

    // 여행 이름
    @Column(name = "travel_name")
    private String travelName;

    // 여행위치 (도,특별시,광역시)
    @Column(name = "location_do")
    private String locationDo;

    // 여행위치 (시군구)
    @Column(name = "location_sigungu")
    private String locationSigungu;

    // 여행일자 (시작)
    @Column(name = "start_date")
    private LocalDate startDate;

    // 여행일자 (끝)
    @Column(name = "end_date")
    private LocalDate endDate;

    // 여행 일자 타입
    @Convert(converter = DateTypeConverter.class)
    @Column(name = "date_type")
    private DateType dateType;

    // 교통 수단
    @Convert(converter = TransportationConverter.class)
    @Column(name = "transportation")
    private Transportation transportation;


    // 여행 스타일
    @Convert(converter = TravelStyleConverter.class)
    @Column(name = "travel_style")
    private TravelStyle travelStyle;

    // 여행 테마
    @Convert(converter = TravelThemeConverter.class)
    @Column(name = "travel_theme")
    private TravelTheme travelTheme;

    // 지역 음식
    @ElementCollection
    @CollectionTable(
            name = "travel_local_food",
            joinColumns = @JoinColumn(name = "travel_id")
    )
    @Column(
            name = "local_food",
            nullable = false
    )
    private List<String> localFoods;

    // AI 키워드 추천
    @ElementCollection
    @CollectionTable(
            name = "travel_recommend_food",
            joinColumns = @JoinColumn(name = "travel_id")
    )
    @Column(name = "food_name")
    private List<String> recommendFoods;

    // 미리 정해진 장소
    @Column(name = "decided_location")
    private String decidedLocation;


}
