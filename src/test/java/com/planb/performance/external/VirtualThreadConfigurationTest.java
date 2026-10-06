package com.planb.performance.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
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

        Binder binder = binder(Map.of());

        assertThat(bind(binder, VIRTUAL_THREADS_ENABLED))
                .isFalse();
        assertThat(bind(binder, KEEP_ALIVE))
                .isFalse();
    }

    @Test
    @DisplayName("가상 스레드 토글의 keep-alive 동시 활성")
    void toggleEnablesVirtualThreadsAndKeepAlive() throws IOException {

        Binder binder = binder(Map.of(
                "SPRING_THREADS_VIRTUAL_ENABLED",
                "true"
        ));

        assertThat(bind(binder, VIRTUAL_THREADS_ENABLED))
                .isTrue();
        assertThat(bind(binder, KEEP_ALIVE))
                .isTrue();
    }

    @Test
    @DisplayName("별도 keep-alive 환경변수는 토글보다 우선")
    void separateKeepAliveVariableOverridesToggle() throws IOException {

        // OS 환경변수가 application.yml보다 우선. 하이픈 제거·구분자 표기 모두 같은 속성에 연결
        for (String keepAliveVariable : new String[] {
                "SPRING_MAIN_KEEPALIVE",
                "SPRING_MAIN_KEEP_ALIVE"
        }) {

            Binder binder = binder(Map.of(
                    "SPRING_THREADS_VIRTUAL_ENABLED",
                    "true",
                    keepAliveVariable,
                    "false"
            ));

            assertThat(bind(binder, VIRTUAL_THREADS_ENABLED))
                    .isTrue();
            assertThat(bind(binder, KEEP_ALIVE))
                    .as(keepAliveVariable)
                    .isFalse();
        }
    }

    // Spring Boot와 같은 우선순위: OS 환경변수 → application.yml
    private Binder binder(Map<String, Object> environmentVariables) throws IOException {

        StandardEnvironment environment = new StandardEnvironment();

        MutablePropertySources sources = environment.getPropertySources();

        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        environmentVariables
                )
        );
        sources.addLast(yaml("application.yml"));

        ConfigurationPropertySources.attach(environment);

        return Binder.get(environment);
    }

    private boolean bind(
            Binder binder,
            String name
    ) {

        return binder
                .bind(name, Boolean.class)
                .get();
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
