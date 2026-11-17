package com.college.complaint.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 160, message = "Full name must be at most 160 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must contain 8 to 72 characters")
        String password,

        @NotNull(message = "Department ID is required")
        UUID departmentId,

        @NotBlank(message = "Stream is required")
        @Pattern(regexp = "(?i)^(MSS|MCA|PGDM|MBA)$", message = "Choose a valid stream")
        String streamName,

        @NotNull(message = "Semester is required")
        @Min(value = 1, message = "Semester must be 1 or 2")
        @Max(value = 2, message = "Semester must be 1 or 2")
        Integer semester
) {
}
