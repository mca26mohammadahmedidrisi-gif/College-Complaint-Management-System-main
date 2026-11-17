package com.college.complaint.service;

import com.college.complaint.exception.ApiException;
import com.college.complaint.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthActionTokenService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EmailDeliveryService email;
    private final SecureRandom random = new SecureRandom();

    public AuthActionTokenService(JdbcTemplate jdbc, UserRepository users,
                                  PasswordEncoder passwordEncoder, EmailDeliveryService email) {
        this.jdbc = jdbc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
    }

    @Transactional
    public void sendVerification(UUID userId, String address) {
        if (email.enabled()) {
            String token = issue(userId, "VERIFY_EMAIL", Duration.ofHours(24));
            if (token != null) email.sendVerification(address, token);
        }
    }

    @Transactional
    public void verify(String token) {
        UUID userId = consume(token, "VERIFY_EMAIL");
        jdbc.update("UPDATE users SET email_verified = TRUE, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", userId);
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        requireEmailDelivery();
        String address = rawEmail.trim().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(address).filter(com.college.complaint.model.UserAccount::isActive)
                .ifPresent(user -> {
                    if (email.enabled()) {
                        String token = issue(user.getUserId(), "PASSWORD_RESET", Duration.ofMinutes(30));
                        if (token != null) email.sendPasswordReset(address, token);
                    }
                });
    }

    @Transactional
    public void resendVerification(String rawEmail) {
        requireEmailDelivery();
        String address = rawEmail.trim().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(address)
                .filter(com.college.complaint.model.UserAccount::isActive)
                .filter(user -> user.getRole() == com.college.complaint.model.Role.STUDENT)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    String token = issue(user.getUserId(), "VERIFY_EMAIL", Duration.ofHours(24));
                    if (token != null) email.sendVerification(address, token);
                });
    }

    @Transactional
    public void resetPassword(String token, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be no more than 72 UTF-8 bytes");
        }
        UUID userId = consume(token, "PASSWORD_RESET");
        jdbc.update("UPDATE users SET password_hash = ?, email_verified = TRUE, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                passwordEncoder.encode(password), userId);
    }

    private String issue(UUID userId, String purpose, Duration lifetime) {
        Integer recentlyIssued = jdbc.queryForObject("""
                SELECT COUNT(*) FROM auth_action_tokens
                WHERE user_id = ? AND purpose = ? AND created_at > CURRENT_TIMESTAMP - INTERVAL '1 minute'
                """, Integer.class, userId, purpose);
        if (recentlyIssued != null && recentlyIssued >= 3) return null;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = HexFormat.of().formatHex(bytes);
        jdbc.update("UPDATE auth_action_tokens SET consumed_at = CURRENT_TIMESTAMP WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL",
                userId, purpose);
        jdbc.update("INSERT INTO auth_action_tokens (user_id, purpose, token_hash, expires_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP + make_interval(secs => ?))",
                userId, purpose, hash(raw), lifetime.toSeconds());
        return raw;
    }

    private UUID consume(String rawToken, String purpose) {
        if (rawToken == null || !rawToken.matches("[a-fA-F0-9]{64}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This link is invalid or has expired");
        }
        UUID userId = jdbc.query("""
                UPDATE auth_action_tokens SET consumed_at = CURRENT_TIMESTAMP
                WHERE token_hash = ? AND purpose = ? AND consumed_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                RETURNING user_id
                """, rs -> rs.next() ? rs.getObject("user_id", UUID.class) : null, hash(rawToken));
        if (userId == null) return invalidToken();
        return userId;
    }

    private UUID invalidToken() {
        throw new ApiException(HttpStatus.BAD_REQUEST, "This link is invalid or has expired");
    }

    private void requireEmailDelivery() {
        if (!email.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery is not enabled. Contact the college administrator.");
        }
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
