package com.planb.domain.health.entity.vo;

import com.planb.domain.health.converter.WalkTypeConverter;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class HealthInfo {

    // 관리 질환. 당뇨·고혈압·이상지질혈증은 함께 나타나는 경우가 많아 여러 개를 받는다.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "health_disease",
            joinColumns = @JoinColumn(name = "health_id"))
    @Column(name = "disease_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<DiseaseType> diseaseTypes = new LinkedHashSet<>();

    // 걷는 정도
    @Convert(converter = WalkTypeConverter.class)
    @Column(name = "walk_type")
    private WalkType walkType;

    public HealthInfo(
            List<DiseaseType> diseaseTypes,
            WalkType walkType
    ) {

        this.diseaseTypes = diseaseTypes == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(diseaseTypes);

        this.walkType = walkType;
    }

    // 순서는 보장하지 않는다. DB에서 다시 읽으면 저장 순서와 달라질 수 있다.
    // 어떤 질환이 담겼는지만 의미가 있고, 평가와 화면 모두 순서를 쓰지 않는다.
    public List<DiseaseType> diseaseTypeList() {

        return List.copyOf(diseaseTypes);
    }
}
