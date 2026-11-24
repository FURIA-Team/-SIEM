package kz.minisiem.minisiem.service;

import kz.minisiem.minisiem.dto.RegisterRequest;
import kz.minisiem.minisiem.dto.UserResponse;
import kz.minisiem.minisiem.entity.SystemLog;
import kz.minisiem.minisiem.entity.User;
import kz.minisiem.minisiem.repository.SystemLogRepository;
import kz.minisiem.minisiem.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SystemLogRepository systemLogRepository;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SystemLogRepository systemLogRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.systemLogRepository = systemLogRepository;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        validateBcryptPasswordLength(request.password());

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUserException("Username is already registered");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateUserException("Email is already registered");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        writeLog("INFO", "USER_REGISTERED", "User registered", saved.getUsername());
        return toResponse(saved);
    }

    @Transactional
    public synchronized UserResponse bootstrapAdministrator(RegisterRequest request) {
        validateBcryptPasswordLength(request.password());
        if (userRepository.countByRoleIgnoreCase("ADMIN") != 0) {
            throw new DuplicateUserException("An administrator has already been configured");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUserException("Username is already registered");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateUserException("Email is already registered");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole("ADMIN");
        User saved = userRepository.save(user);
        writeLog("INFO", "ADMIN_BOOTSTRAPPED", "First administrator configured", saved.getUsername());
        return toResponse(saved);
    }

    @Transactional
    public Authentication authenticate(String usernameOrEmail, String password) {
        validateBcryptPasswordLength(password);
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(usernameOrEmail, password)
        );
        userRepository.findByUsername(authentication.getName()).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
        writeLog("INFO", "AUTHENTICATION", "User signed in", authentication.getName());
        return authentication;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    private void validateBcryptPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new PasswordValidationException("Password must not exceed 72 UTF-8 bytes");
        }
    }

    private void writeLog(String level, String category, String message, String actor) {
        SystemLog log = new SystemLog();
        log.setLevel(level);
        log.setCategory(category);
        log.setMessage(message);
        log.setActor(actor);
        systemLogRepository.save(log);
    }
}
