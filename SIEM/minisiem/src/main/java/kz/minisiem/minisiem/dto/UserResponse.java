package kz.minisiem.minisiem.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        String role,
        LocalDateTime createdAt
) {
}
