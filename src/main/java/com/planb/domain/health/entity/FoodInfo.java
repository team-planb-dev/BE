package com.planb.domain.health.entity;

import com.planb.domain.health.converter.FoodTypeConverter;
import com.planb.domain.health.entity.constant.FoodType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class FoodInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_id",nullable = false)
    private Health health;

    @Column(name = "food_name")
    private String foodName;

    @Convert(converter = FoodTypeConverter.class)
    @Column(name = "food_type")
    private FoodType foodType;
}
