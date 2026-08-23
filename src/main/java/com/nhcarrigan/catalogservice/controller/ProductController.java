package com.nhcarrigan.catalogservice.controller;

import com.nhcarrigan.catalogservice.dto.BulkProductDeleteResponse;
import com.nhcarrigan.catalogservice.dto.BulkStockAdjustmentRequest;
import com.nhcarrigan.catalogservice.dto.InventoryValueResponse;
import com.nhcarrigan.catalogservice.dto.ProductCreationResponse;
import com.nhcarrigan.catalogservice.dto.ProductImportResponse;
import com.nhcarrigan.catalogservice.dto.ProductPageResponse;
import com.nhcarrigan.catalogservice.dto.ProductPatchRequest;
import com.nhcarrigan.catalogservice.dto.ProductRequest;
import com.nhcarrigan.catalogservice.dto.StockAdjustmentRequest;
import com.nhcarrigan.catalogservice.entity.Product;
import com.nhcarrigan.catalogservice.entity.StockAdjustmentLog;
import com.nhcarrigan.catalogservice.exception.InvalidSearchCriteriaException;
import com.nhcarrigan.catalogservice.service.ProductImportService;
import com.nhcarrigan.catalogservice.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller exposing CRUD and search operations for {@link Product} resources. All business
 * logic and validation is delegated to {@link ProductService}; this class is only responsible for
 * mapping HTTP requests to service calls and shaping the HTTP response.
 *
 * <p>Base path: {@code /api/products}.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final ProductService productService;
  private final ProductImportService productImportService;

  public ProductController(
      ProductService productService,
      ProductImportService productImportService) {
    this.productService = productService;
    this.productImportService = productImportService;
  }

  /**
   * Returns a paginated list of products, with support for sorting and optional filtering by price
   * range.
   *
   * <p>If neither minPrice nor maxPrice is supplied, all products are returned.
   *
   * @param pageable the pagination information (page number, size, sort)
   * @param minPrice the minimum price filter for products or null to leave that bound unfiltered
   * @param maxPrice the maximum price filter for products or null to leave that bound unfiltered
   * @return a paginated response containing products and pagination metadata
   * @throws com.nhcarrigan.catalogservice.exception.InvalidPriceRangeException if the given price
   *     range is reversed
   */
  @GetMapping
  public ProductPageResponse getAll(
      @PageableDefault(size = 20) Pageable pageable,
      @RequestParam(required = false) BigDecimal minPrice,
      @RequestParam(required = false) BigDecimal maxPrice) {
    return ProductPageResponse.from(productService.filterByPrice(minPrice, maxPrice, pageable));
  }

  /**
   * Searches for products by a name substring or a category substring, both case-insensitive. The
   * two filters are mutually exclusive: pass exactly one. Providing both, even if one is blank, is
   * rejected with a {@code 400}.
   *
   * @param name an optional substring to match against product names
   * @param category an optional substring to match against product categories
   * @return matching products, or an empty list if none match or no filter is given
   * @throws com.nhcarrigan.catalogservice.exception.InvalidSearchCriteriaException if both name and
   *     category are provided
   */
  @GetMapping("/search")
  public List<Product> search(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String category) {
    if (name != null && category != null) {
      throw new InvalidSearchCriteriaException();
    }
    if (name != null && !name.isBlank()) {
      return productService.searchByName(name);
    }
    if (category != null && !category.isBlank()) {
      return productService.searchByCategory(category);
    }
    return List.of();
  }

  /**
   * Returns every category currently in use across all products
   *
   * @return the distinct category values, or an empty list if the catalog has no products.
   */
  @GetMapping("/categories")
  public List<String> getCategories() {
    return productService.listCategories();
  }

  /**
   * Returns the total inventory value and its breakdown by category.
   *
   * @return an {@link InventoryValueResponse} containing the total value and category breakdown
   */
  @GetMapping("/inventory-value")
  public InventoryValueResponse getInventoryValue() {
    return productService.getInventoryValue();
  }

  /**
   * Retrieves a single product by its id.
   *
   * @param id the product id
   * @return the matching product
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if no product exists
   *     with the given id
   */
  @GetMapping("/{id}")
  public Product getById(@PathVariable Long id) {
    return productService.findById(id);
  }

  /**
   * Returns the stock adjustment history for a product, newest first.
   *
   * @param id the id of the product whose stock history is being requested
   * @return the product's stock adjustment history
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if no product exists
   *     with the given id
   */
  @GetMapping("/{id}/stock-history")
  public List<StockAdjustmentLog> getStockHistory(@PathVariable Long id) {
    return productService.getStockHistory(id);
  }

    /**
     * Retrieves a single product by its SKU.
     *
     * @param sku the product sku
     * @return the matching product
     * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException
     *         if no product exists with the given sku
     */
    @GetMapping("/sku/{sku}")
    public Product getBySku(@PathVariable String sku){
        return productService.findBySku(sku);
    }
  /**
   * Returns products with a stock quantity at or below a certain threshold, provided by user or default.
   *
   * @param threshold the maximum stock quantity for products to include in the result
   * @return a list of products with a stock quantity at or below the threshold
   */
  @GetMapping("/low-stock")
  public List<Product> getLowStock(@RequestParam(defaultValue = "5") Integer threshold) {
    return productService.searchByStockQuantity(threshold);
  }

  /**
   * Creates a new product.
   *
   * @param request the product fields to create; validated via {@link Valid}
   * @return a 201 response containing the newly created product
   * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException if a product with the
   *     same SKU already exists
   */
  @PostMapping
  public ResponseEntity<ProductCreationResponse> create(
      @Valid @RequestBody ProductRequest request) {
    boolean isDuplicateName = !productService.searchByExactName(request.getName()).isEmpty();

    Product created = productService.create(request);

    ProductCreationResponse pcr =
        new ProductCreationResponse(created, isDuplicateName ? "Duplicate Name" : null);

    return ResponseEntity.status(HttpStatus.CREATED).body(pcr);
  }

  /**
   * Creates multiple new products as one atomic operation.
   *
   * @param requests the products' fields to create; validated via {@link Valid}
   * @return a 201 response containing the newly created products
   * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException if a product with the
   *     same SKU already exists or if two products in the request share the same SKU
   */
  @PostMapping("/bulk")
  public ResponseEntity<List<Product>> bulkCreate(
      @NotEmpty(message = "At least one product is required") @RequestBody
          List<@Valid ProductRequest> requests) {
    return ResponseEntity.status(HttpStatus.CREATED).body(productService.bulkCreate(requests));
  }

  @PostMapping(
      value = "/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ProductImportResponse> importProducts(
      @RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(productImportService.importCsv(file));
  }

  /**
   * Replaces all fields of an existing product.
   *
   * @param id the id of the product to update
   * @param request the new field values; validated via {@link Valid}
   * @return the updated product
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if no product exists
   *     with the given id
   * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException if the new SKU collides
   *     with a different existing product
   */
  @PutMapping("/{id}")
  public Product update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
    return productService.update(id, request);
  }

  /**
   * Update any field(s) of an existing product.
   *
   * @param id the id of the product to update
   * @param request the new field values; validated via {@link Valid}
   * @return the updated product
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if no product exists
   *     with the given id
   * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException if the new SKU collides
   *     with a different existing product
   */
  @PatchMapping("/{id}")
  public Product patch(@PathVariable Long id, @Valid @RequestBody ProductPatchRequest request) {
    return productService.patch(id, request);
  }

  /**
   * Deletes a product by its id.
   *
   * @param id the id of the product to delete
   * @return a 204 response with no body
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if no product exists
   *     with the given id
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    productService.delete(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Deletes multiple products in a single request.
   *
   * <p>Existing product ids are deleted, while ids that do not exist are returned as rejected.
   *
   * @param ids the product ids to delete; the list must contain at least one id
   * @return the ids that were deleted and the ids that were rejected
   */
  @DeleteMapping("/bulk")
  public BulkProductDeleteResponse bulkDelete(
      @NotEmpty(message = "At least one product ID is required") @RequestBody List<Long> ids) {
    return productService.bulkDelete(ids);
  }

  /**
   * Adjusts a product's stock by a signed delta (positive to restock, negative to draw down).
   *
   * @param id the id of the product whose stock is being adjusted
   * @param request the signed delta to apply; validated via {@link Valid}
   * @return the product with its updated stock quantity
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if no product exists
   *     with the given id
   * @throws com.nhcarrigan.catalogservice.exception.InsufficientStockException if applying the
   *     delta would take stock below zero
   */
  @PatchMapping("/{id}/stock")
  public Product adjustStock(
      @PathVariable Long id, @Valid @RequestBody StockAdjustmentRequest request) {
    return productService.adjustStock(id, request.getDelta());
  }

  /**
   * Applies multiple stock adjustments as one atomic operation.
   *
   * @param adjustments the stock adjustments to apply
   * @return the products with their updated stock quantities
   */
  @PatchMapping("/stock/bulk")
  public List<Product> bulkAdjustStock(
      @NotEmpty(message = "At least one stock adjustment is required") @RequestBody
          List<@Valid BulkStockAdjustmentRequest> adjustments) {
    return productService.bulkAdjustStock(adjustments);
  }
}
