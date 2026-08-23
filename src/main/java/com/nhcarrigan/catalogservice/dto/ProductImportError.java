package com.nhcarrigan.catalogservice.dto;

public record ProductImportError(
    int row, ProductImportErrorType type, String reason) {}