package com.planb.domain.health.entity;

import com.planb.domain.health.converter.MedicationBasisConverter;
import com.planb.domain.health.entity.constant.MedicationBasis;


import com.planb.domain.health.entity.vo.MealMedicationRule;
import jakarta.persistence.*;

import lombok.*;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "medication_info")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class MedicationInfo {

    public MedicationInfo(
            String drugName,
            LocalTime medicationTime,
            MedicationBasis medicationBasis
    ) {
        this.drugName = resolveDrugName(drugName);
        this.medicationTime = medicationTime;
        this.medicationBasis = medicationBasis;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medication_info_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_id",nullable = false)
    private Health health;


    // 약 이름
    @Column(name = "drug_name")
    private String drugName;

    // 복약 방법
    @Convert(converter = MedicationBasisConverter.class)
    @Column(name = "medication_basis")
    private MedicationBasis medicationBasis;

    /*
     특정 시간대 복약일 경우
     */

    // 복약 시각
    @Column(name = "medication_time")
    private LocalTime medicationTime;

    /*
    식사 기준 사용 시
     */

    // 복약 시기 (아침,점심,저녁 - 복약 타이밍)
    @ElementCollection
    @CollectionTable(
            name = "medication_meal_rule",
            joinColumns = @JoinColumn(name = "medication_info_id")
    )
    private Set<MealMedicationRule> mealMedicationRules;



    /*
     내부 헬퍼 메소드
    */
    private String resolveDrugName(String drugName) {

        return (drugName == null || drugName.isBlank())
                ? generateDefaultDrugName()
                : drugName;
    }

    private String generateDefaultDrugName() {

        return "복약 " + UUID.randomUUID()
                .toString()
                .substring(0, 8);
    }
}