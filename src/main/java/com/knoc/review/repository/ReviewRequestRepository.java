package com.knoc.review.repository;

import com.knoc.order.entity.Order;
import com.knoc.review.entity.ReviewRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRequestRepository extends JpaRepository<ReviewRequest, Long> {
    Optional<ReviewRequest> findByOrder(Order order);
}