package com.planb.unit.global.security.facade;


import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.planb.global.security.dto.response.ReissueResponse;
import com.planb.global.security.facade.RefreshFacade;
import com.planb.global.security.service.RefreshService;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshFacadeTest {

    @Mock

    private RefreshService refreshService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private RefreshFacade refreshFacade;

    @Test
    @DisplayName("토큰 재발급 성공시, ReissueResponse 반환")
    void reissueSuccess() {

        // given
        ReissueResponse response = new ReissueResponse(
                ReissueResponse
                        .ReissueStatus
                        .REFRESH_REISSUED,
                "2026-07-20T15:00:00",
                "new-access-token",
                "new-refresh-token"
        );

        when(refreshService
                .refreshCookies(request))
                .thenReturn(response);

        // when
        ReissueResponse result = refreshFacade
                .reissue(request);

        // then

        assertThat(result)
                .isSameAs(response);

        verify(refreshService)
                .refreshCookies(request);
    }
}