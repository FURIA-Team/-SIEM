package kz.minisiem.minisiem.controller;

import jakarta.validation.Valid;
import kz.minisiem.minisiem.dto.SecurityDtos.AlertResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.AlertStatusRequest;
import kz.minisiem.minisiem.dto.SecurityDtos.CreateUserRequest;
import kz.minisiem.minisiem.dto.SecurityDtos.DashboardResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.EventRequest;
import kz.minisiem.minisiem.dto.SecurityDtos.EventResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.LogResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.PageResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.SettingsRequest;
import kz.minisiem.minisiem.dto.SecurityDtos.SettingsResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.UserManagementResponse;
import kz.minisiem.minisiem.service.SecurityOperationsService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class SecurityOperationsController {
    private final SecurityOperationsService operations;

    public SecurityOperationsController(SecurityOperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/user/dashboard")
    public DashboardResponse dashboard(@RequestParam(defaultValue = "24h") String range) {
        return operations.dashboard(range);
    }

    @GetMapping("/user/events")
    public PageResponse<EventResponse> events(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return operations.listEvents(query, severity, hours, page, size);
    }

    @GetMapping("/user/events/export")
    public ResponseEntity<byte[]> exportEvents(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "24") int hours
    ) {
        byte[] content = operations.exportEvents(query, severity, hours).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("minisiem-security-events.csv").build().toString())
                .body(content);
    }

    @PostMapping("/user/events")
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody EventRequest request, Authentication authentication
    ) {
        return ResponseEntity.status(201).body(operations.createEvent(request, authentication.getName()));
    }

    @GetMapping("/user/alerts")
    public PageResponse<AlertResponse> alerts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return operations.listAlerts(query, severity, status, page, size);
    }

    @GetMapping("/user/alerts/{id}")
    public AlertResponse alert(@PathVariable long id) {
        return operations.getAlert(id);
    }

    @PatchMapping("/user/alerts/{id}")
    public AlertResponse updateAlertStatus(
            @PathVariable long id,
            @Valid @RequestBody AlertStatusRequest request,
            Authentication authentication
    ) {
        return operations.updateAlertStatus(id, request, authentication.getName());
    }

    @GetMapping("/user/logs")
    public PageResponse<LogResponse> logs(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return operations.listLogs(query, page, size);
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserManagementResponse> users(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return operations.listUsers(query, page, size);
    }

    @PostMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserManagementResponse> createUser(
            @Valid @RequestBody CreateUserRequest request, Authentication authentication
    ) {
        return ResponseEntity.status(201).body(operations.createUser(request, authentication.getName()));
    }

    @DeleteMapping("/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable long id, Authentication authentication) {
        operations.deleteUser(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsResponse settings() {
        return operations.getSettings();
    }

    @PutMapping("/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsResponse updateSettings(
            @Valid @RequestBody SettingsRequest request, Authentication authentication
    ) {
        return operations.updateSettings(request, authentication.getName());
    }
}
