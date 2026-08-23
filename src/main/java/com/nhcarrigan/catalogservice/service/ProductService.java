package com.nhcarrigan.catalogservice.service;

import com.nhcarrigan.catalogservice.dto.BulkProductDeleteResponse;
import com.nhcarrigan.catalogservice.dto.BulkStockAdjustmentRequest;
import com.nhcarrigan.catalogservice.dto.InventoryValueResponse;
import com.nhcarrigan.catalogservice.dto.ProductPatchRequest;
import com.nhcarrigan.catalogservice.dto.ProductRequest;
import com.nhcarrigan.catalogservice.entity.Product;
import com.nhcarrigan.catalogservice.entity.StockAdjustmentLog;
import com.nhcarrigan.catalogservice.exception.DuplicateSkuException;
import com.nhcarrigan.catalogservice.exception.InsufficientStockException;
import com.nhcarrigan.catalogservice.exception.InvalidPriceRangeException;
import com.nhcarrigan.catalogservice.exception.ProductNotFoundException;
import com.nhcarrigan.catalogservice.repository.ProductRepository;
import com.nhcarrigan.catalogservice.repository.StockAdjustmentLogRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for managing {@link Product} entities: enforces SKU uniqueness, looks products up
 * by id (raising {@link com.nhcarrigan.catalogservice.exception.ProductNotFoundException} when
 * missing), and applies stock adjustments under the invariant that stock can never go negative.
 * Delegates persistence to {@link ProductRepository}.
 *
 * <p>SKU uniqueness is case-insensitive: SKUs are normalized to uppercase before being persisted,
 * so the existing case-sensitive database constraint is sufficient on its own,
 * since no two differently-cased variants of the same SKU can ever both be persisted.
 */
@Service
public class ProductService {

  private final ProductRepository productRepository;
  private final StockAdjustmentLogRepository stockAdjustmentLogRepository;

  public ProductService(
      ProductRepository productRepository,
      StockAdjustmentLogRepository stockAdjustmentLogRepository) {
    this.productRepository = productRepository;
    this.stockAdjustmentLogRepository = stockAdjustmentLogRepository;
  }

  /**
   * Returns a page of products from the catalog.
   *
   * @param pageable the pagination information
   * @return a page containing the requested products and pagination metadata
   */
  @Transactional(readOnly = true)
  @Cacheable(cacheNames = "products")
  public Page<Product> findAll(Pageable pageable) {
    return productRepository.findAll(pageable);
  }

  /**
   * Retrieves a single product by its id.
   *
   * @param id the product id
   * @return the matching product
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if no product exists
   *     with the given id
   */
  @Transactional(readOnly = true)
  @Cacheable(cacheNames = "product", key = "#id")
  public Product findById(Long id) {
    return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
  }

  /**
  * Retrieves a single product by its SKU.
  *
  * @param sku the product sku
   * @return the matching product
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException
   *         if no product exists with the given sku
  */

  @Transactional(readOnly = true)
  public Product findBySku(String sku){
    String normalizedSku = normalizeSku(sku);
    return productRepository.getBySku(normalizedSku).orElseThrow(() -> new ProductNotFoundException(sku));
  }

  /**
   * Retrieves all stock adjustment log rows for a specified product.
   *
   * <p>If two or more logs' timestamps are tied, then log rows are ordered by log row id(in descending order).
   *
   * @param productId the id of the product to be searched
   * @return matching log rows in descending order of timestamp.
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if no product exists with the given id
   */

  @Transactional(readOnly = true)
  public List<StockAdjustmentLog> getStockHistory(Long productId) {
    findById(productId);
    return stockAdjustmentLogRepository.findByProductIdOrderByTimestampDescIdDesc(productId);
  }

  /**
   * Searches for products whose name contains the given substring, case-insensitive.
   *
   * @param name the substring to match against product names
   * @return matching products, or an empty list if none match
   */
  @Transactional(readOnly = true)
  public List<Product> searchByName(String name) {
    return productRepository.findByNameContainingIgnoreCase(name);
  }

