package com.visitor.system.common;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().success(true).data(data).message("OK").build();
    }

    public static ApiResponse<Void> successMessage(String message) {
        return ApiResponse.<Void>builder().success(true).message(message).build();
    }

    public static ApiResponse<Void> failure(String message) {
        return ApiResponse.<Void>builder().success(false).message(message).build();
    }
}
