package com.fulfilment.application.monolith.fulfilments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FulfilmentAssignmentRepositoryTest {

  private static final String TEST_PRODUCT_PREFIX = "FIT-Q-";
  private static final String TEST_WAREHOUSE_PREFIX = "MWH.FIT.Q.";

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
  class DistinctCountQueries {

    @Test
    void countsDistinctWarehousesForProductAtStore() {
      persistAssignment(1L, 1L, 1L);
      persistAssignment(1L, 1L, 2L);
      persistAssignment(2L, 1L, 3L);

      assertEquals(2L, assignmentRepository.countWarehousesForProductAtStore(1L, 1L));
      assertEquals(1L, assignmentRepository.countWarehousesForProductAtStore(2L, 1L));
    }

    @Test
    void countsDistinctWarehousesForStoreInsteadOfAssignmentRows() {
      persistAssignment(1L, 1L, 1L);
      persistAssignment(1L, 2L, 1L);
      persistAssignment(1L, 3L, 2L);

      assertEquals(2L, assignmentRepository.countWarehousesForStore(1L));
    }

    @Test
    void countsDistinctProductsForWarehouseInsteadOfAssignmentRows() {
      persistAssignment(1L, 1L, 1L);
      persistAssignment(2L, 1L, 1L);
      persistAssignment(2L, 2L, 1L);

      assertEquals(2L, assignmentRepository.countProductsForWarehouse(1L));
    }
  }

  @Nested
  class RelationshipQueries {

    @Test
    void detectsExactAndPartialRelationships() {
      persistAssignment(1L, 1L, 1L);

      assertTrue(assignmentRepository.exists(1L, 1L, 1L));
      assertFalse(assignmentRepository.exists(1L, 1L, 2L));
      assertTrue(assignmentRepository.warehouseAlreadyFulfilsStore(1L, 1L));
      assertFalse(assignmentRepository.warehouseAlreadyFulfilsStore(1L, 2L));
      assertTrue(assignmentRepository.warehouseAlreadyStoresProduct(1L, 1L));
      assertFalse(assignmentRepository.warehouseAlreadyStoresProduct(1L, 2L));
    }

    @Test
    void listsAssignmentsInIdOrderAndFiltersByStore() {
      long firstId = persistAssignment(2L, 2L, 2L);
      long secondId = persistAssignment(1L, 1L, 1L);

      var all = assignmentRepository.listOrdered();
      var forStore = assignmentRepository.listForStore(1L);

      assertEquals(2, all.size());
      assertEquals(firstId, all.get(0).id);
      assertEquals(secondId, all.get(1).id);
      assertEquals(1, forStore.size());
      assertEquals(secondId, forStore.get(0).id);
    }

    @Test
    void countsAssignmentsByReferencedEntity() {
      persistAssignment(1L, 1L, 1L);
      persistAssignment(1L, 2L, 1L);

      assertEquals(2L, assignmentRepository.countByStore(1L));
      assertEquals(1L, assignmentRepository.countByProduct(1L));
      assertEquals(2L, assignmentRepository.countByWarehouse(1L));
    }
  }

  @Nested
  class WarehouseReplacement {

    @Test
    void movesAssignmentsFromArchivedRowsToActiveReplacement() {
      String businessUnitCode = TEST_WAREHOUSE_PREFIX + "REPLACE";
      long archivedWarehouseId = createWarehouse(businessUnitCode, true);
      long replacementWarehouseId = createWarehouse(businessUnitCode, false);
      long assignmentId = persistAssignment(1L, 1L, archivedWarehouseId);

      QuarkusTransaction.requiringNew()
          .run(
              () ->
                  assignmentRepository.moveAssignmentsToReplacement(businessUnitCode));

      long assignedWarehouseId =
          QuarkusTransaction.requiringNew()
              .call(() -> assignmentRepository.findById(assignmentId).warehouse.id);
      assertEquals(replacementWarehouseId, assignedWarehouseId);
    }
  }

  private long persistAssignment(long storeId, long productId, long warehouseId) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              var assignment = new FulfilmentAssignment();
              assignment.store = Store.findById(storeId);
              assignment.product = productRepository.findById(productId);
              assignment.warehouse = warehouseRepository.findById(warehouseId);
              assignmentRepository.persistAndFlush(assignment);
              return assignment.id;
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
