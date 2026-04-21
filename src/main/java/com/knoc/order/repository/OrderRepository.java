package com.knoc.order.repository;

import com.knoc.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // 주문 번호로 조회하는 메서드 추가
    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByJunior_IdOrderByCreatedAtDesc(Long juniorId);

    List<Order> findBySenior_IdOrderByCreatedAtDesc(Long seniorId);

}
