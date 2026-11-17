package com.college.complaint;

import com.college.complaint.model.Building;
import com.college.complaint.model.Classroom;
import com.college.complaint.model.Complaint;
import com.college.complaint.model.ComplaintCategory;
import com.college.complaint.model.ComplaintImage;
import com.college.complaint.model.ComplaintOwnership;
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
import com.college.complaint.storage.SupabaseStorageClient;
import com.college.complaint.exception.StorageServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ComplaintImageIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ComplaintImageRepository imageRepository;
    @Autowired private ComplaintStatusHistoryRepository historyRepository;
    @Autowired private ComplaintOwnershipRepository ownershipRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private ComplaintCategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BuildingRepository buildingRepository;
    @Autowired private FloorRepository floorRepository;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private JwtEncoder jwtEncoder;
    @MockitoBean private SupabaseStorageClient storageClient;

    private UserAccount student;
    private UserAccount faculty;
    private UserAccount admin;
    private UUID complaintId;

    @BeforeEach
    void setUp() {
        reset(storageClient);
        imageRepository.deleteAll();
        historyRepository.deleteAll();
        ownershipRepository.deleteAll();
        complaintRepository.deleteAll();
        classroomRepository.deleteAll();
        floorRepository.deleteAll();
        buildingRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        Building building = buildingRepository.save(new Building("Image Test Building"));
        Floor floor = floorRepository.save(new Floor(building, 0, "Ground Floor"));
        Classroom classroom = classroomRepository.save(new Classroom(floor, "I-101", "Image Test Room"));
        ComplaintCategory category = categoryRepository.save(
                new ComplaintCategory(null, "Image Test Category", true));
        student = userRepository.save(new UserAccount(null, "Student Owner", "image-student@test.local",
                "test-hash", Role.STUDENT));
        faculty = userRepository.save(new UserAccount(null, "Faculty Owner", "image-faculty@test.local",
                "test-hash", Role.FACULTY));
        admin = userRepository.save(new UserAccount(null, "Admin Owner", "image-admin@test.local",
                "test-hash", Role.ADMIN));
        Complaint complaint = complaintRepository.save(new Complaint(
                "Image test complaint", "Image upload test", category, building, floor, classroom));
        complaintId = complaint.getComplaintId();
        ownershipRepository.save(new ComplaintOwnership(complaint, student));
    }

    @Test
    void uploadsImagePersistsMetadataAndDoesNotExposePrivateFieldsOrOriginalFilename() throws Exception {
        when(storageClient.createSignedUrl(anyString(), any(Duration.class)))
                .thenReturn("https://example.supabase.co/storage/v1/object/sign/private?token=short-lived");
        MvcResult result = mockMvc.perform(multipart("/api/complaints/{id}/images", complaintId)
                        .file(pngFile("../../private-name.png"))
                        .header("Authorization", token(student)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.fileSizeBytes").isNumber())
                .andExpect(jsonPath("$.data.imageId").isNotEmpty())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.fullName").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.ownershipId").doesNotExist())
                .andReturn();

        assertEquals(1, imageRepository.count());
        ComplaintImage saved = imageRepository.findAll().get(0);
        assertEquals(complaintId, saved.getComplaint().getComplaintId());
        assertTrue(saved.getStoragePath().matches(complaintId + "/[0-9a-f-]{36}\\.png"));
        assertFalse(saved.getStoragePath().contains("private-name"));
        verify(storageClient).upload(eq(saved.getStoragePath()), any(byte[].class), eq("image/png"));
    }

    @Test
    void allRolesCanUploadToComplaintsTheyOwn() throws Exception {
        for (UserAccount owner : Set.of(student, faculty, admin)) {
            UUID targetComplaint = owner.equals(student) ? complaintId : createOwnedComplaint(owner);
            mockMvc.perform(multipart("/api/complaints/{id}/images", targetComplaint)
                            .file(pngFile("image.png"))
                            .header("Authorization", token(owner)))
                    .andExpect(status().isCreated());
        }
        assertEquals(3, imageRepository.count());
    }

    @Test
    void rejectsInvalidImageContentEvenWhenHeaderClaimsPng() throws Exception {
        MockMultipartFile invalid = new MockMultipartFile("file", "fake.png", "image/png",
                "not really an image".getBytes());
        mockMvc.perform(multipart("/api/complaints/{id}/images", complaintId)
                        .file(invalid).header("Authorization", token(student)))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(0, imageRepository.count());
    }

    @Test
    void rejectsMismatchedMediaType() throws Exception {
        MockMultipartFile mislabeled = new MockMultipartFile("file", "fake.jpg", "image/jpeg", pngBytes());
        mockMvc.perform(multipart("/api/complaints/{id}/images", complaintId)
                        .file(mislabeled).header("Authorization", token(student)))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(0, imageRepository.count());
    }

    @Test
    void rejectsOversizedAndEmptyFiles() throws Exception {
        MockMultipartFile oversized = new MockMultipartFile("file", "large.png", "image/png",
                new byte[5 * 1024 * 1024 + 1]);
        mockMvc.perform(multipart("/api/complaints/{id}/images", complaintId)
                        .file(oversized).header("Authorization", token(student)))
                .andExpect(status().isPayloadTooLarge());

        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        mockMvc.perform(multipart("/api/complaints/{id}/images", complaintId)
                        .file(empty).header("Authorization", token(student)))
                .andExpect(status().isBadRequest());
        assertEquals(0, imageRepository.count());
    }

    @Test
    void rejectsAnonymousAndOtherUsersCannotUploadOrViewImages() throws Exception {
        mockMvc.perform(multipart("/api/complaints/{id}/images", complaintId).file(pngFile("x.png")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/complaints/{id}/images", complaintId)
                        .file(pngFile("x.png")).header("Authorization", token(faculty)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/complaints/{id}/images", complaintId)
                        .header("Authorization", token(admin)))
                .andExpect(status().isNotFound());
        assertEquals(0, imageRepository.count());
    }

    @Test
    void storageFailureDoesNotPersistMetadata() throws Exception {
        doThrow(new StorageServiceException("simulated storage outage"))
                .when(storageClient).upload(anyString(), any(byte[].class), anyString());
        mockMvc.perform(multipart("/api/complaints/{id}/images", complaintId)
                        .file(pngFile("image.png")).header("Authorization", token(student)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Image storage is temporarily unavailable"));
        assertEquals(0, imageRepository.count());
    }

    @Test
    void returnsTemporarySignedUrlsOnlyAfterOwnerAuthorization() throws Exception {
        when(storageClient.createSignedUrl(anyString(), any(Duration.class)))
                .thenReturn("https://example.supabase.co/storage/v1/object/sign/complaint-images/token");
        mockMvc.perform(multipart("/api/complaints/{id}/images", complaintId)
                        .file(pngFile("image.png")).header("Authorization", token(student)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/complaints/{id}/images", complaintId)
                        .header("Authorization", token(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].signedUrl").value(
                        "https://example.supabase.co/storage/v1/object/sign/complaint-images/token"))
                .andExpect(jsonPath("$.data[0].signedUrlExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].fullName").doesNotExist())
                .andExpect(jsonPath("$.data[0].ownershipId").doesNotExist());
        verify(storageClient).createSignedUrl(anyString(), eq(Duration.ofSeconds(300)));
    }

    private UUID createOwnedComplaint(UserAccount owner) {
        ComplaintCategory category = categoryRepository.findAll().get(0);
        Building building = buildingRepository.findAll().get(0);
        Floor floor = floorRepository.findAll().get(0);
        Classroom classroom = classroomRepository.findAll().get(0);
        Complaint complaint = complaintRepository.save(new Complaint(
                "Owned complaint", "For image authorization", category, building, floor, classroom));
        ownershipRepository.save(new ComplaintOwnership(complaint, owner));
        return complaint.getComplaintId();
    }

    private MockMultipartFile pngFile(String filename) throws Exception {
        return new MockMultipartFile("file", filename, "image/png", pngBytes());
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private String token(UserAccount user) {
        Instant now = Instant.now();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("college-complaint-management")
                        .subject(user.getUserId().toString()).issuedAt(now).expiresAt(now.plusSeconds(300))
                        .claim("roles", Set.of(user.getRole().name())).build())).getTokenValue();
    }
}
