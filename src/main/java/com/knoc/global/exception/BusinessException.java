package com.knoc.global.exception;

import lombok.Getter;

@Getter
// 코드에서 직접 던질 예외 클래스
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
