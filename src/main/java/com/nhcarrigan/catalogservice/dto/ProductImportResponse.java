package com.nhcarrigan.catalogservice.dto;

import java.util.List;

/** Summarizes the result of a product CSV import. */
public record ProductImportResponse(
    int created, int failed, List<ProductImportError> errors) {}