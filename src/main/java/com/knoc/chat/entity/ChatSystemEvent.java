package com.knoc.chat.entity;

/**
 * 시스템 메시지 발행을 위한 도메인 이벤트
 * @param roomId   메시지를 보낼 채팅방 ID
 * @param type     메시지의 성격 (결제완료, 리포트도착 등)
 * @param customContent 동적 텍스트 (금액 등 포함). null이면 MessageType의 기본 문구 사용
 * @param referenceId 해당 메시지와 연결될 주문(Order) ID
 */

public record ChatSystemEvent(
        Long roomId,
        MessageType type,
        String customContent,
        Long referenceId
) {}
