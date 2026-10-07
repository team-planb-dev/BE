package com.planb.unit.global.client.kor2Service;

import com.planb.global.client.kor2Service.Kor2ResponseCache;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Kor2ResponseTestCache {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private Kor2ResponseCache cache;

    @BeforeEach
    void setUp() {

        when(redis.opsForValue())
                .thenReturn(values);

        cache = new Kor2ResponseCache(
                redis,
                meterRegistry
        );
    }

    @Test
    @DisplayName("저장된 JSON이 있으면 API 없이 커스텀 역직렬화기로 복원")
    void returnsCachedResponseWithoutLoader() {

        // TourAPI 실제 응답 형식(items.item 배열)
        when(values.get("kor2:searchKeyword2:불국사"))
                .thenReturn("""
                        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                        "body":{"items":{"item":[{"contentid":"126166","contenttypeid":"12",
                        "title":"경주 불국사 [유네스코 세계유산]","addr1":"경상북도 경주시 불국로 385"}]},
                        "numOfRows":1,"pageNo":1,"totalCount":1}}}
                        """);

        AtomicInteger loads = new AtomicInteger();

        StepVerifier
                .create(cache.cached(
                        "searchKeyword2",
                        "불국사",
                        Duration.ofDays(1),
                        Kor2KeywordSearchResponse.class,
                        () -> {
                            loads.incrementAndGet();

                            return Mono.empty();
                        }
                ))
                .assertNext(response -> assertThat(response
                        .response()
                        .body()
                        .items()
                        .item())
                        .extracting(Kor2KeywordSearchResponse.Item::contentid)
                        .containsExactly("126166"))
                .verifyComplete();

        assertThat(loads.get())
                .isZero();
        assertThat(count("searchKeyword2", "hit"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("미적중 시 성공 응답을 저장하고 저장한 JSON은 다시 같은 값으로 복원")
    void storesSuccessfulResponseAndRoundTrips() {

        Kor2KeywordSearchResponse loaded = JsonMapper
                .builder()
                .build()
                .readValue("""
                        {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                        "body":{"items":{"item":[{"contentid":"126166","contenttypeid":"12","title":"불국사"}]},
                        "numOfRows":1,"pageNo":1,"totalCount":1}}}
                        """, Kor2KeywordSearchResponse.class);

        StepVerifier
                .create(cache.cached(
                        "searchKeyword2",
                        "불국사",
                        Duration.ofDays(1),
                        Kor2KeywordSearchResponse.class,
                        () -> Mono.just(loaded)
                ))
                .expectNext(loaded)
                .verifyComplete();

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        verify(values)
                .set(
                        eq("kor2:searchKeyword2:불국사"),
                        json.capture(),
                        eq(Duration.ofDays(1))
                );

        assertThat(JsonMapper
                .builder()
                .build()
                .readValue(json.getValue(), Kor2KeywordSearchResponse.class))
                .isEqualTo(loaded);
        assertThat(count("searchKeyword2", "miss"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("성공이 아닌 resultCode 응답은 저장하지 않음")
    void doesNotStoreNonSuccessResponse() {

        Kor2KeywordSearchResponse limited = new Kor2KeywordSearchResponse(
                new Kor2KeywordSearchResponse.Response(
                        new Kor2KeywordSearchResponse.Header(
                                "22",
                                "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"
                        ),
                        null
                )
        );

        StepVerifier
                .create(cache.cached(
                        "searchKeyword2",
                        "불국사",
                        Duration.ofDays(1),
                        Kor2KeywordSearchResponse.class,
                        () -> Mono.just(limited)
                ))
                .expectNext(limited)
                .verifyComplete();

        verify(values, never())
                .set(
                        anyString(),
                        anyString(),
                        any(Duration.class)
                );
    }

    @Test
    @DisplayName("Redis 조회·저장 실패는 API 결과로 대체")
    void fallsBackWhenRedisFails() {

        when(values.get(anyString()))
                .thenThrow(new IllegalStateException("redis down"));

        Kor2KeywordSearchResponse loaded = new Kor2KeywordSearchResponse(
                new Kor2KeywordSearchResponse.Response(
                        new Kor2KeywordSearchResponse.Header(
                                "0000",
                                "OK"
                        ),
                        null
                )
        );

        doThrow(new IllegalStateException("redis down"))
                .when(values)
                .set(
                        anyString(),
                        anyString(),
                        any(Duration.class)
                );

        StepVerifier
                .create(cache.cached(
                        "searchKeyword2",
                        "불국사",
                        Duration.ofDays(1),
                        Kor2KeywordSearchResponse.class,
                        () -> Mono.just(loaded)
                ))
                .expectNext(loaded)
                .verifyComplete();

        assertThat(count("searchKeyword2", "error"))
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("비활성 캐시는 항상 API를 그대로 호출")
    void disabledCacheAlwaysLoads() {

        Kor2KeywordSearchResponse loaded = new Kor2KeywordSearchResponse(null);

        StepVerifier
                .create(Kor2ResponseCache
                        .disabled()
                        .cached(
                                "searchKeyword2",
                                "불국사",
                                Duration.ofDays(1),
                                Kor2KeywordSearchResponse.class,
                                () -> Mono.just(loaded)
                        ))
                .expectNext(loaded)
                .verifyComplete();
    }

    private double count(
            String endpoint,
            String result
    ) {

        var counter = meterRegistry
                .find("planb.external.kor2.cache")
                .tag("endpoint", endpoint)
                .tag("result", result)
                .counter();

        return counter == null
                ? 0
                : counter.count();
    }
}
