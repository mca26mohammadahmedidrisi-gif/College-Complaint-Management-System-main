package com.college.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ComplaintSubmissionRequest(
        @NotBlank(message = "Complaint title is required")
        @Size(max = 200, message = "Complaint title must be at most 200 characters")
        String title,

        @NotBlank(message = "Complaint description is required")
        @Size(max = 10000, message = "Complaint description must be at most 10000 characters")
        String description,

        @NotNull(message = "Complaint category ID is required")
        UUID categoryId,

        @NotNull(message = "Classroom ID is required")
        UUID classroomId
) {
}
