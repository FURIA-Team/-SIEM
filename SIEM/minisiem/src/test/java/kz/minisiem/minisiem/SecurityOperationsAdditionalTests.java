package kz.minisiem.minisiem;

import kz.minisiem.minisiem.entity.SecurityAlert;
import kz.minisiem.minisiem.entity.User;
import kz.minisiem.minisiem.repository.SecurityAlertRepository;
import kz.minisiem.minisiem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "minisiem.bootstrap.admin-token=")
@AutoConfigureMockMvc
@Transactional
class SecurityOperationsAdditionalTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityAlertRepository alertRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @WithMockUser(username = "analyst", roles = "USER")
    void eventCreationRejectsMissingRequiredFieldsAndUnsupportedSeverity() throws Exception {
        mockMvc.perform(post("/api/user/events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Missing source","severity":"LOW","destination":"gateway"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.source").exists());

        mockMvc.perform(post("/api/user/events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Unsupported severity","severity":"INFO","source":"192.0.2.1","destination":"gateway"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.severity").exists());

        mockMvc.perform(post("/api/user/events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   ","severity":"LOW","source":"192.0.2.1","destination":"gateway"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.title").exists());
    }

    @Test
    @WithMockUser(username = "analyst", roles = "USER")
    void onlyHighAndCriticalEventsCreateAlerts() throws Exception {
        String suffix = UUID.randomUUID().toString();
        for (String severity : new String[]{"LOW", "MEDIUM", "HIGH", "CRITICAL"}) {
            String title = "severity-" + severity + "-" + suffix;
            mockMvc.perform(post("/api/user/events")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"%s","severity":"%s","source":"192.0.2.10","destination":"auth"}
                                    """.formatted(title, severity)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.severity").value(severity));
        }

        var alerts = alertRepository.findAll().stream()
                .filter(alert -> alert.getTitle().endsWith(suffix))
                .toList();
        assertEquals(2, alerts.size());
        assertTrue(alerts.stream().allMatch(alert ->
                alert.getSeverity().equals("HIGH") || alert.getSeverity().equals("CRITICAL")));
    }

    @Test
    @WithMockUser(username = "analyst", roles = "USER")
    void invalidAlertStatusIsRejectedWithoutChangingTheStoredStatus() throws Exception {
        SecurityAlert alert = new SecurityAlert();
        alert.setTitle("Alert for status validation " + UUID.randomUUID());
        alert.setSeverity("HIGH");
        alert.setSource("192.0.2.44");
        alert = alertRepository.saveAndFlush(alert);

        mockMvc.perform(patch("/api/user/alerts/{id}", alert.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.status").exists());

        mockMvc.perform(get("/api/user/alerts/{id}", alert.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        for (String validStatus : new String[]{"INVESTIGATING", "RESOLVED", "OPEN"}) {
            mockMvc.perform(patch("/api/user/alerts/{id}", alert.getId())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + validStatus + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(validStatus));
        }
        mockMvc.perform(get("/api/user/logs").param("query", "Alert #" + alert.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("ALERT_STATUS"));
    }

    @Test
    @WithMockUser(username = "administrator", roles = "ADMIN")
    void administratorCanSearchAndDeleteUsers() throws Exception {
        String username = "searchable" + UUID.randomUUID().toString().replace("-", "");
        String email = username + "@example.com";
        String body = """
                {"username":"%s","email":"%s","password":"SecurePass123","role":"USER"}
                """.formatted(username, email);

        var create = mockMvc.perform(post("/api/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/api/admin/users").param("query", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value(username));

        long id = userRepository.findByUsername(username).orElseThrow().getId();
        mockMvc.perform(delete("/api/admin/users/{id}", id).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/users").param("query", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        assertTrue(create.getResponse().getContentAsString().contains(username));

        String currentUsername = "administrator";
        String currentEmail = currentUsername + "-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"SecurePass123","role":"USER"}
                                """.formatted(currentUsername, currentEmail)))
                .andExpect(status().isCreated());
        long currentId = userRepository.findByUsername(currentUsername).orElseThrow().getId();
        mockMvc.perform(delete("/api/admin/users/{id}", currentId).with(csrf()))
                .andExpect(status().isConflict());
        assertTrue(userRepository.existsById(currentId));
    }

    @Test
    @WithMockUser(username = "analyst", roles = "USER")
    void userCannotCreateOrDeleteManagedAccounts() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"blocked-user","email":"blocked-user@example.com","password":"SecurePass123","role":"USER"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/users/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "operator-admin", roles = "ADMIN")
    void lastAdministratorCannotBeDeleted() throws Exception {
        User lastAdmin = new User();
        lastAdmin.setUsername("last-admin-" + UUID.randomUUID().toString().replace("-", ""));
        lastAdmin.setEmail(lastAdmin.getUsername() + "@example.com");
        lastAdmin.setPassword("not-used-by-this-test");
        lastAdmin.setRole("ADMIN");
        lastAdmin = userRepository.saveAndFlush(lastAdmin);
        Long lastAdminId = lastAdmin.getId();

        userRepository.findAll().stream()
                .filter(user -> "ADMIN".equalsIgnoreCase(user.getRole()) && !user.getId().equals(lastAdminId))
                .forEach(userRepository::delete);
        userRepository.flush();

        mockMvc.perform(delete("/api/admin/users/{id}", lastAdminId).with(csrf()))
                .andExpect(status().isConflict());
        assertTrue(userRepository.existsById(lastAdminId));
    }

    @Test
    @WithMockUser(username = "analyst", roles = "USER")
    void eventCreationWritesAnAuditableLogEntry() throws Exception {
        String title = "Audit log verification " + UUID.randomUUID();
        mockMvc.perform(post("/api/user/events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","severity":"LOW","source":"192.0.2.55","destination":"audit-test"}
                                """.formatted(title)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/user/logs").param("query", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category").value("SECURITY_EVENT"))
                .andExpect(jsonPath("$.content[0].actor").value("analyst"));
    }

    @Test
    @WithMockUser(username = "administrator", roles = "ADMIN")
    void settingsValidationRejectsRetentionOutsideConfiguredRange() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceName":"Test workspace","timezone":"UTC","retentionDays":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.retentionDays").exists());

        mockMvc.perform(put("/api/admin/settings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceName":"Test workspace","timezone":"UTC","retentionDays":3651}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "analyst", roles = "USER")
    void stateChangingEventRequestsRequireCsrfProtection() throws Exception {
        mockMvc.perform(post("/api/user/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"CSRF check","severity":"LOW","source":"192.0.2.1","destination":"gateway"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "analyst", roles = "USER")
    void eventPaginationReturnsDifferentRowsAndAccurateTotals() throws Exception {
        String suffix = UUID.randomUUID().toString();
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(post("/api/user/events")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"page-%s-%02d","severity":"LOW","source":"192.0.2.60","destination":"paging-test"}
                                    """.formatted(suffix, i)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/user/events")
                        .param("query", "page-" + suffix)
                        .param("hours", "0")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.content.length()").value(10));

        mockMvc.perform(get("/api/user/events")
                        .param("query", "page-" + suffix)
                        .param("hours", "0")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.content.length()").value(2));
    }
}
