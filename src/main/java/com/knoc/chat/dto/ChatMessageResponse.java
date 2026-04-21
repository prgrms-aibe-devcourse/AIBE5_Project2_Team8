package com.knoc.chat.dto;

import com.knoc.chat.entity.MessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class ChatMessageResponse {
    private String senderNickname;
    private String content;
    private LocalDateTime createdAt;
    private MessageType messageType;

    public ChatMessageResponse(String senderNickname, String content, LocalDateTime createdAt, MessageType messageType) {
        this.senderNickname = senderNickname;
        this.content = content;
        this.createdAt = createdAt;
        this.messageType = messageType;
    }
}
