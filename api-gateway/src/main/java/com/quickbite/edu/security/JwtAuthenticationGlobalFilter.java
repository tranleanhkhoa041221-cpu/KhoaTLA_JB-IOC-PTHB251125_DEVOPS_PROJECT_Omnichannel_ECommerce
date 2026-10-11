package com.quickbite.edu.security;

import com.quickbite.edu.dto.response.ApiResponse;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Chot chan xac thuc TRUNG TAM cua toan he thong (dung voi vai tro "Single Point of Entry"
 * cua API Gateway trong de bai). Chay SOM nhat co the (order rat am) de tu choi ngay request
 * khong hop le TRUOC KHI ton tai nguyen rate-limit/proxy sang service phia sau.
 *
 * Quy trinh:
 *  1) LOAI BO (strip) bat ky header X-User-Id/X-Username/X-User-Role nao client tu gui len -
 *     neu khong lam buoc nay, 1 client ac y co the tu gan "X-User-Role: ADMIN" de gia mao
 *     quyen admin voi cac service phia sau dang tin tuong mu quang vao header do.
 *  2) Neu request khop danh sach public-endpoints (login/register/vnpay webhook/GET catalog...)
 *     -> cho qua ngay, khong doi hoi Bearer token.
 *  3) Nguoc lai, bat buoc phai co header "Authorization: Bearer <token>" hop le (dung chu ky,
 *     chua het han) - sai/thieu deu tra ve 401 voi body dang ApiResponse chuan cua he thong.
 *  4) Token hop le -> giai ma claims (userId/username/role) -> gan lai thanh header
 *     X-User-Id/X-Username/X-User-Role TRUOC KHI forward - cac service phia sau (Order,
 *     Payment, Notification...) co the TIN TUONG cac header nay ma khong can tu verify JWT
 *     lai lan nua (Gateway da lam thay, tranh verify JWT lap lai o moi service).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenValidator jwtTokenValidator;
    private final PublicEndpointMatcher publicEndpointMatcher;
    private final ObjectMapper objectMapper;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder sanitizedRequestBuilder = exchange.getRequest().mutate()
                .headers(httpHeaders -> {
                    httpHeaders.remove(HEADER_USER_ID);
                    httpHeaders.remove(HEADER_USERNAME);
                    httpHeaders.remove(HEADER_USER_ROLE);
                });

        ServerHttpRequest sanitizedRequest = sanitizedRequestBuilder.build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        if (publicEndpointMatcher.isPublic(sanitizedRequest)) {
            return chain.filter(sanitizedExchange);
        }

        String authHeader = sanitizedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Thieu hoac sai dinh dang Authorization header (can 'Bearer <token>')");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!jwtTokenValidator.isValid(token)) {
            return unauthorized(exchange, "Token khong hop le hoac da het han");
        }

        try {
            Claims claims = jwtTokenValidator.getClaims(token);
            String username = claims.getSubject();
            Long userId = jwtTokenValidator.getUserId(token);
            String role = jwtTokenValidator.getRole(token);

            ServerHttpRequest enrichedRequest = sanitizedRequest.mutate()
                    .headers(httpHeaders -> {
                        if (userId != null) {
                            httpHeaders.set(HEADER_USER_ID, String.valueOf(userId));
                        }
                        if (username != null) {
                            httpHeaders.set(HEADER_USERNAME, username);
                        }
                        if (role != null) {
                            httpHeaders.set(HEADER_USER_ROLE, role);
                        }
                    })
                    .build();

            return chain.filter(sanitizedExchange.mutate().request(enrichedRequest).build());
        } catch (Exception ex) {
            log.warn("Khong the giai ma JWT claims: {}", ex.getMessage());
            return unauthorized(exchange, "Token khong hop le");
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Object> body = ApiResponse.error(message, null);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception ex) {
            bytes = ("{\"success\":false,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
