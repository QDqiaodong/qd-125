package com.bufferblock.config;

import com.bufferblock.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理：业务校验抛出的 RuntimeException 以统一 Result 结构返回，
 * 使前端能直接展示中文错误信息（如待确认移交单冲突、状态不可处理等）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突，事务已回滚: {}", e.getMostSpecificCause().getMessage());
        String message = containsDuplicateKey(e)
                ? "移交单号或唯一数据冲突，登记未完成，请刷新后重试"
                : "数据完整性校验失败，登记未完成，请检查后重试";
        return Result.error(message);
    }

    @ExceptionHandler(DataAccessException.class)
    public Result<Void> handleDataAccess(DataAccessException e) {
        log.error("数据库访问失败，事务已回滚", e);
        return Result.error("数据库操作失败，移交登记未完成，请稍后重试");
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.warn("业务处理异常: {}", e.getMessage());
        return Result.error(e.getMessage() != null ? e.getMessage() : "系统繁忙，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统异常，请联系管理员");
    }

    private boolean containsDuplicateKey(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("duplicate entry") || lower.contains("duplicate key")
                        || lower.contains("unique constraint") || lower.contains("唯一")) {
                    return true;
                }
            }
            if (current instanceof java.sql.SQLException sqlException) {
                int errorCode = sqlException.getErrorCode();
                if (errorCode == 1062 || errorCode == 23505) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
