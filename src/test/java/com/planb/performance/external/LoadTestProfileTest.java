package com.planb.performance.external;

import com.planb.performance.external.ExternalHttpStubServer.Api;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoadTestProfileTest {

    private static final String OPENAI_BASE_URL_KEY =
            "spring.ai.openai.chat.base-url";

    private static final Map<Api, String> BASE_URL_KEYS = Map.of(
            Api.KOR2,
            "external.kor2-service.base-url",
            Api.KAKAO_MAP,
            "external.kakao-map.base-url",
            Api.KAKAO_MOBILITY,
            "external.kakao-mobility.base-url",
            Api.FOOD_NUTRITION,
            "external.food-ntr-cpnt.base-url"
    );

    private static final List<String> KEY_PROPERTIES = List.of(
            "external.kor2-service.service-key",
            "external.kakao-map.api-key",
            "external.kakao-mobility.api-key",
            "external.food-ntr-cpnt.service-key",
            "spring.ai.openai.api-key"
    );

    @Test
    @DisplayName("loadtest 프로파일은 common-loadtest 설정 묶음을 켬")
    void loadtestProfileActivatesCommonLoadtest() throws IOException {

        assertThat(yaml("application.yml")
                        .getProperty("spring.profiles.group.loadtest"))
                .isEqualTo("common-loadtest");

        assertThat(yaml("application-common-loadtest.yml")
                        .getProperty("spring.config.activate.on-profile"))
                .isEqualTo("common-loadtest");

        assertThat(yaml("application-common-loadtest.yml")
                .getProperty("spring.ai.mcp.server.protocol"))
                .isEqualTo("streamable");
    }

    @Test
    @DisplayName("외부 API 네 개의 base URL은 모두 로컬 스텁을 가리킴")
    void externalBaseUrlsPointAtLocalStub() throws IOException {

        PropertySourcesPropertyResolver resolver = resolver(Map.of());

        BASE_URL_KEYS.forEach((api, key) -> assertThat(resolver.getProperty(key))
                .isEqualTo("http://localhost:18080" + api.prefix()));
    }

    @Test
    @DisplayName("LOADTEST_STUB_URL로 스텁 주소를 바꿀 수 있음")
    void stubAddressCanBeOverridden() throws IOException {

        PropertySourcesPropertyResolver resolver = resolver(Map.of(
                "LOADTEST_STUB_URL",
                "http://127.0.0.1:19090"
        ));

        BASE_URL_KEYS.forEach((api, key) -> assertThat(resolver.getProperty(key))
                .isEqualTo("http://127.0.0.1:19090" + api.prefix()));
    }

    @Test
    @DisplayName("OpenAI base URL은 로컬 스텁을 가리킴")
    void openAiBaseUrlPointsAtLocalStub() throws IOException {

        PropertySourcesPropertyResolver resolver = resolver(Map.of());

        assertThat(resolver.getProperty(OPENAI_BASE_URL_KEY))
                .isEqualTo("http://localhost:18081/v1");

        assertThat(resolver.getProperty("spring.ai.openai.timeout"))
                .isEqualTo("20s");

        assertThat(resolver.getProperty("spring.ai.openai.max-retries"))
                .isEqualTo("0");
    }

    @Test
    @DisplayName("loadtest 프로파일의 Tomcat MBean registry 활성화")
    void tomcatMBeanRegistryEnabled() throws IOException {

        assertThat(yaml("application-common-loadtest.yml")
                .getProperty("server.tomcat.mbeanregistry.enabled"))
                .isEqualTo(true);
    }

    @Test
    @DisplayName("LOADTEST_OPENAI_STUB_URL로 OpenAI 스텁 주소 변경")
    void openAiStubAddressOverride() throws IOException {

        PropertySourcesPropertyResolver resolver = resolver(Map.of(
                "LOADTEST_OPENAI_STUB_URL",
                "http://127.0.0.1:19091"
        ));

        assertThat(resolver.getProperty(OPENAI_BASE_URL_KEY))
                .isEqualTo("http://127.0.0.1:19091/v1");
    }

    @Test
    @DisplayName("외부 키는 환경변수 없이 해석되고 실행 환경의 실제 키를 읽지 않음")
    void externalKeysNeverComeFromEnvironment() throws IOException {

        PropertySourcesPropertyResolver resolver = resolver(Map.of(
                        "KOR2_SERVICE_KEY",
                        "real-key",
                        "KAKAO_REST_API_KEY",
                        "real-key",
                        "FOOD_NTR_CPNT_KEY",
                        "real-key",
                        "OPENAI_API_KEY",
                        "real-key"
                ));

        KEY_PROPERTIES.forEach(key -> assertThat(resolver.getProperty(key))
                .isNotBlank()
                .isNotEqualTo("real-key"));
    }

    private PropertySourcesPropertyResolver resolver(Map<String, Object> environment) throws IOException {

        MutablePropertySources sources = new MutablePropertySources();

        sources.addFirst(new MapPropertySource(
                "environment",
                environment
        ));

        sources.addLast(yaml("application-common-loadtest.yml"));

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
