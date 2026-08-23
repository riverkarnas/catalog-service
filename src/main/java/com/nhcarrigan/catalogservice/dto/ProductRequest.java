package com.nhcarrigan.catalogservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Payload for creating or fully updating a product. */
public class ProductRequest {

  @NotBlank(message = "Name must not be blank")
  private String name;

  @NotBlank(message = "SKU must not be blank")
  @Size(min = 3, max = 50, message = "SKU must be between 3 and 50 characters")
  @Pattern(
      regexp = "^[A-Za-z0-9-]+$",
      message = "SKU may only contain letters, numbers, and hyphens")
  private String sku;

  @NotBlank(message = "Category must not be blank")
  private String category;

  @NotNull(message = "Price is required")
  @DecimalMin(value = "0.01", message = "Price must be greater than 0.00")
  private BigDecimal price;

  @NotNull(message = "Stock quantity is required")
  @Min(value = 0, message = "Stock quantity cannot be negative")
  private Integer stockQuantity;

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
