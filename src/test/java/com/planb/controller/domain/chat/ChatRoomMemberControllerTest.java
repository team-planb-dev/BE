package com.planb.controller.domain.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import com.planb.domain.chat.controller.ChatRoomMemberController;
import com.planb.domain.chat.dto.request.AddChatRoomMemberRequest;
import com.planb.domain.chat.dto.request.DeleteChatRoomMemberRequest;
import com.planb.domain.chat.dto.response.AddChatUserResponse;
import com.planb.domain.chat.dto.response.DeleteChatUserResponse;
import com.planb.domain.chat.facade.ChatFacade;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatRoomMemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatRoomMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatFacade chatFacade;

    @Test
    @DisplayName("채팅방 멤버 추가 성공")
    void addMemberSuccess() throws Exception {

        // given
        Long roomId = 1L;

        Long userId = 10L;

        AddChatRoomMemberRequest request =
                new AddChatRoomMemberRequest(
                        roomId,
                        userId
                );

        AddChatUserResponse response =
                new AddChatUserResponse(
                        "woojuice",
                        roomId,
                        "woojuice님이 채팅방에 입장하셨습니다."
                );

        when(chatFacade.addChatUser(
                any(AddChatRoomMemberRequest.class)
        )).thenReturn(response);

        // when & then
        mockMvc.perform(
                        post("/api/v1/chat/member/add")
                                .contentType(MediaType
                                        .APPLICATION_JSON)
                                .content(objectMapper
                                        .writeValueAsString(request))
                )
                .andExpect(status()
                        .isOk())
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                MediaType
                                        .APPLICATION_JSON
                        )
                );

        verify(chatFacade)
                .addChatUser(
                        any(AddChatRoomMemberRequest.class)
                );
    }

    @Test
    @DisplayName("채팅방 멤버 삭제 성공")
    void deleteMemberSuccess() throws Exception {

        // given
        Long roomId = 1L;

        Long userId = 10L;

        DeleteChatRoomMemberRequest request =
                new DeleteChatRoomMemberRequest(
                        roomId,
                        userId
                );

        DeleteChatUserResponse response =
                new DeleteChatUserResponse(
                        roomId,
                        "woojuice",
                        "woojuice님이 퇴장하셨습니다."
                );

        when(chatFacade.deleteChatUser(
                any(DeleteChatRoomMemberRequest.class)
        )).thenReturn(response);


        // when & then
        mockMvc.perform(
                        delete("/api/v1/chat/member/delete")
                                .contentType(MediaType
                                        .APPLICATION_JSON)
                                .content(objectMapper
                                        .writeValueAsString(request))
                )
                .andExpect(status()
                        .isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                );

        verify(chatFacade)
                .deleteChatUser(
                        any(DeleteChatRoomMemberRequest.class)
                );
    }
}