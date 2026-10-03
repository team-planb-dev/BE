package com.planb.domain.health.service;

import com.planb.domain.health.dto.request.CreateHealthRequest;
import com.planb.domain.health.dto.request.CreateHealthWithoutSensitiveAgreeRequest;
import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.dto.request.UpdateCompanionRequest;
import com.planb.domain.health.dto.response.AddCompanionResponse;
import com.planb.domain.health.dto.response.CompanionDetailResponse;
import com.planb.domain.health.dto.response.DeleteCompanionResponse;
import com.planb.domain.health.dto.response.UpdateCompanionResponse;
import com.planb.domain.health.entity.FoodInfo;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.MedicationInfo;
import com.planb.domain.health.entity.vo.HealthInfo;
import com.planb.domain.health.entity.vo.MealInfo;
import com.planb.domain.health.repository.HealthRepository;
import com.planb.domain.user.entity.User;
import com.planb.global.config.exception.HealthExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final HealthRepository healthRepository;

    public Health addCompanion(
            AddCompanionRequest request,
            User user
    ) {

        Health health = validSensitiveAgree(request.toHealthRequest(), user);
        saveHealth(health);

        return health;
    }

    public void updateCompanion(
            Health health,
            UpdateCompanionRequest request
    ) {

        updateHealth(
                health,
                request.toAddCompanionRequest().toHealthRequest()
        );
    }

    public AddCompanionResponse addCompanionResponse(Health health) {

        return new AddCompanionResponse(
                health.getTravelerName(),
                "동행인이 등록되었습니다."
        );
    }

    public UpdateCompanionResponse updateCompanionResponse(Health health) {

        return new UpdateCompanionResponse(
                health.getTravelerName(),
                "동행인 정보가 수정되었습니다."
        );
    }

    public CompanionDetailResponse companionDetailResponse(
            Health health,
            List<FoodInfo> foodInfos,
            List<MedicationInfo> medicationInfos
    ) {

        return CompanionDetailResponse.of(
                health,
                foodInfos,
                medicationInfos
        );
    }

    public DeleteCompanionResponse deleteCompanionResponse() {

        return new DeleteCompanionResponse("해당 동행인 정보가 삭제되었습니다.");
    }


    // 개인정보 동의 여부에 따른 Health 객체 생성
    public Health validSensitiveAgree(CreateHealthRequest request, User user){
        if (request.sensitiveAgree()){
            return makeHealthWithSensitiveAgree(request, user);
        } else {
            return makeHealthWithoutSensitiveAgree(
                    new CreateHealthWithoutSensitiveAgreeRequest(
                            request
                                    .travelerName()),
                    user);
        }
    }

    // Health 객체 생성 (정보동의 O)
    private Health makeHealthWithSensitiveAgree(CreateHealthRequest request, User user){

        return Health
                .builder()
                .travelerName(request
                        .travelerName())
                .sensitiveAgree(request
                        .sensitiveAgree())
                .hasMedication(request
                        .hasMedication())
                .healthInfo(
                        new HealthInfo(
                                request
                                        .healthInfo()
                                        .diseaseTypes(),
                                request
                                        .healthInfo()
                                        .walkType()))
                .mealInfo(
                        new MealInfo(
                                request
                                        .mealInfo()
                                        .applied(),
                                request
                                        .mealInfo()
                                        .breakfastApplied(),
                                request
                                        .mealInfo()
                                        .breakfastTime(),
                                request
                                        .mealInfo()
                                        .lunchApplied(),
                                request
                                        .mealInfo()
                                        .lunchTime(),
                                request
                                        .mealInfo()
                                        .dinnerApplied(),
                                request
                                        .mealInfo()
                                        .dinnerTime()))
                .user(user)
                .build();
    }

    // Health 객체 생성 (정보동의 X)
    private Health makeHealthWithoutSensitiveAgree
    (CreateHealthWithoutSensitiveAgreeRequest request, User user){

        return Health
                .builder()
                .travelerName(request.travelerName())
                .sensitiveAgree(false)
                .hasMedication(false)
                .user(user)
                .build();
    }



    // 동행인 정보 수정 (민감정보 동의 여부에 따라 저장 범위가 달라진다)
    public void updateHealth(
            Health health,
            CreateHealthRequest request
    ) {

        if (!request.sensitiveAgree()) {
            health.update(request
                            .travelerName(),
                    false,
                    false,
                    null,
                    null);

            return;
        }

        health.update(request
                        .travelerName(),
                true,
                request
                        .hasMedication(),
                new HealthInfo(
                        request
                                .healthInfo()
                                .diseaseTypes(),
                        request
                                .healthInfo()
                                .walkType()),
                new MealInfo(
                        request
                                .mealInfo()
                                .applied(),
                        request
                                .mealInfo()
                                .breakfastApplied(),
                        request
                                .mealInfo()
                                .breakfastTime(),
                        request
                                .mealInfo()
                                .lunchApplied(),
                        request
                                .mealInfo()
                                .lunchTime(),
                        request
                                .mealInfo()
                                .dinnerApplied(),
                        request
                                .mealInfo()
                                .dinnerTime()));
    }


    /*
    기본 CRUD 모음
     */

    // Health 객체 저장하기
    public void saveHealth(Health health){
        healthRepository.save(health);
    }

    // Health 객체 삭제하기
    public void deleteHealthById(Long id){
        healthRepository.deleteById(id);
    }

    // Health 객체 조회하기
    public Health getHealthById(Long id){
        return healthRepository
                .findById(id)
                .orElseThrow(()->
                        new BaseException(HealthExceptionEnum
                                .HEALTH_NOT_FOUND));
    }

    public List<Health> getHealthListByUserId(Long userId){
        return healthRepository.findAllByUserId(userId);

    }




}
