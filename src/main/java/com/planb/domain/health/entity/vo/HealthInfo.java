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

    // 당뇨·고혈압·이상지질혈증의 동시 관리를 위한 질환 목록
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

    // DB 재조회 시 저장 순서를 보장하지 않는 질환 목록
    // 평가·화면에서 순서와 무관한 질환 포함 여부
    public List<DiseaseType> diseaseTypeList() {

        return List.copyOf(diseaseTypes);
    }
}
