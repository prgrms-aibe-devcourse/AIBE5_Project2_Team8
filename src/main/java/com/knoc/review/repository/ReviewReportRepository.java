package com.knoc.review.repository;

import com.knoc.review.entity.ReviewReport;
import com.knoc.review.entity.ReviewRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {
    Optional<ReviewReport> findByReviewRequest(ReviewRequest reviewRequest);
}