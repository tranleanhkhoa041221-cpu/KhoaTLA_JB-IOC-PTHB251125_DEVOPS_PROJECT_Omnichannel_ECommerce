package com.quickbite.edu.controller;

import com.quickbite.edu.dto.response.ApiResponse;
import com.quickbite.edu.dto.response.UserContactResponse;
import com.quickbite.edu.exception.ForbiddenException;
import com.quickbite.edu.service.interfaces.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API noi bo cho cac microservice khac (Notification Service) lay email/SĐT khach hang.
 * Khong expose ra Internet cong cong — chi goi trong Docker network / qua Eureka.
 */
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserContactResponse>> getContactById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey
    ) {
        validateApiKey(apiKey);
        UserContactResponse response = userService.getContactById(id);
        return ResponseEntity.ok(ApiResponse.success("Lay thong tin lien he thanh cong", response));
    }

    private void validateApiKey(String apiKey) {
        // 403 Forbidden dung ngu nghia hon 400: request/header hop le ve MAT CU PHAP,
        // chi la khong co QUYEN goi (thieu/sai internal API key) - dung ForbiddenException
        // (co san trong project, da duoc GlobalExceptionHandler xu ly -> HTTP 403).
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            throw new ForbiddenException("Internal API key khong hop le hoac bi thieu");
        }
    }
}
