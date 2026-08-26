package com.planb.global.client.kor2Service.dto.response;

import java.util.List;

public record Kor2RestaurantIntroResponse(
        Response response
) {

    public record Response(
            Header header,
            Body body
    ) {
    }

    public record Header(
            String resultCode,
            String resultMsg
    ) {
    }

    public record Body(
            Items items,
            Integer numOfRows,
            Integer pageNo,
            Integer totalCount
    ) {
    }

    public record Items(
            List<Item> item
    ) {
    }

    public record Item(
            String contentid,
            String contenttypeid,
            String firstmenu,
            String treatmenu
    ) {
    }
}