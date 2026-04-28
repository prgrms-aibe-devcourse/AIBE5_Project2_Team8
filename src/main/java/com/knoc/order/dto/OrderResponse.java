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
    // preparePayment()에서도 해당 dto를 반환하기 때문에 시니어 정보가 필요함
    private String seniorNickname;
    private String seniorProfileImageUrl;
    private String seniorPosition;

    public static OrderResponse from(Order order) {
        return from(order, null); // 기존 호출부는 그대로 동작
    }

    // 시니어 직군이 필요할 때(결제 및 리뷰 시작 모달)만 파라미터 추가
    public static OrderResponse from(Order order, String seniorPosition) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .chatRoomId(order.getChatRoom().getId())
                .amount(order.getAmount())
                .orderStatus(order.getStatus())
                .seniorNickname(order.getSenior().getNickname())
                .seniorProfileImageUrl(order.getSenior().getProfileImageUrl())
                .seniorPosition(seniorPosition)
                .build();
    }
}
