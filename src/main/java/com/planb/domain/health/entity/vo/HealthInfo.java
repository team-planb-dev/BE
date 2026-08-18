package com.planb.domain.health.entity.vo;

import com.planb.domain.health.converter.DiseaseTypeConverter;

import com.planb.domain.health.converter.WalkTypeConverter;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;


@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class HealthInfo {

    // 관리 질환
    @Convert(converter = DiseaseTypeConverter.class)
    @Column(name = "disease_type")
    private DiseaseType diseaseType;

    // 걷는 정도
    @Convert(converter = WalkTypeConverter.class)
    @Column(name = "walk_type")
    private WalkType walkType;


}
