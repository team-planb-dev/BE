package com.planb.domain.travel.entity;


import jakarta.persistence.*;
import lombok.*;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Builder
public class RestaurantDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "plan_schedule_id",
            nullable = false,
            unique = true
    )
    private PlanSchedule planSchedule;

    // 대표 메뉴
    @Column(name = "menu_name")
    private String menuName;

    // 영양 정보
    @Column(name = "carbohydrate")
    private Double carbohydrate;

    @Column(name = "sodium")
    private Double sodium;

    @Column(name = "fat")
    private Double fat;

    // 식당 정보
    @Column(name = "open_time")
    private String openTime;

    // 주소
    @Column(name = "address")
    private String address;

    // 경도 (위치)
    @Column(name = "longitude")
    private String longitude;

    // 위도 (위치)
    @Column(name = "latitude")
    private String latitude;

    // 식당 이미지
    private String imageUrl;
}