package com.zhixing.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException exception) {
        // 开发控制台保留堆栈以便定位，响应仍仅返回安全的业务错误信息。
        LOGGER.error("API request rejected: status={}, code={}", exception.getStatus().value(), exception.getCode(), exception);
        return ResponseEntity.status(exception.getStatus()).body(error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        FieldError field = exception.getBindingResult().getFieldErrors().isEmpty()
                ? null : exception.getBindingResult().getFieldErrors().get(0);
        String message = field == null ? "请求参数不完整" : field.getDefaultMessage();
        LOGGER.info("API validation rejected: field={}", field == null ? "unknown" : field.getField());
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadLimit(Exception exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error("MEDIA_TOO_LARGE", "图片不能超过 5MB"));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, Object>> handleMalformed(Exception exception) {
        return ResponseEntity.badRequest().body(error("INVALID_REQUEST", "请求格式不正确"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled API exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", "服务暂时不可用，请稍后重试"));
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("message", message);
        return body;
    }
}
