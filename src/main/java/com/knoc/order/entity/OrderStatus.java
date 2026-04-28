package com.knoc.order.entity;

public enum OrderStatus {
    // 결제 대기, 결제 완료, 정산 완료(대금이 최종 지급된 상태), 취소됨
    PENDING, PAID, SETTLED, CANCELLED
}