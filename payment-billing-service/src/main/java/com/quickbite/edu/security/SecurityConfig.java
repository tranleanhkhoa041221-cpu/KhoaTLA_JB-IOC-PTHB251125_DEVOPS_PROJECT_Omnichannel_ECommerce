package com.quickbite.edu.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * - /api/payments/vnpay/**  : PUBLIC TUYET DOI - VNPay goi webhook server-to-server, KHONG co
 *   va KHONG THE co Bearer token cua user. Chong gia mao duoc bao dam rieng boi
 *   vnp_SecureHash (HMAC-SHA512) trong VnPayCallbackController/VnPayService, KHONG phai JWT.
 * - /api/payments/{id}/refund : chi ADMIN (hoan tien la nghiep vu ke toan/CSKH, khong danh cho khach).
 * - Con lai                 : bat buoc dang nhap, ownership (chi xem giao dich CUA CHINH MINH)
 *   duoc kiem tra THEM o PaymentController.
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
                        .requestMatchers("/api/payments/vnpay/**", "/actuator/**",
                                "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/payments/*/refund").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
