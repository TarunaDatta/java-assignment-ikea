package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fulfilment.application.monolith.fulfilments.FulfilmentAssignmentRepository;
import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.warehouse.api.beans.Warehouse;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WarehouseResourceTest {

  private static final String PATH = "/warehouse";
  private static final String TEST_CODE_PREFIX = "MWH.RESOURCE.";

  @Inject WarehouseResourceImpl warehouseResource;
  @Inject WarehouseRepository warehouseRepository;
  @Inject FulfilmentAssignmentRepository assignmentRepository;

  @BeforeEach
  @AfterEach
  void cleanTestData() {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              assignmentRepository.deleteAll();
              warehouseRepository.delete(
                  "businessUnitCode like ?1", TEST_CODE_PREFIX + "%");
            });
  }

  @Nested
  class ListWarehouses {

    @Test
    void returnsAllActiveWarehouses() {
      given()
          .when()
          .get(PATH)
          .then()
          .statusCode(200)
          .body(containsString("MWH.001"), containsString("MWH.012"));
    }
  }

  @Nested
  class GetWarehouse {

    @Test
    void returnsWarehouseById() {
      given()
          .when()
          .get(PATH + "/1")
          .then()
          .statusCode(200)
          .body("id", equalTo("1"))
          .body("businessUnitCode", equalTo("MWH.001"))
          .body("location", equalTo("ZWOLLE-001"))
          .body("capacity", equalTo(100))
          .body("stock", equalTo(10));
    }

    @Test
    void returnsNotFoundForUnknownWarehouse() {
      given().when().get(PATH + "/{id}", Long.MAX_VALUE).then().statusCode(404);
    }

    @Test
    void returnsNotFoundForInvalidWarehouseId() {
      given().when().get(PATH + "/invalid").then().statusCode(404);
    }
  }

  @Nested
  class CreateWarehouse {

    @Test
    void createsWarehouseAndChangesResponseStatusToCreated() {
      String businessUnitCode = TEST_CODE_PREFIX + "CREATE";

      given()
          .contentType(ContentType.JSON)
          .body(warehouseJson(businessUnitCode, "HELMOND-001", 40, 10))
          .when()
          .post(PATH)
          .then()
          .statusCode(201)
          .body("businessUnitCode", equalTo(businessUnitCode))
          .body("location", equalTo("HELMOND-001"))
          .body("capacity", equalTo(40))
          .body("stock", equalTo(10));
    }

    @Test
    void returnsBadRequestForInvalidWarehouse() {
      given()
          .contentType(ContentType.JSON)
          .body(warehouseJson("MWH.001", "EINDHOVEN-001", 20, 10))
          .when()
          .post(PATH)
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body("error", equalTo("A warehouse with business unit code MWH.001 already exists."));
    }

    @Test
    void returnsBadRequestForInvalidLocation() {
      given()
          .contentType(ContentType.JSON)
          .body(
              warehouseJson(
                  TEST_CODE_PREFIX + "INVALID-LOCATION", "UNKNOWN-001", 20, 10))
          .when()
          .post(PATH)
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body("error", equalTo("Location UNKNOWN-001 is not valid."));
    }

    @Test
    void returnsBadRequestWhenLocationCapacitySumIsExceeded() {
      given()
          .contentType(ContentType.JSON)
          .body(
              warehouseJson(
                  TEST_CODE_PREFIX + "CAPACITY-SUM", "AMSTERDAM-001", 51, 10))
          .when()
          .post(PATH)
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body(
              "error",
              equalTo(
                  "Warehouse capacity exceeds the maximum capacity for location AMSTERDAM-001."));
    }
  }

  @Nested
  class ArchiveWarehouse {

    @Test
    void archivesActiveWarehouse() {
      String businessUnitCode = TEST_CODE_PREFIX + "ARCHIVE";
      long id = createWarehouse(businessUnitCode, false);

      given().when().delete(PATH + "/{id}", id).then().statusCode(204);

      given().when().get(PATH).then().statusCode(200).body(not(containsString(businessUnitCode)));
    }

    @Test
    void returnsNotFoundForInvalidWarehouseId() {
      given().when().delete(PATH + "/invalid").then().statusCode(404);
    }

    @Test
    void returnsNotFoundForUnknownWarehouse() {
      given().when().delete(PATH + "/{id}", Long.MAX_VALUE).then().statusCode(404);
    }

    @Test
    void returnsNotFoundForAlreadyArchivedWarehouse() {
      long id = createWarehouse(TEST_CODE_PREFIX + "ALREADY-ARCHIVED", true);

      given().when().delete(PATH + "/{id}", id).then().statusCode(404);
    }
  }

  @Nested
  class ReplaceWarehouse {

    @Test
    void replacesActiveWarehouse() {
      String businessUnitCode = TEST_CODE_PREFIX + "REPLACE";
      createWarehouse(businessUnitCode, false);

      given()
          .contentType(ContentType.JSON)
          .body(warehouseJson(businessUnitCode, "EINDHOVEN-001", 20, 0))
          .when()
          .post(PATH + "/{businessUnitCode}/replacement", businessUnitCode)
          .then()
          .statusCode(200)
          .body("businessUnitCode", equalTo(businessUnitCode))
          .body("location", equalTo("EINDHOVEN-001"))
          .body("capacity", equalTo(20))
          .body("stock", equalTo(0));

      assertEquals(1L, countActiveWarehouses(businessUnitCode));
      assertEquals(1L, countArchivedWarehouses(businessUnitCode));
    }

    @Test
    void returnsNotFoundForUnknownBusinessUnitCode() {
      given()
          .contentType(ContentType.JSON)
          .body(warehouseJson("MWH.UNKNOWN", "EINDHOVEN-001", 20, 0))
          .when()
          .post(PATH + "/{businessUnitCode}/replacement", "MWH.UNKNOWN")
          .then()
          .statusCode(404);
    }

    @Test
    void rejectsBlankBusinessUnitCode() {
      NotFoundException exception =
          assertThrows(
              NotFoundException.class,
              () -> warehouseResource.replaceTheCurrentActiveWarehouse(" ", warehouseData()));

      assertEquals("Warehouse with business unit code   does not exist.", exception.getMessage());
    }

    @Test
    void rejectsNullBusinessUnitCode() {
      NotFoundException exception =
          assertThrows(
              NotFoundException.class,
              () -> warehouseResource.replaceTheCurrentActiveWarehouse(null, warehouseData()));

      assertEquals(
          "Warehouse with business unit code null does not exist.", exception.getMessage());
    }

    @Test
    void rejectsMissingWarehouseData() {
      String businessUnitCode = TEST_CODE_PREFIX + "NULL-DATA";
      createWarehouse(businessUnitCode, false);

      WarehouseValidationException exception =
          assertThrows(
              WarehouseValidationException.class,
              () -> warehouseResource.replaceTheCurrentActiveWarehouse(businessUnitCode, null));

      assertEquals("Warehouse data is required.", exception.getMessage());
    }
  }

  private Warehouse warehouseData() {
    Warehouse warehouse = new Warehouse();
    warehouse.setLocation("EINDHOVEN-001");
    warehouse.setCapacity(20);
    warehouse.setStock(0);
    return warehouse;
  }

  private String warehouseJson(
      String businessUnitCode, String location, int capacity, int stock) {
    return """
        {
          "businessUnitCode": "%s",
          "location": "%s",
          "capacity": %d,
          "stock": %d
        }
        """.formatted(businessUnitCode, location, capacity, stock);
  }

  private long createWarehouse(String businessUnitCode, boolean archived) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              var warehouse = new DbWarehouse();
              warehouse.businessUnitCode = businessUnitCode;
              warehouse.location = "EINDHOVEN-001";
              warehouse.capacity = 10;
              warehouse.stock = 0;
              warehouse.createdAt = LocalDateTime.now();
              warehouse.archivedAt = archived ? LocalDateTime.now() : null;
              warehouseRepository.persistAndFlush(warehouse);
              return warehouse.id;
            });
  }

  private long countActiveWarehouses(String businessUnitCode) {
    return QuarkusTransaction.requiringNew()
        .call(
            () ->
                warehouseRepository.count(
                    "businessUnitCode = ?1 and archivedAt is null", businessUnitCode));
  }

  private long countArchivedWarehouses(String businessUnitCode) {
    return QuarkusTransaction.requiringNew()
        .call(
            () ->
                warehouseRepository.count(
                    "businessUnitCode = ?1 and archivedAt is not null", businessUnitCode));
  }
}
