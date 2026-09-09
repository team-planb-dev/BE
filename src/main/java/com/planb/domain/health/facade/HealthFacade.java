package com.planb.domain.health.facade;

import com.planb.domain.health.dto.request.*;
import com.planb.domain.health.dto.response.AddCompanionResponse;
import com.planb.domain.health.dto.response.CompanionSummaryResponse;
import com.planb.domain.health.dto.response.CompanionDetailResponse;
import com.planb.domain.health.dto.response.DeleteCompanionResponse;
import com.planb.domain.health.dto.response.UpdateCompanionResponse;
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



/**
 * 동행인의 건강, 음식 제한, 복약 정보 흐름을 조합하는 Facade.
 *
 * 개인정보 동의 여부에 따라 저장 범위를 결정하고 사용자 소유권을 기준으로
 * 동행인 정보의 생성, 조회, 삭제가 일관되게 처리되도록 각 Service를 조합한다.
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
     * 개인정보 동의 범위에 맞춰 동행인과 건강 관련 정보를 등록한다.
     *
     * 민감정보에 동의하지 않은 경우 기본 동행인 정보만 저장하고,
     * 동의한 경우 음식 제한과 복약 정보까지 같은 등록 흐름에서 저장한다.
     *
     * @param request 등록할 동행인과 건강 정보
     * @param username 동행인을 등록하는 사용자의 username
     * @return 등록된 동행인 정보
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
                healthService.validSensitiveAgree(healthRequest, user);

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
     * 인증 사용자가 소유한 동행인의 요약 정보를 조회한다.
     *
     * @param username 동행인을 소유한 사용자의 username
     * @return 동행인 요약 목록
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
     * 동행인 수정 화면에 필요한 건강, 음식 제한, 복약 정보를 함께 조회한다.
     *
     * @param healthId 조회할 동행인 id
     * @param username 동행인을 소유한 사용자의 username
     * @return 동행인 상세 정보
     * @throws BaseException 요청한 사용자가 해당 동행인을 소유하지 않은 경우
     */
    @Transactional(readOnly = true)
    public CompanionDetailResponse getCompanionDetail(
            Long healthId,
            String username
    ) {

        Health health = findOwnedHealth(healthId, username);

        return CompanionDetailResponse
                .of(health,
                        foodInfoService
                                .getFoodInfoList(healthId),
                        medicationInfoService
                                .findAllByHealthId(healthId));
    }


    /**
     * 동행인의 건강, 음식 제한, 복약 정보를 요청 값으로 덮어쓴다.
     *
     * 음식 제한과 복약 정보는 항목 수가 달라질 수 있어 기존 값을 지우고 다시 저장한다.
     *
     * @param request  수정할 동행인 정보
     * @param username 수정을 요청한 사용자의 username
     * @return 동행인 수정 결과
     * @throws BaseException 요청한 사용자가 해당 동행인을 소유하지 않은 경우
     */
    @Transactional
    public UpdateCompanionResponse updateCompanion(
            UpdateCompanionRequest request,
            String username
    ) {

        Long healthId = request.healthId();

        Health health = findOwnedHealth(healthId, username);

        AddCompanionRequest companionRequest =
                request.toAddCompanionRequest();

        healthService.updateHealth(health,
                companionRequest.toHealthRequest());

        // 기존 하위 정보는 전부 제거한 뒤 요청 값으로 다시 저장한다.
        foodInfoQueryService
                .deleteAllByHealthId(healthId);
        medicationInfoQueryService
                .deleteAllMedicationInfoByHealthId(healthId);

        if (!request.sensitiveAgree()) {
            return new UpdateCompanionResponse(
                    health
                            .getTravelerName(),
                    "동행인 정보가 수정되었습니다.");
        }

        foodInfoService.saveFoodInfoAll(
                foodInfoService.makeFoodInfoList(
                        companionRequest.toFoodInfoRequest(health)
                )
        );

        medicationInfoService.saveMedicationInfoAll(
                medicationInfoService.makeMedicationInfoList(
                        companionRequest.toMedicationInfoRequest(health)
                )
        );

        return new UpdateCompanionResponse(
                health
                        .getTravelerName(),
                "동행인 정보가 수정되었습니다.");
    }


    /**
     * 사용자가 소유한 동행인인지 확인하고 반환한다.
     *
     * @param healthId 확인할 동행인 id
     * @param username 요청한 사용자의 username
     * @return 소유가 확인된 동행인
     * @throws BaseException 요청한 사용자가 해당 동행인을 소유하지 않은 경우
     */
    private Health findOwnedHealth(
            Long healthId,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        if (!healthQueryService.checkHealthWithUser(healthId, userId)) {
            throw new BaseException(HealthExceptionEnum.HEALTH_NOT_FOUND);
        }

        return healthService
                .getHealthById(healthId);
    }

    /**
     * 사용자 소유권을 검증한 뒤 동행인과 연관된 건강 정보를 함께 삭제한다.
     *
     * 참조 관계가 남지 않도록 음식 제한과 복약 정보를 먼저 제거한다.
     *
     * @param request 삭제할 동행인 식별 정보
     * @param username 삭제를 요청한 사용자의 username
     * @return 동행인 삭제 결과
     * @throws BaseException 요청한 사용자가 해당 동행인을 소유하지 않은 경우
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
