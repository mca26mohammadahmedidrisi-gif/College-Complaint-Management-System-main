package com.college.complaint.dto;

import com.college.complaint.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record UserSummary(String fullName, String email, Role role) {
    }

    public record LoginData(String token, String tokenType, long expiresInSeconds,
                            Role role, UserSummary user) {
    }

    public record RegisteredData(UUID userId, UserSummary user, boolean requiresEmailVerification) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) { }

    public record TokenRequest(@NotBlank @Size(max = 128) String token) { }

    public record PasswordResetRequest(@NotBlank @Size(max = 128) String token,
                                       @NotBlank @Size(min = 8, max = 128) String password) { }
}
