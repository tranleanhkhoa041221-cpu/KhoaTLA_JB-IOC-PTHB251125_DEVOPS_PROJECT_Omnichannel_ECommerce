package com.quickbite.edu.exception;

import lombok.extern.slf4j.Slf4j;
import com.quickbite.edu.dto.ValidationError;
import com.quickbite.edu.dto.response.ApiResponse;
import com.quickbite.edu.entity.enums.ProductStatus;
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
        return badRequest("Dữ liệu không hợp lệ", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleJsonParse(HttpMessageNotReadableException ex) {
        Throwable throwable = ex;
        while (throwable != null) {
            if (throwable instanceof tools.jackson.databind.exc.InvalidFormatException formatEx) {
                String fieldName = extractFieldName(formatEx.getMessage());
                ValidationError error = new ValidationError(fieldName, getMessageByType(formatEx.getTargetType()));
                return badRequest("Dữ liệu không hợp lệ", List.of(error));
            }
            throwable = throwable.getCause();
        }
        return badRequest("Dữ liệu không hợp lệ", List.of(new ValidationError("data", "Dữ liệu không hợp lệ")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ValidationError error = new ValidationError(ex.getName(), getMessageByType(ex.getRequiredType()));
        return badRequest("Dữ liệu không hợp lệ", List.of(error));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBindException(BindException ex) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::buildValidationError)
                .toList();
        return badRequest("Dữ liệu không hợp lệ", errors);
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Không thể thực hiện thao tác vì dữ liệu đang được liên kết hoặc bị trùng", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Endpoint không tồn tại: " + ex.getResourcePath(), null));
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
        String message = err.getDefaultMessage() != null ? err.getDefaultMessage() : "Dữ liệu không hợp lệ";
        if ("typeMismatch".equals(err.getCode())) {
            String validationCodes = Arrays.toString(err.getCodes());
            if (validationCodes.contains("ProductStatus")) {
                return new ValidationError(err.getField(), "Trạng thái không hợp lệ. Giá trị hợp lệ: "
                        + Arrays.stream(ProductStatus.values()).map(Enum::name).collect(Collectors.joining(", ")));
            }
        }
        return new ValidationError(err.getField(), message);
    }

    private String extractFieldName(String message) {
        Matcher matcher = Pattern.compile("\\[\"(.*?)\"]").matcher(message);
        return matcher.find() ? matcher.group(1) : "data";
    }

    private String getMessageByType(Class<?> type) {
        if (type == null) {
            return "Dữ liệu không hợp lệ";
        }
        if (type == LocalDate.class) {
            return "Ngày không đúng định dạng dd/MM/yyyy";
        }
        if (type == LocalDateTime.class) {
            return "Ngày giờ không đúng định dạng dd/MM/yyyy HH:mm";
        }
        if (type == Integer.class || type == Long.class || type == int.class || type == long.class) {
            return "Phải là số nguyên";
        }
        if (type == Double.class || type == Float.class || type == double.class || type == float.class
                || type == BigDecimal.class) {
            return "Phải là số";
        }
        if (type == ProductStatus.class) {
            return "Trạng thái không hợp lệ. Giá trị hợp lệ: "
                    + Arrays.stream(ProductStatus.values()).map(Enum::name).collect(Collectors.joining(", "));
        }
        return "Dữ liệu không hợp lệ";
    }
}
