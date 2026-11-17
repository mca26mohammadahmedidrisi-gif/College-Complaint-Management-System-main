package com.college.complaint.dto;

import com.college.complaint.model.Role;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record UserSummary(String fullName, String email, Role role) {
    }

    public record LoginData(String token, String tokenType, long expiresInSeconds,
                            Role role, UserSummary user) {
    }

    public record RegisteredData(UUID userId, UserSummary user) {
    }
}
