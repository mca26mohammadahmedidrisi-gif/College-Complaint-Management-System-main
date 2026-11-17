package com.college.complaint.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @JsonAlias("email")
        @NotBlank(message = "Email or admin ID is required")
        @Size(max = 320, message = "Email or admin ID must be at most 320 characters")
        String identifier,

        @NotBlank(message = "Password is required")
        String password
) {
}
