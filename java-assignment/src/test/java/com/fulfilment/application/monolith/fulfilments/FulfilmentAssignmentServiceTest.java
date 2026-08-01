package com.fulfilment.application.monolith.fulfilments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FulfilmentAssignmentServiceTest {

  private static final String TEST_PRODUCT_PREFIX = "FIT-S-";
  private static final String TEST_WAREHOUSE_PREFIX = "MWH.FIT.S.";

  @Inject FulfilmentAssignmentService assignmentService;
  @Inject FulfilmentAssignmentRepository assignmentRepository;
  @Inject ProductRepository productRepository;
  @Inject WarehouseRepository warehouseRepository;

  @BeforeEach
  @AfterEach
  void cleanTestData() {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              assignmentRepository.deleteAll();
              productRepository.delete("name like ?1", TEST_PRODUCT_PREFIX + "%");
              warehouseRepository.delete(
                  "businessUnitCode like ?1", TEST_WAREHOUSE_PREFIX + "%");
            });
  }

  @Nested
  class CreateAssignment {

    @Test
    void createsAndPersistsAssignment() {
      FulfilmentAssignment result = assignmentService.create(request(1L, 1L, "MWH.001"));

      assertNotNull(result.id);
      assertEquals(
          1L,
          QuarkusTransaction.requiringNew()
              .call(() -> assignmentRepository.count("id", result.id)));
    }

    @Test
    void rejectsDuplicateAssignment() {
      assignmentService.create(request(1L, 1L, "MWH.001"));

      assertFulfilmentException(
          409,
          "The fulfilment assignment already exists.",
          () -> assignmentService.create(request(1L, 1L, "MWH.001")));
    }

    @Test
    void validatesRequiredRequestData() {
      assertFulfilmentException(400, "Request body is required.", () -> assignmentService.create(null));
      assertFulfilmentException(
          400, "Store id is required.", () -> assignmentService.create(request(null, 1L, "MWH.001")));
      assertFulfilmentException(
          400, "Product id is required.", () -> assignmentService.create(request(1L, null, "MWH.001")));
      assertFulfilmentException(
          400,
          "Warehouse business unit code is required.",
          () -> assignmentService.create(request(1L, 1L, " ")));
    }

    @Test
    void rejectsUnknownStore() {
      assertFulfilmentException(
          404,
          "Store with id " + Long.MAX_VALUE + " does not exist.",
          () -> assignmentService.create(request(Long.MAX_VALUE, 1L, "MWH.001")));
    }

    @Test
    void rejectsUnknownProduct() {
      assertFulfilmentException(
          404,
          "Product with id " + Long.MAX_VALUE + " does not exist.",
          () -> assignmentService.create(request(1L, Long.MAX_VALUE, "MWH.001")));
    }

    @Test
    void rejectsUnknownOrArchivedWarehouse() {
      assertFulfilmentException(
          404,
          "Active warehouse with business unit code UNKNOWN does not exist.",
          () -> assignmentService.create(request(1L, 1L, "UNKNOWN")));

      String archivedCode = TEST_WAREHOUSE_PREFIX + "ARCHIVED";
      createWarehouse(archivedCode, true);
      assertFulfilmentException(
          404,
          "Active warehouse with business unit code " + archivedCode + " does not exist.",
          () -> assignmentService.create(request(1L, 1L, archivedCode)));
    }
  }

  @Nested
  class AssignmentConstraints {

    @Test
    void limitsProductToTwoDistinctWarehousesAtStore() {
      assignmentService.create(request(1L, 1L, "MWH.001"));
      assignmentService.create(request(1L, 1L, "MWH.012"));

      assertFulfilmentException(
          409,
          "A product can be fulfilled by at most 2 warehouses per store.",
          () -> assignmentService.create(request(1L, 1L, "MWH.023")));
    }

    @Test
    void limitsStoreToThreeDistinctWarehousesButAllowsReuse() {
      long fourthProduct = createProduct("store-limit");
      assignmentService.create(request(1L, 1L, "MWH.001"));
      assignmentService.create(request(1L, 2L, "MWH.012"));
      assignmentService.create(request(1L, 3L, "MWH.023"));
      assignmentService.create(request(1L, fourthProduct, "MWH.001"));

      String fourthWarehouse = TEST_WAREHOUSE_PREFIX + "004";
      createWarehouse(fourthWarehouse, false);
      assertFulfilmentException(
          409,
          "A store can be fulfilled by at most 3 warehouses.",
          () -> assignmentService.create(request(1L, fourthProduct, fourthWarehouse)));
    }

    @Test
    void limitsWarehouseToFiveDistinctProductsButAllowsReuseAcrossStores() {
      long fourthProduct = createProduct("warehouse-limit-4");
      long fifthProduct = createProduct("warehouse-limit-5");
      long sixthProduct = createProduct("warehouse-limit-6");
      assignmentService.create(request(1L, 1L, "MWH.001"));
      assignmentService.create(request(2L, 1L, "MWH.001"));
      assignmentService.create(request(1L, 2L, "MWH.001"));
      assignmentService.create(request(1L, 3L, "MWH.001"));
      assignmentService.create(request(1L, fourthProduct, "MWH.001"));
      assignmentService.create(request(1L, fifthProduct, "MWH.001"));

      assertFulfilmentException(
          409,
          "A warehouse can store at most 5 types of products.",
          () -> assignmentService.create(request(1L, sixthProduct, "MWH.001")));
    }
  }

  @Nested
  class DeleteAssignment {

    @Test
    void deletesExistingAssignment() {
      FulfilmentAssignment assignment =
          assignmentService.create(request(1L, 1L, "MWH.001"));

      assignmentService.delete(assignment.id);

      assertEquals(
          0L,
          QuarkusTransaction.requiringNew()
              .call(() -> assignmentRepository.count("id", assignment.id)));
    }

    @Test
    void rejectsUnknownAssignment() {
      assertFulfilmentException(
          404,
          "Fulfilment assignment with id " + Long.MAX_VALUE + " does not exist.",
          () -> assignmentService.delete(Long.MAX_VALUE));
    }
  }

  private FulfilmentAssignmentRequest request(
      Long storeId, Long productId, String warehouseBusinessUnitCode) {
    var request = new FulfilmentAssignmentRequest();
    request.storeId = storeId;
    request.productId = productId;
    request.warehouseBusinessUnitCode = warehouseBusinessUnitCode;
    return request;
  }

  private void assertFulfilmentException(int status, String message, Runnable operation) {
    FulfilmentException exception = assertThrows(FulfilmentException.class, operation::run);
    assertEquals(status, exception.status());
    assertEquals(message, exception.getMessage());
  }

  private long createProduct(String suffix) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Product product =
                  new Product(
                      TEST_PRODUCT_PREFIX
                          + suffix
                          + "-"
                          + UUID.randomUUID().toString().substring(0, 6));
              productRepository.persistAndFlush(product);
              return product.id;
            });
  }

  private long createWarehouse(String businessUnitCode, boolean archived) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              var warehouse = new DbWarehouse();
              warehouse.businessUnitCode = businessUnitCode;
              warehouse.location = "AMSTERDAM-001";
              warehouse.capacity = 10;
              warehouse.stock = 0;
              warehouse.createdAt = LocalDateTime.now();
              warehouse.archivedAt = archived ? LocalDateTime.now() : null;
              warehouseRepository.persistAndFlush(warehouse);
              return warehouse.id;
            });
  }
}
