package com.slotmind.exception;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.Instant;
import java.util.List;
@RestControllerAdvice
public class ApiExceptionHandler {
    public record ErrorResponse(Instant timestamp, int status, String message, List<String> errors) {}
    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, List<String> errors) {
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(), message, errors));
    }
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handle(ApiException e) {
        return error(e.getStatus(), e.getMessage(), List.of());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        var errors = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage()).sorted().toList();
        return error(HttpStatus.BAD_REQUEST, "Request validation failed", errors);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> invalidInput(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "Invalid JSON or enum value", List.of());
    }
    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception e) {
        return error(HttpStatus.CONFLICT, "Booking conflict. Refresh available slots and try again.", List.of());
    }
}
