package com.college.complaint.controller;

import com.college.complaint.dto.AuthDtos;
import com.college.complaint.dto.LoginRequest;
import com.college.complaint.dto.RegisterRequest;
import com.college.complaint.service.AuthService;
import com.college.complaint.utility.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthDtos.RegisteredData>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student registered successfully", authService.registerStudent(request)));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginData> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Login successful", authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout(Authentication authentication) {
        // Access tokens are stateless and remain valid until exp. The client must discard
        // this token; server-side revocation is not implemented in this phase.
        return ApiResponse.success("Logout successful; discard this access token",
                Map.of("strategy", "client_discard_until_expiration"));
    }
}
