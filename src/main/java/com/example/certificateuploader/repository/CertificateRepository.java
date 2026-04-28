package com.example.certificateuploader.repository;

import com.example.certificateuploader.model.CertificateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CertificateRepository extends JpaRepository<CertificateEntity, Long> {

    List<CertificateEntity> findByUserIdAndActiveTrue(Long userId);

    List<CertificateEntity> findByUserId(Long userId);

    List<CertificateEntity> findByCourseIdAndActiveTrue(Long courseId);

    List<CertificateEntity> findByCourseId(Long courseId);

    // Course IDs that already have an active certificate for this user
    @Query("""
        SELECT c.course.id FROM CertificateEntity c
        WHERE c.user.id = :userId
        AND c.active = true
        AND c.course IS NOT NULL
    """)
    List<Long> findActiveCourseIdsByUserId(@Param("userId") Long userId);

    // Search + filter with optional active filter
    @Query("""
        SELECT c FROM CertificateEntity c
        WHERE c.user.id = :userId
        AND (:keyword  IS NULL OR LOWER(c.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:courseId IS NULL OR c.course.id = :courseId)
        AND (:fileType IS NULL OR c.fileType = :fileType)
        AND (:activeOnly IS NULL OR c.active = :activeOnly)
        ORDER BY c.active DESC, c.uploadDate DESC
    """)
    List<CertificateEntity> searchCertificatesWithStatus(
            @Param("userId")     Long userId,
            @Param("keyword")    String keyword,
            @Param("courseId")   Long courseId,
            @Param("fileType")   String fileType,
            @Param("activeOnly") Boolean activeOnly
    );

    @Modifying
    @Transactional
    @Query("UPDATE CertificateEntity c SET c.active = false WHERE c.id = :id")
    void softDelete(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE CertificateEntity c SET c.active = true WHERE c.id = :id")
    void restore(@Param("id") Long id);
}