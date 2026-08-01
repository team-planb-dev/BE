package com.planb.controller.domain.user;


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
                        "testUser",
                        "testNickname",
                        "1234"
                );

        UserCreateResponse response =
                new UserCreateResponse(
                        "testUser",
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
                        .value("testUser"));

        verify(userFacade)
                .create(any(UserCreateRequest.class));

    }

    @Test
    @WithMockUser(
            username = "testUser",
            roles = "USER"
    )
    @DisplayName("회원 조회 성공")
    void getUserSuccess() throws Exception {

        UserAuthCache response =
                new UserAuthCache(
                        1L,
                        "testUser",
                        "USER"
                );

        when(userFacade.findByUsername("testUser"))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId")
                        .value(1L))
                .andExpect(jsonPath("$.data.username")
                        .value("testUser"))
                .andExpect(jsonPath("$.data.role")
                        .value("USER"));

        verify(userFacade)
                .findByUsername("testUser");
    }

    @Test
    @WithMockUser(
            username = "testUser",
            roles = "USER"
    )
    @DisplayName("회원 삭제 성공")
    void deleteUserSuccess() throws Exception {

        Instant deletedAt =
                Instant.parse("2026-07-21T00:00:00Z");

        UserDeleteResponse response =
                new UserDeleteResponse(
                        "testUser",
                        deletedAt
                );

        when(userFacade.delete("testUser"))
                .thenReturn(response);

        mockMvc.perform(delete("/api/v1/user/delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username")
                        .value("testUser"))
                .andExpect(jsonPath("$.data.deletedAt")
                        .value("2026-07-21T00:00:00Z"));

        verify(userFacade)
                .delete("testUser");
    }


    // TODO: username 중복 확인 API 구현 후 활성화
    /*
    @Test
    @DisplayName("회원가입 중복 아이디 예외")
    void createUserDuplicateException() throws Exception {

        UserCreateRequest request =
                new UserCreateRequest(
                        "testUser",
                        "1234"
                );

        when(userFacade
                .create(any(UserCreateRequest.class)))
                .thenThrow(new BaseException(
                        BaseExceptionEnum
                                .USER_USERNAME_ALREADY_EXISTS
                ));

        mockMvc
                .perform(post("/api/v1/user/create")
                        .contentType(MediaType
                                .APPLICATION_JSON)
                        .content(objectMapper
                                .writeValueAsString(request)))
                .andExpect(status()
                        .isConflict())
                .andExpect(jsonPath("$.success")
                        .value(false));

        verify(userFacade)
                .create(any(UserCreateRequest.class));
    }
     */

    @Test
    @WithMockUser(
            username = "testUser",
            roles = "USER"
    )
    @DisplayName("존재하지 않는 회원 조회")
    void getUserNotFoundException() throws Exception {

        when(userFacade.findByUsername("testUser"))
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
                .findByUsername("testUser");
    }

    @Test
    @WithMockUser(
            username = "testUser",
            roles = "USER"
    )
    @DisplayName("존재하지 않는 회원 삭제")
    void deleteUserNotFoundException() throws Exception {

        when(userFacade.delete("testUser"))
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
                .delete("testUser");
    }

}