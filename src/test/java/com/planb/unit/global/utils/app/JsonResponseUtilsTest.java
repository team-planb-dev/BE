package com.planb.unit.global.utils.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import com.planb.global.utils.app.JsonResponseUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JsonResponseUtilsTest {

    // Test용 ResponseDTO
    record TestResponse(String message) {

    }

    @Test
    @DisplayName("JSON 응답을 정상적으로 작성")
    void writeJsonResponse() throws Exception {

        // given
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        TestResponse body =
                new TestResponse("success");

        // when
        JsonResponseUtils
                .writeJsonResponse(
                        HttpStatus.OK,
                        response,
                        body
        );

        // then
        assertThat(response
                .getStatus())
                .isEqualTo(HttpStatus
                        .OK
                        .value());

        assertThat(response
                .getContentType())
                .contains("application/json");

        assertThat(response
                .getCharacterEncoding())
                .isEqualTo("UTF-8");

        assertThat(response
                .getContentAsString())
                .contains("success");

    }

}