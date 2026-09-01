package com.planb.domain.health.service;

import com.planb.domain.health.dto.request.CreateHealthRequest;
import com.planb.domain.health.dto.request.CreateHealthWithoutSensitiveAgreeRequest;
import com.planb.domain.health.entity.Health;
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


    // 개인정보 동의 여부에 따른 Health 객체 생성
    public Health validSensitiveAgree(CreateHealthRequest request,User user){
        if(request.sensitiveAgree()){
            return makeHealthWithSensitiveAgree(request,user);
        } else {
            return makeHealthWithoutSensitiveAgree(
                    new CreateHealthWithoutSensitiveAgreeRequest(
                            request
                                    .travelerName()),
                    user);
        }
    }

    // Health 객체 생성 (정보동의 O)
    private Health makeHealthWithSensitiveAgree(CreateHealthRequest request,User user){

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
                                        .diseaseType(),
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
