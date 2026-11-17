package com.college.complaint.service;

import com.college.complaint.dto.LocationDtos;
import com.college.complaint.exception.ApiException;
import com.college.complaint.model.Building;
import com.college.complaint.model.Classroom;
import com.college.complaint.model.Floor;
import com.college.complaint.repository.BuildingRepository;
import com.college.complaint.repository.ClassroomRepository;
import com.college.complaint.repository.FloorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LocationService {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ClassroomRepository classroomRepository;

    public LocationService(BuildingRepository buildingRepository,
                           FloorRepository floorRepository,
                           ClassroomRepository classroomRepository) {
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
        this.classroomRepository = classroomRepository;
    }

    public List<LocationDtos.BuildingResponse> getBuildings() {
        return buildingRepository.findAllByOrderByBuildingNameAsc().stream()
                .map(this::toBuildingResponse)
                .toList();
    }

    public List<LocationDtos.FloorResponse> getFloors(UUID buildingId) {
        requireBuilding(buildingId);
        return floorRepository.findAllByBuilding_BuildingIdOrderByFloorNumberAsc(buildingId).stream()
                .map(this::toFloorResponse)
                .toList();
    }

    public List<LocationDtos.ClassroomResponse> getClassrooms(UUID floorId) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Floor not found"));
        return classroomRepository.findAllByFloor_FloorIdOrderByClassroomNumberAsc(floor.getFloorId()).stream()
                .map(this::toClassroomResponse)
                .toList();
    }

    private void requireBuilding(UUID buildingId) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Building not found");
        }
    }

    private LocationDtos.BuildingResponse toBuildingResponse(Building building) {
        return new LocationDtos.BuildingResponse(building.getBuildingId(), building.getBuildingName());
    }

    private LocationDtos.FloorResponse toFloorResponse(Floor floor) {
        return new LocationDtos.FloorResponse(
                floor.getFloorId(), floor.getBuilding().getBuildingId(), floor.getFloorNumber(), floor.getFloorName());
    }

    private LocationDtos.ClassroomResponse toClassroomResponse(Classroom classroom) {
        return new LocationDtos.ClassroomResponse(
                classroom.getClassroomId(), classroom.getFloor().getFloorId(),
                classroom.getClassroomNumber(), classroom.getClassroomName());
    }
}
