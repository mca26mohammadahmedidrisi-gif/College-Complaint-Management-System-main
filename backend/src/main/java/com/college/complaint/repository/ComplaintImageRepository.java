package com.college.complaint.repository;

import com.college.complaint.model.ComplaintImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComplaintImageRepository extends JpaRepository<ComplaintImage, UUID> {

    List<ComplaintImage> findAllByComplaint_ComplaintIdOrderByCreatedAtAsc(UUID complaintId);
}
