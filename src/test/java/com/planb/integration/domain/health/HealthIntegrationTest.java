package com.planb.integration.domain.health;

import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.dto.request.DeleteCompanionRequest;
import com.planb.domain.health.dto.request.MealMedicationRuleDetail;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.global.security.dto.request.LoginRequest;
import com.planb.integration.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Health API 통합 테스트
 * 동행인 등록, 간단 조회, 삭제 기능과 인증 실패 상황 검증
 */
public class HealthIntegrationTest extends IntegrationTest {

    /*
    API 호출 URL 모음
     */
    private static final String CREATE_USER_URL =
            "/api/v1/user/create";

    private static final String LOGIN_URL =
            "/login";

    private static final String ADD_COMPANION_URL =
            "/api/v1/health/add-traveler";

    private static final String COMPANION_SUMMARY_URL =
            "/api/v1/health/get-companion-summary";

    private static final String DELETE_COMPANION_URL =
            "/api/v1/health/delete-companion";


    /*
    테스트 User 정보
     */
    private static final String NICKNAME =
            "healthTestNickname";

    private static final String PASSWORD =
            "test1234!";


    @Autowired
    private ObjectMapper objectMapper;


    /**
     * 동행인 등록
     */
    @Test
    @DisplayName("회원가입 및 로그인 후 동행인 등록 성공")
    void addCompanionSuccess() throws Exception {

        // given
        String username =
                createUniqueUsername();

        createUser(username);

        LoginResult loginResult =
                login(username);

        AddCompanionRequest request =
                createCompanionRequest();

        // when & then
        mockMvc.perform(
                        post(ADD_COMPANION_URL)
                                .header(
                                        "Authorization",
                                        loginResult.accessToken()
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
                )
                .andExpect(
                        jsonPath("$.data.travelerName")
                                .value("동행인1")
                )
                .andExpect(
                        jsonPath("$.data.message")
                                .value("동행인이 등록되었습니다.")
                )
                .andExpect(
                        jsonPath("$.error")
                                .isEmpty()
                );
    }


    /**
     * 동행인 간단 조회
     */
    @Test
    @DisplayName("동행인 등록 후 간단 조회 성공")
    void getCompanionSummarySuccess() throws Exception {

        // given
        String username =
                createUniqueUsername();

        createUser(username);

        LoginResult loginResult =
                login(username);

        addCompanion(
                loginResult.accessToken()
        );

        // when & then
        mockMvc.perform(
                        get(COMPANION_SUMMARY_URL)
                                .header(
                                        "Authorization",
                                        loginResult.accessToken()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.companionList")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.data.companionList[0].healthId")
                                .isNumber()
                )
                .andExpect(
                        jsonPath("$.data.companionList[0].travelerName")
                                .value("동행인1")
                )
                .andExpect(
                        jsonPath("$.data.companionList[0].hasAllergy")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.companionList[0].hasMedication")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.companionList[0].diseaseType")
                                .value("DIABETES")
                )
                .andExpect(
                        jsonPath("$.error")
                                .isEmpty()
                );
    }


