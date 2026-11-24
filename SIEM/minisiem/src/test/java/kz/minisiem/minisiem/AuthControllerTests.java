package kz.minisiem.minisiem;

import kz.minisiem.minisiem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registrationLoginCurrentUserAndLogoutUseSecureSession() throws Exception {
        String username = "auth" + UUID.randomUUID().toString().replace("-", "");
        String email = username + "@example.com";
        String password = "SecurePass123";
        String registerBody = """
                {"username":"%s","email":"%s","password":"%s"}
                """.formatted(username, email, password);

        mockMvc.perform(post("/api/auth/register")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        var storedUser = userRepository.findByUsername(username).orElseThrow();
        assertTrue(storedUser.getPassword().startsWith("$2"));
        assertNotEquals(password, storedUser.getPassword());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateRegistrationAndInvalidLoginReturnClientErrors() throws Exception {
        String username = "dup" + UUID.randomUUID().toString().replace("-", "");
        String email = username + "@example.com";
        String registerBody = """
                {"username":"%s","email":"%s","password":"SecurePass123"}
                """.formatted(username, email);

        mockMvc.perform(post("/api/auth/register")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"%s","password":"wrong-password"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username/email or password"));
    }

    @Test
    void privateEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void registrationRejectsPasswordsThatExceedBcryptByteLimit() throws Exception {
        String username = "bytes" + UUID.randomUUID().toString().replace("-", "");

        mockMvc.perform(post("/api/auth/register")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя"}
                                """.formatted(username, username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.password")
                        .value("Password must not exceed 72 UTF-8 bytes"));
    }
}
