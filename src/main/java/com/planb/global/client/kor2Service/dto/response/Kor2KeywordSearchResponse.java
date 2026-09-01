package com.planb.global.client.kor2Service.dto.response;

import com.planb.global.constant.serializer.external.Kor2KeywordSearchItemsDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public record Kor2KeywordSearchResponse(
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
            @JsonDeserialize(using = Kor2KeywordSearchItemsDeserializer.class)
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
            String addr1,
            String addr2,
            String zipcode,
            String contentid,
            String contenttypeid,
            String createdtime,
            String firstimage,
            String firstimage2,
            String cpyrhtDivCd,
            String mapx,
            String mapy,
            String mlevel,
            String modifiedtime,
            String tel,
            String title,
            String lDongRegnCd,
            String lDongSignguCd,
            String lclsSystm1,
            String lclsSystm2,
            String lclsSystm3
    ) {
    }
}