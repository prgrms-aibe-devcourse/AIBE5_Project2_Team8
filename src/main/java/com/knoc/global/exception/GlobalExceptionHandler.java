package com.knoc.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

// [비즈니스 로직 예외 처리기]
// - 컨트롤러 내부에서 발생하여 밖으로 던져진 예외를 가로채는 역할을 합니다.
// - 특정 비즈니스 상황(예: 잔액 부족, 중복 가입 등)에서 우리가 직접 throw한 예외를 처리합니다.
// - 상세한 에러 메시지를 Model에 담아 뷰(HTML)에 전달할 수 있다는 장점이 있습니다.
// - 예외 발생 시 로그를 남기도록 개선했습니다.

@Slf4j // 로그를 찍기 위해 추가 (private statis final Logger log...와 같음)
@ControllerAdvice // // 모든 컨트롤러에서 발생하는 예외를 감시하겠다고 선언
public class GlobalExceptionHandler {

    // 프로젝트에서 정의한 BusinessException이 발생했을 때 호출됨
    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, HttpServletRequest request, Model model) {
        // 비즈니스 예외는 의도된 에러이므로 경고(warn) 수준으로 로그 기록
        log.warn("Business Exception: {}", e.getMessage());

        String accept = request.getHeader("Accept");
        ErrorCode errorCode = e.getErrorCode();
        // 1. API(AJAX) 요청일 경우 (Accept 헤더에 application/json이 포함된 경우)
        if (accept != null && accept.contains("application/json")) {
            // 브라우저 콘솔이나 자바스크립트에 에러 정보를 JSON으로 전달
            return ResponseEntity.status(errorCode.getStatus())
                    .body(Map.of("message", errorCode.getMessage(), "status", errorCode.getStatus()));
        }

        // 2. 일반 페이지 요청인 경우
        model.addAttribute("message", e.getMessage());
        model.addAttribute("status", errorCode.getStatus());
        // 에러 코드에 따라 적절한 페이지로 이동
        return "error/" + errorCode.getStatus(); // templates/error/400.html 등으로 매핑
    }

    // [서버 오류 로깅 및 처리]
    // - 위에서 처리하지 못한 그 외 모든 예외(NullPointer 등)를 처리합니다.
    @ExceptionHandler(Exception.class)
    public Object handleAllException(Exception e, HttpServletRequest request) throws Exception {

        // 400, 403, 404 등 '스프링 표준 예외(ResponseStatusException 등)'는
        // 직접 처리하지 않고 다시 던져서 CustomErrorController에게 양보합니다.
        if (e instanceof NoResourceFoundException ||
            e instanceof org.springframework.web.server.ResponseStatusException) {
            throw e;
        }

        // 그 외의 예상치 못한 서버 에러는 에러(error) 수준 로그 찍고 500 처리
        log.error("Unexpected Server Error: ", e);

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return ResponseEntity.status(500)
                    .body(Map.of("message", ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), "status", 500));
        }

        return "error/500";
    }
}
