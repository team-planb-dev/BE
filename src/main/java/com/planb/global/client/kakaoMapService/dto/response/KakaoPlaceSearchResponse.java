package com.planb.global.client.kakaoMapService.dto.response;

import java.util.List;

public record KakaoPlaceSearchResponse(
        Meta meta,
        List<Document> documents
) {

    public record Meta(
            Integer total_count,
            Integer pageable_count,
            Boolean is_end
    ) {
    }

    public record Document(
            String id,
            String place_name,
            String category_name,
            String category_group_code,
            String category_group_name,
            String phone,
            String address_name,
            String road_address_name,
            String x,
            String y,
            String place_url,
            String distance
    ) {
    }
}