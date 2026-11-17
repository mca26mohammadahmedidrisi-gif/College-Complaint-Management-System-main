package com.college.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintRemovalRequest(
        @NotBlank(message = "A reason is required to remove a complaint")
        @Size(max = 1000, message = "The removal reason must be at most 1000 characters")
        String reason
) { }
