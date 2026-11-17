package com.college.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintCommentRequest(@NotBlank @Size(max = 4000) String body) { }
