package com.nhcarrigan.catalogservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** A single catalog item. Products are uniquely identified by SKU. */
@Entity
@Table(
    name = "products",
    uniqueConstraints = @UniqueConstraint(columnNames = "sku"),
    indexes = @Index(name = "idx_products_category", columnList = "category"))
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank(message = "Name must not be blank")
  @Column(nullable = false)
  private String name;

  @NotBlank(message = "SKU must not be blank")
  @Column(nullable = false, unique = true)
  private String sku;

  @NotBlank(message = "Category must not be blank")
  @Column(nullable = false)
  private String category;

  @NotNull(message = "Price is required")
  @DecimalMin(value = "0.01", message = "Price must be greater than 0.00")
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @NotNull(message = "Stock quantity is required")
  @Min(value = 0, message = "Stock quantity cannot be negative")
  @Column(nullable = false)
  private Integer stockQuantity;

  @Version private Long version;

  public Long getVersion() {
    return version;
  }

  @Column(length = 500)
  @Size(max = 500, message = "Description must not exceed 500 characters")
  private String description;

  protected Product() {
    // required by JPA
  }

  public Product(
      String name,
      String sku,
      String category,
      BigDecimal price,
      Integer stockQuantity,
      String description) {
    this.name = name;
    this.sku = sku;
    this.category = category;
    this.price = price;
    this.stockQuantity = stockQuantity;
    this.description = description; // allowed to be null, since its Optional
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

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
    this.sku = sku;
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
