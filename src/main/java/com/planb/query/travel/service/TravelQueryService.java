package com.planb.query.travel.service;

import com.planb.domain.travel.entity.constant.TravelListFilter;
import com.planb.query.travel.dto.response.TravelConditionQueryResponse;
import com.planb.query.travel.dto.response.TravelListItemQueryResponse;
import com.planb.query.travel.repository.TravelQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TravelQueryService {

    private final TravelQueryRepository travelQueryRepository;

    // travelId로 여행조건 데이터 조회
    public TravelConditionQueryResponse getTravelConditionQueryResponse(Long travelId){

        return travelQueryRepository.findTravelConditionById(travelId);
    }

    // userId와 탭 구분으로 여행 목록 조회
    public List<TravelListItemQueryResponse> getTravelList(
            Long userId,
            TravelListFilter filter,
            LocalDate today
    ) {

        return travelQueryRepository.findAllByUserId(userId, filter, today);
    }

    // 여행 id 목록으로 목록 썸네일 조회
    public Map<Long, String> getThumbnailUrls(List<Long> travelIds){

        return travelQueryRepository.findThumbnailUrlsByTravelIds(travelIds);
    }

    // 공유 토큰으로 여행 id 조회
    public Long getTravelIdByShareToken(String shareToken){

        return travelQueryRepository
                .findTravelIdByShareToken(shareToken)
                .orElseThrow(() -> new BaseException(BaseExceptionEnum
                        .ENTITY_NOT_FOUND));
    }

    // travelId가 해당 userId 소유인지 확인
    public boolean existsByIdAndUserId(Long travelId, Long userId){

        return travelQueryRepository.existsByIdAndUserId(travelId, userId);
    }
}