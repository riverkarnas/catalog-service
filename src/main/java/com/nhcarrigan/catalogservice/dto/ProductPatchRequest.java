package com.nhcarrigan.catalogservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.lang.Nullable;

/** Payload for partially updating (PATCH) a product. */
public class ProductPatchRequest {

  @Nullable private String name;

  @Nullable
  @Size(min = 3, max = 50, message = "SKU must be between 3 and 50 characters")
  @Pattern(
      regexp = "^[A-Za-z0-9-]+$",
      message = "SKU may only contain letters, numbers, and hyphens")
  private String sku;

  @Nullable private String category;

  @Nullable
  @DecimalMin(value = "0.01", message = "Price must be greater than 0.00")
  private BigDecimal price;

  @Nullable
  @Min(value = 0, message = "Stock quantity cannot be negative")
  private Integer stockQuantity;

  @Nullable
  @Size(max = 500, message = "Description must not exceed 500 characters")
  private String description;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSku() {
    return sku;
  }

  public void setSku(String sku) {
    this.sku = sku == null ? null : sku.trim();
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public Integer getStockQuantity() {
    return stockQuantity;
  }

  public void setStockQuantity(Integer stockQuantity) {
    this.stockQuantity = stockQuantity;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
