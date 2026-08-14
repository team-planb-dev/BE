package com.planb.query.health.service;


import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.query.health.repository.HealthQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HealthQueryService {

    private final HealthQueryRepository healthQueryRepository;



    // userId를 통해 건강 요약정보 가져오기
    public List<HealthSummaryQueryResponse> getHealthSummaryList(Long userId){

        return healthQueryRepository.findHealthSummaryList(userId);
    }

    public boolean checkHealthWithUser
            (Long healthId,
             Long userId){

        return healthQueryRepository
                .existsByHealthIdAndUserId(
                        healthId,
                        userId);
    }




}
