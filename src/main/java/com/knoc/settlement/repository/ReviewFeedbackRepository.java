package com.knoc.settlement.repository;

import com.knoc.settlement.entity.ReviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewFeedbackRepository extends JpaRepository<ReviewFeedback,Long> {
    boolean existsByOrderId(Long orderId);
}
