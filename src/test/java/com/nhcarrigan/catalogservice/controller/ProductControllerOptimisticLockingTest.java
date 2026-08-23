package com.nhcarrigan.catalogservice.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhcarrigan.catalogservice.dto.StockAdjustmentRequest;
import com.nhcarrigan.catalogservice.exception.GlobalExceptionHandler;
import com.nhcarrigan.catalogservice.service.ProductImportService;
import com.nhcarrigan.catalogservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerOptimisticLockingTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private ProductService productService;

  @MockBean private ProductImportService productImportService;

  @Test
  void optimisticLockingFailureReturns409Conflict() throws Exception {
    StockAdjustmentRequest request = new StockAdjustmentRequest();
    request.setDelta(5);

    when(productService.adjustStock(anyLong(), anyInt()))
        .thenThrow(new OptimisticLockingFailureException("Version conflict"));

    mockMvc
        .perform(
            patch("/api/products/{id}/stock", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status", is(409)))
        .andExpect(jsonPath("$.error", is("Conflict")))
        .andExpect(
            jsonPath(
                "$.message", is("The product was modified by another request. Please retry.")));
  }
}
