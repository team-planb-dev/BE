package com.planb.global.client.kor2Service;

import com.planb.global.client.ApiClient;
import com.planb.global.client.kor2Service.properties.Kor2ServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class Kor2ServiceClient
        extends ApiClient<Kor2ServiceProperties> {



    public Kor2ServiceClient
            (WebClient.Builder webClientBuilder,
             Kor2ServiceProperties properties) {
        super(webClientBuilder,properties);
    }

    public String serviceKey() {
        return properties.serviceKey();
    }

    public String baseUrl(){
        return properties.baseUrl();
    }


}
