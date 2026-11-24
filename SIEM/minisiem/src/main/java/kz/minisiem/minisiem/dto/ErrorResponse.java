package kz.minisiem.minisiem.dto;

import java.util.Map;

public record ErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> validationErrors
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Map.of());
    }
}
