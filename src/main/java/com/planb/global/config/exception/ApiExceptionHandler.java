package com.planb.global.config.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.planb.global.config.exception.domain.BadRequestException;
import com.planb.global.config.exception.domain.BaseDataException;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.config.exception.domain.ForbiddenException;
import com.planb.global.config.exception.dto.ApiResult;

import java.nio.file.AccessDeniedException;

import static com.planb.global.config.exception.BaseExceptionEnum.*;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String LOG_FORMAT = "Class : {}, Code : {}, Message : {}";

    /* ==================================================
     * WARN LEVEL EXCEPTIONS
     * - 예상 가능한 예외
     * - 사용자 요청 / 비즈니스 로직 문제
     * ================================================== */

    /**
     * 애플리케이션에서 정의한 기본 비즈니스 예외
     */
    @ExceptionHandler(BaseException.class)
    public ApiResult<Void> baseExceptionHandler(BaseException e) {

        logWarnException(e, e.getErrorCode());

        return ApiResult.fail(
                e.getErrorCode(),
                e.getMessage()
        );
    }

    /**
     * 데이터 포함 비즈니스 예외
     */
    @ExceptionHandler(BaseDataException.class)
    public ApiResult<Object> baseDataExceptionHandler(BaseDataException e) {

        logWarnException(e, e.getErrorCode());

        return ApiResult.fail(
                e.getErrorCode(),
                e.getMessage(),
                e.getData()
        );
    }

    /**
     * Validation 실패 (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e) {

        String errorMessage = extractValidationMessage(e.getBindingResult());

        logWarnException(e, EXCEPTION_VALIDATION);

        return ApiResult.fail(
                EXCEPTION_VALIDATION.getCode(),
                errorMessage
        );
    }

    /**
     * 권한 없음 - 커스텀 예외
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(ForbiddenException.class)
    public ApiResult<Void> forbiddenExceptionHandler(ForbiddenException e) {

        logWarnException(e, FORBIDDEN);

        return ApiResult.fail(
                e.getErrorCode(),
                e.getMessage()
        );
    }

    /**
     * 권한 없음 - Spring Security
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public ApiResult<Void> accessDeniedExceptionHandler(AccessDeniedException e) {

        logWarnException(e, FORBIDDEN);

        return ApiResult.fail(
                FORBIDDEN.getCode(),
                "AccessDeniedException"
        );
    }

    /**
     * 잘못된 요청 (400)
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BadRequestException.class)
    public ApiResult<Void> badRequestExceptionHandler(BadRequestException e) {

        logWarnException(e, BAD_REQUEST);

        return ApiResult.fail(
                e.getErrorCode(),
                e.getMessage()
        );
    }

    /* ==================================================
     * ERROR LEVEL EXCEPTIONS
     * - 서버 내부 문제
     * - 즉시 확인 대상
     * ================================================== */

    /**
     * JPA / Query 사용 오류
     */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ApiResult<Void> handleInvalidDataAccessApiUsageException(
            InvalidDataAccessApiUsageException e) {

        logErrorException(e, EXCEPTION_ISSUED);

        if (isNullIdAccess(e)) {
            return ApiResult.fail(ENTITY_NOT_FOUND);
        }

        return ApiResult.fail(EXCEPTION_ISSUED);
    }

    /**
     * 타입 불일치 (PathVariable, RequestParam)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResult<Void> methodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {

        logErrorException(e, ENTITY_NOT_FOUND);

        return ApiResult.fail(ENTITY_NOT_FOUND);
    }

    /**
     * 런타임 예외 (예상 못 한 오류)
     */
    @ExceptionHandler(RuntimeException.class)
    public ApiResult<Void> runtimeExceptionHandler(RuntimeException e) {

        logErrorException(e, EXCEPTION_ISSUED);

        return ApiResult.fail(EXCEPTION_ISSUED);
    }

    /**
     * 최상위 예외 (완전 예상 밖)
     */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> exceptionHandler(Exception e) {

        logErrorException(e, EXCEPTION_ISSUED);

        return ApiResult.fail(EXCEPTION_ISSUED);
    }

    /* ==================================================
     * INTERNAL HELPER METHODS
     * ================================================== */

    // Warn 로그
    private void logWarnException(Exception e, Object errorCode) {
        log.warn(
                LOG_FORMAT,
                e.getClass().getSimpleName(),
                errorCode,
                e.getMessage()
        );
    }

    // Error 로그
    private void logErrorException(Exception e, Object errorCode) {
        log.error(
                LOG_FORMAT,
                e.getClass().getSimpleName(),
                errorCode,
                e.getMessage()
        );
    }

    // Validation 메시지 추출
    private String extractValidationMessage(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .findFirst()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return fieldError.getField() + " - " + fieldError.getDefaultMessage();
                    }
                    return error.getDefaultMessage();
                })
                .orElse("");
    }

    // NULL ID 접근 판별
    private boolean isNullIdAccess(InvalidDataAccessApiUsageException e) {
        String message = e.getMessage();
        return message != null &&
                (message.contains("The given id must not be null")
                        || message.contains("eq(null) is not allowed. Use isNull() instead"));
    }
}
