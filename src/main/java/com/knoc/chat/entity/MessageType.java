package com.knoc.chat.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {

    // 일반 채팅은 시스템이 쏴주는 고정 문구가 없으므로 빈 칸("")으로 둔다.
    USER(""),

    // 아래부터는 이벤트 발생 시 템플릿에 들어갈 기본 문구
    PAYMENT_REQUESTED("시니어님이 %,d원 결제를 요청했습니다.\n결제를 완료하시면 상세 리뷰 요청서를 작성하실 수 있습니다."),
    PAYMENT_COMPLETED("결제가 성공적으로 처리되었습니다.\n결제 금액은 구매 확정 시까지 Knoc에서 안전하게 보호합니다."),
    REVIEW_REQUESTED("리뷰를 진행할 코드 정보와 상세한 요청 사항을 폼에 작성해주세요."),
    REVIEW_SUBMITTED("상세 리뷰 요청서가 성공적으로 접수되었습니다.\n곧 시니어의 코드 리뷰가 시작됩니다."),
    REPORT_COMPLETED("시니어님이 상세 코드 리뷰 리포트를 등록했습니다.\n주니어님은 리포트를 확인하고 구매를 확정해 주세요."),
    WORKSPACE_READY("협업을 위한 워크스페이스가 준비되었습니다.\n이제 코드를 공유하고 리뷰를 시작해 보세요.");


    private final String template;

    // 템플릿에 금액 등 동적 값을 넣어야 할 때 사용하는 유틸 메서드
    public String formatMessage(Object... args) {
        if(this.template == null) return "";
        return String.format(this.template, args);
    }
}