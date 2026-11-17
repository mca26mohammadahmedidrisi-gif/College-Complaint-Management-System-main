package com.college.complaint.repository;

import com.college.complaint.model.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

    List<Classroom> findAllByFloor_FloorIdOrderByClassroomNumberAsc(UUID floorId);
}
