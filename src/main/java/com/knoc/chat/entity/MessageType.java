package com.knoc.chat.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {

    // 일반 채팅은 시스템이 쏴주는 고정 문구가 없으므로 빈 칸("")으로 둔다.
    USER(""),

    // 아래부터는 이벤트 발생 시 템플릿에 들어갈 기본 문구
    SYSTEM("시스템 알림입니다."),
    PAYMENT_REQUESTED("결제 요청이 도착했습니다. 아래 버튼을 눌러 결제를 진행해주세요."),
    PAYMENT_COMPLETED("결제가 완료되었습니다. 상세 리뷰 요청서 작성 버튼이 활성화되었습니다.");

    // 각 ENUM 상수가 품고 있을 기본 문구
    private final String defaultTemplate;
}