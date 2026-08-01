package com.planb.integration.domain.user.dto;

import jakarta.servlet.http.Cookie;

public record LoginResult(String accessToken,
                          Cookie refreshTokenCookie) {
}
