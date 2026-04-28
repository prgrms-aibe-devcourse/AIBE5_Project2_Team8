package com.knoc.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {
    // JWT 인증완료 -> 내용만 보내고 Principal로 검증
    // 전송할 메시지 내용
    private String content;
}
