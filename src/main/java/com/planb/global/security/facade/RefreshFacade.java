package com.planb.global.security.facade;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.dto.response.ReissueResponse;
import com.planb.global.security.service.RefreshService;

@Component
@RequiredArgsConstructor
public class RefreshFacade {

    private final RefreshService refreshService;

    public ReissueResponse reissue(HttpServletRequest request){

        ReissueResponse response = refreshService.refreshCookies(request);

        return switch (response.status()) {

            case REFRESH_EXPIRED ->
                    throw new BaseException(BaseExceptionEnum.REFRESH_TOKEN_NOT_FOUND);

            case REFRESH_NULL ->
                    throw new BaseException(BaseExceptionEnum.REFRESH_TOKEN_EXPIRED);

            default ->
                    response;
        };

    }
}
