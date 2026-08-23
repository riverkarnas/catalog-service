package com.nhcarrigan.catalogservice.service;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Parses product CSV files into structured rows.
 *
 * <p>This component is responsible only for CSV structure and parsing. Product validation and
 * persistence are handled by the import service.
 */
@Component
public class ProductCsvParser {

  private static final List<String> REQUIRED_HEADERS =
      List.of("name", "sku", "category", "price", "stockQuantity");

  /**
   * Parses a product CSV file.
   *
   * @param file the uploaded CSV file
   * @return the parsed product rows
   * @throws IOException if the file cannot be read
   * @throws IllegalArgumentException if the CSV header is invalid
   */
  public List<ParsedProductRow> parse(MultipartFile file) throws IOException {
    try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
      return parse(reader);
    }
  }

  List<ParsedProductRow> parse(Reader reader) throws IOException {
    CSVFormat format =
        CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(false)
            .setTrim(false)
            .get();

    try (CSVParser parser = format.parse(reader)) {
      validateHeaders(parser.getHeaderNames());

      List<ParsedProductRow> rows = new ArrayList<>();

      for (CSVRecord record : parser) {
        rows.add(
            new ParsedProductRow(
                (int) record.getRecordNumber() + 1,
                record.get("name"),
                record.get("sku"),
                record.get("category"),
                record.get("price"),
                record.get("stockQuantity"),
                record.isMapped("description") ? record.get("description") : null));
      }

      return rows;
    }
  }

  private void validateHeaders(List<String> headers) {
    if (!headers.containsAll(REQUIRED_HEADERS)) {
      throw new IllegalArgumentException(
          "CSV must contain the required headers: " + REQUIRED_HEADERS);
    }
  }

  record ParsedProductRow(
      int rowNumber,
      String name,
      String sku,
      String category,
      String price,
      String stockQuantity,
      String description) {}
}