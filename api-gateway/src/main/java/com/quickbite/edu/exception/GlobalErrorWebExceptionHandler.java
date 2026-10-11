package com.quickbite.edu.exception;

import com.quickbite.edu.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Dang ky voi @Order(-2) de chay TRUOC DefaultErrorWebExceptionHandler cua Spring Boot
 * (order mac dinh -1) - thay the hoan toan trang loi HTML/Whitelabel mac dinh bang JSON
 * dung dinh dang ApiResponse CHUNG cua ca he thong, de client nhan duoc 1 kieu JSON duy
 * nhat du loi phat sinh tu Gateway hay tu service phia sau.
 *
 * Quan trong: KHONG bao gio tra nguyen stack trace/thong tin noi bo ra ngoai cho client -
 * chi log chi tiet o server, response chi chua thong diep an toan, chung chung.
 */
@Component
@Order(-2)
@Slf4j
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status;
        String message;

        if (ex instanceof ResponseStatusException responseStatusException) {
            status = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            message = status == HttpStatus.NOT_FOUND
                    ? "Khong tim thay duong dan API nay"
                    : responseStatusException.getReason() != null
                        ? responseStatusException.getReason() : "Da co loi xay ra";
        } else if (ex instanceof ConnectException || ex instanceof TimeoutException
                || ex.getClass().getSimpleName().contains("TimeoutException")) {
            // Service phia sau dang DOWN hoac khong phan hoi kip - day chinh la tinh huong
            // "tat dot ngot 1 service" ma muc 5 de bai yeu cau demo: Gateway van phai tra ve
            // JSON ro rang (503) thay vi crash/treo, de cac service doc lap khac khong bi anh huong.
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Dich vu hien khong kha dung, vui long thu lai sau";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Da co loi xay ra o Gateway";
        }

        log.error("Gateway error - status={} path={}", status,
                exchange.getRequest().getURI().getPath(), ex);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Object> body = ApiResponse.error(message, null);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializationError) {
            bytes = ("{\"success\":false,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
