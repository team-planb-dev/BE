package com.planb.domain.travel.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum PlaceType implements CodeCommInterface {

    ATTRACTION("ATTRACTION", "관광지"),
    CULTURAL_FACILITY("CULTURAL_FACILITY", "문화시설"),
    FESTIVAL("FESTIVAL", "축제/공연/행사"),
    TRAVEL_COURSE("TRAVEL_COURSE", "여행코스"),
    LEISURE_SPORTS("LEISURE_SPORTS", "레포츠"),
    ACCOMMODATION("ACCOMMODATION", "숙박"),
    SHOPPING("SHOPPING", "쇼핑"),
    RESTAURANT("RESTAURANT", "음식점");

    private final String code;
    private final String codeName;

    public static PlaceType fromContentTypeId(String contentTypeId) {

        return switch (contentTypeId) {
            case "12" -> ATTRACTION;
            case "14" -> CULTURAL_FACILITY;
            case "15" -> FESTIVAL;
            case "25" -> TRAVEL_COURSE;
            case "28" -> LEISURE_SPORTS;
            case "32" -> ACCOMMODATION;
            case "38" -> SHOPPING;
            case "39" -> RESTAURANT;
            default -> null;
        };
    }
}
