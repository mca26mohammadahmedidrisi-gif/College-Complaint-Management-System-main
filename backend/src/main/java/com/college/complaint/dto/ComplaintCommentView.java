package com.college.complaint.dto;

import java.time.Instant;
import java.util.UUID;

public record ComplaintCommentView(UUID commentId, String author, String body, Instant createdAt) { }