  /**
   * Searches for products whose name exactly matches the given string, case-insensitive.
   *
   * @param name the string to match against product names
   * @return matching products, or an empty list if none match
   */
  @Transactional(readOnly = true)
  public List<Product> searchByExactName(String name) {
    return productRepository.findByNameIgnoreCase(name);
  }

  /**
   * Searches for products whose category contains the given substring, case-insensitive.
   *
   * @param category the substring to match against product categories
   * @return matching products, or an empty list if none match
   */
  @Transactional(readOnly = true)
  public List<Product> searchByCategory(String category) {
    return productRepository.findByCategoryContainingIgnoreCase(category);
  }

  /**
   * Returns every category currently in use across all products
   *
   * @return the distinct category values, or an empty list if the catalog has no products.
   */
  @Transactional(readOnly = true)
  public List<String> listCategories() {
    return productRepository.listCategories();
  }

  /**
   * Creates and persists a new product.
   *
   * @param request the fields for the new product
   * @return the persisted product, including its generated id
   * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException if a product with the
   *     same SKU already exists
   */
  @Transactional
  @CacheEvict(
      cacheNames = {"products", "product"},
      allEntries = true)
  public Product create(ProductRequest request) {
    String normalizedSku = normalizeSku(request.getSku());
    if (productRepository.existsBySku(normalizedSku)) {
      throw new DuplicateSkuException(normalizedSku);
    }
    Product product =
        new Product(
            request.getName(),
            normalizedSku,
            request.getCategory(),
            request.getPrice(),
            request.getStockQuantity(),
            request.getDescription());
    return productRepository.save(product);
  }

  /**
   * Creates and persists multiple products as one atomic operation.
   *
   * <p>Every product is validated before any persistence occurs. If any sku already exists in the
   * database, or two products to be added have the same sku, the entire batch is rejected and the
   * transaction is rolled back.
   *
   * @param requests the product creation requests
   * @return the products created by the batch
   * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException if sku already exists or
   *     duplicate sku in batch
   */
  @Transactional
  public List<Product> bulkCreate(List<ProductRequest> requests) {
    Set<String> newSku = new HashSet<>();
    List<Product> newProducts = new ArrayList<>();

    for (ProductRequest request : requests) {
      String normalizedSku = normalizeSku(request.getSku());
      if (productRepository.existsBySku(normalizedSku)) {
        throw new DuplicateSkuException(normalizedSku);
      }
      if (!newSku.add(normalizedSku)) {
        throw new DuplicateSkuException(normalizedSku);
      }
      Product product =
          new Product(
              request.getName(),
              normalizedSku,
              request.getCategory(),
              request.getPrice(),
              request.getStockQuantity(),
              request.getDescription());
      newProducts.add(product);
    }
    return productRepository.saveAll(newProducts);
  }

  /**
   * Replaces all fields of an existing product.
   *
   * @param id the id of the product to update
   * @param request the new field values
   * @return the updated, persisted product
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if no product exists
   *     with the given id
   * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException if the new SKU collides
   *     with a different existing product
   */
  @Transactional
  @CacheEvict(
      cacheNames = {"products", "product"},
      allEntries = true)
  public Product update(Long id, ProductRequest request) {
    Product existing = findById(id);
    String normalizedSku = normalizeSku(request.getSku());
    if (!existing.getSku().equalsIgnoreCase(request.getSku())
        && productRepository.existsBySku(normalizedSku)) {
      throw new DuplicateSkuException(normalizedSku);
    }

    existing.setName(request.getName());
    existing.setSku(normalizedSku);
    existing.setCategory(request.getCategory());
    existing.setPrice(request.getPrice());
    existing.setStockQuantity(request.getStockQuantity());
    existing.setDescription(request.getDescription());
    return productRepository.save(existing);
  }

