package com.knoc.global.config;

import com.knoc.global.exception.ErrorCode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

// [전역 에러 컨트롤러]
// - 스프링의 컨트롤러에 도달하기 전이나, 스프링 밖(서블릿 컨테이너 레벨)에서 발생하는 에러를 처리합니다.
// - 대표적으로 404 Not Found(존재하지 않는 URL) 같은 상황은 컨트롤러를 타지 않으므로 여기서 잡아야 합니다.
// - 즉, GlobalExceptionHandler가 잡지 못하는 '모든 구멍'을 메우는 마지막 방어선입니다.

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request) {
        // HTTP 상태 코드를 요청 객체에서 꺼내옴
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String accept = request.getHeader("accept");

        if  (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            // ErrorCode Enum에서 적절한 에러 정보 추출
            String message = ErrorCode.INTERNAL_SERVER_ERROR.getMessage();
            if (statusCode == HttpStatus.NOT_FOUND.value()) message = ErrorCode.ENTITY_NOT_FOUND.getMessage();
            else if (statusCode == HttpStatus.FORBIDDEN.value()) message = ErrorCode.ACCESS_DENIED.getMessage();
            else if (statusCode == HttpStatus.BAD_REQUEST.value()) message = ErrorCode.INVALID_INPUT_VALUE.getMessage();

            // 1. AJAX(JSON) 요청인 경우: ErrorCode 기반 메시지로 응답
            if (accept != null && accept.contains("application/json")) {
                return ResponseEntity.status(statusCode)
                        .body(Map.of("status", statusCode, "message", message));
            }

            // 2. 일반 페이지(HTML) 요청인 경우: 상태 코드별 HTML 페이지 반환
            if (statusCode == HttpStatus.NOT_FOUND.value()) return "error/404";
            if (statusCode == HttpStatus.FORBIDDEN.value()) return "error/403";
            if (statusCode == HttpStatus.BAD_REQUEST.value()) return "error/400";
        }
        // 그 외의 모든 에러는 기본 500 페이지 보여줌
        return "error/500";
    }
}
