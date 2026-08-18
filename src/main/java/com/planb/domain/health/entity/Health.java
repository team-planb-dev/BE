package com.planb.domain.health.entity;


import com.planb.domain.health.entity.vo.HealthInfo;
import com.planb.domain.health.entity.vo.MealInfo;
import com.planb.domain.user.entity.User;
import com.planb.global.converter.BooleanToYNConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Health {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "health_id")
    private Long id;

    @Column(name = "traveler_name",nullable = false)
    private String travelerName;

    // 민감 정보 조회 동의여부
    @Convert(converter = BooleanToYNConverter.class)
    @Column(
            name = "sensitive_agree",
            nullable = false,
            length = 1)
    private boolean sensitiveAgree;

    // 복약 정보 존재여부
    @Convert(converter = BooleanToYNConverter.class)
    @Column(
            name = "has_medication",
            nullable = false,
            length = 1
    )
    private boolean hasMedication;

    // 건강 정보
    @Embedded
    private HealthInfo healthInfo;

    // 식사 정보
    @Embedded
    private MealInfo mealInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id",nullable = false)
    private User user;

}
