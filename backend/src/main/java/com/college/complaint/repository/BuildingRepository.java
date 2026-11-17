package com.college.complaint.repository;

import com.college.complaint.model.Building;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BuildingRepository extends JpaRepository<Building, UUID> {

    List<Building> findAllByOrderByBuildingNameAsc();
}
