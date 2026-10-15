package com.planb.unit.global.client.foodNtrCpnt;

import com.planb.global.client.foodNtrCpnt.FoodNtrCpntClient;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.properties.FoodNtrCpntProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class FoodNtrCpntTestClient {

    private static final URI SEARCH_URI = URI.create(
            "https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02?serviceKey=secret&type=json"
    );

    @Test
    @DisplayName("2xx 게이트웨이 오류 XML은 반환 코드를 담은 디코딩 실패로 전파")
    void propagatesGatewayReasonCode() {

        FoodNtrCpntClient client = client(ClientResponse
                .create(HttpStatus.OK)
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .body("""
                        <OpenAPI_ServiceResponse>
                            <cmmMsgHeader>
                                <errMsg>SERVICE ERROR</errMsg>
                                <returnAuthMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</returnAuthMsg>
                                <returnReasonCode>22</returnReasonCode>
                            </cmmMsgHeader>
                        </OpenAPI_ServiceResponse>
                        """)
                .build());

        StepVerifier
                .create(client.get(
                        SEARCH_URI,
                        FoodNtrCpntResponse.class
                ))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DecodingException.class)
                        .hasMessageContaining("returnReasonCode=22")
                        .hasMessageContaining("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR")
                        .hasMessageNotContaining("secret"))
                .verify();
    }

    @Test
    @DisplayName("정상 JSON 응답은 그대로 해석")
    void decodesJsonBody() {

        FoodNtrCpntClient client = client(ClientResponse
                .create(HttpStatus.OK)
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .body("""
                        {"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                        "body":{"items":[{"FOOD_NM_KR":"갈치구이","SERVING_SIZE":"100mL","AMT_NUM7":"1.5"}],
                        "numOfRows":1,"pageNo":1,"totalCount":1}}
                        """)
                .build());

        StepVerifier
                .create(client.get(
                        SEARCH_URI,
                        FoodNtrCpntResponse.class
                ))
                .assertNext(response -> assertThat(response
                        .body()
                        .items()
                        .getFirst()
                        .servingSize())
                        .isEqualTo("100mL"))
                .verifyComplete();
    }

    private FoodNtrCpntClient client(ClientResponse response) {

        WebClient.Builder builder = WebClient
                .builder()
                .exchangeFunction(request -> Mono.just(response));

        return new FoodNtrCpntClient(
                builder,
                new FoodNtrCpntProperties(
                        "https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo02",
                        "secret"
                )
        );
    }
}
