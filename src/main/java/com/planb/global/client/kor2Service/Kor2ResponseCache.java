package com.planb.global.client.kor2Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * TourAPI 성공 응답의 Redis 캐시
 *
 * 개발계정 일일 1,000건 한도 안에서 같은 지역·메뉴의 반복 조회 절감
 * 성공 resultCode 응답만 JSON 문자열로 저장, Redis 장애 시 API 결과로 대체
 */
@Slf4j
@Component
public class Kor2ResponseCache {

    private static final String KEY_PREFIX = "kor2:";

    private static final Set<String> SUCCESS_CODES = Set.of(
            "0000",
            "00"
    );

    // 응답 레코드의 tools.jackson 커스텀 역직렬화기를 그대로 쓰는 기본 매퍼
    private static final JsonMapper MAPPER = JsonMapper
            .builder()
            .build();

    private final StringRedisTemplate redis;

    private final MeterRegistry meterRegistry;

    @Autowired
    public Kor2ResponseCache(
            StringRedisTemplate redis,
            MeterRegistry meterRegistry
    ) {

        this.redis = redis;
        this.meterRegistry = meterRegistry;
    }

    // 캐시 없이 항상 API를 호출하는 인스턴스 (단위 테스트·수동 생성용)
    public static Kor2ResponseCache disabled() {

        return new Kor2ResponseCache(
                null,
                null
        );
    }

    public <T extends Kor2Result> Mono<T> cached(
            String endpoint,
            String key,
            Duration ttl,
            Class<T> type,
            Supplier<Mono<T>> loader
    ) {

        if (redis == null) {
            return Mono.defer(loader);
        }

        String redisKey = KEY_PREFIX + endpoint + ":" + key;

        return read(
                endpoint,
                redisKey,
                type
        ).switchIfEmpty(Mono.defer(() -> loader
                .get()
                .flatMap(response -> store(
                        endpoint,
                        redisKey,
                        ttl,
                        response
                ).thenReturn(response))));
    }

    // Redis 조회는 blocking이라 boundedElastic에서 실행, 실패·역직렬화 오류는 미적중 처리
    private <T> Mono<T> read(
            String endpoint,
            String redisKey,
            Class<T> type
    ) {

        return Mono
                .fromCallable(() -> {
                    String json = redis
                            .opsForValue()
                            .get(redisKey);

                    return json == null
                            ? null
                            : MAPPER.readValue(json, type);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(ignored -> record(endpoint, "hit"))
                .switchIfEmpty(Mono.fromRunnable(() -> record(endpoint, "miss")))
                .onErrorResume(failure -> {
                    log.warn(
                            "[TOURAPI CACHE] 조회 실패 - endpoint: {}, 원인: {}",
                            endpoint,
                            failure.toString()
                    );
                    record(endpoint, "error");

                    return Mono.empty();
                });
    }

    // 성공 응답만 저장, API 응답 스레드를 막지 않도록 boundedElastic에서 실행
    private Mono<Void> store(
            String endpoint,
            String redisKey,
            Duration ttl,
            Kor2Result response
    ) {

        String resultCode = response.resultCode();

        if (resultCode == null || !SUCCESS_CODES.contains(resultCode)) {
            return Mono.empty();
        }

        return Mono
                .<Void>fromRunnable(() -> redis
                        .opsForValue()
                        .set(
                                redisKey,
                                MAPPER.writeValueAsString(response),
                                ttl
                        ))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(failure -> {
                    log.warn(
                            "[TOURAPI CACHE] 저장 실패 - endpoint: {}, 원인: {}",
                            endpoint,
                            failure.toString()
                    );
                    record(endpoint, "error");

                    return Mono.empty();
                });
    }

    private void record(
            String endpoint,
            String result
    ) {

        Counter
                .builder("planb.external.kor2.cache")
                .tag("endpoint", endpoint)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
