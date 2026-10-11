package com.quickbite.edu.config;

import com.quickbite.edu.security.JwtTokenValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * KeyResolver TU DOC va GIAI MA Authorization header CUA CHINH REQUEST GOC (khong phu
 * thuoc vao viec JwtAuthenticationGlobalFilter da chay truoc hay chua) - tranh rang buoc
 * ve thu tu thuc thi giua GlobalFilter va GatewayFilterFactory (RequestRateLimiter), von
 * la 2 co che sap xep order khac nhau trong Spring Cloud Gateway va de gay loi tinh vi
 * neu ca 2 dua tren gia dinh "ai chay truoc ai".
 */
@Configuration
@RequiredArgsConstructor
public class RateLimiterConfig {

    private final JwtTokenValidator jwtTokenValidator;

    /** Dung cho cac route nhay cam (login/register) - LUON key theo IP, bat ke co token hay khong. */
    // @Primary: RequestRateLimiterGatewayFilterFactory inject 1 KeyResolver mac dinh; co 2 bean thi khoi dong that bai
    // ("expected single matching bean but found 2"). Cac route van chi dinh key-resolver rieng trong application.yml.
    @Bean
    @Primary
    public KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just("ip:" + resolveClientIp(exchange.getRequest()));
    }

    /** Dung cho cac route thong thuong - key theo user (tu token) neu co, khong thi theo IP. */
    @Bean
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String username = jwtTokenValidator.getUsername(authHeader.substring(7));
                    if (username != null && !username.isBlank()) {
                        return Mono.just("user:" + username);
                    }
                } catch (Exception ignored) {
                    // Token khong hop le -> roi xuong fallback theo IP; JwtAuthenticationGlobalFilter
                    // se la noi chinh thuc tra ve 401 cho request nay, KeyResolver chi lo rate-limit.
                }
            }
            return Mono.just("ip:" + resolveClientIp(exchange.getRequest()));
        };
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
    }
}
