package com.college.complaint.repository;

import com.college.complaint.model.Floor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FloorRepository extends JpaRepository<Floor, UUID> {

    List<Floor> findAllByBuilding_BuildingIdOrderByFloorNumberAsc(UUID buildingId);
}
