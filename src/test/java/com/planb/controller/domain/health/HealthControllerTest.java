package com.planb.controller.domain.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.domain.health.controller.HealthController;
import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.dto.request.DeleteCompanionRequest;
import com.planb.domain.health.dto.response.AddCompanionResponse;
import com.planb.domain.health.dto.response.CompanionSummaryResponse;
import com.planb.domain.health.dto.response.DeleteCompanionResponse;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.facade.HealthFacade;
import com.planb.global.config.app.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AppConfig.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HealthFacade healthFacade;

    @Test
    @WithMockUser(
            username = "testUser@example.com",
            roles = "USER"
    )
    @DisplayName("동행자 등록 성공")
    void addTravelerSuccess() throws Exception {

        // given
        AddCompanionRequest request =
                new AddCompanionRequest(
                        "동행인1",
                        false,
                        false,
                        null,
                        null,
                        List.of(),
                        List.of()
                );

        AddCompanionResponse response =
                new AddCompanionResponse(
                        "동행인1",
                        "동행인이 등록되었습니다."
                );

        when(healthFacade.addCompanion(
                any(AddCompanionRequest.class),
                eq("testUser@example.com")
        )).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/health/add-traveler")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success")
                        .value(true));

        verify(healthFacade)
                .addCompanion(
                        any(AddCompanionRequest.class),
                        eq("testUser@example.com")
                );
    }

    @Test
    @WithMockUser(
            username = "testUser@example.com",
            roles = "USER"
    )
    @DisplayName("동행자 삭제 성공")
    void deleteCompanionSuccess() throws Exception {

        // given
        DeleteCompanionRequest request =
                new DeleteCompanionRequest(1L);

        DeleteCompanionResponse response =
                new DeleteCompanionResponse(
                        "해당 동행인 정보가 삭제되었습니다."
                );

        when(healthFacade.deleteCompanion(
                any(DeleteCompanionRequest.class),
                eq("testUser@example.com")
        )).thenReturn(response);

        // when & then
        mockMvc.perform(delete("/api/v1/health/delete-companion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success")
                        .value(true));

        verify(healthFacade)
                .deleteCompanion(
                        any(DeleteCompanionRequest.class),
                        eq("testUser@example.com")
                );
    }

    @Test
    @WithMockUser(
            username = "testUser@example.com",
            roles = "USER"
    )
    @DisplayName("동행자 간단 조회 성공")
    void getCompanionSummarySuccess() throws Exception {

        // given
        CompanionSummaryResponse response =
                new CompanionSummaryResponse(
                        List.of(
                                new CompanionSummaryResponse.CompanionSummaryDetail(
                                        1L,
                                        "동행인1",
                                        true,
                                        true,
                                        DiseaseType.DIABETES
                                )
                        )
                );

        when(healthFacade.getCompanionSummary(
                "testUser@example.com"
        )).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get("/api/v1/health/get-companion-summary")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success")
                        .value(true));

        verify(healthFacade)
                .getCompanionSummary(
                        "testUser@example.com"
                );
    }
}