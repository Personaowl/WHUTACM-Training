package com.whut.training.exception;

import com.whut.training.common.ApiResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局异常处理器。
 *
 * <p>将业务异常、参数校验异常、请求体解析异常和兜底异常统一转换为 {@link com.whut.training.common.ApiResponse}。
 * 错误响应同时使用正确的 HTTP 状态码和统一业务响应体，便于网关、监控和前端准确识别失败。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     *
     * @param ex 业务异常。
     * @return 统一失败响应。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return response(ex.getCode(), ex.getMessage());
    }

    /**
     * 处理参数校验、请求体读取和缺失请求头等 400 类问题。
     *
     * @param ex 被捕获的异常。
     * @return 统一失败响应。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException manv
                && manv.getBindingResult().getFieldError() != null) {
            return response(400, manv.getBindingResult().getFieldError().getDefaultMessage());
        }
        if (ex instanceof BindException be
                && be.getBindingResult().getFieldError() != null) {
            return response(400, be.getBindingResult().getFieldError().getDefaultMessage());
        }
        if (ex instanceof MissingRequestHeaderException mrh) {
            return response(400, "missing required header: " + mrh.getHeaderName());
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return response(400, "request body is invalid or missing");
        }
        return response(400, "invalid request");
    }

    /**
     * 处理非法参数异常。
     *
     * @param ex 非法参数异常。
     * @return 统一失败响应。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return response(400, ex.getMessage());
    }

    /**
     * 将不存在的接口或静态资源正确转换为 404，而不是落入 500 兜底处理。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return response(404, "resource not found");
    }

    /**
     * 处理未显式分类的兜底异常。
     *
     * @param ex 未知异常。
     * @return 统一失败响应。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        log.error("Unhandled request exception", ex);
        return response(500, "internal server error");
    }

    private ResponseEntity<ApiResponse<Void>> response(int status, String message) {
        int safeStatus = status >= 400 && status <= 599 ? status : 500;
        return ResponseEntity
                .status(HttpStatusCode.valueOf(safeStatus))
                .body(ApiResponse.fail(safeStatus, message));
    }
}
