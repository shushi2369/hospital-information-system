package com.his.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 全局异常处理（《01 总体架构设计》§6.3）：业务异常返回错误码，未捕获异常对前端隐藏堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record FieldErrorItem(String field, String message) {
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Object>> handleBiz(BizException e) {
        log.info("业务异常 {}: {}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                .body(R.fail(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<R<Object>> handleConstraint(jakarta.validation.ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .reduce((a, b) -> a + "；" + b)
                .orElse("");
        return ResponseEntity.badRequest().body(R.fail(ErrorCode.A0001, "参数校验失败：" + detail));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<List<FieldErrorItem>>> handleValid(MethodArgumentNotValidException e) {
        List<FieldErrorItem> items = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldErrorItem(f.getField(), f.getDefaultMessage()))
                .toList();
        String detail = items.stream()
                .map(i -> i.field() + " " + i.message())
                .reduce((a, b) -> a + "；" + b)
                .orElse("");
        R<List<FieldErrorItem>> r = R.fail(ErrorCode.A0001, "参数校验失败：" + detail);
        r.setData(items);
        return ResponseEntity.badRequest().body(r);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<R<Object>> handleUnreadable(Exception e) {
        return ResponseEntity.badRequest().body(R.fail(ErrorCode.A0001, "请求体格式错误"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Object>> handleDenied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(R.fail(ErrorCode.A0003));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<R<Object>> handleAuth(AuthenticationException e) {
        return ResponseEntity.status(401).body(R.fail(ErrorCode.A0002));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Object>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(404).body(R.fail(ErrorCode.A0001, "资源不存在"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<R<Object>> handleData(DataAccessException e) {
        log.error("数据访问异常", e);
        return ResponseEntity.status(500).body(R.fail(ErrorCode.C9002));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Object>> handleOther(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(500).body(R.fail(ErrorCode.C9001));
    }
}
