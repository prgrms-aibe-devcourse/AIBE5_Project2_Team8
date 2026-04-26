package com.knoc.chat.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {

    // 일반 채팅은 시스템이 쏴주는 고정 문구가 없으므로 빈 칸("")으로 둔다.
    USER(""),

    // 아래부터는 이벤트 발생 시 템플릿에 들어갈 기본 문구
    PAYMENT_REQUESTED("시니어님이 %,d원 결제를 요청했습니다..."),
    PAYMENT_COMPLETED("결제가 성공적으로 처리되었습니다.\n결제 금액..."),
    PAYMENT_FAILED("결제가 실패하거나 취소되었습니다. 다시 시도해..."),
    REVIEW_REQUESTED("리뷰를 진행할 코드 정보와 상세한 요청 사항..."),
    REVIEW_SUBMITTED("상세 리뷰 요청서가 성공적으로 접수되었습니다..."),
    REPORT_COMPLETED("시니어님이 상세 코드 리뷰 리포트를 등록했습니..."),
    WORKSPACE_READY("협업을 위한 워크스페이스가 준비되었습니다.\n..."),
    PURCHASE_CONFIRMED("구매가 확정되었습니다. 멘토링이 성공적..."),
    ROOM_CLOSE("멘토링이 종료되어 채팅창이 읽기 전용으로 전환됩니..."),
    ROOM_REOPEN("멘토링이 다시 시작되었습니다! 자유롭게 대화..."),
    REVIEW_WRITTEN("멘토링 후기가 작성되었습니다. 아래 버튼을...");


    private final String template;

    // 템플릿에 금액 등 동적 값을 넣어야 할 때 사용하는 유틸 메서드
    public String formatMessage(Object... args) {
        if(this.template == null) return "";
        return String.format(this.template, args);
    }
}