package com.knoc.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

// [비즈니스 로직 예외 처리기]
// - 컨트롤러 내부에서 발생하여 밖으로 던져진 예외를 가로채는 역할을 합니다.
// - 특정 비즈니스 상황(예: 잔액 부족, 중복 가입 등)에서 우리가 직접 throw한 예외를 처리합니다.
// - 상세한 에러 메시지를 Model에 담아 뷰(HTML)에 전달할 수 있다는 장점이 있습니다.

@ControllerAdvice
public class GlobalExceptionHandler {

    // 프로젝트에서 정의한 BusinessException이 발생했을 때 호출됨
    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, HttpServletRequest request, Model model) {
        String accept = request.getHeader("Accept");

        // 1. API(AJAX) 요청일 경우 (Accept 헤더에 application/json이 포함된 경우)
        if (accept != null && accept.contains("application/json")) {
            ErrorCode errorCode = e.getErrorCode();

            Map<String, Object> body = new HashMap<>();
            body.put("message", errorCode.getMessage());
            body.put("status", errorCode.getStatus());

            return ResponseEntity
                    .status(errorCode.getStatus())
                    .body(body);
        }

        // 2. 일반 페이지 요청인 경우
        model.addAttribute("message", e.getMessage());
        int status = e.getErrorCode().getStatus();
        model.addAttribute("status", status);

        // 에러 코드에 따라 적절한 페이지로 이동
        return "error/" + status; // templates/error/400.html 등으로 매핑
    }

    // 위에서 처리하지 못한 그 외 모든 예외(500 에러) 처리
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        String accept = request.getHeader("Accept");

        // AJAX 요청인 경우 JSON으로 500 에러 응답
        if (accept != null && accept.contains("application/json")) {
            Map<String, Object> body = new HashMap<>();
            body.put("message", "서버 내부 오류가 발생했습니다.");
            body.put("status", 500);
            return ResponseEntity
                    .status(500)
                    .body(body);
        }

        return "error/500";
    }
}
