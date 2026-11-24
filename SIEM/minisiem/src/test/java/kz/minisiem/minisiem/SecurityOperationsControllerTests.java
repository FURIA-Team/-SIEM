package kz.minisiem.minisiem;

import kz.minisiem.minisiem.repository.SecurityAlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "minisiem.bootstrap.admin-token=")
@AutoConfigureMockMvc
@Transactional
class SecurityOperationsControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityAlertRepository alertRepository;

    @Test
    void securityDataEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/user/events")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "analyst", roles = "USER")
    void eventsArePersistedSearchableExportedAndCreateAlerts() throws Exception {
        String title = "Failed logins " + UUID.randomUUID();
        String body = """
                {"title":"%s","severity":"HIGH","source":"192.0.2.42","destination":"auth-01","details":"Repeated authentication failures"}
                """.formatted(title);
        MvcResult createResult = mockMvc.perform(post("/api/user/events")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.submittedBy").value("analyst"))
                .andReturn();

        assertTrue(createResult.getResponse().getContentAsString().contains(title));
        mockMvc.perform(get("/api/user/events")
                        .param("query", "192.0.2.42")
                        .param("severity", "HIGH")
                        .param("hours", "1")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value(title));

        mockMvc.perform(get("/api/user/events")
                        .param("query", title)
                        .param("hours", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        MvcResult csv = mockMvc.perform(get("/api/user/events/export")
                        .param("query", "192.0.2.42")
                        .param("severity", "HIGH")
                        .param("hours", "1"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(csv.getResponse().getContentAsString().contains("192.0.2.42"));

        var alert = alertRepository.findAll().stream()
                .filter(item -> title.equals(item.getTitle()))
                .findFirst()
                .orElseThrow();
        mockMvc.perform(patch("/api/user/alerts/{id}", alert.getId())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVESTIGATING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"));

        mockMvc.perform(get("/api/user/alerts")
                        .param("query", title)
                        .param("status", "INVESTIGATING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "analyst", roles = "USER")
    void userCannotManageAccountsOrSystemSettings() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/settings")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/settings")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceName\":\"Example\",\"timezone\":\"UTC\",\"retentionDays\":30}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "administrator", roles = "ADMIN")
    void administratorCanCreateUsersAndPersistSettings() throws Exception {
        String username = "managed" + UUID.randomUUID().toString().replace("-", "");
        mockMvc.perform(post("/api/admin/users")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"SecurePass123","role":"USER"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(put("/api/admin/settings")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceName\":\"Security team\",\"timezone\":\"UTC\",\"retentionDays\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Security team"))
                .andExpect(jsonPath("$.retentionDays").value(60));

        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Security team"));
    }

    @Test
    @WithMockUser(username = "analyst", roles = "USER")
    void filtersRejectInvalidValuesAndNoDataProducesAnEmptyDashboard() throws Exception {
        mockMvc.perform(get("/api/user/events").param("severity", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/user/events").param("hours", "3"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/user/events").param("size", "11"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/user/dashboard").param("range", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activity.length()").value(12))
                .andExpect(jsonPath("$.securityEvents24h").isNumber());
    }

    @Test
    void bootstrapIsUnavailableWithoutAnExplicitServerToken() throws Exception {
        mockMvc.perform(post("/api/auth/bootstrap-admin")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"first-admin","email":"first-admin@example.com","password":"SecurePass123","token":"long-token-that-will-not-be-accepted"}
                                """))
                .andExpect(status().isServiceUnavailable());
    }
}
