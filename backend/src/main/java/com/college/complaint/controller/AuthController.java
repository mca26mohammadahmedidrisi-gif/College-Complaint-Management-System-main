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
    private final com.college.complaint.service.AuthActionTokenService authActionTokens;

    public AuthController(AuthService authService,
                          com.college.complaint.service.AuthActionTokenService authActionTokens) {
        this.authService = authService;
        this.authActionTokens = authActionTokens;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthDtos.RegisteredData>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthDtos.RegisteredData registered = authService.registerStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(registered.requiresEmailVerification()
                        ? "Student registered. Check your email to verify the account before signing in."
                        : "Student registered successfully", registered));
    }

    @PostMapping("/verify-email")
    public ApiResponse<Map<String, String>> verifyEmail(@Valid @RequestBody AuthDtos.TokenRequest request) {
        authActionTokens.verify(request.token());
        return ApiResponse.success("Email verified successfully", Map.of("message", "You can now sign in."));
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Map<String, String>> resendVerification(@Valid @RequestBody AuthDtos.EmailRequest request) {
        authActionTokens.resendVerification(request.email());
        return ApiResponse.success("If the account needs verification, a link has been sent", Map.of("message", "Check your inbox."));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Map<String, String>> forgotPassword(@Valid @RequestBody AuthDtos.EmailRequest request) {
        authActionTokens.requestPasswordReset(request.email());
        return ApiResponse.success("If the account exists, a reset link has been sent", Map.of("message", "Check your inbox."));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Map<String, String>> resetPassword(@Valid @RequestBody AuthDtos.PasswordResetRequest request) {
        authActionTokens.resetPassword(request.token(), request.password());
        return ApiResponse.success("Password changed successfully", Map.of("message", "You can now sign in."));
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
