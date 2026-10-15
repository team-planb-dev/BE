package com.planb.global.client.foodNtrCpnt;

import com.planb.global.client.ApiClient;
import com.planb.global.client.DataPortalGatewayException;
import com.planb.global.client.foodNtrCpnt.properties.FoodNtrCpntProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class FoodNtrCpntClient extends ApiClient<FoodNtrCpntProperties> {

    public FoodNtrCpntClient
            (WebClient.Builder webClientBuilder,
             FoodNtrCpntProperties properties) {
        // 같은 공공데이터포털 게이트웨이(apis.data.go.kr)의 오류 XML 반환 코드 보존
        // TourAPI 매뉴얼이 아닌 포털 공통 FAQ 기준 추론
        super(
                webClientBuilder.filter(
                        DataPortalGatewayException.detector(MAX_IN_MEMORY_BYTES)
                ),
                properties
        );
    }

    public String serviceKey() {
        return properties.serviceKey();
    }

    public String baseUrl() {
        return properties.baseUrl();
    }





}
