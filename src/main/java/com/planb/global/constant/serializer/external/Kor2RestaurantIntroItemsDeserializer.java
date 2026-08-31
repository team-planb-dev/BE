package com.planb.global.constant.serializer.external;

import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;

public class Kor2RestaurantIntroItemsDeserializer
        extends Kor2AbstractItemsDeserializer<Kor2RestaurantIntroResponse.Items, Kor2RestaurantIntroResponse.Item> {

    public Kor2RestaurantIntroItemsDeserializer() {
        super(
                Kor2RestaurantIntroResponse.Items.class,
                Kor2RestaurantIntroResponse.Item.class,
                Kor2RestaurantIntroResponse.Items::new
        );
    }
}