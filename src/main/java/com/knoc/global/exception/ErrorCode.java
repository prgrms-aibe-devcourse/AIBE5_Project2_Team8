package com.knoc.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
// 에러 정보를 모아둔 열거형
public enum ErrorCode {
    INVALID_INPUT_VALUE(400, "잘못된 요청 파라미터입니다."),
    ACCESS_DENIED(403, "해당 페이지에 접근할 권한이 없습니다."),
    ENTITY_NOT_FOUND(404, "찾을 수 없는 페이지입니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String message;
}
