package com.fulfilment.application.monolith.fulfilments;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FulfilmentAssignmentLifecycleTest {

  private static final String PATH = "/fulfilment-assignments";
  private static final String TEST_WAREHOUSE_PREFIX = "MWH.FIT.L.";

  @Inject FulfilmentAssignmentRepository assignmentRepository;
  @Inject WarehouseRepository warehouseRepository;

  @BeforeEach
  @AfterEach
  void cleanTestData() {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              assignmentRepository.deleteAll();
              warehouseRepository.delete(
                  "businessUnitCode like ?1", TEST_WAREHOUSE_PREFIX + "%");
            });
  }

  @Test
  void preventsRemovingEntitiesReferencedByAnAssignment() {
    postAssignment(1L, 1L, "MWH.001");

    given().when().delete("/store/1").then().statusCode(409);
    given().when().delete("/product/1").then().statusCode(409);
    given()
        .when()
        .delete("/warehouse/1")
        .then()
        .statusCode(409)
        .body("code", equalTo(409))
        .body(
            "error",
            equalTo("Warehouse cannot be archived while it has active fulfilment assignments."));
  }

  @Test
  void preservesAssignmentsWhenWarehouseIsReplaced() {
    String businessUnitCode = TEST_WAREHOUSE_PREFIX + "REPLACE";
    long originalWarehouseId = createWarehouse(businessUnitCode);
    postAssignment(1L, 1L, businessUnitCode);

    given()
        .contentType("application/json")
        .body(
            Map.of(
                "businessUnitCode", businessUnitCode,
                "location", "AMSTERDAM-001",
                "capacity", 10,
                "stock", 0))
        .when()
        .post("/warehouse/{businessUnitCode}/replacement", businessUnitCode)
        .then()
        .statusCode(200);

    given()
        .queryParam("storeId", 1)
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].warehouseBusinessUnitCode", equalTo(businessUnitCode))
        .body("[0].warehouseId", not(equalTo((int) originalWarehouseId)));
  }

  private void postAssignment(long storeId, long productId, String businessUnitCode) {
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "storeId", storeId,
                "productId", productId,
                "warehouseBusinessUnitCode", businessUnitCode))
        .when()
        .post(PATH)
        .then()
        .statusCode(201);
  }

  private long createWarehouse(String businessUnitCode) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              var warehouse = new DbWarehouse();
              warehouse.businessUnitCode = businessUnitCode;
              warehouse.location = "AMSTERDAM-001";
              warehouse.capacity = 10;
              warehouse.stock = 0;
              warehouse.createdAt = LocalDateTime.now();
              warehouseRepository.persistAndFlush(warehouse);
              return warehouse.id;
            });
  }
}
