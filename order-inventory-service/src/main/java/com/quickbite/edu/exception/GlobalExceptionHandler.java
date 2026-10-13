package com.quickbite.edu.exception;

import lombok.extern.slf4j.Slf4j;
import com.quickbite.edu.dto.ValidationError;
import com.quickbite.edu.dto.response.ApiResponse;
import com.quickbite.edu.entity.enums.OrderStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::buildValidationError)
                .toList();
        return badRequest("Du lieu khong hop le", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleJsonParse(HttpMessageNotReadableException ex) {
        return badRequest("Du lieu JSON khong hop le", List.of(new ValidationError("data", "Du lieu khong hop le")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ValidationError error = new ValidationError(ex.getName(), getMessageByType(ex.getRequiredType()));
        return badRequest("Du lieu khong hop le", List.of(error));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBindException(BindException ex) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::buildValidationError)
                .toList();
        return badRequest("Du lieu khong hop le", errors);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<?>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<?>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<?>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Khong the thuc hien thao tac vi du lieu dang bi trung hoac lien ket", null));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<?>> handleOptimisticLock(org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Du lieu vua bi thay doi boi tien trinh khac, vui long thu lai", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Endpoint khong ton tai: " + ex.getResourcePath(), null));
    }

    /** Loi tu dich vu phia sau (gRPC Identity/Product cham hoac khong ket noi duoc): loi phia SERVER, khong phai loi cua client. */
    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<ApiResponse<?>> handleDownstream(DownstreamServiceException ex) {
        log.warn("Dich vu phia sau loi ({}): {}", ex.getStatus().value(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .header("Retry-After", "2")
                .body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception ex) {
        log.error("Loi khong xac dinh", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ex.getMessage(), null));
    }

    private ResponseEntity<ApiResponse<?>> badRequest(String message, List<ValidationError> errors) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message, errors));
    }

    private ValidationError buildValidationError(FieldError err) {
        String message = err.getDefaultMessage() != null ? err.getDefaultMessage() : "Du lieu khong hop le";
        if ("typeMismatch".equals(err.getCode())) {
            String validationCodes = Arrays.toString(err.getCodes());
            if (validationCodes.contains("OrderStatus")) {
                return new ValidationError(err.getField(), "Trang thai khong hop le. Gia tri hop le: "
                        + Arrays.stream(OrderStatus.values()).map(Enum::name).collect(Collectors.joining(", ")));
            }
        }
        return new ValidationError(err.getField(), message);
    }

    private String extractFieldName(String message) {
        Matcher matcher = Pattern.compile("\\[\"(.*?)\"]").matcher(message == null ? "" : message);
        return matcher.find() ? matcher.group(1) : "data";
    }

    private String getMessageByType(Class<?> type) {
        if (type == null) {
            return "Du lieu khong hop le";
        }
        if (type == LocalDate.class) {
            return "Ngay khong dung dinh dang dd/MM/yyyy";
        }
        if (type == LocalDateTime.class) {
            return "Ngay gio khong dung dinh dang dd/MM/yyyy HH:mm";
        }
        if (type == Integer.class || type == Long.class || type == int.class || type == long.class) {
            return "Phai la so nguyen";
        }
        if (type == Double.class || type == Float.class || type == double.class || type == float.class
                || type == BigDecimal.class) {
            return "Phai la so";
        }
        if (type == OrderStatus.class) {
            return "Trang thai khong hop le. Gia tri hop le: "
                    + Arrays.stream(OrderStatus.values()).map(Enum::name).collect(Collectors.joining(", "));
        }
        return "Du lieu khong hop le";
    }
}
