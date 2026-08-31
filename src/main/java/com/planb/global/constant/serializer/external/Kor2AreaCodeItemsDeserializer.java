package com.planb.global.constant.serializer.external;

import com.planb.global.client.kor2Service.dto.response.Kor2AreaCodeResponse;

public class Kor2AreaCodeItemsDeserializer
        extends Kor2AbstractItemsDeserializer<Kor2AreaCodeResponse.Items, Kor2AreaCodeResponse.Item> {

    public Kor2AreaCodeItemsDeserializer() {
        super(
                Kor2AreaCodeResponse.Items.class,
                Kor2AreaCodeResponse.Item.class,
                Kor2AreaCodeResponse.Items::new
        );
    }
}