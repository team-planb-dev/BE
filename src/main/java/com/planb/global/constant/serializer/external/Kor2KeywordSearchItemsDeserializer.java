package com.planb.global.constant.serializer.external;

import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;

public class Kor2KeywordSearchItemsDeserializer
        extends Kor2AbstractItemsDeserializer<Kor2KeywordSearchResponse.Items, Kor2KeywordSearchResponse.Item> {

    public Kor2KeywordSearchItemsDeserializer() {
        super(
                Kor2KeywordSearchResponse.Items.class,
                Kor2KeywordSearchResponse.Item.class,
                Kor2KeywordSearchResponse.Items::new
        );
    }
}