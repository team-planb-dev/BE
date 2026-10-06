package com.planb.performance.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadConfigurationTest {

    private static final String VIRTUAL_THREADS_ENABLED =
            "spring.threads.virtual.enabled";

    private static final String KEEP_ALIVE =
            "spring.main.keep-alive";

    @Test
    @DisplayName("기본 platform thread와 keep-alive 비활성")
    void defaultsToPlatformThreads() throws IOException {

        PropertySourcesPropertyResolver resolver = resolver(Map.of());

        assertThat(resolver.getProperty(VIRTUAL_THREADS_ENABLED))
                .isEqualTo("false");
        assertThat(resolver.getProperty(KEEP_ALIVE))
                .isEqualTo("false");
    }

    @Test
    @DisplayName("가상 스레드 토글의 keep-alive 동시 활성")
    void toggleEnablesVirtualThreadsAndKeepAlive() throws IOException {

        PropertySourcesPropertyResolver resolver = resolver(Map.of(
                "SPRING_THREADS_VIRTUAL_ENABLED",
                "true"
        ));

        assertThat(resolver.getProperty(VIRTUAL_THREADS_ENABLED))
                .isEqualTo("true");
        assertThat(resolver.getProperty(KEEP_ALIVE))
                .isEqualTo("true");
    }

    private PropertySourcesPropertyResolver resolver(
            Map<String, Object> environment
    ) throws IOException {

        MutablePropertySources sources = new MutablePropertySources();

        sources.addFirst(new MapPropertySource(
                "environment",
                environment
        ));
        sources.addLast(yaml("application.yml"));

        return new PropertySourcesPropertyResolver(sources);
    }

    private PropertySource<?> yaml(String name) throws IOException {

        return new YamlPropertySourceLoader()
                .load(
                        name,
                        new ClassPathResource(name)
                )
                .getFirst();
    }
}
