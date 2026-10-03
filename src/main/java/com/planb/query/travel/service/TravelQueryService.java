package com.planb.query.travel.service;

import com.planb.domain.travel.entity.constant.TravelListFilter;
import com.planb.domain.travel.dto.response.TravelListItemResponse;
import com.planb.domain.travel.dto.response.TravelListResponse;
import com.planb.global.config.exception.domain.ForbiddenException;
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
    public TravelConditionQueryResponse getTravelConditionQueryResponse(Long travelId) {

        return travelQueryRepository.findTravelConditionById(travelId);
    }

    // userId와 탭 구분으로 여행 목록 조회
    public List<TravelListItemQueryResponse> getTravelList(
            Long userId,
            TravelListFilter filter,
            LocalDate today
    ) {

        return travelQueryRepository.findAllByUserId(
                userId,
                filter,
                today
        );
    }

    // 여행 id 목록으로 목록 썸네일 조회
    public Map<Long, String> getThumbnailUrls(List<Long> travelIds) {

        return travelQueryRepository.findThumbnailUrlsByTravelIds(travelIds);
    }

    // 공유 토큰으로 여행 id 조회
    public Long getTravelIdByShareToken(String shareToken) {

        return travelQueryRepository
                .findTravelIdByShareToken(shareToken)
                .orElseThrow(() -> new BaseException(BaseExceptionEnum
                        .ENTITY_NOT_FOUND));
    }

    // travelId가 해당 userId 소유인지 확인
    public boolean existsByIdAndUserId(Long travelId, Long userId) {

        return travelQueryRepository.existsByIdAndUserId(travelId, userId);
    }

    public void validateOwner(
            Long travelId,
            Long userId
    ) {

        if (!existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }
    }

    public TravelListResponse getTravelListResponse(
            Long userId,
            TravelListFilter filter
    ) {

        LocalDate today = LocalDate.now();
        List<TravelListItemQueryResponse> travels = getTravelList(
                userId,
                filter,
                today
        );
        Map<Long, String> thumbnailUrls = getThumbnailUrls(travels
                .stream()
                .map(TravelListItemQueryResponse::travelId)
                .toList());

        return new TravelListResponse(travels
                .stream()
                .map(travel -> TravelListItemResponse.of(
                        travel,
                        today,
                        thumbnailUrls.get(travel.travelId())
                ))
                .toList());
    }
}
