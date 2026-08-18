package com.planb.controller.domain.user;


import com.planb.domain.user.dto.request.CheckNicknameDuplicationRequest;
import com.planb.domain.user.dto.request.CheckUsernameDuplicationRequest;
import com.planb.domain.user.dto.response.CheckNicknameDuplicationResponse;
import com.planb.domain.user.dto.response.CheckUsernameDuplicationResponse;
import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.planb.domain.user.controller.UserController;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.domain.user.dto.response.UserCreateResponse;
import com.planb.domain.user.dto.response.UserDeleteResponse;
import com.planb.domain.user.facade.UserFacade;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.dto.UserAuthCache;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserFacade userFacade;

    @Test
    @DisplayName("회원가입 성공")
    void createUserSuccess() throws Exception {
        UserCreateRequest request =

                new UserCreateRequest(
                        "testUser@example.com",
                        "testNickname",
                        "test1234",
                        true,
                        true,
                        true
                );

        UserCreateResponse response =
                new UserCreateResponse(
                        "testUser@example.com",
                        Instant.now(),
                        Instant.now()
                );

        when(userFacade.create(any(UserCreateRequest.class)))
                .thenReturn(response);

        mockMvc
                .perform(post("/api/v1/user/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status()
                        .isCreated())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andExpect(jsonPath("$.data.username")
                        .value("testUser@example.com"));

        verify(userFacade)
                .create(any(UserCreateRequest.class));

    }

    @Test
    @WithMockUser(
            username = "testUser@example.com",
            roles = "USER"
    )
    @DisplayName("회원 조회 성공")
    void getUserSuccess() throws Exception {

        UserAuthCache response =
                new UserAuthCache(
                        1L,
                        "testUser@example.com",
                        "USER"
                );

        when(userFacade.findByUsername("testUser@example.com"))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId")
                        .value(1L))
                .andExpect(jsonPath("$.data.username")
                        .value("testUser@example.com"))
                .andExpect(jsonPath("$.data.role")
                        .value("USER"));

        verify(userFacade)
                .findByUsername("testUser@example.com");
    }

    @Test
    @WithMockUser(
            username = "testUser@example.com",
            roles = "USER"
    )
    @DisplayName("회원 삭제 성공")
    void deleteUserSuccess() throws Exception {

        Instant deletedAt =
                Instant.parse("2026-08-11T00:00:00Z");

        UserDeleteResponse response =
                new UserDeleteResponse(
                        "testUser@example.com",
                        deletedAt
                );

        when(userFacade.delete("testUser@example.com"))
                .thenReturn(response);

        mockMvc.perform(delete("/api/v1/user/delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username")
                        .value("testUser@example.com"))
                .andExpect(jsonPath("$.data.deletedAt")
                        .value("2026-08-11T00:00:00Z"));

        verify(userFacade)
                .delete("testUser@example.com");
    }


    @Test
    @WithMockUser(
            username = "testUser@example.com",
            roles = "USER"
    )
    @DisplayName("존재하지 않는 회원 조회")
    void getUserNotFoundException() throws Exception {

        when(userFacade.findByUsername("testUser@example.com"))
                .thenThrow(new BaseException(
                        BaseExceptionEnum.USER_NOT_FOUND
                ));

        mockMvc.perform(get("/api/v1/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.errorCode")
                        .value("BASE.EXCEPTION.USER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message")
                        .value("해당 유저를 찾을 수 없습니다."));

        verify(userFacade)
                .findByUsername("testUser@example.com");
    }

    @Test
    @WithMockUser(
            username = "testUser@example.com",
            roles = "USER"
    )
    @DisplayName("존재하지 않는 회원 삭제")
    void deleteUserNotFoundException() throws Exception {

        when(userFacade.delete("testUser@example.com"))
                .thenThrow(new BaseException(
                        BaseExceptionEnum.USER_NOT_FOUND
                ));

        mockMvc.perform(delete("/api/v1/user/delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success")
                        .value(false))
                .andExpect(jsonPath("$.data")
                        .doesNotExist())
                .andExpect(jsonPath("$.error.errorCode")
                        .value("BASE.EXCEPTION.USER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message")
                        .value("해당 유저를 찾을 수 없습니다."));

        verify(userFacade)
                .delete("testUser@example.com");
    }

    @Test
    @DisplayName("username(email) 중복 조회 성공")
    void checkUsernameDuplicationSuccess() throws Exception {

        // given
        CheckUsernameDuplicationRequest request =
                new CheckUsernameDuplicationRequest(
                        "test@example.com"
                );

        CheckUsernameDuplicationResponse response =
                CheckUsernameDuplicationResponse.result(true);

        when(userFacade
                .checkUsernameDuplication(
                        any(CheckUsernameDuplicationRequest.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/v1/user/check/duplication/username")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper
                                        .writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andExpect(jsonPath("$.data.duplicate")
                        .value(true))
                .andExpect(jsonPath("$.data.message")
                        .value("이미 존재하는 이메일 입니다."));

        verify(userFacade)
                .checkUsernameDuplication(
                        any(CheckUsernameDuplicationRequest.class));
    }

    @Test
    @DisplayName("nickname 중복 조회 성공")
    void checkNicknameDuplicationSuccess() throws Exception {

        // given
        CheckNicknameDuplicationRequest request =
                new CheckNicknameDuplicationRequest(
                        "testNickname"
                );

        CheckNicknameDuplicationResponse response =
                CheckNicknameDuplicationResponse.result(true);

        when(userFacade
                .checkNicknameDuplication(
                        any(CheckNicknameDuplicationRequest.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/v1/user/check/duplication/nickname")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper
                                        .writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andExpect(jsonPath("$.data.duplicate")
                        .value(true))
                .andExpect(jsonPath("$.data.message")
                        .value("이미 존재하는 닉네임 입니다."));

        verify(userFacade)
                .checkNicknameDuplication(
                        any(CheckNicknameDuplicationRequest.class));
    }

}