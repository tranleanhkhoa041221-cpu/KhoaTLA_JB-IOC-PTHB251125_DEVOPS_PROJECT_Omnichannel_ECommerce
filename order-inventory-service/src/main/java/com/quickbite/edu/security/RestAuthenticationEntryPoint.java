package com.quickbite.edu.security;

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
 * 401 UNAUTHORIZED - thieu Authorization header, token sai dinh dang, sai chu ky hoac het han.
 * Mac dinh Spring Security (khong co form-login/http-basic) se tra ve 403 cho truong hop nay
 * neu KHONG khai bao AuthenticationEntryPoint rieng - ghi de lai cho dung chuan 401 theo yeu cau.
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
        ApiResponse<Object> body = ApiResponse.error(
                "Thieu hoac token xac thuc khong hop le/da het han", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
