package kz.minisiem.minisiem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "minisiem.bootstrap.admin-token=test-bootstrap-token-that-is-at-least-32-characters")
@AutoConfigureMockMvc
@Transactional
class AdminBootstrapControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void bootstrapTokenCreatesOnlyTheFirstAdministrator() throws Exception {
        String request = """
                {"username":"first-admin","email":"first-admin@example.com","password":"SecurePass123","token":"test-bootstrap-token-that-is-at-least-32-characters"}
                """;
        mockMvc.perform(post("/api/auth/bootstrap-admin")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/api/auth/bootstrap-admin")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());
    }

    @Test
    void bootstrapRejectsInvalidTokens() throws Exception {
        mockMvc.perform(post("/api/auth/bootstrap-admin")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"first-admin","email":"first-admin@example.com","password":"SecurePass123","token":"wrong-bootstrap-token-that-is-at-least-32-characters"}
                                """))
                .andExpect(status().isForbidden());
    }
}