    /**
     * 동행인 삭제
     */
    @Test
    @DisplayName("동행인 등록 후 삭제 성공")
    void deleteCompanionSuccess() throws Exception {

        // given
        String username =
                createUniqueUsername();

        createUser(username);

        LoginResult loginResult =
                login(username);

        addCompanion(
                loginResult.accessToken()
        );

        Long healthId =
                getHealthId(
                        loginResult.accessToken()
                );

        DeleteCompanionRequest request =
                new DeleteCompanionRequest(
                        healthId
                );

        // when & then
        mockMvc.perform(
                        delete(DELETE_COMPANION_URL)
                                .header(
                                        "Authorization",
                                        loginResult.accessToken()
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
                )
                .andExpect(
                        jsonPath("$.data.message")
                                .value(
                                        "해당 동행인 정보가 삭제되었습니다."
                                )
                )
                .andExpect(
                        jsonPath("$.error")
                                .isEmpty()
                );

        /*
        삭제 후 다시 조회하여
        동행인 목록에서 제거되었는지 확인
         */
        mockMvc.perform(
                        get(COMPANION_SUMMARY_URL)
                                .header(
                                        "Authorization",
                                        loginResult.accessToken()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.companionList")
                                .isEmpty()
                );
    }


    /**
     * 인증 실패
     */
    @Test
    @DisplayName("Access Token 없이 동행인 등록 시 인증 실패")
    void addCompanionUnauthorized() throws Exception {

        // given
        AddCompanionRequest request =
                createCompanionRequest();

        // when & then
        mockMvc.perform(
                        post(ADD_COMPANION_URL)
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
                        status().isForbidden()
                );
    }


    @Test
    @DisplayName("Access Token 없이 동행인 간단 조회 시 인증 실패")
    void getCompanionSummaryUnauthorized() throws Exception {

        // when & then
        mockMvc.perform(
                        get(COMPANION_SUMMARY_URL)
                )
                .andExpect(
                        status().isForbidden()
                );
    }


    @Test
    @DisplayName("Access Token 없이 동행인 삭제 시 인증 실패")
    void deleteCompanionUnauthorized() throws Exception {

        // given
        DeleteCompanionRequest request =
                new DeleteCompanionRequest(
                        1L
                );

        // when & then
        mockMvc.perform(
                        delete(DELETE_COMPANION_URL)
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
                        status().isForbidden()
                );
    }


    /*
    동행인 등록 Request 생성
     */
    private AddCompanionRequest createCompanionRequest() {

        return new AddCompanionRequest(
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
                        LocalTime.of(
                                8,
                                0
                        ),

                        true,
                        LocalTime.of(
                                12,
                                0
                        ),

                        true,
                        LocalTime.of(
                                18,
                                0
                        )
                ),

                List.of(
                        new AddCompanionRequest.FoodInfoDetail(
                                "땅콩",
                                FoodType.ALLERGY
                        ),

                        new AddCompanionRequest.FoodInfoDetail(
                                "오이",
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
                                                RelatedMeal.BREAKFAST,
                                                MealTiming.AFTER_MEAL,
                                                30
                                        ),

                                        new MealMedicationRuleDetail(
                                                RelatedMeal.LUNCH,
                                                MealTiming.AFTER_MEAL,
                                                30
                                        ),

                                        new MealMedicationRuleDetail(
                                                RelatedMeal.DINNER,
                                                MealTiming.AFTER_MEAL,
                                                30
                                        )
                                )
                        )
                )
        );
    }


    /*
    테스트 회원 생성
     */
    private void createUser(
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
                )
                .andExpect(
                        jsonPath("$.data.username")
                                .value(username)
                );
    }


    /*
    로그인 후 AccessToken 및 RefreshToken 반환
     */
    private LoginResult login(
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
                                jsonPath("$.success")
                                        .value(true)
                        )
                        .andExpect(
                                jsonPath("$.data.username")
                                        .value(username)
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

        assertThat(refreshTokenCookie)
                .isNotNull();

        assertThat(
                refreshTokenCookie.getValue()
        ).isNotBlank();

        return new LoginResult(
                accessToken,
                refreshTokenCookie
        );
    }


    /*
    동행인 등록 공통 메소드
     */
    private void addCompanion(
            String accessToken
    ) throws Exception {

        AddCompanionRequest request =
                createCompanionRequest();

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
                )
                .andExpect(
                        jsonPath("$.data.travelerName")
                                .value("동행인1")
                );
    }


    /*
    간단 조회 결과에서 healthId 추출
     */
    private Long getHealthId(
            String accessToken
    ) throws Exception {

        MvcResult result =
                mockMvc.perform(
                                get(COMPANION_SUMMARY_URL)
                                        .header(
                                                "Authorization",
                                                accessToken
                                        )
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                jsonPath("$.success")
                                        .value(true)
                        )
                        .andReturn();

        return objectMapper
                .readTree(
                        result
                                .getResponse()
                                .getContentAsString()
                )
                .get("data")
                .get("companionList")
                .get(0)
                .get("healthId")
                .asLong();
    }


    /*
    테스트 username 생성
     */
    private String createUniqueUsername() {

        return "health-test-"
                + UUID.randomUUID()
                .toString()
                .substring(
                        0,
                        8
                )
                + "@example.com";
    }


    /*
    로그인 결과 내부 DTO
     */
    private record LoginResult(
            String accessToken,
            Cookie refreshTokenCookie
    ) {
    }
}