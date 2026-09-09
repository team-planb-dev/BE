package com.planb.unit.global.client.kor2Service;

import com.planb.global.client.kor2Service.Kor2ServiceClient;
import com.planb.global.client.kor2Service.dto.response.Kor2AreaCodeResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Kor2ServiceHandlerTest {

    @Mock
    private Kor2ServiceClient kor2ServiceClient;

    private Kor2ServiceHandler handler;

    @BeforeEach
    void setUp() {

        handler = new Kor2ServiceHandler(kor2ServiceClient);

        when(kor2ServiceClient.baseUrl())
                .thenReturn("https://example.com");

        when(kor2ServiceClient.serviceKey())
                .thenReturn("service-key");
    }

    @Test
    @DisplayName("관광지는 시도 코드만으로 areaBasedList2 조회")
    void searchesAttractionsByAreaWithoutSigungu() {

        when(
                kor2ServiceClient
                        .get(
                                any(URI.class),
                                eq(Kor2AreaCodeResponse.class)
                        )
        ).thenReturn(Mono.just(areaCodes("1", "서울")));

        when(
                kor2ServiceClient
                        .get(
                                any(URI.class),
                                eq(Kor2KeywordSearchResponse.class)
                        )
        ).thenReturn(Mono.just(emptyPlaces()));

        handler
                .searchAttractions(
                        "서울",
                        "종로구"
                )
                .block();

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);

        verify(kor2ServiceClient)
                .get(
                        uri.capture(),
                        eq(Kor2KeywordSearchResponse.class)
                );

        assertThat(uri.getValue().getPath())
                .isEqualTo("/areaBasedList2");

        assertThat(uri.getValue().getQuery())
                .contains("areaCode=1")
                .contains("contentTypeId=12")
                .doesNotContain("sigunguCode")
                .doesNotContain("keyword");
    }

    @Test
    @DisplayName("도 지역 관광지는 시군구 코드까지 포함하여 areaBasedList2 조회")
    void searchesProvinceAttractionsBySigungu() {

        when(
                kor2ServiceClient
                        .get(
                                any(URI.class),
                                eq(Kor2AreaCodeResponse.class)
                        )
        ).thenReturn(
                Mono.just(areaCodes("32", "강원특별자치도")),
                Mono.just(areaCodes("1", "강릉시"))
        );

        when(
                kor2ServiceClient
                        .get(
                                any(URI.class),
                                eq(Kor2KeywordSearchResponse.class)
                        )
        ).thenReturn(Mono.just(emptyPlaces()));

        handler
                .searchAttractions(
                        "강원특별자치도",
                        "강릉시"
                )
                .block();

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);

        verify(kor2ServiceClient)
                .get(
                        uri.capture(),
                        eq(Kor2KeywordSearchResponse.class)
                );

        assertThat(uri.getValue().getPath())
                .isEqualTo("/areaBasedList2");

        assertThat(uri.getValue().getQuery())
                .contains("areaCode=32")
                .contains("sigunguCode=1")
                .contains("contentTypeId=12")
                .doesNotContain("keyword");
    }

    @Test
    @DisplayName("도 지역 음식점은 시군구 코드가 포함된 키워드 검색")
    void searchesRestaurantsBySigungu() {

        when(
                kor2ServiceClient
                        .get(
                                any(URI.class),
                                eq(Kor2AreaCodeResponse.class)
                        )
        ).thenReturn(
                Mono.just(areaCodes("35", "경상북도")),
                Mono.just(areaCodes("2", "경주시"))
        );

        when(
                kor2ServiceClient
                        .get(
                                any(URI.class),
                                eq(Kor2KeywordSearchResponse.class)
                        )
        ).thenReturn(Mono.just(emptyPlaces()));

        handler
                .searchRestaurants(
                        "쌈밥",
                        "경상북도",
                        "경주시"
                )
                .block();

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);

        verify(kor2ServiceClient)
                .get(
                        uri.capture(),
                        eq(Kor2KeywordSearchResponse.class)
                );

        assertThat(uri.getValue().getPath())
                .isEqualTo("/searchKeyword2");

        assertThat(uri.getValue().getQuery())
                .contains("keyword=쌈밥")
                .contains("areaCode=35")
                .contains("sigunguCode=2")
                .contains("contentTypeId=39");
    }

    // 광역 지역은 관광지 조회와 같은 기준으로 시/도 전체를 검색해야 한다
    @Test
    @DisplayName("광역 지역 음식점은 시군구 코드 없이 시/도 전체 키워드 검색")
    void searchesRestaurantsAcrossMetropolitanArea() {

        when(
                kor2ServiceClient
                        .get(
                                any(URI.class),
                                eq(Kor2AreaCodeResponse.class)
                        )
        ).thenReturn(Mono.just(areaCodes("6", "부산")));

        when(
                kor2ServiceClient
                        .get(
                                any(URI.class),
                                eq(Kor2KeywordSearchResponse.class)
                        )
        ).thenReturn(Mono.just(emptyPlaces()));

        handler
                .searchRestaurants(
                        "밀면",
                        "부산",
                        "해운대구"
                )
                .block();

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);

        verify(kor2ServiceClient)
                .get(
                        uri.capture(),
                        eq(Kor2KeywordSearchResponse.class)
                );

        assertThat(uri.getValue().getQuery())
                .contains("keyword=밀면")
                .contains("areaCode=6")
                .contains("contentTypeId=39")
                .doesNotContain("sigunguCode");

        verify(
                kor2ServiceClient,
                times(1)
        )
                .get(
                        any(URI.class),
                        eq(Kor2AreaCodeResponse.class)
                );
    }

    private Kor2AreaCodeResponse areaCodes(
            String code,
            String name
    ) {

        return new Kor2AreaCodeResponse(
                new Kor2AreaCodeResponse.Response(
                        null,
                        new Kor2AreaCodeResponse.Body(
                                new Kor2AreaCodeResponse.Items(
                                        List.of(
                                                new Kor2AreaCodeResponse.Item(
                                                        "1",
                                                        code,
                                                        name
                                                )
                                        )
                                ),
                                1,
                                1,
                                1
                        )
                )
        );
    }

    private Kor2KeywordSearchResponse emptyPlaces() {

        return new Kor2KeywordSearchResponse(
                new Kor2KeywordSearchResponse.Response(
                        null,
                        new Kor2KeywordSearchResponse.Body(
                                new Kor2KeywordSearchResponse.Items(List.of()),
                                0,
                                1,
                                0
                        )
                )
        );
    }
}
