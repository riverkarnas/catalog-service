package com.nhcarrigan.catalogservice.service;

import com.nhcarrigan.catalogservice.dto.ProductImportError;
import com.nhcarrigan.catalogservice.dto.ProductImportErrorType;
import com.nhcarrigan.catalogservice.dto.ProductImportResponse;
import com.nhcarrigan.catalogservice.dto.ProductRequest;
import com.nhcarrigan.catalogservice.entity.Product;
import com.nhcarrigan.catalogservice.exception.CsvImportException;
import com.nhcarrigan.catalogservice.exception.DuplicateSkuException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImportService {

  private final ProductCsvParser csvParser;
  private final ProductService productService;
  private final Validator validator;

  public ProductImportService(
      ProductCsvParser csvParser,
      Validator validator,
      ProductService productService) {
    this.csvParser = csvParser;
    this.validator = validator;
    this.productService = productService;
  }

  /**
   * Imports valid product rows and collects row-level errors.
   *
   * <p>Validation errors are reported without attempting persistence. Valid rows are passed through
   * {@link ProductService#create(ProductRequest)} so normal product creation rules remain
   * centralized.
   */
  public ProductImportResult importProducts(
      List<ProductCsvParser.ParsedProductRow> rows) {

    ProductImportValidationResult validationResult = validateRows(rows);

    List<Product> importedProducts = new ArrayList<>();
    List<ProductImportError> errors = new ArrayList<>(validationResult.errors());

    for (ValidatedProduct validatedProduct : validationResult.validProducts()) {
      try {
        importedProducts.add(productService.create(validatedProduct.request()));
      } catch (DuplicateSkuException exception) {
        errors.add(
            new ProductImportError(
                validatedProduct.rowNumber(),
                ProductImportErrorType.DUPLICATE_SKU,
                exception.getMessage()));
      }
    }

    return new ProductImportResult(importedProducts, errors);
  }

  /**
   * Validates product rows without persisting them.
   *
   * <p>Validation errors are collected per row so that valid rows can continue through the import.
   */
  public ProductImportValidationResult validateRows(
      List<ProductCsvParser.ParsedProductRow> rows) {

    List<ProductImportError> errors = new ArrayList<>();
    List<ValidatedProduct> validProducts = new ArrayList<>();
    Set<String> seenSkus = new HashSet<>();

    for (ProductCsvParser.ParsedProductRow row : rows) {
      ProductRequest request = toProductRequest(row);

      List<String> rowErrors = validateRequest(request);

      String normalizedSku = normalizeSku(request.getSku());

      if (rowErrors.isEmpty()
          && normalizedSku != null
          && !seenSkus.add(normalizedSku)) {
        rowErrors.add("Duplicate SKU in import: " + request.getSku());
      }

      if (rowErrors.isEmpty()) {
        validProducts.add(new ValidatedProduct(row.rowNumber(), request));
      } else {
        errors.add(
            new ProductImportError(
                row.rowNumber(),
                ProductImportErrorType.VALIDATION_ERROR,
                String.join("; ", rowErrors)));
      }
    }

    return new ProductImportValidationResult(validProducts, errors);
  }

  public ProductImportResponse importCsv(MultipartFile file) {
    try {
      List<ProductCsvParser.ParsedProductRow> rows = csvParser.parse(file);

      ProductImportResult result = importProducts(rows);

      return new ProductImportResponse(
          result.importedProducts().size(),
          result.errors().size(),
          result.errors());
    } catch (IOException exception) {
      throw new CsvImportException("Unable to read CSV file", exception);
    }
  }

  private ProductRequest toProductRequest(ProductCsvParser.ParsedProductRow row) {
    ProductRequest request = new ProductRequest();
    request.setName(row.name());
    request.setSku(row.sku());
    request.setCategory(row.category());
    request.setPrice(parsePrice(row.price()));
    request.setStockQuantity(parseStockQuantity(row.stockQuantity()));
    request.setDescription(row.description());
    return request;
  }

  private List<String> validateRequest(ProductRequest request) {
    List<String> errors = new ArrayList<>();

    if (request.getPrice() == null) {
      errors.add("Price is required");
    }

    if (request.getStockQuantity() == null) {
      errors.add("Stock quantity is required");
    }

    Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);

    violations.stream()
        .map(ConstraintViolation::getMessage)
        .filter(message -> !errors.contains(message))
        .forEach(errors::add);

    return errors;
  }

  private BigDecimal parsePrice(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private Integer parseStockQuantity(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private String normalizeSku(String sku) {
    if (sku == null || sku.isBlank()) {
      return null;
    }

    return sku.trim().toUpperCase(Locale.ROOT);
  }

  public record ValidatedProduct(int rowNumber, ProductRequest request) {}

  public record ProductImportValidationResult(
      List<ValidatedProduct> validProducts, List<ProductImportError> errors) {}

  public record ProductImportResult(
      List<Product> importedProducts, List<ProductImportError> errors) {}
}
