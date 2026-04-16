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

    // 회원 관련 (Member)
    MEMBER_NOT_FOUND(404, "존재하지 않는 회원입니다."),

    // 채팅 관련 (Chat)
    CHATROOM_NOT_FOUND(404, "존재하지 않는 채팅방입니다."),

    // 주문 관련 (Order)
    ORDER_NOT_FOUND(404, "존재하지 않는 주문입니다."),
    NOT_SENIOR_IN_ROOM(403, "해당 채팅방의 시니어가 아닙니다."), // 권한 검증용

    ORDER_REQUEST_IN_PROGRESS(429, "요청이 처리 중입니다. 잠시 후 다시 시도해주세요."); // 멱등 요청 중복 방지용

    private final int status;
    private final String message;
}
