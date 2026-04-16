package com.knoc.order.dto;

import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
// 주문 생성이 성공했음을 알리고, 이후 채팅방에 시스템 메시지를 보내거나 상태를 업데이트할 때 사용할 정보를 담음
public class OrderResponse {
    private Long orderId;
    private String orderNumber;
    private Long chatRoomId;
    private int amount;
    private OrderStatus orderStatus;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .chatRoomId(order.getChatRoom().getId())
                .amount(order.getAmount())
                .orderStatus(order.getStatus())
                .build();
    }
}
