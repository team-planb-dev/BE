package com.planb.global.client.kor2Service.dto.response;

import com.planb.global.client.kor2Service.Kor2Result;
import com.planb.global.constant.serializer.external.Kor2RestaurantIntroItemsDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public record Kor2RestaurantIntroResponse(
        Response response
) implements Kor2Result {

    @Override
    public String resultCode() {

        return response == null || response.header() == null
                ? null
                : response
                        .header()
                        .resultCode();
    }

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
            @JsonDeserialize(using = Kor2RestaurantIntroItemsDeserializer.class)
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
            String treatmenu,
            String opentimefood
    ) {

        public Item(
                String contentid,
                String contenttypeid,
                String firstmenu,
                String treatmenu
        ) {

            this(contentid, contenttypeid, firstmenu, treatmenu, null);
        }
    }
}