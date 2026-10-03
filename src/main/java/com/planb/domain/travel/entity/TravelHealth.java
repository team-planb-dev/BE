package com.planb.domain.travel.entity;

import com.planb.domain.health.entity.Health;
import jakarta.persistence.*;
import lombok.*;

/**
 * 여행별 참여 구성원과 여행의 연결
 */
@Entity
@Table(
        name = "travel_health",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_travel_health",
                columnNames = {"travel_id", "health_id"}
        )
)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Getter
public class TravelHealth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "travel_health_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "travel_id",
            nullable = false
    )
    private Travel travel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "health_id",
            nullable = false
    )
    private Health health;
}
