package com.college.complaint.controller;

import com.college.complaint.dto.LocationDtos;
import com.college.complaint.service.LocationService;
import com.college.complaint.utility.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/buildings")
    public ApiResponse<List<LocationDtos.BuildingResponse>> getBuildings() {
        return ApiResponse.success("Buildings retrieved successfully", locationService.getBuildings());
    }

    @GetMapping("/buildings/{buildingId}/floors")
    public ApiResponse<List<LocationDtos.FloorResponse>> getFloors(@PathVariable UUID buildingId) {
        return ApiResponse.success("Floors retrieved successfully", locationService.getFloors(buildingId));
    }

    @GetMapping("/floors/{floorId}/classrooms")
    public ApiResponse<List<LocationDtos.ClassroomResponse>> getClassrooms(@PathVariable UUID floorId) {
        return ApiResponse.success("Classrooms retrieved successfully", locationService.getClassrooms(floorId));
    }
}
