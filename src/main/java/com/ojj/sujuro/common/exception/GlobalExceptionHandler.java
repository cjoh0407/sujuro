package com.ojj.sujuro.common.exception;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 컨트롤러에서 발생한 예외를 공통으로 처리한다.
 *
 * - BusinessException : 메시지를 담아 error/business 화면으로 보낸다. (HTTP 400)
 * - 권한 오류(403), 없는 주소(404), 예상하지 못한 오류(500)는 여기서 가로채지 않는다.
 *   Spring Boot 기본 오류 처리가 templates/error/4xx.html, 5xx.html 로 보여 준다.
 *   (AccessDeniedException을 여기서 잡으면 보안 필터의 403 처리가 깨지므로 일부러 두지 않는다.)
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException e, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("message", e.getMessage());
        return "error/business";
    }
}
