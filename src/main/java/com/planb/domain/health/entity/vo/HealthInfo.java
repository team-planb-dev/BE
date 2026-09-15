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

    // 선택한 순서를 유지한 목록으로 돌려준다. 화면과 AI 컨텍스트가 순서를 그대로 쓴다.
    public List<DiseaseType> diseaseTypeList() {

        return List.copyOf(diseaseTypes);
    }
}
