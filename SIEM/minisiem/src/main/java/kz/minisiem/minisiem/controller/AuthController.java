package kz.minisiem.minisiem.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kz.minisiem.minisiem.dto.SecurityDtos.BootstrapAdminRequest;
import kz.minisiem.minisiem.dto.AuthResponse;
import kz.minisiem.minisiem.dto.LoginRequest;
import kz.minisiem.minisiem.dto.RegisterRequest;
import kz.minisiem.minisiem.dto.UserResponse;
import kz.minisiem.minisiem.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    @Value("${minisiem.bootstrap.admin-token:}")
    private String adminBootstrapToken;

    public AuthController(AuthService authService, SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<UserResponse> bootstrapAdmin(@Valid @RequestBody BootstrapAdminRequest request) {
        if (adminBootstrapToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Administrator bootstrap is disabled; configure MINISIEM_ADMIN_BOOTSTRAP_TOKEN"
            );
        }
        boolean validToken = MessageDigest.isEqual(
                adminBootstrapToken.getBytes(StandardCharsets.UTF_8),
                request.token().getBytes(StandardCharsets.UTF_8)
        );
        if (!validToken) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid administrator bootstrap token");
        }
        RegisterRequest registration = new RegisterRequest(request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.bootstrapAdministrator(registration));
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        Authentication authentication = authService.authenticate(
                request.usernameOrEmail(),
                request.password()
        );

        if (servletRequest.getSession(false) != null) {
            servletRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);

        return new AuthResponse(authService.getCurrentUser(authentication.getName()));
    }

    @GetMapping("/me")
    public UserResponse currentUser(Authentication authentication) {
        return authService.getCurrentUser(authentication.getName());
    }

    @GetMapping("/csrf")
    public CsrfToken csrfToken(CsrfToken csrfToken) {
        return csrfToken;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
