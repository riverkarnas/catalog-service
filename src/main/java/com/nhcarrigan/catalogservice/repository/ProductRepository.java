package com.nhcarrigan.catalogservice.repository;

import com.nhcarrigan.catalogservice.entity.Product;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

  Optional<Product> getBySku(String sku);

  boolean existsBySku(String sku);

  List<Product> findByNameContainingIgnoreCase(String name);

  List<Product> findByNameIgnoreCase(String name);
  
  List<Product> findByCategoryContainingIgnoreCase(String category);

  List<Product> findByStockQuantityIsLessThanEqual(Integer threshold);

  @Query("SELECT DISTINCT p.category FROM Product p")
  List<String> listCategories();

  // Price Range Filter
  Page<Product> findByPriceGreaterThanEqual(BigDecimal minPrice, Pageable pageable); // only a floor

  Page<Product> findByPriceLessThanEqual(BigDecimal maxPrice, Pageable pageable); // only a ceiling

  Page<Product> findByPriceBetween(
      BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable); // both floor and ceiling

  @Query(
      """
      SELECT COALESCE(SUM(p.price * p.stockQuantity), 0)
      FROM Product p
      """)
  BigDecimal calculateTotalInventoryValue();

  @Query(
      """
      SELECT p.category, COALESCE(SUM(p.price * p.stockQuantity), 0)
      FROM Product p
      GROUP BY p.category
      """)
  List<Object[]> calculateInventoryValueByCategory();
}
