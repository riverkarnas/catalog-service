package com.nhcarrigan.catalogservice.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhcarrigan.catalogservice.dto.ProductRequest;
import com.nhcarrigan.catalogservice.dto.StockAdjustmentRequest;
import com.nhcarrigan.catalogservice.entity.Product;
import com.nhcarrigan.catalogservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ProductRepository productRepository;

  private ProductRequest validRequest(String sku) {
    ProductRequest request = new ProductRequest();
    request.setName("Integration Test Product");
    request.setSku(sku);
    request.setCategory("Test Category");
    request.setPrice(new BigDecimal("19.99"));
    request.setStockQuantity(20);
    return request;
  }

  private Product createTestProduct(String sku, int stock) {
    Product product =
        new Product(
            "Bulk Test Product",
            sku,
            "Test Category",
            new BigDecimal("19.99"),
            stock,
            "Product created for bulk stock adjustment tests.");

    return productRepository.save(product);
  }

  private Product createInventoryValueTestProduct(
      String name, String sku, String category, String price, int stock) {
    Product product =
        new Product(
            name,
            sku,
            category,
            new BigDecimal(price),
            stock,
            "Product created for inventory value tests.");

    return productRepository.save(product);
  }

  @Test
  void getBySkuReturnsProduct() throws Exception{
    Product testProduct = createTestProduct("TEST-PRODUCT", 1);
    mockMvc
            .perform(get("/api/products/sku/{sku}", testProduct.getSku())) 
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sku", is("TEST-PRODUCT")));
            }
  @Test
  void getBySkuThrowsMissing() throws Exception{
     mockMvc
            .perform(get("/api/products/sku/{sku}", "MISSING-SKU" + System.nanoTime()))
            .andExpect(status().isNotFound());
  }

  @Test
  void createProductReturns201AndBody() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name", is("Integration Test Product")))
        .andExpect(jsonPath("$.stockQuantity", is(20)));
  }

  @Test
  void createProductWithBlankNameReturns400() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    request.setName("");

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("Validation Failed")));
  }

  @Test
  void createProductWithNegativePriceReturns400() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    request.setPrice(new BigDecimal("-5.00"));

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0]", is("price: Price must be greater than 0.00")));
  }

  @Test
  void createProductWithZeroPriceReturns400() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    request.setPrice(new BigDecimal("0.00"));

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0]", is("price: Price must be greater than 0.00")));
  }

  @Test
  void bulkCreateProductReturns201AndBody() throws Exception {
    ProductRequest request1 = validRequest("CTRL-SKU-" + System.nanoTime());
    ProductRequest request2 = validRequest("CTRL-SKU-" + System.nanoTime());
    List<ProductRequest> requests = List.of(request1, request2);

    mockMvc
        .perform(
            post("/api/products/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requests)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].name", is("Integration Test Product")))
        .andExpect(jsonPath("$[1].stockQuantity", is(20)));
  }

  @Test
  void bulkCreateProductReturnsConflictWhenProductsCollide() throws Exception {
    createTestProduct("SKU-9000", 20);
    ProductRequest request1 = validRequest("SKU-9000");
    ProductRequest request2 = validRequest("CTRL-SKU-" + System.nanoTime());
    List<ProductRequest> requests = List.of(request1, request2);

    mockMvc
        .perform(
            post("/api/products/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requests)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("Conflict")));
  }

  @Test
  void bulkCreateProductReturnsConflictWhenProductsInBatchShareSKU() throws Exception {
    ProductRequest request1 = validRequest("SKU-6000");
    ProductRequest request2 = validRequest("SKU-6000");
    List<ProductRequest> requests = List.of(request1, request2);

    mockMvc
        .perform(
            post("/api/products/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requests)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("Conflict")));
  }

  @Test
  void bulkDeleteSeparatesValidAndInvalidIds() throws Exception {
    Product firstProduct = createTestProduct("BULK-DELETE-1", 10);
    Product secondProduct = createTestProduct("BULK-DELETE-2", 5);

    String request =
        """
        [
            %d,
            -999,
            %d
        ]
        """
            .formatted(
                firstProduct.getId(),
                secondProduct.getId());

    mockMvc
        .perform(
            delete("/api/products/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted", hasSize(2)))
        .andExpect(jsonPath("$.deleted[0]", is(firstProduct.getId().intValue())))
        .andExpect(jsonPath("$.deleted[1]", is(secondProduct.getId().intValue())))
        .andExpect(jsonPath("$.rejected", hasSize(1)))
        .andExpect(jsonPath("$.rejected[0]", is(-999)));
  }

  @Test
  void bulkDeleteRejectsEmptyRequest() throws Exception {
    mockMvc
        .perform(
            delete("/api/products/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("Validation Failed")));
  }

  @Test
  void getUnknownProductReturns404() throws Exception {
    mockMvc.perform(get("/api/products/999999")).andExpect(status().isNotFound());
  }

  @Test
  void getCategoriesReturnsDistinctCategories() throws Exception {
    mockMvc
        .perform(get("/api/products/categories"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$", containsInAnyOrder("Electronics", "Office Supplies", "Furniture")));
  }

  @Test
  void searchByCategoryReturnsMatchingProducts() throws Exception {
    mockMvc
        .perform(get("/api/products/search").param("category", "Electronics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", not(empty())))
        .andExpect(jsonPath("$[*].category", everyItem(is("Electronics"))));
  }

  @Test
  void searchByUnknownCategoryReturnsEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/products/search").param("category", "NonExistentCategory"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", empty()));
  }

  @Test
  void searchByEmptyCategoryReturnsEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/products/search").param("category", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", empty()));
  }

  @Test
  void searchByNameReturnsMatchingProducts() throws Exception {
    mockMvc
        .perform(get("/api/products/search").param("name", "Keyboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", not(empty())))
        .andExpect(jsonPath("$[*].name", everyItem(containsStringIgnoringCase("Keyboard"))));
  }

  @Test
  void searchByUnknownNameReturnsEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/products/search").param("name", "NoSuchProduct"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", empty()));
  }

  @Test
  void searchWithoutParamsReturnsEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/products/search"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", empty()));
  }

  @Test
  void searchWithBothNameAndCategoryReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/products/search").param("name", "Keyboard").param("category", "Electronics"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("Bad Request")))
        .andExpect(jsonPath("$.message", is("Provide either a name or a category, not both")));
  }

  @Test
  void searchWithNameAndBlankCategoryReturns400() throws Exception {
    mockMvc
        .perform(get("/api/products/search").param("name", "Keyboard").param("category", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("Bad Request")));
  }

  @Test
  void searchWithBlankNameAndCategoryReturns400() throws Exception {
    mockMvc
        .perform(get("/api/products/search").param("name", "").param("category", "Electronics"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("Bad Request")));
  }

  @Test
  void getEmptyCategoriesReturnsEmpty() throws Exception {
    productRepository.deleteAll();
    mockMvc
        .perform(get("/api/products/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", empty()));
  }

  @Test
  void getInventoryValueReturnsTotalAndCategoryBreakdown() throws Exception {
    productRepository.deleteAll();

    createInventoryValueTestProduct("Keyboard", "INV-VALUE-1", "Electronics", "10.00", 5);
    createInventoryValueTestProduct("Mouse", "INV-VALUE-2", "Electronics", "20.00", 3);
    createInventoryValueTestProduct("Desk", "INV-VALUE-3", "Furniture", "15.00", 2);

    mockMvc
        .perform(get("/api/products/inventory-value"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalValue", is(140.0)))
        .andExpect(jsonPath("$.byCategory.Electronics", is(110.0)))
        .andExpect(jsonPath("$.byCategory.Furniture", is(30.0)));
  }

  @Test
  void getInventoryValueReturnsZeroForEmptyCatalog() throws Exception {
    productRepository.deleteAll();

    mockMvc
        .perform(get("/api/products/inventory-value"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalValue", is(0)))
        .andExpect(jsonPath("$.byCategory", anEmptyMap()));
  }

  @Test
  void getInventoryValueIgnoresValueOfZeroStockProducts() throws Exception {
    productRepository.deleteAll();

    createInventoryValueTestProduct(
        "Zero Stock Product", "INV-VALUE-ZERO", "Electronics", "100.00", 0);
    createInventoryValueTestProduct(
        "In Stock Product", "INV-VALUE-STOCK", "Electronics", "10.00", 5);

    mockMvc
        .perform(get("/api/products/inventory-value"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalValue", is(50.0)))
        .andExpect(jsonPath("$.byCategory.Electronics", is(50.0)));
  }

  @Test
  void deleteProductRemovesIt() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    String body =
        mockMvc
            .perform(
                post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long id = objectMapper.readTree(body).get("id").asLong();

    mockMvc.perform(delete("/api/products/{id}", id)).andExpect(status().isNoContent());

    mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
  }

  @Test
  void adjustStockRejectsDropBelowZero() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    request.setStockQuantity(3);
    String body =
        mockMvc
            .perform(
                post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long id = objectMapper.readTree(body).get("id").asLong();

    StockAdjustmentRequest adjustment = new StockAdjustmentRequest();
    adjustment.setDelta(-4);

    mockMvc
        .perform(
            patch("/api/products/{id}/stock", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adjustment)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void adjustStockAppliesPositiveDelta() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    request.setStockQuantity(3);
    String body =
        mockMvc
            .perform(
                post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long id = objectMapper.readTree(body).get("id").asLong();

    StockAdjustmentRequest adjustment = new StockAdjustmentRequest();
    adjustment.setDelta(7);

    mockMvc
        .perform(
            patch("/api/products/{id}/stock", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adjustment)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity", is(10)));
  }

  @Test
  void adjustStockAllowsReducingExactlyToZero() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    request.setStockQuantity(10);

    String body =
        mockMvc
            .perform(
                post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long id = objectMapper.readTree(body).get("id").asLong();

    StockAdjustmentRequest adjustment = new StockAdjustmentRequest();
    adjustment.setDelta(-10);

    mockMvc
        .perform(
            patch("/api/products/{id}/stock", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adjustment)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity", is(0)));
  }

  @Test
  void sequentialStockAdjustmentsRejectWhenCombinedTheyGoBelowZero() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    request.setStockQuantity(10);

    String body =
        mockMvc
            .perform(
                post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long id = objectMapper.readTree(body).get("id").asLong();

    StockAdjustmentRequest firstAdjustment = new StockAdjustmentRequest();
    firstAdjustment.setDelta(-6);

    mockMvc
        .perform(
            patch("/api/products/{id}/stock", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstAdjustment)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity", is(4)));

    StockAdjustmentRequest secondAdjustment = new StockAdjustmentRequest();
    secondAdjustment.setDelta(-5);

    mockMvc
        .perform(
            patch("/api/products/{id}/stock", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondAdjustment)))
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(get("/api/products/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity", is(4)));
  }

  @Test
  void getStockHistoryReturnsAdjustmentHistory() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    request.setStockQuantity(10);

    String body =
        mockMvc
            .perform(
                post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long id = objectMapper.readTree(body).get("id").asLong();

    StockAdjustmentRequest adjustment = new StockAdjustmentRequest();
    adjustment.setDelta(5);

    mockMvc
        .perform(
            patch("/api/products/{id}/stock", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adjustment)))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/products/{id}/stock-history", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].productId", is(id.intValue())))
        .andExpect(jsonPath("$[0].delta", is(5)))
        .andExpect(jsonPath("$[0].resultingQuantity", is(15)))
        .andExpect(jsonPath("$[0].timestamp").exists());
  }

  @Test
  void getStockHistoryReturnsNewestFirst() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    request.setStockQuantity(10);

    String body =
        mockMvc
            .perform(
                post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long id = objectMapper.readTree(body).get("id").asLong();

    StockAdjustmentRequest firstAdjustment = new StockAdjustmentRequest();
    firstAdjustment.setDelta(5);

    mockMvc.perform(
        patch("/api/products/{id}/stock", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(firstAdjustment)));

    StockAdjustmentRequest secondAdjustment = new StockAdjustmentRequest();
    secondAdjustment.setDelta(-3);

    mockMvc.perform(
        patch("/api/products/{id}/stock", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(secondAdjustment)));

    mockMvc
        .perform(get("/api/products/{id}/stock-history", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].delta", is(-3)))
        .andExpect(jsonPath("$[0].resultingQuantity", is(12)))
        .andExpect(jsonPath("$[1].delta", is(5)))
        .andExpect(jsonPath("$[1].resultingQuantity", is(15)));
  }

  @Test
  void getStockHistoryForUnknownProductReturns404() throws Exception {
    mockMvc
        .perform(get("/api/products/{id}/stock-history", 999999))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error", is("Not Found")));
  }

  @Test
  void getUnknownProductReturns404WithCorrectShape() throws Exception {
    mockMvc
        .perform(get("/api/products/999999"))
        .andExpect(status().isNotFound())
        .andExpectAll(
            jsonPath("$.timestamp").exists(),
            jsonPath("$.status", is(404)),
            jsonPath("$.error", is("Not Found")),
            jsonPath("$.message").exists(),
            jsonPath("$.details").exists());
  }

  @Test
  void createDescriptionReturns201AndBody() throws Exception {
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    request.setDescription("This is a pilot Description for the product.");

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.description", is("This is a pilot Description for the product.")));
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void logMethodPathStatusOnProductRoutes(CapturedOutput output) throws Exception {
    String expectedLogGet = "[GET] /api/products: 200" + System.lineSeparator();
    mockMvc.perform(get("/api/products"));
    assert output.getOut().endsWith(expectedLogGet)
        : "Requests against /api/products should produce logs (GET)";

    String expectedLogPost = "[POST] /api/products: 201" + System.lineSeparator();
    ProductRequest request = validRequest("CTRL-SKU-" + System.nanoTime());
    mockMvc.perform(
        post("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    assert output.getOut().endsWith(expectedLogPost)
        : "Requests against /api/products should produce logs (POST)";

    mockMvc.perform(get("/actuator/health"));
    assert output.getOut().endsWith(expectedLogPost)
        : "Requests against routes other than /api/products should not produce logs";
  }

  @Test
  void bulkAdjustStockReturnsUpdatedProducts() throws Exception {
    Product firstProduct = createTestProduct("BULK-TEST-1", 20);
    Product secondProduct = createTestProduct("BULK-TEST-2", 10);

    String request =
        """
                [
                    {
                        "productId": %d,
                        "delta": 5
                    },
                    {
                        "productId": %d,
                        "delta": -3
                    }
                ]
                """
            .formatted(firstProduct.getId(), secondProduct.getId());

    mockMvc
        .perform(
            patch("/api/products/stock/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].id", is(firstProduct.getId().intValue())))
        .andExpect(jsonPath("$[0].stockQuantity", is(25)))
        .andExpect(jsonPath("$[1].id", is(secondProduct.getId().intValue())))
        .andExpect(jsonPath("$[1].stockQuantity", is(7)));
  }

  @Test
  void bulkAdjustStockRejectsEmptyRequest() throws Exception {
    mockMvc
        .perform(
            patch("/api/products/stock/bulk").contentType(MediaType.APPLICATION_JSON).content("[]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("Validation Failed")));
  }

  @Test
  void bulkAdjustStockRejectsMissingProductId() throws Exception {
    String request =
        """
                [
                    {
                        "delta": 5
                    }
                ]
                """;

    mockMvc
        .perform(
            patch("/api/products/stock/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("Validation Failed")));
  }

  @Test
  void bulkAdjustStockRejectsMissingDelta() throws Exception {
    String request =
        """
                [
                    {
                        "productId": 1
                    }
                ]
                """;

    mockMvc
        .perform(
            patch("/api/products/stock/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("Validation Failed")));
  }

  @Test
  void bulkAdjustStockReturns422ForInsufficientStock() throws Exception {
    String request =
        """
                [
                    {
                        "productId": 1,
                        "delta": -999
                    }
                ]
                """;

    mockMvc
        .perform(
            patch("/api/products/stock/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error", is("Unprocessable Entity")));
  }

  @Test
  void bulkAdjustStockReturns404ForUnknownProduct() throws Exception {
    String request =
        """
                [
                    {
                        "productId": 999999,
                        "delta": 5
                    }
                ]
                """;

    mockMvc
        .perform(
            patch("/api/products/stock/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error", is("Not Found")));
  }

  @Test
  void bulkAdjustStockRollsBackEntireBatchWhenOneAdjustmentFails() throws Exception {
    Product firstProduct = createTestProduct("BULK-ROLLBACK-1", 20);
    Product secondProduct = createTestProduct("BULK-ROLLBACK-2", 10);

    String request =
        """
                [
                    {
                        "productId": %d,
                        "delta": 5
                    },
                    {
                        "productId": %d,
                        "delta": -11
                    }
                ]
                """
            .formatted(firstProduct.getId(), secondProduct.getId());

    mockMvc
        .perform(
            patch("/api/products/stock/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error", is("Unprocessable Entity")));

    Product firstReloaded = productRepository.findById(firstProduct.getId()).orElseThrow();

    Product secondReloaded = productRepository.findById(secondProduct.getId()).orElseThrow();

    assertThat(firstReloaded.getStockQuantity(), is(20));
    assertThat(secondReloaded.getStockQuantity(), is(10));
  }

  @Test
  void getProductsReturnsFirstPageWithDefaultSize() throws Exception {
    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(7)))
        .andExpect(jsonPath("$.totalElements", is(7)))
        .andExpect(jsonPath("$.totalPages", is(1)));
  }

  @Test
  void getProductsSupportsPagination() throws Exception {
    mockMvc
        .perform(get("/api/products").param("page", "0").param("size", "2").param("sort", "id,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[0].id", is(1)))
        .andExpect(jsonPath("$.content[1].id", is(2)))
        .andExpect(jsonPath("$.totalElements", is(7)))
        .andExpect(jsonPath("$.totalPages", is(4)));
  }

  @Test
  void getProductsReturnsSecondPage() throws Exception {
    mockMvc
        .perform(get("/api/products").param("page", "1").param("size", "2").param("sort", "id,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[0].id", is(3)))
        .andExpect(jsonPath("$.content[1].id", is(4)))
        .andExpect(jsonPath("$.totalElements", is(7)))
        .andExpect(jsonPath("$.totalPages", is(4)));
  }

  @Test
  void getProductsReturnsEmptyContentForOutOfRangePage() throws Exception {
    mockMvc
        .perform(get("/api/products").param("page", "99").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", empty()))
        .andExpect(jsonPath("$.totalElements", is(7)))
        .andExpect(jsonPath("$.totalPages", is(1)));
  }

  @Test
  void filterByPriceMinOnlyReturnsMatchingProducts() throws Exception {
    mockMvc
        .perform(get("/api/products").param("minPrice", "45"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.totalElements", is(3)));
  }

  @Test
  void filterByPriceMaxOnlyReturnsMatchingProducts() throws Exception {
    mockMvc
        .perform(get("/api/products").param("maxPrice", "25"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.totalElements", is(3)));
  }

  @Test
  void filterByPriceMinAndMaxReturnsMatchingProducts() throws Exception {
    mockMvc
        .perform(get("/api/products").param("minPrice", "18").param("maxPrice", "60"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(5)))
        .andExpect(jsonPath("$.totalElements", is(5)));
  }

  @Test
  void filterByPriceReversedReturns400() throws Exception {
    mockMvc
        .perform(get("/api/products").param("minPrice", "60").param("maxPrice", "18"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("Bad Request")))
        .andExpect(
            jsonPath(
                "$.message",
                is(
                    "Cannot filter using reversed price range: minimum 60 is greater than maximum 18")));
  }

  @Test
  void creationWithDupeNameProducesWarning() throws Exception {
    String dupeName = "Test Duplicate Product Name";

    ProductRequest request1 = validRequest("CTRL-SKU-" + System.nanoTime());
    request1.setName(dupeName);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
        .andExpect(jsonPath("$.warning").doesNotExist());

    ProductRequest request2 = validRequest("CTRL-SKU-" + System.nanoTime());
    request2.setName(dupeName.toUpperCase());

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
        .andExpect(jsonPath("$.warning").exists());
  }

  @Test
  void creationWithDupeNameSubstringDoesNotProduceWarning() throws Exception {
    String dupeName = "Test Duplicate Product Name";

    ProductRequest request1 = validRequest("CTRL-SKU-" + System.nanoTime());
    request1.setName(dupeName);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
        .andExpect(jsonPath("$.warning").doesNotExist());

    ProductRequest request2 = validRequest("CTRL-SKU-" + System.nanoTime());
    request2.setName(dupeName.substring(0, 5));

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(jsonPath("$.warning").doesNotExist());
  }

  @Test
  void patchProductSomeFields() throws Exception {
    Product original = productRepository.findById(4L).orElseThrow();

    // convert to deep copy
    original = objectMapper.readValue(objectMapper.writeValueAsString(original), Product.class);
    mockMvc
            .perform(
                    patch("/api/products/4")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                        {
                                            "name": "Patch test product name",
                                            "description": "Test description."
                                        }
                                        """))
            .andExpectAll(
                    jsonPath("$.name", is("Patch test product name")),
                    jsonPath("$.description", is("Test description.")),
                    jsonPath("$.sku", is(original.getSku())),
                    jsonPath("$.category", is(original.getCategory())),
                    jsonPath("$.price", is(original.getPrice().doubleValue())),
                    jsonPath("$.stockQuantity", is(original.getStockQuantity())));
  }

  @Test
  void patchProductNoFields() throws Exception {
    Product original = productRepository.findById(4L).orElseThrow();
    // convert to deep copy
    original = objectMapper.readValue(objectMapper.writeValueAsString(original), Product.class);
    mockMvc
            .perform(patch("/api/products/4").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpectAll(
                    jsonPath("$.name", is(original.getName())),
                    jsonPath("$.sku", is(original.getSku())),
                    jsonPath("$.category", is(original.getCategory())),
                    jsonPath("$.price", is(original.getPrice().doubleValue())),
                    jsonPath("$.stockQuantity", is(original.getStockQuantity())),
                    jsonPath("$.description", is(original.getDescription())));
  }
}
