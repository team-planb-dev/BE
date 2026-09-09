package com.planb.domain.travel.dto.response;

/**
 * 일정 저장 확정 결과.
 *
 * @param travelId 저장한 여행 id
 * @param saved    저장 확정 여부, 저장 후에는 항상 true
 */
public record SaveTravelResponse(

        Long travelId,
        boolean saved) {
}
