package com.knoc.global.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

// [전역 에러 컨트롤러]
// - 스프링의 컨트롤러에 도달하기 전이나, 스프링 밖(서블릿 컨테이너 레벨)에서 발생하는 에러를 처리합니다.
// - 대표적으로 404 Not Found(존재하지 않는 URL) 같은 상황은 컨트롤러를 타지 않으므로 여기서 잡아야 합니다.
// - 즉, GlobalExceptionHandler가 잡지 못하는 '모든 구멍'을 메우는 마지막 방어선입니다.

@Controller
public class CustomErrorController implements ErrorController {
    // 스프링 부트 에러 기본 경로인 /error로 들어오는 요청 처리
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        // HTTP 상태 코드를 요청 객체에서 꺼내옴
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if  (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            // 상태 코드에 따른 페이지 분기
            if (statusCode == HttpStatus.NOT_FOUND.value()) return "error/404";
            if (statusCode == HttpStatus.FORBIDDEN.value()) return "error/403";
            if (statusCode == HttpStatus.BAD_REQUEST.value()) return "error/400";
        }
        // 위 3가지 경우 외의 모든 에러는 기본적으로 500 페이지 보여줌
        return "error/500";
    }
}
