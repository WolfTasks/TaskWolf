package com.taskowolf.core.infrastructure

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Exceptions that can carry a MessageSource key + args instead of free text. */
interface LocalizedException {
    val messageKey: String?
    val args: Array<out Any?>
}

class NotFoundException : RuntimeException, LocalizedException {
    override val messageKey: String?
    override val args: Array<out Any?>
    constructor(message: String) : super(message) { messageKey = null; args = emptyArray() }
    constructor(messageKey: String, args: Array<out Any?>) : super(messageKey) { this.messageKey = messageKey; this.args = args }
    companion object {
        fun keyed(key: String, vararg args: Any?): NotFoundException = NotFoundException(key, args)
    }
}

class ForbiddenException : RuntimeException, LocalizedException {
    override val messageKey: String?
    override val args: Array<out Any?>
    constructor(message: String) : super(message) { messageKey = null; args = emptyArray() }
    constructor(messageKey: String, args: Array<out Any?>) : super(messageKey) { this.messageKey = messageKey; this.args = args }
    companion object {
        fun keyed(key: String, vararg args: Any?): ForbiddenException = ForbiddenException(key, args)
    }
}

class ConflictException : RuntimeException, LocalizedException {
    override val messageKey: String?
    override val args: Array<out Any?>
    constructor(message: String) : super(message) { messageKey = null; args = emptyArray() }
    constructor(messageKey: String, args: Array<out Any?>) : super(messageKey) { this.messageKey = messageKey; this.args = args }
    companion object {
        fun keyed(key: String, vararg args: Any?): ConflictException = ConflictException(key, args)
    }
}

class BadRequestException : RuntimeException, LocalizedException {
    override val messageKey: String?
    override val args: Array<out Any?>
    constructor(message: String) : super(message) { messageKey = null; args = emptyArray() }
    constructor(messageKey: String, args: Array<out Any?>) : super(messageKey) { this.messageKey = messageKey; this.args = args }
    companion object {
        fun keyed(key: String, vararg args: Any?): BadRequestException = BadRequestException(key, args)
    }
}

@RestControllerAdvice
class GlobalExceptionHandler(private val messages: LocalizedMessages) {

    /** Key path → resolve against request locale; else the exception's own message; else the fallback key. */
    private fun resolve(ex: RuntimeException, fallbackKey: String): String {
        val loc = ex as? LocalizedException
        return loc?.messageKey?.let { messages.get(it, *loc.args) } ?: ex.message ?: messages.get(fallbackKey)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("NOT_FOUND", resolve(ex, "common.notFound")))

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("FORBIDDEN", resolve(ex, "common.forbidden")))

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("CONFLICT", resolve(ex, "common.conflict")))

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("BAD_REQUEST", resolve(ex, "common.badRequest")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("BAD_REQUEST", ex.message ?: messages.get("common.badRequest")))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("BAD_REQUEST", messages.get("common.malformedBody")))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("VALIDATION_ERROR", messages.get("common.validationFailed"), details))
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("CONFLICT", messages.get("common.dataConflict")))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("FORBIDDEN", ex.message ?: messages.get("common.forbidden")))

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception) =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse("INTERNAL_ERROR", messages.get("common.internalError")))
}
