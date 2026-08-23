package com.nhcarrigan.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nhcarrigan.catalogservice.dto.ProductImportErrorType;
import com.nhcarrigan.catalogservice.dto.ProductImportResponse;
import com.nhcarrigan.catalogservice.dto.ProductRequest;
import com.nhcarrigan.catalogservice.entity.Product;
import com.nhcarrigan.catalogservice.exception.CsvImportException;
import com.nhcarrigan.catalogservice.exception.DuplicateSkuException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ProductImportServiceTest {

  private final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  private ProductCsvParser csvParser;
  private ProductService productService;
  private ProductImportService service;

  @BeforeEach
  void setUp() {
    csvParser = mock(ProductCsvParser.class);
    productService = mock(ProductService.class);
    service = new ProductImportService(csvParser, validator, productService);
  }

  @Test
  void validatesValidRows() {
    ProductCsvParser.ParsedProductRow row =
        new ProductCsvParser.ParsedProductRow(
            2, "Keyboard", "SKU-001", "Electronics", "49.99", "10", null);

    ProductImportService.ProductImportValidationResult result =
        service.validateRows(List.of(row));

    assertThat(result.validProducts()).hasSize(1);
    assertThat(result.errors()).isEmpty();

    ProductRequest request = result.validProducts().get(0).request();

    assertThat(request.getName()).isEqualTo("Keyboard");
    assertThat(request.getSku()).isEqualTo("SKU-001");
    assertThat(request.getPrice()).isEqualByComparingTo("49.99");
    assertThat(request.getStockQuantity()).isEqualTo(10);
  }

  @Test
  void reportsInvalidPrice() {
    ProductCsvParser.ParsedProductRow row =
        new ProductCsvParser.ParsedProductRow(
            3, "Keyboard", "SKU-001", "Electronics", "-5.00", "10", null);

    ProductImportService.ProductImportValidationResult result =
        service.validateRows(List.of(row));

    assertThat(result.validProducts()).isEmpty();
    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.row()).isEqualTo(3);
              assertThat(error.type()).isEqualTo(ProductImportErrorType.VALIDATION_ERROR);
              assertThat(error.reason()).contains("Price must be greater than 0.00");
            });
  }

  @Test
  void reportsMissingRequiredField() {
    ProductCsvParser.ParsedProductRow row =
        new ProductCsvParser.ParsedProductRow(
            4, "", "SKU-001", "Electronics", "49.99", "10", null);

    ProductImportService.ProductImportValidationResult result =
        service.validateRows(List.of(row));

    assertThat(result.validProducts()).isEmpty();
    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.row()).isEqualTo(4);
              assertThat(error.type()).isEqualTo(ProductImportErrorType.VALIDATION_ERROR);
              assertThat(error.reason()).contains("Name must not be blank");
            });
  }

  @Test
  void rejectsDuplicateSkuWithinImport() {
    List<ProductCsvParser.ParsedProductRow> rows =
        List.of(
            new ProductCsvParser.ParsedProductRow(
                2, "Keyboard", "SKU-001", "Electronics", "49.99", "10", null),
            new ProductCsvParser.ParsedProductRow(
                3, "Mouse", "SKU-001", "Electronics", "19.99", "20", null));

    ProductImportService.ProductImportValidationResult result =
        service.validateRows(rows);

    assertThat(result.validProducts()).hasSize(1);
    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.row()).isEqualTo(3);
              assertThat(error.type()).isEqualTo(ProductImportErrorType.VALIDATION_ERROR);
              assertThat(error.reason()).contains("Duplicate SKU");
            });
  }

  @Test
  void treatsSkuComparisonAsCaseInsensitive() {
    List<ProductCsvParser.ParsedProductRow> rows =
        List.of(
            new ProductCsvParser.ParsedProductRow(
                2, "Keyboard", "SKU-001", "Electronics", "49.99", "10", null),
            new ProductCsvParser.ParsedProductRow(
                3, "Mouse", "sku-001", "Electronics", "19.99", "20", null));

    ProductImportService.ProductImportValidationResult result =
        service.validateRows(rows);

    assertThat(result.validProducts()).hasSize(1);
    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.row()).isEqualTo(3);
              assertThat(error.type()).isEqualTo(ProductImportErrorType.VALIDATION_ERROR);
            });
  }

  @Test
  void reportsInvalidNumericValues() {
    List<ProductCsvParser.ParsedProductRow> rows =
        List.of(
            new ProductCsvParser.ParsedProductRow(
                2, "Keyboard", "SKU-001", "Electronics", "not-a-price", "10", null),
            new ProductCsvParser.ParsedProductRow(
                3, "Mouse", "SKU-002", "Electronics", "19.99", "not-a-number", null));

    ProductImportService.ProductImportValidationResult result =
        service.validateRows(rows);

    assertThat(result.validProducts()).isEmpty();
    assertThat(result.errors()).hasSize(2);
    assertThat(result.errors().get(0).row()).isEqualTo(2);
    assertThat(result.errors().get(1).row()).isEqualTo(3);
  }

  @Test
  void importsValidProductsThroughProductService() {
    ProductCsvParser.ParsedProductRow row =
        new ProductCsvParser.ParsedProductRow(
            2, "Keyboard", "SKU-001", "Electronics", "49.99", "10", null);

    Product savedProduct =
        new Product(
            "Keyboard",
            "SKU-001",
            "Electronics",
            new BigDecimal("49.99"),
            10,
            null);

    when(productService.create(any(ProductRequest.class))).thenReturn(savedProduct);

    ProductImportService.ProductImportResult result =
        service.importProducts(List.of(row));

    assertThat(result.importedProducts()).containsExactly(savedProduct);
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void reportsDuplicateSkuWhenProductServiceRejectsCreation() {
    ProductCsvParser.ParsedProductRow row =
        new ProductCsvParser.ParsedProductRow(
            5, "Keyboard", "SKU-001", "Electronics", "49.99", "10", null);

    when(productService.create(any(ProductRequest.class)))
        .thenThrow(new DuplicateSkuException("SKU-001"));

    ProductImportService.ProductImportResult result =
        service.importProducts(List.of(row));

    assertThat(result.importedProducts()).isEmpty();
    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.row()).isEqualTo(5);
              assertThat(error.type()).isEqualTo(ProductImportErrorType.DUPLICATE_SKU);
              assertThat(error.reason()).contains("SKU-001");
            });
  }

  @Test
  void continuesImportingAfterDuplicateSku() {
    List<ProductCsvParser.ParsedProductRow> rows =
        List.of(
            new ProductCsvParser.ParsedProductRow(
                2, "Keyboard", "SKU-001", "Electronics", "49.99", "10", null),
            new ProductCsvParser.ParsedProductRow(
                3, "Mouse", "SKU-002", "Electronics", "19.99", "20", null));

    Product secondProduct =
        new Product(
            "Mouse",
            "SKU-002",
            "Electronics",
            new BigDecimal("19.99"),
            20,
            null);

    when(productService.create(any(ProductRequest.class)))
        .thenThrow(new DuplicateSkuException("SKU-001"))
        .thenReturn(secondProduct);

    ProductImportService.ProductImportResult result =
        service.importProducts(rows);

    assertThat(result.importedProducts()).containsExactly(secondProduct);
    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.row()).isEqualTo(2);
              assertThat(error.type()).isEqualTo(ProductImportErrorType.DUPLICATE_SKU);
            });
  }

  @Test
  void importsCsvFile() throws Exception {
    String csv =
        """
        name,sku,category,price,stockQuantity,description
        Keyboard,SKU-001,Electronics,49.99,10,Mechanical keyboard
        """;

    MultipartFile file =
        new MockMultipartFile(
            "file",
            "products.csv",
            "text/csv",
            csv.getBytes(StandardCharsets.UTF_8));

    ProductCsvParser.ParsedProductRow row =
        new ProductCsvParser.ParsedProductRow(
            2,
            "Keyboard",
            "SKU-001",
            "Electronics",
            "49.99",
            "10",
            "Mechanical keyboard");

    when(csvParser.parse(file)).thenReturn(List.of(row));

    Product savedProduct =
        new Product(
            "Keyboard",
            "SKU-001",
            "Electronics",
            new BigDecimal("49.99"),
            10,
            "Mechanical keyboard");
    when(productService.create(any(ProductRequest.class)))
        .thenReturn(savedProduct);

    ProductImportResponse result = service.importCsv(file);

    assertThat(result.created()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(0);
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void wrapsCsvParserIOException() throws Exception {
    MultipartFile file =
        new MockMultipartFile(
            "file",
            "products.csv",
            "text/csv",
            "name,sku,category,price,stockQuantity,description\n".getBytes(StandardCharsets.UTF_8));

    IOException cause = new IOException("Unable to read file");

    when(csvParser.parse(file)).thenThrow(cause);

    assertThatThrownBy(() -> service.importCsv(file))
        .isInstanceOf(CsvImportException.class)
        .hasMessage("Unable to read CSV file")
        .hasCause(cause);
  }
}
