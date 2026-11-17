package com.college.complaint.controller;

import com.college.complaint.dto.DepartmentResponse;
import com.college.complaint.repository.DepartmentRepository;
import com.college.complaint.utility.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    private final DepartmentRepository departments;

    public DepartmentController(DepartmentRepository departments) {
        this.departments = departments;
    }

    /** Public reference data is needed before a student has an access token. */
    @GetMapping
    public ApiResponse<List<DepartmentResponse>> getDepartments() {
        List<DepartmentResponse> rows = departments.findAll().stream()
                .sorted((left, right) -> left.getDepartmentName().compareToIgnoreCase(right.getDepartmentName()))
                .map(item -> new DepartmentResponse(item.getDepartmentId(), item.getDepartmentName()))
                .toList();
        return ApiResponse.success("Departments retrieved successfully", rows);
    }
}
