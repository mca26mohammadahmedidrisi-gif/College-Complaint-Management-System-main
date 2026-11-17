package com.college.complaint.repository;

import com.college.complaint.model.ComplaintCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplaintCategoryRepository extends JpaRepository<ComplaintCategory, UUID> {

    Optional<ComplaintCategory> findByCategoryIdAndActiveTrue(UUID categoryId);

    List<ComplaintCategory> findAllByActiveTrueOrderByCategoryNameAsc();
}
