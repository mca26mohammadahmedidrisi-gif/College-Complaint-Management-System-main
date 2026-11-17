package com.college.complaint.service;

import com.college.complaint.dto.AuthDtos;
import com.college.complaint.dto.LoginRequest;
import com.college.complaint.dto.ProvisionUserRequest;
import com.college.complaint.dto.RegisterRequest;
import com.college.complaint.exception.ApiException;
import com.college.complaint.model.Role;
import com.college.complaint.model.UserAccount;
import com.college.complaint.repository.DepartmentRepository;
import com.college.complaint.repository.UserRepository;
import com.college.complaint.security.JwtTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid sign-in ID or password";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final String dummyPasswordHash;
    private final AuthActionTokenService authActionTokens;
    private final EmailDeliveryService emailDelivery;

    public AuthService(UserRepository userRepository,
                       DepartmentRepository departmentRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       AuthActionTokenService authActionTokens,
                       EmailDeliveryService emailDelivery) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.authActionTokens = authActionTokens;
        this.emailDelivery = emailDelivery;
        // Do a BCrypt match even when an email does not exist to reduce timing-based
        // account enumeration. This random hash is never stored or used for login.
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthDtos.RegisteredData registerStudent(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        ensurePasswordFitsBcrypt(request.password());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        UserAccount account = new UserAccount(
                null,
                request.fullName().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                Role.STUDENT);
        account.setStudentAcademicProfile(request.streamName().trim().toUpperCase(Locale.ROOT), request.semester());
        account.setEmailVerified(!emailDelivery.enabled());
        UserAccount saved = userRepository.save(account);
        if (emailDelivery.enabled()) authActionTokens.sendVerification(saved.getUserId(), saved.getEmail());
        return new AuthDtos.RegisteredData(saved.getUserId(), toSummary(saved), emailDelivery.enabled());
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginData login(LoginRequest request) {
        String loginIdentifier = request.identifier().trim().toLowerCase(Locale.ROOT);
        UserAccount account = userRepository.findByEmailIgnoreCase(loginIdentifier).orElse(null);
        String hashToCheck = account == null ? dummyPasswordHash : account.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (account == null || !account.isActive() || !passwordMatches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        if (!account.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Verify your email before signing in");
        }

        JwtTokenService.IssuedToken issuedToken = jwtTokenService.issue(account);
        return new AuthDtos.LoginData(
                issuedToken.token(),
                "Bearer",
                issuedToken.expiresInSeconds(),
                account.getRole(),
                toSummary(account));
    }

    @Transactional
    public AuthDtos.RegisteredData provisionPrivilegedUser(ProvisionUserRequest request) {
        if (request.role() != Role.FACULTY && request.role() != Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This operation can create FACULTY or ADMIN accounts only");
        }
        return createPrivilegedAccount(
                request.fullName(), request.email(), request.password(), request.departmentId(), request.role());
    }

    @Transactional
    public AuthDtos.RegisteredData provisionBootstrapAdmin(String fullName, String email, String password) {
        String loginId = normalizeEmail(email);
        ensurePasswordFitsBcrypt(password);
        UserAccount existing = userRepository.findByEmailIgnoreCase(loginId).orElse(null);
        if (existing != null) {
            if (existing.getRole() != Role.ADMIN) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "The configured bootstrap login ID belongs to a non-admin account");
            }
            existing.updatePasswordHash(passwordEncoder.encode(password));
            existing.setEmailVerified(true);
            UserAccount saved = userRepository.save(existing);
            return new AuthDtos.RegisteredData(saved.getUserId(), toSummary(saved), false);
        }
        return createPrivilegedAccount(fullName, loginId, password, null, Role.ADMIN);
    }

    private AuthDtos.RegisteredData createPrivilegedAccount(
            String fullName, String email, String password, UUID departmentId, Role role) {
        String normalizedEmail = normalizeEmail(email);
        ensurePasswordFitsBcrypt(password);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        if (role == Role.FACULTY && (departmentId == null || !departmentRepository.existsById(departmentId))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Faculty must be assigned to an existing department");
        }
        if (role == Role.ADMIN && departmentId != null && !departmentRepository.existsById(departmentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The selected department does not exist");
        }

        UserAccount account = new UserAccount(
                departmentId, fullName.trim(), normalizedEmail, passwordEncoder.encode(password), role);
        UserAccount saved = userRepository.save(account);
        return new AuthDtos.RegisteredData(saved.getUserId(), toSummary(saved), false);
    }

    private AuthDtos.UserSummary toSummary(UserAccount account) {
        return new AuthDtos.UserSummary(account.getFullName(), account.getEmail(), account.getRole(),
                account.getStreamName(), account.getSemester());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void ensurePasswordFitsBcrypt(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must be no more than 72 UTF-8 bytes when using BCrypt");
        }
    }
}
