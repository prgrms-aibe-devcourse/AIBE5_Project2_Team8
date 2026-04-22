package com.knoc.settlement.repository;

import com.knoc.settlement.entity.ReviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewFeedbackRepository extends JpaRepository<ReviewFeedback,Long> {
    boolean existsByOrderId(Long orderId);

    List<ReviewFeedback> findTop3BySeniorProfile_IdOrderByCreatedAtDesc(Long seniorProfileId);

    List<ReviewFeedback> findBySeniorProfile_Id(Long seniorProfileId);

    List<ReviewFeedback> findBySeniorProfile_IdOrderByCreatedAtDesc(Long seniorProfileId);

<<<<<<< HEAD
    org.springframework.data.domain.Page<ReviewFeedback> findBySeniorProfile_IdOrderByCreatedAtDesc(Long seniorProfileId, org.springframework.data.domain.Pageable pageable);

=======
>>>>>>> ec1b409 (feat: 시니어가 자신의 리뷰 목록 조회를 위한 dto,service)
}
