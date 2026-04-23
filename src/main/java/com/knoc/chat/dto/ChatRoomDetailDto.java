package com.knoc.chat.dto;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;

import java.util.List;
import java.util.Map;

// 채팅방 상세 페이지용 DTO
public record ChatRoomDetailDto(
        Long selectedRoomId,
        List<ChatMessage> messages,
        String currentNickname,
        List<ChatRoom> rooms,
        ChatRoom selectedRoom,
        Long firstMessageId,
        Map<Long, ChatMessage> latestMessages,
        String roomStatus
) {}
