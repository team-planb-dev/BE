package com.planb.performance.observability;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.PropertiesMeterFilter;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * loadtest 프로필의 SLO 버킷 설정 검증
 */
class LoadTestMetricsProfileTest {

    private final SimpleMeterRegistry registry = registryWithLoadtestProfile();

    @Test
    @DisplayName("orchestration은 30초를 넘는 SLO 버킷을 가짐")
    void orchestrationHasBucketsBeyondThirtySeconds() {

        assertThat(bounds(timer("planb.travel.ai.orchestration")))
                .contains(
                3.0,
                6.0,
                8.0,
                30.0,
                60.0,
                120.0,
                300.0
        );
    }

    @Test
    @DisplayName("서버 HTTP 요청도 SLO 버킷을 가짐")
    void serverRequestsHaveBuckets() {

        assertThat(bounds(timer("http.server.requests")))
                .isNotEmpty()
                .contains(
                0.1,
                1.0,
                3.0,
                6.0,
                8.0
        );
    }

    @Test
    @DisplayName("외부 HTTP 요청과 재시도 timer에는 버킷을 만들지 않음")
    void otherTimersStayWithoutBuckets() {

        assertThat(bounds(timer("http.client.requests")))
                .isEmpty();
        assertThat(bounds(timer("planb.ai.retry")))
                .isEmpty();
    }

    @Test
    @DisplayName("SLO 경계는 오름차순이고 30초를 넘는 값까지 이어짐")
    void boundsAreAscendingAndReachPastThirtySeconds() {

        List<Double> bounds = bounds(timer("planb.travel.ai.orchestration"));

        assertThat(bounds)
                .isSorted();
        assertThat(bounds.getLast())
                .isGreaterThan(30.0);
    }

    private Timer timer(String name) {

        Timer timer = Timer
                .builder(name)
                .tag("case", "test")
                .register(registry);

        timer.record(Duration.ofMillis(80));

        return timer;
    }

    // 초 단위 버킷 상한, 버킷이 없으면 빈 목록
    private List<Double> bounds(Timer timer) {

        return Arrays
                .stream(timer
                        .takeSnapshot()
                        .histogramCounts())
                .map(bucket -> bucket.bucket(TimeUnit.SECONDS))
                .toList();
    }

    private SimpleMeterRegistry registryWithLoadtestProfile() {

        MetricsProperties properties;

        try {
            properties = new Binder(
                    ConfigurationPropertySources.from(
                            new YamlPropertySourceLoader()
                                    .load(
                                            "loadtest",
                                            new ClassPathResource("application-common-loadtest.yml")
                                    )
                                    .getFirst()
                    )
            )
                    .bind(
                            "management.metrics",
                            MetricsProperties.class
                    )
                    .orElseGet(MetricsProperties::new);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        meterRegistry
                .config()
                .meterFilter(new PropertiesMeterFilter(properties));

        return meterRegistry;
    }
}
