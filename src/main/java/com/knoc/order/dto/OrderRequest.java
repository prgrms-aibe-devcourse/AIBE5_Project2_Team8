package com.knoc.order.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 시니어가 '결제 요청 보내기' 버튼을 누를 때 서버로 전달될 데이터 (OrderController에서 받음)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderRequest {
    private Long chatRoomId; // 채팅방 ID
    private Long juniorId; // 결제할 대상 (주니어)
    private int amount; // 요청 금액

    public OrderRequest(Long chatRoomId, Long juniorId, int amount) {
        this.chatRoomId = chatRoomId;
        this.juniorId = juniorId;
        this.amount = amount;
    }
}
