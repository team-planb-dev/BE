package com.planb.global.client.foodNtrCpnt;

import com.planb.global.client.ApiClient;
import com.planb.global.client.foodNtrCpnt.properties.FoodNtrCpntProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class FoodNtrCpntClient extends ApiClient<FoodNtrCpntProperties> {

    public FoodNtrCpntClient
            (WebClient.Builder webClientBuilder,
             FoodNtrCpntProperties properties) {
        super(webClientBuilder,properties);
    }

    public String serviceKey() {
        return properties.serviceKey();
    }

    public String baseUrl(){
        return properties.baseUrl();
    }





}
