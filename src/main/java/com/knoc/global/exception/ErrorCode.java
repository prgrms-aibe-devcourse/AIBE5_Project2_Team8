package com.knoc.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
// 에러 정보를 모아둔 열거형
public enum ErrorCode {
    // 공통
    INVALID_INPUT_VALUE(400, "잘못된 요청 파라미터입니다."),
    ACCESS_DENIED(403, "해당 페이지에 접근할 권한이 없습니다."),
    ENTITY_NOT_FOUND(404, "찾을 수 없는 페이지입니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),

    //시니어 프로필 관련(Senior)
    SENIOR_PROFILE_NOT_FOUND(404,"시니어 프로필이 존재하지 않습니다"),

    // 회원 관련 (Member)
    MEMBER_NOT_FOUND(404, "존재하지 않는 회원입니다."),

    // 채팅 관련 (Chat)
    CHATROOM_NOT_FOUND(404, "존재하지 않는 채팅방입니다."),

    // 주문 관련 (Order)
    ORDER_NOT_FOUND(404, "존재하지 않는 주문입니다."),
    NOT_SENIOR_IN_ROOM(403, "해당 채팅방의 시니어가 아닙니다."), // 권한 검증용
    NOT_JUNIOR_FOR_ORDER(403, "해당 주문의 주니어가 아닙니다."), // 권한 검증용
    ORDER_CANNOT_BE_PAID(400, "현재 주문 상태에서는 결제를 진행할 수 없습니다."),
    ORDER_PAYMENT_CONFLICT(409, "동시에 결제가 시도되어 처리에 실패했습니다. 다시 시도해주세요."),
    ORDER_PAYMENT_AMOUNT_MISMATCH(400, "결제 금액이 주문 금액과 일치하지 않습니다."),

    // 리뷰 관련 (Review)
    REVIEW_ALREADY_EXISTS(409, "이미 해당 주문에 대한 후기가 존재합니다."),
    REVIEW_NOT_ALLOWED(403, "결제 완료된 주문만 후기를 작성할 수 있습니다."),

    // 이메일 관련 (Auth)
    ALREADY_VERIFIED(400, "이미 완료된 인증입니다."),
    EXPIRED_VERIFICATION_CODE(400, "인증번호 유효기간이 지났습니다."),
    INVALID_VERIFICATION_CODE(400, "인증번호가 일치하지 않습니다."),
    INVALID_EMAIL_DOMAIN(400, "기업 이메일만 인증 가능합니다."),
    EMAIL_VERIFICATION_NOT_FOUND(404, "인증 요청 내역이 없습니다.");

    private final int status;
    private final String message;
}
