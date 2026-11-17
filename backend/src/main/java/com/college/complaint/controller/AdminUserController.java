package com.college.complaint.controller;

import com.college.complaint.dto.AuthDtos;
import com.college.complaint.dto.AdminUserView;
import com.college.complaint.dto.ProvisionUserRequest;
import com.college.complaint.model.Department;
import com.college.complaint.repository.DepartmentRepository;
import com.college.complaint.repository.UserRepository;
import com.college.complaint.service.AuthService;
import com.college.complaint.utility.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AuthService authService;
    private final UserRepository users;
    private final DepartmentRepository departments;

    public AdminUserController(AuthService authService, UserRepository users, DepartmentRepository departments) {
        this.authService = authService;
        this.users = users;
        this.departments = departments;
    }

    @GetMapping
    public ApiResponse<List<AdminUserView>> listAccounts() {
        Map<UUID, String> departmentNames = departments.findAll().stream()
                .collect(Collectors.toMap(Department::getDepartmentId, Department::getDepartmentName));
        List<AdminUserView> rows = users.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(user -> new AdminUserView(user.getUserId(), user.getFullName(), user.getEmail(),
                        user.getRole(), user.getDepartmentId(), departmentNames.get(user.getDepartmentId()),
                        user.isActive(), user.getCreatedAt()))
                .toList();
        return ApiResponse.success("Accounts retrieved successfully", rows);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AuthDtos.RegisteredData>> createFacultyOrAdmin(
            @Valid @RequestBody ProvisionUserRequest request) {
        AuthDtos.RegisteredData created = authService.provisionPrivilegedUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", created));
    }
}
