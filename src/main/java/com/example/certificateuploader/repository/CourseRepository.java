package com.example.certificateuploader.repository;

import com.example.certificateuploader.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // Only active courses
    List<Course> findByActiveTrue();

    // All courses under a category regardless of status
    List<Course> findByCategoryId(Long categoryId);

    // Active courses under a category
    List<Course> findByCategoryIdAndActiveTrue(Long categoryId);

    // Soft deactivate
    @Modifying
    @Transactional
    @Query("UPDATE Course c SET c.active = false WHERE c.id = :id")
    void deactivate(@Param("id") Long id);

    // Restore
    @Modifying
    @Transactional
    @Query("UPDATE Course c SET c.active = true WHERE c.id = :id")
    void restore(@Param("id") Long id);
}