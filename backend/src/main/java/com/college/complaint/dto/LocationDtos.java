package com.college.complaint.dto;

import java.util.UUID;

public final class LocationDtos {

    private LocationDtos() {
    }

    public record BuildingResponse(UUID buildingId, String buildingName) {
    }

    public record FloorResponse(UUID floorId, UUID buildingId, Integer floorNumber, String floorName) {
    }

    public record ClassroomResponse(UUID classroomId, UUID floorId,
                                    String classroomNumber, String classroomName) {
    }
}
