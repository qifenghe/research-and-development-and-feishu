package com.lhr.rnd.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("INVALID_REQUEST", "请补充或修正必填内容：" +
                exception.getBindingResult().getFieldErrors().stream().map(e -> e.getDefaultMessage()).distinct().collect(java.util.stream.Collectors.joining("；"))));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity
                .status("MATERIAL_INTAKE_FORBIDDEN".equals(exception.code()) ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Unhandled API request failure", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("SYSTEM_ERROR", "系统异常"));
    }
}
