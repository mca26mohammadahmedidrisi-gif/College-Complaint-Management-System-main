package com.college.complaint.dto;

import java.util.UUID;

public record ComplaintCategoryResponse(UUID categoryId, String categoryName) {
}
