package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
public class WarehouseEndpointIT {

  private static final String PATH = "/warehouse";

  @Nested
  class ListWarehouses {

    @Test
    void returnsAllWarehouses() {
      given()
          .when()
          .get(PATH)
          .then()
          .statusCode(200)
          .body(containsString("MWH.001"), containsString("MWH.012"), containsString("MWH.023"));
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
      given().when().get(PATH + "/999999").then().statusCode(404);
    }

    @Test
    void returnsNotFoundForInvalidWarehouseId() {
      given().when().get(PATH + "/invalid").then().statusCode(404);
    }
  }

  @Nested
  class CreateWarehouse {

    @Test
    void returnsCreatedWarehouseWith201() {
      given()
          .contentType(ContentType.JSON)
          .body(warehouseJson("MWH.IT.100", "HELMOND-001", 40, 10))
          .when()
          .post(PATH)
          .then()
          .statusCode(201)
          .body("businessUnitCode", equalTo("MWH.IT.100"))
          .body("location", equalTo("HELMOND-001"))
          .body("capacity", equalTo(40))
          .body("stock", equalTo(10));
    }

    @Test
    void returnsBadRequestForDuplicateBusinessUnitCode() {
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
    void returnsBadRequestForUnknownLocation() {
      given()
          .contentType(ContentType.JSON)
          .body(warehouseJson("MWH.IT.101", "UNKNOWN-001", 20, 10))
          .when()
          .post(PATH)
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body("error", equalTo("Location UNKNOWN-001 is not valid."));
    }

    @Test
    void returnsBadRequestWhenLocationWarehouseLimitIsReached() {
      given()
          .contentType(ContentType.JSON)
          .body(warehouseJson("MWH.IT.102", "ZWOLLE-001", 20, 10))
          .when()
          .post(PATH)
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body(
              "error",
              equalTo(
                  "The maximum number of warehouses at location ZWOLLE-001 has been reached."));
    }

    @Test
    void returnsBadRequestWhenLocationCapacityIsExceeded() {
      given()
          .contentType(ContentType.JSON)
          .body(warehouseJson("MWH.IT.103", "AMSTERDAM-001", 51, 10))
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

    @Test
    void returnsBadRequestWhenStockExceedsWarehouseCapacity() {
      given()
          .contentType(ContentType.JSON)
          .body(warehouseJson("MWH.IT.104", "EINDHOVEN-001", 20, 21))
          .when()
          .post(PATH)
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body("error", equalTo("Warehouse capacity cannot be lower than its stock."));
    }
  }

  @Nested
  class ArchiveWarehouse {

    @Test
    void archivesWarehouse() {
      // Archive endpoint coverage will be enabled when its implementation is completed.
    }
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
}
