package com.quickbite.edu.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.quickbite.edu.dto.ValidationError;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/** Dong bo 100% voi ApiResponse cua cac service khac - de client nhan duoc CUNG 1 dinh dang
 * JSON du loi den tu Gateway (401/403/429/503) hay tu chinh service phia sau. */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private List<ValidationError> errors;

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true).message(message).data(data).errors(null)
                .timestamp(LocalDateTime.now()).build();
    }

    public static <T> ApiResponse<T> error(String message, List<ValidationError> errors) {
        return ApiResponse.<T>builder()
                .success(false).message(message).data(null).errors(errors)
                .timestamp(LocalDateTime.now()).build();
    }
}
