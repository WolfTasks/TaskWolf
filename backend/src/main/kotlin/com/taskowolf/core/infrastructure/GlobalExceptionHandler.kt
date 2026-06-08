package com.taskowolf.core.infrastructure

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class NotFoundException(message: String) : RuntimeException(message)
class ForbiddenException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("NOT_FOUND", ex.message ?: "Resource not found"))

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse("FORBIDDEN", ex.message ?: "Access denied"))

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors
            .associate { it.field to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("VALIDATION_ERROR", "Validation failed", details))
    }
}
