package com.knoc.chat.dto;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;

import java.util.List;
import java.util.Map;

// 채팅방 목록 페이지 용 DTO
public record ChatRoomListDto(
        List<ChatRoom> rooms,
        Map<Long, ChatMessage> latestMessages,
        String currentNickname
) {}
