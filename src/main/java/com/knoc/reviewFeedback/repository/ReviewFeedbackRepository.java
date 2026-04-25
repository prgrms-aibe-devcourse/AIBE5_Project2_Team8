package com.knoc.reviewFeedback.repository;

import com.knoc.senior.entity.SeniorProfile;
import com.knoc.reviewFeedback.entity.ReviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewFeedbackRepository extends JpaRepository<ReviewFeedback,Long> {
    boolean existsByOrderId(Long orderId);

    java.util.Optional<ReviewFeedback> findByOrderId(Long orderId);

    List<ReviewFeedback> findTop3BySeniorProfile_IdOrderByCreatedAtDesc(Long seniorProfileId);

    List<ReviewFeedback> findBySeniorProfile_Id(Long seniorProfileId);

    List<ReviewFeedback> findBySeniorProfile_IdOrderByCreatedAtDesc(Long seniorProfileId);

    org.springframework.data.domain.Page<ReviewFeedback> findBySeniorProfile_IdOrderByCreatedAtDesc(Long seniorProfileId, org.springframework.data.domain.Pageable pageable);

    List<ReviewFeedback> findAllByOrderByCreatedAtDesc();

    @Query("SELECT r.seniorProfile FROM ReviewFeedback r WHERE r.createdAt >= :startOfMonth GROUP BY r.seniorProfile ORDER BY COUNT(r) DESC")
    List<SeniorProfile> findTop3ActiveSeniorsThisMonth(@Param("startOfMonth") LocalDateTime startOfMonth, org.springframework.data.domain.Pageable pageable);
}
