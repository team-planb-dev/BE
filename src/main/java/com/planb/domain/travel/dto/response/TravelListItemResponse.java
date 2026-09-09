package com.planb.domain.travel.dto.response;

import com.planb.domain.travel.entity.constant.TravelStatus;
import com.planb.query.travel.dto.response.TravelListItemQueryResponse;

import java.time.LocalDate;

/**
 * 여행 목록의 카드 한 장.
 *
 * @param status       조회 시점 기준으로 계산한 진행 상태
 * @param thumbnailUrl 일정 중 첫 번째 이미지, 이미지가 하나도 없으면 null
 */
public record TravelListItemResponse(

        Long travelId,
        String travelName,
        String locationDo,
        String locationSigungu,
        LocalDate startDate,
        LocalDate endDate,
        TravelStatus status,
        String thumbnailUrl
) {

    public static TravelListItemResponse of(
            TravelListItemQueryResponse travel,
            LocalDate today,
            String thumbnailUrl
    ) {

        return new TravelListItemResponse(
                travel
                        .travelId(),
                travel
                        .travelName(),
                travel
                        .locationDo(),
                travel
                        .locationSigungu(),
                travel
                        .startDate(),
                travel
                        .endDate(),
                TravelStatus
                        .of(today,
                                travel.startDate(),
                                travel.endDate()),
                thumbnailUrl
        );
    }
}
