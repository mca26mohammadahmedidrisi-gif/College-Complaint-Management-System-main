package com.college.complaint.security;

import com.college.complaint.dto.AuthDtos;
import com.college.complaint.exception.ApiException;
import com.college.complaint.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.bootstrap.admin.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AuthService authService;
    private final String fullName;
    private final String email;
    private final String password;

    public AdminBootstrapRunner(AuthService authService,
                                @Value("${app.bootstrap.admin.full-name}") String fullName,
                                @Value("${app.bootstrap.admin.email}") String email,
                                @Value("${app.bootstrap.admin.password}") String password) {
        this.authService = authService;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Admin bootstrap is enabled but BOOTSTRAP_ADMIN_FULL_NAME, EMAIL, and PASSWORD are required");
        }
        try {
            AuthDtos.RegisteredData created = authService.provisionInitialAdmin(fullName, email, password);
            // Do not log credentials or the returned account data.
            System.out.println("Initial administrator account provisioned.");
        } catch (ApiException exception) {
            if (exception.getStatus().value() == 409
                    && exception.getMessage().equals("An admin account is already provisioned")) {
                System.out.println("Administrator already exists; bootstrap skipped.");
                return;
            }
            throw exception;
        }
    }
}
