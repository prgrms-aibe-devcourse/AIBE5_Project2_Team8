package com.knoc.global.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

// [비즈니스 로직 예외 처리기]
// - 컨트롤러 내부에서 발생하여 밖으로 던져진 예외를 가로채는 역할을 합니다.
// - 특정 비즈니스 상황(예: 잔액 부족, 중복 가입 등)에서 우리가 직접 throw한 예외를 처리합니다.
// - 상세한 에러 메시지를 Model에 담아 뷰(HTML)에 전달할 수 있다는 장점이 있습니다.

@ControllerAdvice
public class GlobalExceptionHandler {

    // 프로젝트에서 정의한 BusinessException이 발생했을 때 호출됨
    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException e, Model model) {
        model.addAttribute("message", e.getMessage());
        int status = e.getErrorCode().getStatus();
        model.addAttribute("status", status);

        // 에러 코드에 따라 적절한 페이지로 이동
        return "error/" + status; // templates/error/400.html 등으로 매핑
    }

    // 위에처 처리하지 못한 그 외 모든 예외(500 에러) 처리
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        return "error/500";
    }
}
