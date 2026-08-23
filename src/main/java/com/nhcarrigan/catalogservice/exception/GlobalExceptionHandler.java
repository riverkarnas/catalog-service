package com.nhcarrigan.catalogservice.exception;

import java.util.List;
import com.nhcarrigan.catalogservice.exception.CsvImportException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    List<String> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .toList();
    ApiError body =
        new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Failed",
            "One or more fields failed validation",
            details);
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex) {

    List<String> details =
        ex.getAllValidationResults().stream()
            .flatMap(result -> result.getResolvableErrors().stream())
            .map(error -> error.getDefaultMessage())
            .toList();

    ApiError body =
        new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Failed",
            "One or more fields failed validation",
            details);

    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(ProductNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(ProductNotFoundException ex) {
    ApiError body =
        new ApiError(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), List.of());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  @ExceptionHandler(DuplicateSkuException.class)
  public ResponseEntity<ApiError> handleDuplicateSku(DuplicateSkuException ex) {
    ApiError body =
        new ApiError(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage(), List.of());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiError> handleOptimisticLockingFailure(
      OptimisticLockingFailureException ex) {

    ApiError body =
        new ApiError(
            HttpStatus.CONFLICT.value(),
            "Conflict",
            "The product was modified by another request. Please retry.",
            List.of());

    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex) {
    ApiError body =
        new ApiError(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "Unprocessable Entity",
            ex.getMessage(),
            List.of());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
  }

  @ExceptionHandler(InvalidSearchCriteriaException.class)
  public ResponseEntity<ApiError> handleInvalidSearchCriteria(InvalidSearchCriteriaException ex) {
    ApiError body =
        new ApiError(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), List.of());
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(InvalidPriceRangeException.class)
  public ResponseEntity<ApiError> handleInvalidPriceRange(InvalidPriceRangeException ex) {
    ApiError body =
        new ApiError(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), List.of());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(CsvImportException.class)
  public ResponseEntity<ApiError> handleCsvImportException(CsvImportException exception) {
    ApiError body =
        new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            exception.getMessage(),
            List.of());

    return ResponseEntity.badRequest().body(body);
  }
}
