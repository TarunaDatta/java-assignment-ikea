package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
public class WarehouseEndpointIT {

  private static final String PATH = "/warehouse";

  @Test
  void packagedApplicationListsWarehouses() {
    given()
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body(containsString("MWH.001"), containsString("MWH.012"));
  }

  @Test
  void packagedApplicationCreatesWarehouse() {
    given()
        .contentType(ContentType.JSON)
        .body(warehouseJson("MWH.IT.100", "HELMOND-001", 40, 10))
        .when()
        .post(PATH)
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("businessUnitCode", equalTo("MWH.IT.100"));
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
