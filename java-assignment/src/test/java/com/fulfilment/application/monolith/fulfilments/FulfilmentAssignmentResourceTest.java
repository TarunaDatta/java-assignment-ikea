package com.fulfilment.application.monolith.fulfilments;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FulfilmentAssignmentResourceTest {

  private static final String PATH = "/fulfilment-assignments";
  private static final String TEST_PRODUCT_PREFIX = "FIT-R-";
  private static final String TEST_WAREHOUSE_PREFIX = "MWH.FIT.R.";

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
  class ListAssignments {

    @Test
    void returnsAllAssignments() {
      postAssignment(1L, 1L, "MWH.001", 201);
      postAssignment(2L, 2L, "MWH.012", 201);

      given()
          .when()
          .get(PATH)
          .then()
          .statusCode(200)
          .body("$", hasSize(2))
          .body("[0].storeId", equalTo(1))
          .body("[1].storeId", equalTo(2));
    }

    @Test
    void filtersAssignmentsByStore() {
      postAssignment(1L, 1L, "MWH.001", 201);
      postAssignment(2L, 2L, "MWH.012", 201);

      given()
          .queryParam("storeId", 2)
          .when()
          .get(PATH)
          .then()
          .statusCode(200)
          .body("$", hasSize(1))
          .body("[0].storeId", equalTo(2));
    }

    @Test
    void returnsEmptyListWhenThereAreNoAssignments() {
      given().when().get(PATH).then().statusCode(200).body("$", hasSize(0));
    }
  }

  @Nested
  class GetAssignment {

    @Test
    void returnsAnExistingAssignment() {
      int assignmentId = postAssignment(1L, 1L, "MWH.001", 201);

      given()
          .when()
          .get(PATH + "/{id}", assignmentId)
          .then()
          .statusCode(200)
          .body("id", equalTo(assignmentId))
          .body("storeId", equalTo(1))
          .body("productId", equalTo(1))
          .body("warehouseId", equalTo(1))
          .body("warehouseBusinessUnitCode", equalTo("MWH.001"));
    }

    @Test
    void returnsNotFoundForUnknownAssignment() {
      given()
          .when()
          .get(PATH + "/{id}", Long.MAX_VALUE)
          .then()
          .statusCode(404)
          .body("code", equalTo(404));
    }
  }

  @Nested
  class CreateAssignment {

    @Test
    void createsAnAssignment() {
      given()
          .contentType("application/json")
          .body(request(1L, 1L, "MWH.001"))
          .when()
          .post(PATH)
          .then()
          .statusCode(201)
          .body("id", notNullValue())
          .body("storeId", equalTo(1))
          .body("productId", equalTo(1))
          .body("warehouseId", equalTo(1))
          .body("warehouseBusinessUnitCode", equalTo("MWH.001"));
    }

    @Test
    void rejectsDuplicateAssignment() {
      postAssignment(1L, 1L, "MWH.001", 201);

      postAssignment(1L, 1L, "MWH.001", 409);
    }

    @Test
    void limitsAProductToTwoWarehousesAtAStore() {
      postAssignment(1L, 1L, "MWH.001", 201);
      postAssignment(1L, 1L, "MWH.012", 201);

      given()
          .contentType("application/json")
          .body(request(1L, 1L, "MWH.023"))
          .when()
          .post(PATH)
          .then()
          .statusCode(409)
          .body("error", equalTo("A product can be fulfilled by at most 2 warehouses per store."));
    }

    @Test
    void limitsAStoreToThreeDistinctWarehouses() {
      long fourthProduct = createProduct("store-limit");
      postAssignment(1L, 1L, "MWH.001", 201);
      postAssignment(1L, 2L, "MWH.012", 201);
      postAssignment(1L, 3L, "MWH.023", 201);

      // Reusing a warehouse does not consume another distinct-warehouse slot.
      postAssignment(1L, fourthProduct, "MWH.001", 201);

      createWarehouse(TEST_WAREHOUSE_PREFIX + "004", false);
      given()
          .contentType("application/json")
          .body(request(1L, fourthProduct, TEST_WAREHOUSE_PREFIX + "004"))
          .when()
          .post(PATH)
          .then()
          .statusCode(409)
          .body("error", equalTo("A store can be fulfilled by at most 3 warehouses."));
    }

    @Test
    void limitsAWarehouseToFiveDistinctProductTypes() {
      long fourthProduct = createProduct("warehouse-limit-4");
      long fifthProduct = createProduct("warehouse-limit-5");
      long sixthProduct = createProduct("warehouse-limit-6");

      postAssignment(1L, 1L, "MWH.001", 201);
      // Reusing product 1 at another store still represents one product type.
      postAssignment(2L, 1L, "MWH.001", 201);
      postAssignment(1L, 2L, "MWH.001", 201);
      postAssignment(1L, 3L, "MWH.001", 201);
      postAssignment(1L, fourthProduct, "MWH.001", 201);
      postAssignment(1L, fifthProduct, "MWH.001", 201);

      given()
          .contentType("application/json")
          .body(request(1L, sixthProduct, "MWH.001"))
          .when()
          .post(PATH)
          .then()
          .statusCode(409)
          .body("error", equalTo("A warehouse can store at most 5 types of products."));
    }

    @Test
    void rejectsMissingStoreId() {
      assertBadRequest(
          Map.of("productId", 1, "warehouseBusinessUnitCode", "MWH.001"),
          "Store id is required.");
    }

    @Test
    void rejectsMissingProductId() {
      assertBadRequest(
          Map.of("storeId", 1, "warehouseBusinessUnitCode", "MWH.001"),
          "Product id is required.");
    }

    @Test
    void rejectsMissingWarehouseBusinessUnitCode() {
      assertBadRequest(Map.of("storeId", 1, "productId", 1),
          "Warehouse business unit code is required.");
    }

    @Test
    void rejectsUnknownStore() {
      assertNotFound(
          request(Long.MAX_VALUE, 1L, "MWH.001"),
          "Store with id " + Long.MAX_VALUE + " does not exist.");
    }

    @Test
    void rejectsUnknownProduct() {
      assertNotFound(
          request(1L, Long.MAX_VALUE, "MWH.001"),
          "Product with id " + Long.MAX_VALUE + " does not exist.");
    }

    @Test
    void rejectsUnknownWarehouse() {
      assertNotFound(
          request(1L, 1L, "UNKNOWN"),
          "Active warehouse with business unit code UNKNOWN does not exist.");
    }

    @Test
    void rejectsArchivedWarehouse() {
      String businessUnitCode = TEST_WAREHOUSE_PREFIX + "ARCHIVED";
      createWarehouse(businessUnitCode, true);

      assertNotFound(
          request(1L, 1L, businessUnitCode),
          "Active warehouse with business unit code " + businessUnitCode + " does not exist.");
    }
  }

  @Nested
  class DeleteAssignment {

    @Test
    void deletesAnExistingAssignment() {
      int assignmentId = postAssignment(1L, 1L, "MWH.001", 201);

      given().when().delete(PATH + "/{id}", assignmentId).then().statusCode(204);
      given().when().get(PATH + "/{id}", assignmentId).then().statusCode(404);
    }

    @Test
    void returnsNotFoundForUnknownAssignment() {
      given()
          .when()
          .delete(PATH + "/{id}", Long.MAX_VALUE)
          .then()
          .statusCode(404)
          .body("code", equalTo(404));
    }
  }

  private int postAssignment(
      long storeId, long productId, String warehouseBusinessUnitCode, int expectedStatus) {
    var response =
        given()
            .contentType("application/json")
            .body(request(storeId, productId, warehouseBusinessUnitCode))
            .when()
            .post(PATH)
            .then()
            .statusCode(expectedStatus);
    return expectedStatus == 201 ? response.extract().path("id") : -1;
  }

  private void assertBadRequest(Map<String, Object> body, String message) {
    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post(PATH)
        .then()
        .statusCode(400)
        .body("error", equalTo(message));
  }

  private void assertNotFound(Map<String, Object> body, String message) {
    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post(PATH)
        .then()
        .statusCode(404)
        .body("error", equalTo(message));
  }

  private Map<String, Object> request(
      long storeId, long productId, String warehouseBusinessUnitCode) {
    return Map.of(
        "storeId", storeId,
        "productId", productId,
        "warehouseBusinessUnitCode", warehouseBusinessUnitCode);
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
