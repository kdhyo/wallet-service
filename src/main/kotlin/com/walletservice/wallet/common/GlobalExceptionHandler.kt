package com.walletservice.wallet.common

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import java.time.LocalDateTime
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        return buildResponse(ex.errorCode, ex.message, request)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: ErrorCode.INVALID_REQUEST.defaultMessage
        return buildResponse(ErrorCode.INVALID_REQUEST, message, request)
    }

    @ExceptionHandler(BindException::class)
    fun handleBindException(ex: BindException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: ErrorCode.INVALID_REQUEST.defaultMessage
        return buildResponse(ErrorCode.INVALID_REQUEST, message, request)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val message = ex.constraintViolations.firstOrNull()?.message
            ?: ErrorCode.INVALID_REQUEST.defaultMessage
        return buildResponse(ErrorCode.INVALID_REQUEST, message, request)
    }

    @ExceptionHandler(Exception::class)
    fun handleFallback(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.error(ex) { "처리되지 않은 예외가 발생했습니다. path=${request.requestURI}" }
        return buildResponse(
            errorCode = ErrorCode.INTERNAL_ERROR,
            message = ErrorCode.INTERNAL_ERROR.defaultMessage,
            request = request,
        )
    }

    private fun buildResponse(
        errorCode: ErrorCode,
        message: String,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val body = ErrorResponse(
            code = errorCode.code,
            message = message,
            timestamp = LocalDateTime.now(),
            path = request.requestURI,
        )
        return ResponseEntity.status(errorCode.httpStatus).body(body)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
