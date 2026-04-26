package com.knoc.order.repository;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // 주문 번호로 조회하는 메서드 추가
    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByJunior_IdOrderByCreatedAtDesc(Long juniorId);

    List<Order> findBySenior_IdOrderByCreatedAtDesc(Long seniorId);

    // 해당 채팅방에 결제 요청(Order)이 이미 발행된 적이 있는지 여부.
    // 시니어의 '결제 요청하기' 버튼 초기 노출 제어에 사용 (한 채팅방당 한 번만 요청하는 정책).
    boolean existsByChatRoom_Id(Long chatRoomId);

    boolean existsByChatRoomAndStatusIn(ChatRoom chatRoom, List<OrderStatus> statuses);

}
