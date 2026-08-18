package com.planb.unit.domain.health.service;

import com.planb.domain.health.dto.request.CreateMedicationInfoRequest;
import com.planb.domain.health.dto.request.MealMedicationRuleDetail;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.MedicationInfo;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.vo.MealMedicationRule;
import com.planb.domain.health.repository.MedicationInfoRepository;
import com.planb.domain.health.service.MedicationInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MedicationInfoServiceTest {

    @Mock
    private MedicationInfoRepository medicationInfoRepository;

    @Mock
    private Health health;

    private MedicationInfoService medicationInfoService;

    @BeforeEach
    void setUp() {
        medicationInfoService =
                new MedicationInfoService(
                        medicationInfoRepository
                );
    }

    @Test
    @DisplayName("복약 정보 요청 기반 MedicationInfo 리스트 생성")
    void makeMedicationInfoListSuccess() {

        // given
        MedicationBasis medicationBasis =
                MedicationBasis.values()[0];

        RelatedMeal relatedMeal =
                RelatedMeal.values()[0];

        MealTiming mealTiming =
                MealTiming.values()[0];

        MealMedicationRuleDetail ruleDetail =
                new MealMedicationRuleDetail(
                        relatedMeal,
                        mealTiming,
                        30
                );

        CreateMedicationInfoRequest request =
                new CreateMedicationInfoRequest(
                        health,
                        List.of(
                                new CreateMedicationInfoRequest.MedicationInfoDetail(
                                        "테스트 약",
                                        medicationBasis,
                                        LocalTime.of(8, 0),
                                        Set.of(ruleDetail)
                                )
                        )
                );

        // when
        List<MedicationInfo> result =
                medicationInfoService
                        .makeMedicationInfoList(request);

        // then
        assertEquals(1, result.size());

        MedicationInfo medicationInfo =
                result.get(0);

        assertSame(
                health,
                medicationInfo.getHealth()
        );

        assertEquals(
                "테스트 약",
                medicationInfo.getDrugName()
        );

        assertEquals(
                medicationBasis,
                medicationInfo.getMedicationBasis()
        );

        assertEquals(
                LocalTime.of(8, 0),
                medicationInfo.getMedicationTime()
        );

        assertEquals(
                1,
                medicationInfo
                        .getMealMedicationRules()
                        .size()
        );

        MealMedicationRule rule =
                medicationInfo
                        .getMealMedicationRules()
                        .iterator()
                        .next();

        assertEquals(
                relatedMeal,
                rule.getRelatedMeal()
        );

        assertEquals(
                mealTiming,
                rule.getMealTiming()
        );

        assertEquals(
                30,
                rule.getIntervalMinutes()
        );
    }

    @Test
    @DisplayName("MedicationInfo 리스트 일괄 저장")
    void saveMedicationInfoAllSuccess() {

        // given
        List<MedicationInfo> medicationInfos =
                List.of(
                        MedicationInfo.builder()
                                .health(health)
                                .drugName("테스트 약")
                                .medicationBasis(
                                        MedicationBasis.values()[0]
                                )
                                .build()
                );

        // when
        medicationInfoService
                .saveMedicationInfoAll(
                        medicationInfos
                );

        // then
        verify(medicationInfoRepository)
                .saveAll(medicationInfos);
    }
}