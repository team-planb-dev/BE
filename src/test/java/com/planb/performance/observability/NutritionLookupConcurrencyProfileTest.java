package com.planb.performance.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionLookupConcurrencyProfileTest {

    @Test
    @DisplayName("운영 프로필은 영양 조회를 기본 순차 실행으로 유지")
    void productionDefaultsToSequentialNutritionLookups() throws IOException {

        MutablePropertySources sources = new MutablePropertySources();

        sources.addLast(new YamlPropertySourceLoader()
                .load(
                        "production",
                        new ClassPathResource("application-common-prod.yml")
                )
                .getFirst());

        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(sources);

        assertThat(resolver.getProperty("planb.travel.nutrition.lookup-concurrency", Integer.class))
                .isEqualTo(1);
    }
}
