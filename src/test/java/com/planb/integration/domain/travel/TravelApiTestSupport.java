package com.planb.integration.domain.travel;

import tools.jackson.databind.ObjectMapper;
import com.planb.integration.IntegrationTest;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.global.security.dto.request.LoginRequest;
import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.dto.request.MealMedicationRuleDetail;
import com.planb.domain.health.entity.constant.*;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class TravelApiTestSupport extends IntegrationTest {

    private static final String CREATE_USER_URL = "/api/v1/user/create";
    private static final String LOGIN_URL = "/login";
    private static final String ADD_COMPANION_URL = "/api/v1/health/add-traveler";
    private static final String NICKNAME = "travelTestNickname";
    private static final String PASSWORD = "test1234!";

    @Autowired
    protected ObjectMapper objectMapper;

    /*
    테스트 회원 생성
     */
    protected void createUser(
            String username
    ) throws Exception {

        UserCreateRequest request =
                new UserCreateRequest(
                        username,
                        NICKNAME,
                        PASSWORD,
                        true,
                        true,
                        true
                );

        mockMvc.perform(
                        post(CREATE_USER_URL)
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );
    }

    /*
    로그인 후 AccessToken 및 RefreshToken 반환
     */
    protected LoginResult login(
            String username
    ) throws Exception {

        LoginRequest request =
                new LoginRequest(
                        username,
                        PASSWORD
                );

        MvcResult result =
                mockMvc.perform(
                                post(LOGIN_URL)
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        request
                                                )
                                        )
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                header().string(
                                        "Authorization",
                                        startsWith("Bearer ")
                                )
                        )
                        .andExpect(
                                cookie().exists(
                                        "refreshToken"
                                )
                        )
                        .andReturn();

        String accessToken =
                result
                        .getResponse()
                        .getHeader(
                                "Authorization"
                        );

        Cookie refreshTokenCookie =
                result
                        .getResponse()
                        .getCookie(
                                "refreshToken"
                        );

        assertThat(accessToken)
                .isNotBlank()
                .startsWith(
                        "Bearer "
                );

        return new LoginResult(
                accessToken,
                refreshTokenCookie
        );
    }

    /*
    동행인(건강정보) 등록 - AI 일정 생성 시 실제 Health 컨텍스트로 반영됨
     */
    protected void addCompanion(
            String accessToken
    ) throws Exception {

        AddCompanionRequest request =
                new AddCompanionRequest(
                        "동행인1",
                        true,
                        true,

                        new AddCompanionRequest.HealthInfo(
                                DiseaseType.DIABETES,
                                WalkType.MODERATE
                        ),

                        new AddCompanionRequest.MealInfo(
                                true,

                                true,
                                LocalTime.of(8, 0),

                                true,
                                LocalTime.of(12, 0),

                                true,
                                LocalTime.of(18, 0)
                        ),

                        List.of(
                                new AddCompanionRequest.FoodInfoDetail(
                                        "새우",
                                        FoodType.ALLERGY
                                ),

                                new AddCompanionRequest.FoodInfoDetail(
                                        "과도하게 단 음식",
                                        FoodType.AVOID
                                )
                        ),

                        List.of(
                                new AddCompanionRequest.MedicationInfoDetail(
                                        "테스트 복약",
                                        MedicationBasis.WITH_MEAL,
                                        null,

                                        Set.of(
                                                new MealMedicationRuleDetail(
                                                        RelatedMeal.LUNCH,
                                                        MealTiming.AFTER_MEAL,
                                                        30
                                                )
                                        )
                                )
                        )
                );

        mockMvc.perform(
                        post(ADD_COMPANION_URL)
                                .header(
                                        "Authorization",
                                        accessToken
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );
    }

    protected String createUniqueUsername() {

        return "travel-test-"
                + UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                + "@example.com";
    }

    /*
    로그인 결과 내부 DTO
     */
    protected record LoginResult(
            String accessToken,
            Cookie refreshTokenCookie
    ) {
    }
}
