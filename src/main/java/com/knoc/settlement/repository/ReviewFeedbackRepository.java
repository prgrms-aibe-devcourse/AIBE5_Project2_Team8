package com.knoc.settlement.repository;

import com.knoc.settlement.entity.ReviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewFeedbackRepository extends JpaRepository<ReviewFeedback,Long> {
    boolean existsByOrderId(Long orderId);

    List<ReviewFeedback> findTop3BySeniorProfile_IdOrderByCreatedAtDesc(Long seniorProfileId);

    List<ReviewFeedback> findBySeniorProfile_Id(Long seniorProfileId);

}
