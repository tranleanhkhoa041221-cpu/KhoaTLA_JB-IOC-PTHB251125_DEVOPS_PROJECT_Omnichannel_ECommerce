package com.quickbite.edu.exception;

import lombok.extern.slf4j.Slf4j;
import com.quickbite.edu.dto.ValidationError;
import com.quickbite.edu.dto.response.ApiResponse;
import com.quickbite.edu.entity.enums.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.BindException;
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
    public ResponseEntity<ApiResponse<?>> handleValidation(
            MethodArgumentNotValidException ex) {

        List<ValidationError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::buildValidationError)
                .toList();

        return badRequest(
                "Dữ liệu không hợp lệ",
                errors
        );
    }


    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleJsonParse(
            HttpMessageNotReadableException ex) {

        Throwable throwable = ex;

        while (throwable != null) {

            if (throwable instanceof tools.jackson.databind.exc.InvalidFormatException formatEx) {

                String fieldName = extractFieldName(
                        formatEx.getMessage()
                );

                ValidationError error = new ValidationError(
                        fieldName,
                        getMessageByType(formatEx.getTargetType())
                );

                return badRequest(
                        "Dữ liệu không hợp lệ",
                        List.of(error)
                );
            }

            throwable = throwable.getCause();
        }

        return badRequest(
                "Dữ liệu không hợp lệ",
                List.of(
                        new ValidationError(
                                "data",
                                "Dữ liệu không hợp lệ"
                        )
                )
        );
    }


    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        ValidationError error = new ValidationError(
                ex.getName(),
                getMessageByType(ex.getRequiredType())
        );

        return badRequest(
                "Dữ liệu không hợp lệ",
                List.of(error)
        );
    }


    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBindException(
            BindException ex) {

        List<ValidationError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::buildValidationError)
                .toList();

        return badRequest(
                "Dữ liệu không hợp lệ",
                errors
        );
    }


    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(
            NotFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        null
                ));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<?>> handleBadRequest(
            BadRequestException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        null
                ));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<?>> handleConflict(
            ConflictException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        null
                ));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Bạn không có quyền thực hiện thao tác này", null));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<?>> handleForbidden(
            ForbiddenException ex) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        null
                ));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<?>> handleDisabled(
            DisabledException ex) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        null
                ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrity(
            DataIntegrityViolationException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "Không thể thực hiện thao tác vì dữ liệu đang được liên kết hoặc bị trùng",
                        null
                ));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(
            Exception ex) {

        log.error("Loi khong xac dinh", ex);

        return ResponseEntity.status(
                        HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        null
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFound(
            NoResourceFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        "Endpoint không tồn tại: "
                                + ex.getResourcePath(),
                        null
                ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleBadCredential(
            BadCredentialsException e
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.getMessage(), null));
    }


    private ResponseEntity<ApiResponse<?>> badRequest(
            String message,
            List<ValidationError> errors) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        message,
                        errors
                ));
    }

    private ValidationError buildValidationError(FieldError err) {
        String message = err.getDefaultMessage();
        if (message == null) {
            message = "Dữ liệu không hợp lệ";
        }

        if ("typeMismatch".equals(err.getCode())) {
            String validationCodes = Arrays.toString(err.getCodes());

            if (validationCodes.contains("java.time.LocalDate")) {
                return new ValidationError(err.getField(), "Ngày không đúng định dạng dd/MM/yyyy");
            }
            if (validationCodes.contains("java.time.LocalDateTime")) {
                return new ValidationError(err.getField(), "Ngày giờ không đúng định dạng dd/MM/yyyy HH:mm");
            }
            if (validationCodes.contains("Integer") || validationCodes.contains("Long")
                    || validationCodes.contains(".int") || validationCodes.contains(".long")) {
                return new ValidationError(err.getField(), "Phải là số nguyên");
            }
            if (validationCodes.contains("Double") || validationCodes.contains("Float")
                    || validationCodes.contains("BigDecimal") || validationCodes.contains(".double") || validationCodes.contains(".float")) {
                return new ValidationError(err.getField(), "Phải là số");
            }

            if (validationCodes.contains("Role")) {
                return new ValidationError(err.getField(), "Vai trò không hợp lệ. Giá trị hợp lệ: "
                        + Arrays.stream(Role.values()).map(Enum::name).collect(Collectors.joining(", ")));
            }
//            if (validationCodes.contains("UserRole")) {
//                return new ValidationError(err.getField(), "Role không hợp lệ. Giá trị hợp lệ: "
//                        + Arrays.stream(UserRole.values()).map(Enum::name).collect(Collectors.joining(", ")));
//            }
//            if (validationCodes.contains("InternshipAssignmentsStatus")) {
//                return new ValidationError(err.getField(), "Status không hợp lệ. Giá trị hợp lệ: "
//                        + Arrays.stream(InternshipAssignmentsStatus.values()).map(Enum::name).collect(Collectors.joining(", ")));
//            }
        }

        if (message.contains("Role")) {
            return new ValidationError(err.getField(), "Vai trò không hợp lệ. Giá trị hợp lệ: "
                    + Arrays.stream(Role.values()).map(Enum::name).collect(Collectors.joining(", ")));
        }

//        if (message.contains("UserRole")) {
//            return new ValidationError(err.getField(), "Role không hợp lệ. Giá trị hợp lệ: "
//                    + Arrays.stream(UserRole.values()).map(Enum::name).collect(Collectors.joining(", ")));
//        }
//
//        if (message.contains("InternshipAssignmentsStatus")) {
//            return new ValidationError(err.getField(), "Status không hợp lệ. Giá trị hợp lệ: "
//                    + Arrays.stream(InternshipAssignmentsStatus.values()).map(Enum::name).collect(Collectors.joining(", ")));
//        }

        return new ValidationError(err.getField(), message);
    }

    private String extractFieldName(String message) {

        Matcher matcher = Pattern
                .compile("\\[\"(.*?)\"]")
                .matcher(message);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "data";
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


        if (type == Integer.class
                || type == Long.class
                || type == int.class
                || type == long.class) {

            return "Phải là số nguyên";
        }


        if (type == Double.class
                || type == Float.class
                || type == double.class
                || type == float.class
                || type == BigDecimal.class) {

            return "Phải là số";
        }


//        if (type == UserRole.class) {
//
//            return "Role không hợp lệ. Giá trị hợp lệ: "
//                    + Arrays.stream(UserRole.values())
//                    .map(Enum::name)
//                    .collect(Collectors.joining(", "));
//        }
//
//        if (type == InternshipAssignmentsStatus.class) {
//
//            return "Status không hợp lệ. Giá trị hợp lệ: "
//                    + Arrays.stream(
//                            InternshipAssignmentsStatus.values())
//                    .map(Enum::name)
//                    .collect(Collectors.joining(", "));
//        }

        if (type == Role.class) {
            return "Vai trò không hợp lệ. Giá trị hợp lệ: "
                    + Arrays.stream(Role.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
        }

        return "Dữ liệu không hợp lệ";
    }
}