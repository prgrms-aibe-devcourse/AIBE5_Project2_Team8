package com.knoc.chat.dto;

import com.knoc.chat.entity.MessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class ChatMessageResponse {
    private Long id;

    // 보낸 사람 닉네임
    private String senderNickname;

    // 메시지 내용
    private String content;

    // 전송 시간
    private LocalDateTime createdAt;

    // USER / PAYMENT_REQUESTED / ...
    private MessageType messageType;

    // 시스템 메시지가 참조하는 외부 엔티티 PK (예: Order.id) USER 메시지면 null
    private Long referenceId;

    // PAYMENT_REQUESTED 같은 금액 기반 시스템 메시지에서 결제 버튼 렌더링에 사용. 그 외엔 null
    private Integer amount;

    public ChatMessageResponse(Long id, String senderNickname, String content, LocalDateTime createdAt, MessageType messageType, Long referenceId, Integer amount) {
        this.id = id;
        this.senderNickname = senderNickname;
        this.content = content;
        this.createdAt = createdAt;
        this.messageType = messageType;
        this.referenceId = referenceId;
        this.amount = amount;
    }
}
