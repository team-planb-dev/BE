package com.planb.domain.travel.entity;

import com.planb.domain.health.entity.Health;
import jakarta.persistence.*;
import lombok.*;

/**
 * 이번 여행에 참여하는 구성원(Health)과 Travel의 연결.
 *
 * Health는 사용자 계정에 계속 남아 있고 여행마다 선택만 달라지므로
 * Travel과 Health 사이를 별도 관계로 저장한다.
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
