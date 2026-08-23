package com.nhcarrigan.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductCsvParserTest {

  private final ProductCsvParser parser = new ProductCsvParser();

  @Test
  void parsesValidRows() throws Exception {
    String csv =
        """
        name,sku,category,price,stockQuantity
        Keyboard,SKU-001,Electronics,49.99,10
        Mouse,SKU-002,Electronics,19.99,20
        """;

    List<ProductCsvParser.ParsedProductRow> rows = parser.parse(new StringReader(csv));

    assertThat(rows).hasSize(2);

    assertThat(rows.get(0).rowNumber()).isEqualTo(2);
    assertThat(rows.get(0).name()).isEqualTo("Keyboard");
    assertThat(rows.get(0).sku()).isEqualTo("SKU-001");
    assertThat(rows.get(0).category()).isEqualTo("Electronics");
    assertThat(rows.get(0).price()).isEqualTo("49.99");
    assertThat(rows.get(0).stockQuantity()).isEqualTo("10");

    assertThat(rows.get(1).rowNumber()).isEqualTo(3);
  }

  @Test
  void supportsOptionalDescription() throws Exception {
    String csv =
        """
        name,sku,category,price,stockQuantity,description
        Keyboard,SKU-001,Electronics,49.99,10,Mechanical keyboard
        """;

    List<ProductCsvParser.ParsedProductRow> rows = parser.parse(new StringReader(csv));

    assertThat(rows.get(0).description()).isEqualTo("Mechanical keyboard");
  }

  @Test
  void allowsColumnsInDifferentOrder() throws Exception {
    String csv =
        """
        sku,name,price,stockQuantity,category
        SKU-001,Keyboard,49.99,10,Electronics
        """;

    List<ProductCsvParser.ParsedProductRow> rows = parser.parse(new StringReader(csv));

    assertThat(rows.get(0).name()).isEqualTo("Keyboard");
    assertThat(rows.get(0).sku()).isEqualTo("SKU-001");
    assertThat(rows.get(0).category()).isEqualTo("Electronics");
    assertThat(rows.get(0).price()).isEqualTo("49.99");
    assertThat(rows.get(0).stockQuantity()).isEqualTo("10");
  }

  @Test
  void preservesCommasInsideQuotedFields() throws Exception {
    String csv =
        """
        name,sku,category,price,stockQuantity
        Desk,SKU-001,"Office, Large",199.99,4
        """;

    List<ProductCsvParser.ParsedProductRow> rows = parser.parse(new StringReader(csv));

    assertThat(rows.get(0).category()).isEqualTo("Office, Large");
  }

  @Test
  void preservesEmptyFields() throws Exception {
    String csv =
        """
        name,sku,category,price,stockQuantity
        Keyboard,,Electronics,49.99,10
        """;

    List<ProductCsvParser.ParsedProductRow> rows = parser.parse(new StringReader(csv));

    assertThat(rows.get(0).sku()).isEmpty();
  }

  @Test
  void rejectsMissingRequiredHeaders() {
    String csv =
        """
        name,sku,price,stockQuantity
        Keyboard,SKU-001,49.99,10
        """;

    assertThatThrownBy(() -> parser.parse(new StringReader(csv)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required headers");
  }

  @Test
  void doesNotRequireOptionalDescriptionHeader() throws Exception {
    String csv =
        """
        name,sku,category,price,stockQuantity
        Keyboard,SKU-001,Electronics,49.99,10
        """;

    List<ProductCsvParser.ParsedProductRow> rows = parser.parse(new StringReader(csv));

    assertThat(rows.get(0).description()).isNull();
  }
}