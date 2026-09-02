package com.planb.domain.travel.entity;

import com.planb.domain.travel.converter.RecommendationTagConverter;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "travel_id",
            nullable = false,
            unique = true
    )
    private Travel travel;

    @Column(name = "plan_name")
    private String planName;

    // 일정 전체(모든 PlanSchedule)에 반영된 RecommendationTag 모음
    // AI 생성이 아닌 PlanSchedule.tags 기반 Java 레벨 집계값
    @ElementCollection
    @CollectionTable(
            name = "plan_recommendation_tag",
            joinColumns = @JoinColumn(
                    name = "plan_id"
            )
    )
    @Convert(converter = RecommendationTagConverter.class)
    @Column(name = "tag")
    @Builder.Default
    private Set<RecommendationTag> tags = new HashSet<>();

    // RecommendationTag 집계값 갱신
    public void updateTags(Set<RecommendationTag> tags) {
        this.tags = tags;
    }
}