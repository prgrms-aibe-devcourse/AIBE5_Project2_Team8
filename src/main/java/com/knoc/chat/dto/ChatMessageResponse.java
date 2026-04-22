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

    // USER
    private MessageType messageType;

    public ChatMessageResponse(Long id, String senderNickname, String content, LocalDateTime createdAt, MessageType messageType) {
        this.id = id;
        this.senderNickname = senderNickname;
        this.content = content;
        this.createdAt = createdAt;
        this.messageType = messageType;
    }
}
