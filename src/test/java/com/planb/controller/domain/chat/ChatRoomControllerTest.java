package com.planb.controller.domain.chat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.planb.domain.chat.controller.ChatRoomController;
import com.planb.domain.chat.dto.request.CreateChatRoomRequest;
import com.planb.domain.chat.dto.request.DeleteChatRoomRequest;
import com.planb.domain.chat.dto.response.CreateChatRoomResponse;
import com.planb.domain.chat.dto.response.DeleteChatRoomResponse;
import com.planb.domain.chat.facade.ChatFacade;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatRoomController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatFacade chatFacade;

    @Test
    @WithMockUser(
            username = "testUser",
            roles = "USER"
    )
    @DisplayName("채팅방 생성 성공")
    void createChatRoomSuccess() throws Exception {

        // given
        CreateChatRoomRequest request =
                mock(CreateChatRoomRequest.class);

        CreateChatRoomResponse response =
                mock(CreateChatRoomResponse.class);

        when(chatFacade.createChatRoom(
                any(CreateChatRoomRequest.class)
        )).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/chat/room/create")
                        .contentType(MediaType
                                .APPLICATION_JSON)
                        .content(objectMapper
                                .writeValueAsString(request)))
                .andExpect(status()
                        .isCreated())
                .andExpect(jsonPath("$.success")
                        .value(true));

        verify(chatFacade)
                .createChatRoom(
                        any(CreateChatRoomRequest.class)
                );
    }

    @Test
    @WithMockUser(
            username = "testUser",
            roles = "USER"
    )
    @DisplayName("채팅방 삭제 성공")
    void deleteChatRoomSuccess() throws Exception {
        // given
        DeleteChatRoomRequest request =
                mock(DeleteChatRoomRequest.class);

        DeleteChatRoomResponse response =
                mock(DeleteChatRoomResponse.class);

        when(chatFacade.deleteChatRoom(
                any(DeleteChatRoomRequest.class)
        )).thenReturn(response);

        // when & then
        mockMvc.perform(delete("/api/v1/chat/room/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success")
                        .value(true));

        verify(chatFacade)
                .deleteChatRoom(
                        any(DeleteChatRoomRequest.class)
                );
    }
}