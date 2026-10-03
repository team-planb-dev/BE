package com.planb.domain.travel.dto.response;

import com.planb.domain.travel.entity.constant.TravelStatus;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.query.travel.dto.response.TravelListItemQueryResponse;

import java.time.LocalDate;

/**
 * 여행 목록 카드의 조회 시점 상태와 첫 이미지
 * @param status 조회 시점 기준으로 계산한 진행 상태
 * @param travelTheme 여행 생성 시 선택한 테마, 미선택 시 null 허용
 * @param thumbnailUrl 일정 중 첫 번째 이미지, 이미지가 없으면 null 허용
 */
public record TravelListItemResponse(

        Long travelId,
        String travelName,
        String locationDo,
        String locationSigungu,
        LocalDate startDate,
        LocalDate endDate,
        TravelStatus status,
        TravelTheme travelTheme,
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
                travel
                        .travelTheme(),
                thumbnailUrl
        );
    }
}
