package com.planb.global.utils.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;


public class JsonResponseUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void writeJsonResponse(HttpStatus httpStatus,
                                         HttpServletResponse httpServletResponse,
                                         Object object)
    throws IOException {

        httpServletResponse
                .setContentType("application/json");
        httpServletResponse
                .setCharacterEncoding("UTF-8");
        httpServletResponse
                .setStatus(httpStatus.value());
        httpServletResponse
                .getWriter().write(objectMapper.writeValueAsString(object));
    }

}
