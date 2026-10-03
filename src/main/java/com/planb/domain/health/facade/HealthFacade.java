package com.planb.domain.health.facade;

import com.planb.domain.health.dto.request.*;
import com.planb.domain.health.dto.response.AddCompanionResponse;
import com.planb.domain.health.dto.response.CompanionSummaryResponse;
import com.planb.domain.health.dto.response.CompanionDetailResponse;
import com.planb.domain.health.dto.response.DeleteCompanionResponse;
import com.planb.domain.health.dto.response.UpdateCompanionResponse;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.FoodInfo;
import com.planb.domain.health.entity.MedicationInfo;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.user.entity.User;
import com.planb.query.health.service.FoodInfoQueryService;
import com.planb.query.health.service.HealthQueryService;
import com.planb.query.health.service.MedicationInfoQueryService;
import com.planb.query.user.service.UserQueryService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;



/**
 * 동행인의 건강 정보 관리 흐름을 조합
 */
@Component
@RequiredArgsConstructor
public class HealthFacade {

    private final HealthService healthService;
    private final FoodInfoService foodInfoService;
    private final MedicationInfoService medicationInfoService;

    private final UserQueryService userQueryService;
    private final HealthQueryService healthQueryService;
    private final FoodInfoQueryService foodInfoQueryService;
    private final MedicationInfoQueryService medicationInfoQueryService;



    /**
     * 동행인과 건강 정보를 등록
     */
    @Transactional
    public AddCompanionResponse addCompanion(AddCompanionRequest request,
                                             String username) {

        User user = userQueryService.findByUsername(username); // 사용자 조회

        Health health = healthService.addCompanion(request, user); // 동행인 등록

        foodInfoService.saveForCompanion(request, health); // 음식 제한 저장

        medicationInfoService.saveForCompanion(request, health); // 복약 정보 저장

        return healthService.addCompanionResponse(health); // 등록 결과 반환
    }

    /**
     * 동행인 건강 정보 요약을 조회
     */
    @Transactional(readOnly = true)
    public CompanionSummaryResponse getCompanionSummary
    (String username) {

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        return healthQueryService.getCompanionSummaryResponse(userId); // 동행인 요약 조회

    }

    /**
     * 동행인 건강 정보 상세를 조회
     */
    @Transactional(readOnly = true)
    public CompanionDetailResponse getCompanionDetail(
            Long healthId,
            String username
    ) {

        Health health = findOwnedHealth(healthId, username); // 동행인 소유권 검증 및 조회

        List<FoodInfo> foodInfos = foodInfoService.getFoodInfoList(healthId); // 음식 제한 조회

        List<MedicationInfo> medicationInfos = medicationInfoService.findAllByHealthId(healthId); // 복약 정보 조회

        return healthService.companionDetailResponse(
                health,
                foodInfos,
                medicationInfos
        ); // 동행인 상세 반환
    }


    /**
     * 동행인 건강 정보를 수정
     */
    @Transactional
    public UpdateCompanionResponse updateCompanion(
            UpdateCompanionRequest request,
            String username
    ) {

        Health health = findOwnedHealth(request.healthId(), username); // 동행인 소유권 검증 및 조회

        healthService.updateCompanion(health, request); // 동행인 정보 수정

        foodInfoQueryService.deleteAllByHealthId(request.healthId()); // 기존 음식 제한 삭제

        medicationInfoQueryService.deleteAllMedicationInfoByHealthId(request.healthId()); // 기존 복약 정보 삭제

        foodInfoService.saveForCompanion(request, health); // 음식 제한 저장

        medicationInfoService.saveForCompanion(request, health); // 복약 정보 저장

        return healthService.updateCompanionResponse(health); // 수정 결과 반환
    }


    /**
     * 사용자가 소유한 동행인을 조회
     */
    private Health findOwnedHealth(
            Long healthId,
            String username
    ) {

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        healthQueryService.validateOwned(healthId, userId); // 동행인 소유권 검증

        return healthService.getHealthById(healthId); // 동행인 조회
    }

    /**
     * 동행인과 건강 정보를 삭제
     */
    @Transactional
    public DeleteCompanionResponse deleteCompanion(
            DeleteCompanionRequest request,
            String username
    ) {

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        healthQueryService.validateOwned(request.healthId(), userId); // 동행인 소유권 검증

        foodInfoQueryService.deleteAllByHealthId(request.healthId()); // 음식 제한 삭제

        medicationInfoQueryService.deleteAllMedicationInfoByHealthId(request.healthId()); // 복약 정보 삭제

        healthService.deleteHealthById(request.healthId()); // 동행인 삭제

        return healthService.deleteCompanionResponse(); // 삭제 결과 반환
    }

}