  /**
   * Update any field(s) of an existing product.
   *
   * @param id the id of the product to update
   * @param request the new field values
   * @return the updated, persisted product
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if no product exists
   *     with the given id
   * @throws com.nhcarrigan.catalogservice.exception.DuplicateSkuException if the new SKU collides
   *     with a different existing product
   */
  @Transactional
  @CacheEvict(cacheNames = { "products", "product" }, allEntries = true)
  public Product patch(Long id, ProductPatchRequest request) {
    Product existing = findById(id);
    String normalizedSku = request.getSku() != null ? normalizeSku(request.getSku()) : null;

    if (request.getSku() != null && !existing.getSku().equalsIgnoreCase(request.getSku())
        && productRepository.existsBySku(normalizedSku)) {
      throw new DuplicateSkuException(normalizedSku);
    }
    if (request.getName() != null)
      existing.setName(request.getName());
    if (request.getSku() != null)
      existing.setSku(normalizedSku);
    if (request.getCategory() != null)
      existing.setCategory(request.getCategory());
    if (request.getPrice() != null)
      existing.setPrice(request.getPrice());
    if (request.getStockQuantity() != null)
      existing.setStockQuantity(request.getStockQuantity());
    if (request.getDescription() != null)
      existing.setDescription(request.getDescription());

    return productRepository.save(existing);
  }

  /**
   * Deletes a product by its id.
   *
   * <p>Log rows still reference the deleted product's id, but remain meaningful on
   * their own since each one already captures the product's name and SKU at the
   * time it was written.
   *
   * @param id the id of the product to delete
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if no product exists
   *     with the given id
   */
  @Transactional
  @CacheEvict(
      cacheNames = {"products", "product"},
      allEntries = true)
  public void delete(Long id) {
    Product existing = findById(id);
    productRepository.delete(existing);
  }

  /**
   * Deletes multiple products in a best-effort operation.
   *
   * <p>Existing product ids are deleted, while ids that do not correspond to an existing product are
   * returned as rejected. A missing product does not prevent other valid products from being deleted.
   *
   * @param ids the product ids to delete
   * @return the ids that were deleted and the ids that were rejected
   */
  @Transactional
  @CacheEvict(
      cacheNames = {"products", "product"},
      allEntries = true)
  public BulkProductDeleteResponse bulkDelete(List<Long> ids) {
    List<Long> deleted = new ArrayList<>();
    List<Long> rejected = new ArrayList<>();

    for (Long id : ids) {
      if (productRepository.existsById(id)) {
        productRepository.deleteById(id);
        deleted.add(id);
      } else {
        rejected.add(id);
      }
    }

    return new BulkProductDeleteResponse(deleted, rejected);
  }

  /**
   * Applies a signed delta to a product's stock quantity. Positive deltas restock, negative deltas
   * draw down stock. The operation is rejected (no partial writes) if it would take stock below
   * zero.
   */
  @Transactional
  @CacheEvict(
      cacheNames = {"products", "product"},
      allEntries = true)
  public Product adjustStock(Long id, int delta) {
    Product product = findById(id);
    int newQuantity = product.getStockQuantity() + delta;

    if (newQuantity < 0) {
      throw new InsufficientStockException(id, product.getStockQuantity(), delta);
    } 
    product.setStockQuantity(newQuantity);
    Product savedProduct = productRepository.save(product);

    stockAdjustmentLogRepository.save(new StockAdjustmentLog(id, delta, newQuantity, product.getName(), product.getSku()));

    return savedProduct;
  }

