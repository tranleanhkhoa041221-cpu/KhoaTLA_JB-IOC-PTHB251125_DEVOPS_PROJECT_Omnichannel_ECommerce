package com.quickbite.edu.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * - GET  /api/catalog/**  : PUBLIC - khach chua dang nhap van phai duyet/tim san pham duoc
 *   (dung voi "GET /api/catalog/**" da duoc danh dau public o API Gateway).
 * - POST/PUT/DELETE /api/catalog/products/** : ADMIN hoac SELLER - nguoi ban (SELLER) can tu
 *   quan ly san pham cua minh. SUA LOI TICH HOP (regression): ban truoc cua file nay dung
 *   ".anyRequest().hasRole(\"ADMIN\")" cho TOAN BO request con lai, chan ca SELLER - trong khi
 *   identity-user-service (UserServiceImpl.register()) CHO PHEP tu dang ky voi role "SELLER"
 *   (chi chan tu dang ky "ADMIN"), va enum Role van co gia tri SELLER. Truoc do du an co 1
 *   HandlerInterceptor rieng (AdminRoleInterceptor, da bi xoa khoi codebase) cho phep ca
 *   ADMIN+SELLER quan ly /products (chi /categories, /brands la ADMIN-only) - gio chuyen logic
 *   nay thang vao Spring Security (don gian/sach hon, cung 1 co che voi cac service khac trong
 *   du an - vd order-inventory-service/payment-billing-service deu dung hasRole/hasAnyRole
 *   truc tiep trong SecurityConfig, khong dung interceptor rieng) de SELLER khong con bi 403
 *   khi tao/sua/xoa san pham cua chinh ho.
 * - POST/PUT/DELETE /api/catalog/categories/**, /api/catalog/brands/** : CHI ADMIN - quan ly
 *   danh muc/thuong hieu toan he thong, KHONG danh cho SELLER hay khach.
 * - gRPC (ProductGrpcService) nam NGOAI pham vi Spring Security (Servlet filter chain) - duoc
 *   bao ve o tang mang (chi Order Service trong cung Docker network moi goi toi duoc, port gRPC
 *   KHONG duoc public qua Nginx/UFW - xem huong dan VPS trong de bai).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/catalog/products/**").hasAnyRole("ADMIN", "SELLER")
                        .requestMatchers(HttpMethod.PUT, "/api/catalog/products/**").hasAnyRole("ADMIN", "SELLER")
                        .requestMatchers(HttpMethod.DELETE, "/api/catalog/products/**").hasAnyRole("ADMIN", "SELLER")
                        .anyRequest().hasRole("ADMIN"))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
