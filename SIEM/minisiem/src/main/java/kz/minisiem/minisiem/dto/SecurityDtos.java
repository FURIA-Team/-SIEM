package kz.minisiem.minisiem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class SecurityDtos {
    private SecurityDtos() {
    }

    public record EventRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Pattern(regexp = "(?i)critical|high|medium|low") String severity,
            @NotBlank @Size(max = 100) String source,
            @NotBlank @Size(max = 200) String destination,
            @Size(max = 2000) String details,
            LocalDateTime occurredAt
    ) {
    }

    public record EventResponse(
            Long id,
            String title,
            String severity,
            String source,
            String destination,
            String status,
            String details,
            String submittedBy,
            LocalDateTime occurredAt
    ) {
    }

    public record AlertStatusRequest(
            @NotBlank @Pattern(regexp = "(?i)open|investigating|resolved") String status
    ) {
    }

    public record AlertResponse(
            Long id,
            Long eventId,
            String title,
            String severity,
            String source,
            String status,
            String details,
            LocalDateTime detectedAt
    ) {
    }

    public record LogResponse(
            Long id,
            String level,
            String category,
            String message,
            String actor,
            LocalDateTime createdAt
    ) {
    }

    public record UserManagementResponse(
            Long id,
            String username,
            String email,
            String role,
            LocalDateTime createdAt,
            LocalDateTime lastLoginAt
    ) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Pattern(regexp = "(?i)USER|ADMIN") String role
    ) {
    }

    public record BootstrapAdminRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 32, max = 256) String token
    ) {
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record ChartPoint(String label, long events, long threats) {
    }

    public record DashboardResponse(
            long securityEvents24h,
            long criticalThreats,
            long activeUsers,
            long openEvents,
            long unresolvedAlerts,
            String workspaceName,
            List<ChartPoint> activity,
            List<EventResponse> recentEvents,
            List<AlertResponse> priorityAlerts
    ) {
    }

    public record SettingsRequest(
            @NotBlank @Size(max = 100) String workspaceName,
            @NotBlank @Size(max = 60) String timezone,
            @Min(1) @Max(3650) int retentionDays
    ) {
    }

    public record SettingsResponse(
            String workspaceName,
            String timezone,
            int retentionDays,
            LocalDateTime updatedAt
    ) {
    }
}
