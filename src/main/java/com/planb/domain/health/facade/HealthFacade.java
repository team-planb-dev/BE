package com.planb.domain.health.facade;

import com.planb.domain.health.dto.request.*;
import com.planb.domain.health.dto.response.AddCompanionResponse;
import com.planb.domain.health.dto.response.CompanionSummaryResponse;
import com.planb.domain.health.dto.response.DeleteCompanionResponse;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.user.entity.User;
import com.planb.global.config.exception.HealthExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.health.service.FoodInfoQueryService;
import com.planb.query.health.service.HealthQueryService;
import com.planb.query.health.service.MedicationInfoQueryService;
import com.planb.query.user.service.UserQueryService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;



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
     개인정보 동의에 따른 health 정보 생성 후
     나머지 객체 생성 및 저장
     */
    @Transactional
    public AddCompanionResponse addCompanion(AddCompanionRequest request,
                                             String username) {

        User user = userQueryService
                .findByUsername(username);

        CreateHealthRequest healthRequest =
                request.toHealthRequest();

        // Health 객체 생성
        Health health =
                healthService.validSensitiveAgree(healthRequest,user);

        // Health 저장
        healthService.saveHealth(health);

        if (!request.sensitiveAgree()) {
            return new AddCompanionResponse(
                    healthRequest
                            .travelerName(),
                    "동행인이 등록되었습니다.");
        }

        CreateFoodInfoRequest foodInfoRequest =
                request.toFoodInfoRequest(health);

        CreateMedicationInfoRequest medicationInfoRequest =
                request.toMedicationInfoRequest(health);

        // FoodInfo 객체 생성 후 , 저장
        foodInfoService.saveFoodInfoAll(
                foodInfoService.makeFoodInfoList(foodInfoRequest)
        );

        // MedicationInfo 객체 생성 후 , 저장
        medicationInfoService.saveMedicationInfoAll(
                medicationInfoService.makeMedicationInfoList(
                        medicationInfoRequest
                )
        );

        return new AddCompanionResponse(
                healthRequest
                        .travelerName(),
                "동행인이 등록되었습니다.");
    }

    /**
     * UserDetails를 통해 동행인 리스트 조회 (간단조회)
     */
    @Transactional(readOnly = true)
    public CompanionSummaryResponse getCompanionSummary
    (String username){

        // UserAuthCache에서 userId 가져오기
        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        return CompanionSummaryResponse
                .from(healthQueryService
                        .getHealthSummaryList(userId));

    }

    /**
     * 동행인 상세 조회
     */
    @Transactional(readOnly = true)
    public void getCompanionDetail(){

        // username을 UserAuthCache에서 userId를 조회

        // userId로 Health 조회

        // HealthId로 MedicationInfo , FoodInfo를 묶어서 조회
    }

    /**
     * 단일 동행인 삭제
     */
    @Transactional
    public DeleteCompanionResponse deleteCompanion(
            DeleteCompanionRequest request,
            String username
    ) {

        Long healthId = request.healthId();

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        if (!healthQueryService.checkHealthWithUser(healthId, userId)) {
            throw new BaseException(HealthExceptionEnum.HEALTH_NOT_FOUND);
        }

        // 연관 객체 삭제
        foodInfoQueryService
                .deleteAllByHealthId(healthId);
        medicationInfoQueryService
                .deleteAllMedicationInfoByHealthId(healthId);

        // health 객체 삭제
        healthService.deleteHealthById(healthId);

        return new DeleteCompanionResponse("해당 동행인 정보가 삭제되었습니다.");
    }

}
