package com.ojj.sujuro.common.exception;

/**
 * 업무 규칙을 어겼을 때 던지는 예외. (예: 이미 등록된 번호, 허용되지 않는 상태 변경)
 * 메시지는 사용자에게 그대로 보여 주므로 "무엇을 고쳐야 하는지" 알 수 있게 쓴다. (NFR-019)
 * {@link GlobalExceptionHandler}가 받아서 오류 화면(error/business)으로 보여 준다.
 *
 * 사용 예: throw new BusinessException("이미 등록된 공고입니다.");
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }
}
