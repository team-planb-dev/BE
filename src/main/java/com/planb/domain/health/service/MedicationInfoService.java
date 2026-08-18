package com.planb.domain.health.service;

import com.planb.domain.health.dto.request.CreateMedicationInfoRequest;
import com.planb.domain.health.entity.MedicationInfo;
import com.planb.domain.health.entity.vo.MealMedicationRule;
import com.planb.domain.health.repository.MedicationInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicationInfoService {

    private final MedicationInfoRepository medicationInfoRepository;

    public List<MedicationInfo> makeMedicationInfoList(
            CreateMedicationInfoRequest request
    ) {

        return request
                .medicationInfoList()
                .stream()
                .map(medication -> {

                    Set<MealMedicationRule> rules =
                            medication
                                    .mealMedicationRuleDetails()
                                    .stream()
                                    .map(rule ->
                                            new MealMedicationRule(
                                                    rule.relatedMeal(),
                                                    rule.mealTiming(),
                                                    rule.intervalMinutes()
                                            )
                                    )
                                    .collect(Collectors.toSet());

                    return MedicationInfo
                            .builder()
                            .health(request.health())
                            .drugName(medication.drugName())
                            .medicationBasis(medication.medicationBasis())
                            .medicationTime(medication.medicationTime())
                            .mealMedicationRules(rules)
                            .build();
                })
                .toList();
    }

    /*
    기본 CRUD 모음
     */
    public void saveMedicationInfoAll(
            List<MedicationInfo> medicationInfoList
    ) {
        medicationInfoRepository.saveAll(medicationInfoList);
    }
}