package com.quickbite.edu.config;

import com.quickbite.edu.dto.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * SUA LOI: truoc ban sua nay, SecurityConfig KHONG khai bao AuthenticationEntryPoint rieng.
 * Mac dinh Spring Security (khong co form-login/http-basic) tra ve 403 FORBIDDEN cho ca truong
 * hop "thieu/sai token" lan "dung token nhung sai quyen", vi pham yeu cau de bai: phai phan biet
 * 401 (chua xac thuc) va 403 (xac thuc roi nhung khong du quyen). Bean nay ep dung 401.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error("Thieu hoac token xac thuc khong hop le/da het han", null)));
    }
}
