package com.knoc.chat.entity;

public enum MessageType {
    USER,          // 일반 채팅
    SYSTEM,        // 단순 알림 (예: "채팅방이 생성되었습니다. 시니어에게 인사를 건네보세요!"),
    ORDER_REQUEST, // 결제 요청 (결제 버튼 필요)
    REVIEW_REQUEST // 리뷰 요청 (상세 리뷰 요청서 작성 버튼 필요)
}