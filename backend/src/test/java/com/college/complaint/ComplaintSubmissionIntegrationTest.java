package com.college.complaint;

import com.college.complaint.model.Building;
import com.college.complaint.model.Classroom;
import com.college.complaint.model.Complaint;
import com.college.complaint.model.ComplaintCategory;
import com.college.complaint.model.ComplaintStatus;
import com.college.complaint.model.Floor;
import com.college.complaint.model.Role;
import com.college.complaint.model.UserAccount;
import com.college.complaint.repository.BuildingRepository;
import com.college.complaint.repository.ClassroomRepository;
import com.college.complaint.repository.ComplaintCategoryRepository;
import com.college.complaint.repository.ComplaintImageRepository;
import com.college.complaint.repository.ComplaintOwnershipRepository;
import com.college.complaint.repository.ComplaintRepository;
import com.college.complaint.repository.ComplaintStatusHistoryRepository;
import com.college.complaint.repository.FloorRepository;
import com.college.complaint.repository.UserRepository;
import com.college.complaint.security.ComplaintTrackingReferenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ComplaintSubmissionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private BuildingRepository buildingRepository;
    @Autowired private FloorRepository floorRepository;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private ComplaintCategoryRepository categoryRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private ComplaintImageRepository imageRepository;
    @Autowired private ComplaintOwnershipRepository ownershipRepository;
    @Autowired private ComplaintStatusHistoryRepository historyRepository;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private ComplaintTrackingReferenceService trackingReferenceService;

    private UUID categoryId;
    private UUID inactiveCategoryId;
    private UUID classroomId;
    private UserAccount student;
    private UserAccount faculty;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        clearComplaintData();
        classroomRepository.deleteAll();
        floorRepository.deleteAll();
        buildingRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        Building building = buildingRepository.save(new Building("Complaint Test Building"));
        Floor floor = floorRepository.save(new Floor(building, 0, "Ground Floor"));
        Classroom classroom = classroomRepository.save(new Classroom(floor, "T-101", "Test Room"));
        classroomId = classroom.getClassroomId();
        ComplaintCategory category = categoryRepository.save(
                new ComplaintCategory(null, "Test Facilities", true));
        categoryId = category.getCategoryId();
        ComplaintCategory inactive = categoryRepository.save(
                new ComplaintCategory(null, "Inactive Category", false));
        inactiveCategoryId = inactive.getCategoryId();

        student = userRepository.save(new UserAccount(null, "Student Submitter", "student-complaint@test.local",
                "test-hash", Role.STUDENT));
        faculty = userRepository.save(new UserAccount(null, "Faculty Submitter", "faculty-complaint@test.local",
                "test-hash", Role.FACULTY));
        admin = userRepository.save(new UserAccount(null, "Admin Submitter", "admin-complaint@test.local",
                "test-hash", Role.ADMIN));
    }

    @Test
    void studentCanSubmitAndResponseContainsNoSubmitterInformation() throws Exception {
        JsonNode response = submit(student);
        assertPersistedSubmission(response, student);
        JsonNode data = response.path("data");
        assertFalse(data.has("userId"));
        assertFalse(data.has("email"));
        assertFalse(data.has("fullName"));
        assertFalse(data.has("ownershipId"));
        assertFalse(data.has("complaintId"));
    }

    @Test
    void facultyCanSubmitComplaint() throws Exception {
        assertPersistedSubmission(submit(faculty), faculty);
    }

    @Test
    void adminCanSubmitComplaint() throws Exception {
        assertPersistedSubmission(submit(admin), admin);
    }

    @Test
    void rejectsBlankTitleAndDescription() throws Exception {
        String token = bearerToken(student);
        mockMvc.perform(post("/api/complaints").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(" ", "Description", categoryId, classroomId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.title").exists());
        mockMvc.perform(post("/api/complaints").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Title", "", categoryId, classroomId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.description").exists());
        assertEquals(0, complaintRepository.count());
    }

    @Test
    void rejectsMissingOrInactiveCategory() throws Exception {
        String token = bearerToken(student);
        for (UUID badId : Set.of(UUID.randomUUID(), inactiveCategoryId)) {
            mockMvc.perform(post("/api/complaints").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson("Title", "Description", badId, classroomId)))
                    .andExpect(status().isNotFound());
        }
        assertEquals(0, complaintRepository.count());
    }

    @Test
    void rejectsMissingClassroom() throws Exception {
        mockMvc.perform(post("/api/complaints").header("Authorization", bearerToken(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Title", "Description", categoryId, UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Classroom not found"));
        assertEquals(0, complaintRepository.count());
    }

    @Test
    void rejectsUnauthenticatedSubmission() throws Exception {
        mockMvc.perform(post("/api/complaints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Title", "Description", categoryId, classroomId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generatesUniqueSignedTrackingReferences() throws Exception {
        JsonNode first = submit(student);
        JsonNode second = submit(student);
        String firstReference = first.path("data").path("trackingReference").asText();
        String secondReference = second.path("data").path("trackingReference").asText();
        assertNotEquals(firstReference, secondReference);
        assertEquals(2, complaintRepository.count());
        var complaints = complaintRepository.findAll();
        assertTrue(complaints.stream().anyMatch(c -> trackingReferenceService.verifies(
                firstReference, c.getComplaintId())));
        assertTrue(complaints.stream().anyMatch(c -> trackingReferenceService.verifies(
                secondReference, c.getComplaintId())));
        assertFalse(trackingReferenceService.verifies(firstReference + "x", complaints.get(0).getComplaintId()));
        UUID resolvedId = trackingReferenceService.resolve(firstReference).orElseThrow();
        assertTrue(complaintRepository.existsById(resolvedId));
        assertTrue(trackingReferenceService.resolve(firstReference + "x").isEmpty());
    }

    @Test
    void ownerCanListAndRetrieveOnlyTheirIdentityFreeComplaints() throws Exception {
        String reference = submit(student).path("data").path("trackingReference").asText();
        mockMvc.perform(get("/api/complaints/mine").header("Authorization", bearerToken(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].trackingReference").value(reference))
                .andExpect(jsonPath("$.data[0].history.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].email").doesNotExist());

        mockMvc.perform(get("/api/complaints/mine/{reference}", reference)
                        .header("Authorization", bearerToken(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingReference").value(reference));

        UserAccount otherStudent = userRepository.save(new UserAccount(null, "Other Student",
                "other-student@test.local", "test-hash", Role.STUDENT));
        mockMvc.perform(get("/api/complaints/mine/{reference}", reference)
                        .header("Authorization", bearerToken(otherStudent)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanListIdentityFreeComplaintsAndStatusChangesAreRecorded() throws Exception {
        String reference = submit(student).path("data").path("trackingReference").asText();
        UUID complaintId = trackingReferenceService.resolve(reference).orElseThrow();
        mockMvc.perform(get("/api/complaints/staff").header("Authorization", bearerToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].fullName").doesNotExist())
                .andExpect(jsonPath("$.data[0].email").doesNotExist());

        mockMvc.perform(put("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", bearerToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"remarks\":\"\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", bearerToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ASSIGNED\",\"remarks\":\"Forwarded to the department\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.history.length()").value(2));

        mockMvc.perform(put("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", bearerToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"remarks\":\"Inspection started\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.remarks").value("Inspection started"))
                .andExpect(jsonPath("$.data.history.length()").value(3));

        mockMvc.perform(put("/api/complaints/{id}/status", complaintId)
                        .header("Authorization", bearerToken(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"remarks\":\"\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void categoriesEndpointReturnsOnlyActiveCategories() throws Exception {
        mockMvc.perform(get("/api/complaints/categories").header("Authorization", bearerToken(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].categoryName").value("Test Facilities"))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist());
    }

    private JsonNode submit(UserAccount user) throws Exception {
        String raw = mockMvc.perform(post("/api/complaints")
                        .header("Authorization", bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Broken light", "The ceiling light is not working.", categoryId, classroomId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value(ComplaintStatus.PENDING.name()))
                .andExpect(jsonPath("$.data.trackingReference").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(raw);
    }

    private void assertPersistedSubmission(JsonNode response, UserAccount submitter) {
        String reference = response.path("data").path("trackingReference").asText();
        assertNotNull(reference);
        assertTrue(complaintRepository.findAll().stream().anyMatch(complaint -> {
            assertEquals(ComplaintStatus.PENDING, complaint.getStatus());
            assertTrue(trackingReferenceService.verifies(reference, complaint.getComplaintId()));
            var ownership = ownershipRepository.findByComplaint_ComplaintId(complaint.getComplaintId()).orElseThrow();
            assertEquals(submitter.getUserId(), ownership.getUser().getUserId());
            var history = historyRepository.findAllByComplaint_ComplaintIdOrderByChangedAtAsc(complaint.getComplaintId());
            assertEquals(1, history.size());
            assertEquals(ComplaintStatus.PENDING, history.get(0).getNewStatus());
            assertEquals(null, history.get(0).getPreviousStatus());
            return true;
        }));
    }

    private String bearerToken(UserAccount user) {
        Instant now = Instant.now();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("college-complaint-management")
                        .subject(user.getUserId().toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim("roles", Set.of(user.getRole().name()))
                        .build())).getTokenValue();
        return "Bearer " + token;
    }

    private String requestJson(String title, String description, UUID category, UUID classroom) {
        return """
                {"title":"%s","description":"%s","categoryId":"%s","classroomId":"%s"}
                """.formatted(title, description, category, classroom);
    }

    private void clearComplaintData() {
        imageRepository.deleteAll();
        historyRepository.deleteAll();
        ownershipRepository.deleteAll();
        complaintRepository.deleteAll();
    }
}
