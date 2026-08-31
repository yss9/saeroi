package com.ssafy.upcycle.common.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.security.SecurityErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RestAccessDeniedHandler implements AccessDeniedHandler, HandlerExceptionResolver {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(SecurityErrorCode.FORBIDDEN.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(SecurityErrorCode.FORBIDDEN));
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        if (!(exception instanceof AccessDeniedException accessDeniedException)) {
            return null;
        }
        try {
            handle(request, response, accessDeniedException);
            return new ModelAndView();
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to write access denied response", ioException);
        }
    }
}
