package com.college.complaint.controller;

import com.college.complaint.dto.AuthDtos;
import com.college.complaint.dto.ProvisionUserRequest;
import com.college.complaint.service.AuthService;
import com.college.complaint.utility.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AuthDtos.RegisteredData>> createFacultyOrAdmin(
            @Valid @RequestBody ProvisionUserRequest request) {
        AuthDtos.RegisteredData created = authService.provisionPrivilegedUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", created));
    }
}
