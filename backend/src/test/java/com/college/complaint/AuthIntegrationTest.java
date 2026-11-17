package com.college.complaint;

import com.college.complaint.model.Department;
import com.college.complaint.model.Role;
import com.college.complaint.model.UserAccount;
import com.college.complaint.repository.DepartmentRepository;
import com.college.complaint.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String PASSWORD = "CollegePass123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtEncoder jwtEncoder;

    private UUID departmentId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        departmentRepository.deleteAll();
        Department department = departmentRepository.save(
                new Department(UUID.randomUUID(), "Facilities"));
        departmentId = department.getDepartmentId();
    }

    @Test
    void registersStudentAndStoresOnlyBcryptHash() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Student One", "student@example.com", PASSWORD, departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.email").value("student@example.com"))
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());

        UserAccount saved = userRepository.findByEmailIgnoreCase("student@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Role.STUDENT, saved.getRole());
        org.junit.jupiter.api.Assertions.assertNotEquals(PASSWORD, saved.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(PASSWORD, saved.getPasswordHash()));
    }

    @Test
    void ignoresAttemptToChoosePrivilegedRoleDuringPublicRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Student One","email":"student@example.com","password":"CollegePass123","departmentId":"%s","role":"ADMIN"}
                                """.formatted(departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Faculty Attempt","email":"faculty-attempt@example.com","password":"CollegePass123","departmentId":"%s","role":"FACULTY"}
                                """.formatted(departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"));
    }

    @Test
    void adminCanProvisionFacultyAccountAndItIsSavedWithDepartmentAndBcryptHash() throws Exception {
        UserAccount administrator = userRepository.save(new UserAccount(
                null, "Admin User", "provision-admin@example.com", passwordEncoder.encode(PASSWORD), Role.ADMIN));
        String adminToken = tokenFor(administrator);

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Faculty Member","email":"faculty-created@example.com","password":"CollegePass123","departmentId":"%s","role":"FACULTY"}
                                """.formatted(departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.role").value("FACULTY"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());

        UserAccount created = userRepository.findByEmailIgnoreCase("faculty-created@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Role.FACULTY, created.getRole());
        org.junit.jupiter.api.Assertions.assertEquals(departmentId, created.getDepartmentId());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(PASSWORD, created.getPasswordHash()));

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data[1].passwordHash").doesNotExist());

        UserAccount student = userRepository.save(new UserAccount(
                departmentId, "Student User", "directory-student@example.com", passwordEncoder.encode(PASSWORD), Role.STUDENT));
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + tokenFor(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDuplicateEmailWithoutRevealingAccountDetails() throws Exception {
        register("duplicate@example.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Student Two", "DUPLICATE@example.com", PASSWORD, departmentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    void rejectsInvalidRegistrationFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists())
                .andExpect(jsonPath("$.data.departmentId").exists());
    }

    @Test
    void studentCanLoginAndReceivesJwtAndBasicUserInformation() throws Exception {
        register("login@example.com");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("LOGIN@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.fullName").value("Student One"))
                .andExpect(jsonPath("$.data.user.email").value("login@example.com"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void rejectsInvalidPasswordWithGenericMessage() throws Exception {
        register("login@example.com");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("login@example.com", "WrongPassword123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void rejectsInvalidLoginEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("bad-email", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.email").exists());
    }

    @Test
    void passwordRecoveryDoesNotClaimToSendEmailWhenSmtpIsNotConfigured() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Email delivery is not enabled")));
    }

    @Test
    void facultyAndAdminAccountsCanLogin() throws Exception {
        UserAccount faculty = userRepository.save(new UserAccount(
                departmentId, "Faculty User", "faculty@example.com", passwordEncoder.encode(PASSWORD), Role.FACULTY));
        UserAccount admin = userRepository.save(new UserAccount(
                null, "Admin User", "admin@example.com", passwordEncoder.encode(PASSWORD), Role.ADMIN));

        assertLoginRole(faculty.getEmail(), Role.FACULTY);
        assertLoginRole(admin.getEmail(), Role.ADMIN);
    }

    @Test
    void rejectsUnauthorizedAndWrongRoleRequests() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        register("student@example.com");
        String studentToken = loginForToken("student@example.com");
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        String expiredToken = tokenWithTimes(Instant.now().minusSeconds(120), Instant.now().minusSeconds(60), Role.ADMIN);
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer not.a.valid.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDocumentsThatClientMustDiscardToken() throws Exception {
        register("logout@example.com");
        String token = loginForToken("logout@example.com");
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategy").value("client_discard_until_expiration"));
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("Student One", email, PASSWORD, departmentId)))
                .andExpect(status().isCreated());
    }

    private void assertLoginRole(String email, Role role) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(role.name()));
    }

    private String loginForToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("token").asText();
    }

    private String tokenFor(UserAccount user) {
        Instant now = Instant.now();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("college-complaint-management")
                        .subject(user.getUserId().toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim("roles", Set.of(user.getRole().name()))
                        .build())).getTokenValue();
    }

    private String tokenWithTimes(Instant issuedAt, Instant expiresAt, Role role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("college-complaint-management")
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", Set.of(role.name()))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String registerJson(String name, String email, String password, UUID department) {
        return """
                {"fullName":"%s","email":"%s","password":"%s","departmentId":"%s"}
                """.formatted(name, email, password, department);
    }

    private String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }
}
