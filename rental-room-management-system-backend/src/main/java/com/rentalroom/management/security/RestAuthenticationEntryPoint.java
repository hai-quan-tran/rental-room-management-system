package com.rentalroom.management.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Without this, Spring Security's default entry point (Http403ForbiddenEntryPoint) answers every
 * missing/invalid/expired-token request with an empty-body 403 — indistinguishable from a real
 * @PreAuthorize role rejection, and invisible to the frontend's refresh-on-401 interceptor. This
 * makes "not authenticated" consistently a 401 with the standard ApiResponse envelope, so an expired
 * access token triggers a silent refresh instead of forcing the user to log in again.
 *
 * Written by hand (no ObjectMapper) since this runs inside the security filter chain, before
 * Spring MVC's message converters are in the picture.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"success":false,"message":"Phiên đăng nhập đã hết hạn hoặc không hợp lệ","data":null,"timestamp":"%s"}
                """.formatted(Instant.now()).strip());
    }
}
