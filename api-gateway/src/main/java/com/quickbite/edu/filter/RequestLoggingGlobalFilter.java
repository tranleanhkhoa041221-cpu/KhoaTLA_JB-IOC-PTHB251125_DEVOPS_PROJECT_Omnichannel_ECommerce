package com.quickbite.edu.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Chay DAU TIEN trong toan bo filter chain (HIGHEST_PRECEDENCE) de moi request - ke ca
 * request bi JwtAuthenticationGlobalFilter tu choi - deu co 1 "X-Request-Id" xuyen suot,
 * phuc vu truy vet log qua nhieu service (poor-man's correlation id, buoc dem truoc khi
 * dau tu day du Jaeger/Zipkin + OpenTelemetry nhu de bai mo ta o muc 4.6). Cac service
 * phia sau co the doc lai header nay va dua vao MDC cua log framework rieng.
 */
@Component
@Slf4j
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        String finalRequestId = requestId;

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID_HEADER, finalRequestId)
                .build();
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, finalRequestId);

        long startedAt = System.currentTimeMillis();
        log.info("--> [{}] {} {}", finalRequestId, request.getMethod(), request.getURI().getPath());

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doOnSuccess(unused -> logCompletion(exchange, finalRequestId, startedAt))
                .doOnError(error -> logCompletion(exchange, finalRequestId, startedAt));
    }

    private void logCompletion(ServerWebExchange exchange, String requestId, long startedAt) {
        long durationMs = System.currentTimeMillis() - startedAt;
        Integer status = exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value() : null;
        log.info("<-- [{}] status={} durationMs={}", requestId, status, durationMs);
    }
}
