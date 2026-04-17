package com.knoc.chat.entity;

public enum MessageType {
    USER,              // 일반 채팅
    SYSTEM,            // 단순 알림 (예: "채팅방이 생성되었습니다. 시니어에게 인사를 건네보세요!"),
    PAYMENT_REQUESTED, // 시니어가 결제를 요청함 (결제 버튼 노출)
    PAYMENT_COMPLETED, // 결제 완료됨 (주니어에게 "상세 리뷰 요청서 작성" 버튼 노출)
}