package com.college.complaint;

import com.college.complaint.model.Building;
import com.college.complaint.model.Classroom;
import com.college.complaint.model.Floor;
import com.college.complaint.model.Role;
import com.college.complaint.repository.BuildingRepository;
import com.college.complaint.repository.ClassroomRepository;
import com.college.complaint.repository.ComplaintImageRepository;
import com.college.complaint.repository.ComplaintOwnershipRepository;
import com.college.complaint.repository.ComplaintRepository;
import com.college.complaint.repository.ComplaintStatusHistoryRepository;
import com.college.complaint.repository.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LocationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private FloorRepository floorRepository;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private ComplaintImageRepository imageRepository;
    @Autowired
    private ComplaintStatusHistoryRepository historyRepository;
    @Autowired
    private ComplaintOwnershipRepository ownershipRepository;
    @Autowired
    private ComplaintRepository complaintRepository;
    @Autowired
    private JwtEncoder jwtEncoder;

    private Building mainBuilding;
    private Building scienceBuilding;
    private Floor secondFloor;
    private Floor scienceFloor;

    @BeforeEach
    void setUp() {
        imageRepository.deleteAll();
        historyRepository.deleteAll();
        ownershipRepository.deleteAll();
        complaintRepository.deleteAll();
        classroomRepository.deleteAll();
        floorRepository.deleteAll();
        buildingRepository.deleteAll();

        mainBuilding = buildingRepository.save(new Building("Main Building"));
        scienceBuilding = buildingRepository.save(new Building("Science Building"));

        Floor groundFloor = floorRepository.save(new Floor(mainBuilding, 0, "Ground Floor"));
        secondFloor = floorRepository.save(new Floor(mainBuilding, 2, "Second Floor"));
        scienceFloor = floorRepository.save(new Floor(scienceBuilding, 0, "Ground Floor"));

        classroomRepository.save(new Classroom(groundFloor, "101", "Room 101"));
        classroomRepository.save(new Classroom(secondFloor, "204", "Room 204"));
        classroomRepository.save(new Classroom(secondFloor, "205", "Computer Lab 205"));
        classroomRepository.save(new Classroom(scienceFloor, "S101", "Science Lab 101"));
    }

    @Test
    void returnsBuildingsAsDtosWithoutIdentityFields() throws Exception {
        mockMvc.perform(get("/api/locations/buildings")
                        .header("Authorization", bearerToken(Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].buildingName").value("Main Building"))
                .andExpect(jsonPath("$.data[0].buildingId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].email").doesNotExist());
    }

    @Test
    void returnsOnlyFloorsOfRequestedBuilding() throws Exception {
        mockMvc.perform(get("/api/locations/buildings/{buildingId}/floors", mainBuilding.getBuildingId())
                        .header("Authorization", bearerToken(Role.FACULTY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].floorNumber").value(0))
                .andExpect(jsonPath("$.data[0].buildingId").value(mainBuilding.getBuildingId().toString()))
                .andExpect(jsonPath("$.data[1].floorName").value("Second Floor"));
    }

    @Test
    void returnsOnlyClassroomsOnRequestedFloor() throws Exception {
        mockMvc.perform(get("/api/locations/floors/{floorId}/classrooms", secondFloor.getFloorId())
                        .header("Authorization", bearerToken(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].floorId").value(secondFloor.getFloorId().toString()))
                .andExpect(jsonPath("$.data[0].classroomNumber").value("204"))
                .andExpect(jsonPath("$.data[1].classroomName").value("Computer Lab 205"));
    }

    @Test
    void allThreeRolesCanAccessAllLocationEndpoints() throws Exception {
        for (Role role : Role.values()) {
            String token = bearerToken(role);
            mockMvc.perform(get("/api/locations/buildings").header("Authorization", token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/locations/buildings/{buildingId}/floors", scienceBuilding.getBuildingId())
                            .header("Authorization", token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/locations/floors/{floorId}/classrooms", scienceFloor.getFloorId())
                            .header("Authorization", token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void returnsNotFoundForMissingBuildingOrFloor() throws Exception {
        String token = bearerToken(Role.STUDENT);
        mockMvc.perform(get("/api/locations/buildings/{buildingId}/floors", UUID.randomUUID())
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Building not found"));
        mockMvc.perform(get("/api/locations/floors/{floorId}/classrooms", UUID.randomUUID())
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Floor not found"));
    }

    @Test
    void rejectsMalformedIdsAndAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/locations/buildings/not-a-uuid/floors")
                        .header("Authorization", bearerToken(Role.STUDENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Path parameter has an invalid format"));

        mockMvc.perform(get("/api/locations/buildings"))
                .andExpect(status().isUnauthorized());
    }

    private String bearerToken(Role role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("college-complaint-management")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", Set.of(role.name()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return "Bearer " + token;
    }
}
