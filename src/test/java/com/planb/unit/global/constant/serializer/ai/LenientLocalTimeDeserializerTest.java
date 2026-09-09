package com.planb.unit.global.constant.serializer.ai;

import com.planb.global.constant.serializer.ai.LenientLocalTimeDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class LenientLocalTimeDeserializerTest {

    private final JsonMapper jsonMapper = buildJsonMapper();

    // LenientLocalTimeDeserializer만 등록한 JsonMapper 생성
    private JsonMapper buildJsonMapper() {

        SimpleModule module = new SimpleModule();
        module.addDeserializer(LocalTime.class, new LenientLocalTimeDeserializer());

        return JsonMapper.builder()
                .addModule(module)
                .build();
    }

    @Test
    @DisplayName("문자열 형태(HH:mm) 시간 파싱")
    void deserializeStringFormat() {

        // given
        String json = "\"08:30\"";

        // when
        LocalTime result =
                jsonMapper.readValue(json, LocalTime.class);

        // then
        assertThat(result)
                .isEqualTo(LocalTime.of(8, 30));
    }

    @Test
    @DisplayName("[시, 분] 배열 형태 시간 파싱")
    void deserializeArrayFormatWithoutSeconds() {

        // given
        String json = "[8, 30]";

        // when
        LocalTime result =
                jsonMapper.readValue(json, LocalTime.class);

        // then
        assertThat(result)
                .isEqualTo(LocalTime.of(8, 30));
    }

    @Test
    @DisplayName("[시, 분, 초] 배열 형태 시간 파싱")
    void deserializeArrayFormatWithSeconds() {

        // given
        String json = "[8, 30, 15]";

        // when
        LocalTime result =
                jsonMapper.readValue(json, LocalTime.class);

        // then
        assertThat(result)
                .isEqualTo(LocalTime.of(8, 30, 15));
    }

    @Test
    @DisplayName("null 값은 null로 파싱")
    void deserializeNullReturnsNull() {

        // given
        String json = "null";

        // when
        LocalTime result =
                jsonMapper.readValue(json, LocalTime.class);

        // then
        assertThat(result)
                .isNull();
    }

    @Test
    @DisplayName("빈 문자열은 null로 파싱")
    void deserializeBlankStringReturnsNull() {

        // given
        String json = "\"   \"";

        // when
        LocalTime result =
                jsonMapper.readValue(json, LocalTime.class);

        // then
        assertThat(result)
                .isNull();
    }

    @Test
    @DisplayName("문자열 \"null\"은 null로 파싱")
    void deserializeStringNullReturnsNull() {

        // given
        String json = "\"null\"";

        // when
        LocalTime result =
                jsonMapper.readValue(json, LocalTime.class);

        // then
        assertThat(result)
                .isNull();
    }
}