  /**
   * Applies multiple stock adjustments as one atomic operation.
   *
   * <p>Every adjustment is validated before any stock quantity is changed. If any product is
   * missing or any adjustment would result in negative stock, the entire batch is rejected and the
   * transaction is rolled back.
   *
   * @param adjustments the stock adjustments to apply
   * @return the products affected by the batch, in first-seen order
   * @throws com.nhcarrigan.catalogservice.exception.ProductNotFoundException if any product does
   *     not exist
   * @throws com.nhcarrigan.catalogservice.exception.InsufficientStockException if any adjustment
   *     would result in negative stock
   */
  @Transactional
  @CacheEvict(
      cacheNames = {"products", "product"},
      allEntries = true)
  public List<Product> bulkAdjustStock(List<BulkStockAdjustmentRequest> adjustments) {

    Map<Long, Product> productsById = new LinkedHashMap<>();
    Map<Long, Integer> projectedStock = new LinkedHashMap<>();
    List<StockAdjustmentLog> logs = new ArrayList<>();

    // Phase 1: validate the entire batch without changing any product.
    for (BulkStockAdjustmentRequest adjustment : adjustments) {
      Long productId = adjustment.productId();

      Product product = productsById.computeIfAbsent(productId, this::findById);

      int currentStock = projectedStock.getOrDefault(productId, product.getStockQuantity());

      int newQuantity = currentStock + adjustment.delta();

      if (newQuantity < 0) {
        throw new InsufficientStockException(productId, currentStock, adjustment.delta());
      }

      projectedStock.put(productId, newQuantity);

      logs.add(new StockAdjustmentLog(productId, adjustment.delta(), newQuantity, product.getName(), product.getSku()));
    }

    // Phase 2: apply changes only after the entire batch is valid.
    for (Map.Entry<Long, Product> entry : productsById.entrySet()) {
      Product product = entry.getValue();
      product.setStockQuantity(projectedStock.get(entry.getKey()));
    }

    stockAdjustmentLogRepository.saveAll(logs);

    return new ArrayList<>(productsById.values());
  }

  /**
   * Filters products by a given price range.
   *
   * <p>If neither parameter is supplied, then returns all products
   *
   * @param minPrice the minimum price filter for products or null to leave that bound unfiltered
   * @param maxPrice the maximum price filter for products or null to leave that bound unfiltered
   * @param pageable the pagination information (page number, size, sort)
   * @return a page containing the requested products and pagination metadata
   * @throws com.nhcarrigan.catalogservice.exception.InvalidPriceRangeException if the given price
   *     range is reversed
   */
  @Transactional(readOnly = true)
  @Cacheable(cacheNames = "products")
  public Page<Product> filterByPrice(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
    if (minPrice != null && maxPrice != null) {
      // both supplied
      if (minPrice.compareTo(maxPrice) > 0) {
        throw new InvalidPriceRangeException(minPrice, maxPrice);
      }
      return productRepository.findByPriceBetween(minPrice, maxPrice, pageable);

    } else if (minPrice != null) {
      // only a floor
      return productRepository.findByPriceGreaterThanEqual(minPrice, pageable);
    } else if (maxPrice != null) {
      // only a ceiling
      return productRepository.findByPriceLessThanEqual(maxPrice, pageable);
    } else {
      return productRepository.findAll(pageable);
    }
  }

  @Transactional(readOnly = true)
  public InventoryValueResponse getInventoryValue() {
    BigDecimal totalValue = productRepository.calculateTotalInventoryValue();

    Map<String, BigDecimal> byCategory =
        productRepository.calculateInventoryValueByCategory().stream()
            .collect(
                Collectors.toMap(
                    row -> (String) row[0],
                    row -> (BigDecimal) row[1],
                    BigDecimal::add,
                    LinkedHashMap::new));

    return new InventoryValueResponse(totalValue, byCategory);
  }

  private String normalizeSku(String sku){
    return sku.toUpperCase(Locale.ROOT);
  }

  /**
   * Searches for products with a stock quantity at or below a certain threshold, given by user or default.
   * returns a list of products that meet the criteria.
   * 
   * @param threshold the maximum stock quantity for products to include in the result
   * @return a list of products with a stock quantity at or below the threshold
   */
  @Transactional(readOnly = true)
  public List<Product> searchByStockQuantity(Integer threshold) {
    return productRepository.findByStockQuantityIsLessThanEqual(threshold);
  }
}
