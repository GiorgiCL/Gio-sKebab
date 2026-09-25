package com.kebabshop.backend.restaurant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = RestaurantAdminController.class)
class RestaurantAdminErrorHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> invalidInput() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid restaurant or opening-hours data");
    }

    @ExceptionHandler({DataIntegrityViolationException.class,
            org.hibernate.exception.ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> conflict() {
        return problem(HttpStatus.CONFLICT, "Conflicting restaurant data");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> unsupportedMediaType() {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type must be application/json");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> status(ResponseStatusException exception) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
