package kz.minisiem.minisiem.service;

import kz.minisiem.minisiem.dto.SecurityDtos.AlertResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.AlertStatusRequest;
import kz.minisiem.minisiem.dto.SecurityDtos.ChartPoint;
import kz.minisiem.minisiem.dto.SecurityDtos.CreateUserRequest;
import kz.minisiem.minisiem.dto.SecurityDtos.DashboardResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.EventRequest;
import kz.minisiem.minisiem.dto.SecurityDtos.EventResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.LogResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.PageResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.SettingsRequest;
import kz.minisiem.minisiem.dto.SecurityDtos.SettingsResponse;
import kz.minisiem.minisiem.dto.SecurityDtos.UserManagementResponse;
import kz.minisiem.minisiem.entity.SecurityAlert;
import kz.minisiem.minisiem.entity.SecurityEvent;
import kz.minisiem.minisiem.entity.SystemLog;
import kz.minisiem.minisiem.entity.SystemSettings;
import kz.minisiem.minisiem.entity.User;
import kz.minisiem.minisiem.repository.SecurityAlertRepository;
import kz.minisiem.minisiem.repository.SecurityEventRepository;
import kz.minisiem.minisiem.repository.SystemLogRepository;
import kz.minisiem.minisiem.repository.SystemSettingsRepository;
import kz.minisiem.minisiem.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SecurityOperationsService {
    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> ALERT_STATUSES = Set.of("OPEN", "INVESTIGATING", "RESOLVED");
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);
    private static final DateTimeFormatter CLOCK_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

    private final SecurityEventRepository events;
    private final SecurityAlertRepository alerts;
    private final SystemLogRepository logs;
    private final SystemSettingsRepository settings;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public SecurityOperationsService(
            SecurityEventRepository events,
            SecurityAlertRepository alerts,
            SystemLogRepository logs,
            SystemSettingsRepository settings,
            UserRepository users,
            PasswordEncoder passwordEncoder
    ) {
        this.events = events;
        this.alerts = alerts;
        this.logs = logs;
        this.settings = settings;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(String range) {
        int hours = rangeHours(range);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusHours(24);
        LocalDateTime rangeStart = now.minusHours(hours);
        List<SecurityEvent> rangeEvents = events.findAllByOccurredAtAfterOrderByOccurredAtAsc(rangeStart);
        long intervalMinutes = Math.max(1, Duration.ofHours(hours).toMinutes() / 12);
        long[] eventCounts = new long[12];
        long[] threatCounts = new long[12];
        for (SecurityEvent event : rangeEvents) {
            long elapsedMinutes = Math.max(0, Duration.between(rangeStart, event.getOccurredAt()).toMinutes());
            int bucket = (int) Math.min(11, elapsedMinutes / intervalMinutes);
            eventCounts[bucket]++;
            if (isPriority(event.getSeverity())) threatCounts[bucket]++;
        }

        List<ChartPoint> activity = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            LocalDateTime bucketStart = rangeStart.plusMinutes(i * intervalMinutes);
            String label = hours == 168 ? bucketStart.format(DAY_LABEL) : bucketStart.format(CLOCK_LABEL);
            activity.add(new ChartPoint(label, eventCounts[i], threatCounts[i]));
        }

        List<EventResponse> recent = events.findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(this::toEventResponse).getContent();
        List<AlertResponse> priority = alerts.findAllByStatusNotIgnoreCaseOrderByDetectedAtDesc(
                        "RESOLVED", PageRequest.of(0, 5))
                .stream().map(this::toAlertResponse).toList();

        return new DashboardResponse(
                events.countByOccurredAtAfter(dayAgo),
                alerts.countBySeverityIgnoreCaseAndStatusNotIgnoreCase("CRITICAL", "RESOLVED"),
                users.countByLastLoginAtAfter(dayAgo),
                events.countByStatusIgnoreCase("OPEN"),
                alerts.countByStatusNotIgnoreCase("RESOLVED"),
                settings.findById(1L).map(SystemSettings::getWorkspaceName).orElse("MiniSIEM"),
                activity,
                recent,
                priority
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> listEvents(
            String query, String severity, int hours, int page, int size
    ) {
        validateSeverity(severity);
        LocalDateTime since = hours == 0 ? null : LocalDateTime.now().minusHours(validateHours(hours));
        Pageable pageable = pageRequest(page, size, "occurredAt");
        return mapPage(events.search(normalizeQuery(query), normalize(severity), since, null, pageable),
                this::toEventResponse);
    }

    @Transactional
    public EventResponse createEvent(EventRequest request, String actor) {
        SecurityEvent event = new SecurityEvent();
        event.setTitle(request.title().trim());
        event.setSeverity(request.severity().toUpperCase(Locale.ROOT));
        event.setSource(request.source().trim());
        event.setDestination(request.destination().trim());
        event.setDetails(trimToNull(request.details()));
        event.setOccurredAt(request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt());
        event.setSubmittedBy(actor);
        SecurityEvent saved = events.save(event);

        if (isPriority(saved.getSeverity())) {
            SecurityAlert alert = new SecurityAlert();
            alert.setEventId(saved.getId());
            alert.setTitle(saved.getTitle());
            alert.setSeverity(saved.getSeverity());
            alert.setSource(saved.getSource());
            alert.setDetails(saved.getDetails());
            alert.setDetectedAt(saved.getOccurredAt());
            alerts.save(alert);
        }
        writeLog("INFO", "SECURITY_EVENT", "Security event recorded: " + saved.getTitle(), actor);
        return toEventResponse(saved);
    }

    @Transactional(readOnly = true)
    public String exportEvents(String query, String severity, int hours) {
        validateSeverity(severity);
        LocalDateTime since = hours == 0 ? null : LocalDateTime.now().minusHours(validateHours(hours));
        List<SecurityEvent> matching = events.searchAll(normalizeQuery(query), normalize(severity), since, null);
        StringBuilder csv = new StringBuilder("ID,Time,Event,Severity,Source,Destination,Status,Details,Submitted by\r\n");
        for (SecurityEvent event : matching) {
            csv.append(csv(event.getId())).append(',')
                    .append(csv(event.getOccurredAt())).append(',')
                    .append(csv(event.getTitle())).append(',')
                    .append(csv(event.getSeverity())).append(',')
                    .append(csv(event.getSource())).append(',')
                    .append(csv(event.getDestination())).append(',')
                    .append(csv(event.getStatus())).append(',')
                    .append(csv(event.getDetails())).append(',')
                    .append(csv(event.getSubmittedBy())).append("\r\n");
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> listAlerts(
            String query, String severity, String status, int page, int size
    ) {
        validateSeverity(severity);
        validateAlertStatus(status);
        return mapPage(alerts.search(normalizeQuery(query), normalize(severity), normalize(status),
                        pageRequest(page, size, "detectedAt")),
                this::toAlertResponse);
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlert(long id) {
        return alerts.findById(id).map(this::toAlertResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
    }

    @Transactional
    public AlertResponse updateAlertStatus(long id, AlertStatusRequest request, String actor) {
        String status = request.status().toUpperCase(Locale.ROOT);
        validateAlertStatus(status);
        SecurityAlert alert = alerts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        String previousStatus = alert.getStatus();
        alert.setStatus(status);
        SecurityAlert saved = alerts.save(alert);
        writeLog("INFO", "ALERT_STATUS", "Alert #" + id + " changed from " + previousStatus + " to " + status, actor);
        return toAlertResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<LogResponse> listLogs(String query, int page, int size) {
        return mapPage(logs.search(normalizeQuery(query),
                        pageRequest(page, size, "createdAt")),
                this::toLogResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserManagementResponse> listUsers(String query, int page, int size) {
        Pageable pageable = pageRequest(page, size, "createdAt");
        Page<User> results = query == null || query.isBlank()
                ? users.findAll(pageable)
                : users.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        query.trim(), query.trim(), pageable);
        return mapPage(results, this::toUserResponse);
    }

    @Transactional
    public UserManagementResponse createUser(CreateUserRequest request, String actor) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new PasswordValidationException("Password must not exceed 72 UTF-8 bytes");
        }
        if (users.existsByUsername(request.username().trim())) {
            throw new DuplicateUserException("Username is already registered");
        }
        if (users.existsByEmail(request.email().trim())) {
            throw new DuplicateUserException("Email is already registered");
        }
        User user = new User();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role().toUpperCase(Locale.ROOT));
        User saved = users.save(user);
        writeLog("INFO", "USER_CREATED", "User created: " + saved.getUsername() + " (" + saved.getRole() + ")", actor);
        return toUserResponse(saved);
    }

    @Transactional
    public void deleteUser(long id, String actor) {
        User user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getUsername().equals(actor)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot delete your own account");
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole()) && users.countByRoleIgnoreCase("ADMIN") <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last administrator cannot be deleted");
        }
        users.delete(user);
        writeLog("WARN", "USER_DELETED", "User deleted: " + user.getUsername(), actor);
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        return settings.findById(1L).map(this::toSettingsResponse)
                .orElseGet(() -> new SettingsResponse("MiniSIEM", "UTC", 90, null));
    }

    @Transactional
    public SettingsResponse updateSettings(SettingsRequest request, String actor) {
        SystemSettings configuration = settings.findById(1L).orElseGet(SystemSettings::new);
        configuration.setWorkspaceName(request.workspaceName().trim());
        configuration.setTimezone(request.timezone().trim());
        configuration.setRetentionDays(request.retentionDays());
        SystemSettings saved = settings.save(configuration);
        writeLog("INFO", "SETTINGS_UPDATED", "System settings updated", actor);
        return toSettingsResponse(saved);
    }

    private Pageable pageRequest(int page, int size, String sort) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must not be negative");
        if (!PAGE_SIZES.contains(size)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be 10, 25, 50, or 100");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort));
    }

    private int validateHours(int hours) {
        if (!Set.of(1, 6, 24, 168).contains(hours)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Time range must be 1, 6, 24, or 168 hours");
        }
        return hours;
    }

    private int rangeHours(String range) {
        return switch (range == null ? "24h" : range.toLowerCase(Locale.ROOT)) {
            case "1h" -> 1;
            case "6h" -> 6;
            case "24h" -> 24;
            case "7d" -> 168;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported dashboard range");
        };
    }

    private void validateSeverity(String severity) {
        if (severity != null && !severity.isBlank() && !SEVERITIES.contains(severity.toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported event severity");
        }
    }

    private void validateAlertStatus(String status) {
        if (status != null && !status.isBlank() && !ALERT_STATUSES.contains(status.toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported alert status");
        }
    }

    private boolean isPriority(String severity) {
        return "CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeQuery(String query) {
        return normalize(query);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <S, T> PageResponse<T> mapPage(Page<S> page, java.util.function.Function<S, T> mapper) {
        return new PageResponse<>(page.map(mapper).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private EventResponse toEventResponse(SecurityEvent event) {
        return new EventResponse(event.getId(), event.getTitle(), event.getSeverity(), event.getSource(),
                event.getDestination(), event.getStatus(), event.getDetails(), event.getSubmittedBy(),
                event.getOccurredAt());
    }

    private AlertResponse toAlertResponse(SecurityAlert alert) {
        return new AlertResponse(alert.getId(), alert.getEventId(), alert.getTitle(), alert.getSeverity(),
                alert.getSource(), alert.getStatus(), alert.getDetails(), alert.getDetectedAt());
    }

    private LogResponse toLogResponse(SystemLog log) {
        return new LogResponse(log.getId(), log.getLevel(), log.getCategory(), log.getMessage(),
                log.getActor(), log.getCreatedAt());
    }

    private UserManagementResponse toUserResponse(User user) {
        return new UserManagementResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.getCreatedAt(), user.getLastLoginAt());
    }

    private SettingsResponse toSettingsResponse(SystemSettings configuration) {
        return new SettingsResponse(configuration.getWorkspaceName(), configuration.getTimezone(),
                configuration.getRetentionDays(), configuration.getUpdatedAt());
    }

    private String csv(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private void writeLog(String level, String category, String message, String actor) {
        SystemLog log = new SystemLog();
        log.setLevel(level);
        log.setCategory(category);
        log.setMessage(message);
        log.setActor(actor);
        logs.save(log);
    }
}
