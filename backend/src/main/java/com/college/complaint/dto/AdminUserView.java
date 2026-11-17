package com.college.complaint.dto;

import com.college.complaint.model.Role;
import java.time.Instant;
import java.util.UUID;

/** Administrator-only account directory row. Never includes password hashes. */
public record AdminUserView(
        UUID userId,
        String fullName,
        String email,
        Role role,
        UUID departmentId,
        String departmentName,
        boolean active,
        Instant createdAt
) { }
