package com.fulfilment.application.monolith.stores;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
class StoreResourceTest {

  private static final String TEST_NAME_PREFIX = "IT-";

  @InjectMock LegacyStoreManagerGateway legacyStoreManagerGateway;

  @BeforeEach
  void setUp() {
    reset(legacyStoreManagerGateway);
    deleteTestStores();
  }

  @AfterEach
  void tearDown() {
    deleteTestStores();
  }

  @Nested
  class GetStores {

    @Test
    void returnsStores() {
      String name = uniqueName("get-all");
      persistStore(name, 4);

      given()
          .when()
          .get("/store")
          .then()
          .statusCode(200)
          .body("name", hasItem(name));
    }

    @Test
    void returnsEmptyListWhenThereAreNoStores() {
      deleteAllStores();

      try {
        given().when().get("/store").then().statusCode(200).body("size()", equalTo(0));
      } finally {
        restoreSeedStores();
      }
    }

    @Test
    void returnsMultipleStores() {
      String firstName = uniqueName("get-first");
      String secondName = uniqueName("get-second");
      persistStore(firstName, 4);
      persistStore(secondName, 6);

      given()
          .when()
          .get("/store")
          .then()
          .statusCode(200)
          .body("name", hasItems(firstName, secondName));
    }
  }

  @Nested
  class GetStore {

    @Test
    void returnsExistingStore() {
      String name = uniqueName("get");
      long id = persistStore(name, 7);

      given()
          .when()
          .get("/store/{id}", id)
          .then()
          .statusCode(200)
          .body("id", equalTo((int) id))
          .body("name", equalTo(name))
          .body("quantityProductsInStock", equalTo(7));
    }

    @Test
    void returnsNotFoundForUnknownStore() {
      given()
          .when()
          .get("/store/{id}", Long.MAX_VALUE)
          .then()
          .statusCode(404)
          .body("code", equalTo(404));
    }
  }

  @Nested
  class CreateStore {

    @Test
    void createsStoreAndSynchronizesIt() {
      String name = uniqueName("create");

      given()
          .contentType("application/json")
          .body(storeRequest(name, 12))
          .when()
          .post("/store")
          .then()
          .statusCode(201)
          .body("id", notNullValue())
          .body("name", equalTo(name))
          .body("quantityProductsInStock", equalTo(12));

      verify(legacyStoreManagerGateway)
          .createStoreOnLegacySystem(
              argThat(
                  store ->
                      store.id != null
                          && name.equals(store.name)
                          && store.quantityProductsInStock == 12));
    }

    @Test
    void rejectsRequestContainingAnId() {
      given()
          .contentType("application/json")
          .body(Map.of("id", 123, "name", uniqueName("invalid-id"), "quantityProductsInStock", 1))
          .when()
          .post("/store")
          .then()
          .statusCode(422)
          .body("code", equalTo(422));

      verify(legacyStoreManagerGateway, never()).createStoreOnLegacySystem(argThat(store -> true));
    }

    @Test
    void doesNotSynchronizeWhenDatabaseCommitFails() {
      // TONSTAD is inserted by import.sql and Store.name has a unique constraint.
      given()
          .contentType("application/json")
          .body(storeRequest("TONSTAD", 99))
          .when()
          .post("/store")
          .then()
          .statusCode(500)
          .body("code", equalTo(500));

      verify(legacyStoreManagerGateway, never()).createStoreOnLegacySystem(argThat(store -> true));
    }
  }

  @Nested
  class UpdateStore {

    @Test
    void updatesStoreAndSynchronizesCommittedState() {
      long id = persistStore(uniqueName("put-before"), 3);
      String updatedName = uniqueName("put-after");

      given()
          .contentType("application/json")
          .body(storeRequest(updatedName, 15))
          .when()
          .put("/store/{id}", id)
          .then()
          .statusCode(200)
          .body("id", equalTo((int) id))
          .body("name", equalTo(updatedName))
          .body("quantityProductsInStock", equalTo(15));

      verify(legacyStoreManagerGateway)
          .updateStoreOnLegacySystem(
              argThat(
                  store ->
                      store.id.equals(id)
                          && updatedName.equals(store.name)
                          && store.quantityProductsInStock == 15));

      given()
          .when()
          .get("/store/{id}", id)
          .then()
          .statusCode(200)
          .body("name", equalTo(updatedName))
          .body("quantityProductsInStock", equalTo(15));
    }

    @Test
    void rejectsRequestWithoutName() {
      long id = persistStore(uniqueName("put-no-name"), 3);

      given()
          .contentType("application/json")
          .body(Map.of("quantityProductsInStock", 8))
          .when()
          .put("/store/{id}", id)
          .then()
          .statusCode(422)
          .body("code", equalTo(422));

      verify(legacyStoreManagerGateway, never()).updateStoreOnLegacySystem(argThat(store -> true));
    }

    @Test
    void returnsNotFoundForUnknownStore() {
      given()
          .contentType("application/json")
          .body(storeRequest(uniqueName("put-missing"), 8))
          .when()
          .put("/store/{id}", Long.MAX_VALUE)
          .then()
          .statusCode(404)
          .body("code", equalTo(404));

      verify(legacyStoreManagerGateway, never()).updateStoreOnLegacySystem(argThat(store -> true));
    }

    @Test
    void doesNotSynchronizeWhenDatabaseCommitFails() {
      String originalName = uniqueName("put-rollback");
      long id = persistStore(originalName, 3);

      given()
          .contentType("application/json")
          .body(storeRequest("TONSTAD", 18))
          .when()
          .put("/store/{id}", id)
          .then()
          .statusCode(500)
          .body("code", equalTo(500));

      verify(legacyStoreManagerGateway, never()).updateStoreOnLegacySystem(argThat(store -> true));

      given()
          .when()
          .get("/store/{id}", id)
          .then()
          .statusCode(200)
          .body("name", equalTo(originalName))
          .body("quantityProductsInStock", equalTo(3));
    }
  }

  @Nested
  class PatchStore {

    @Test
    void patchesStoreAndSynchronizesCommittedState() {
      long id = persistStore(uniqueName("patch-before"), 2);
      String updatedName = uniqueName("patch-after");

      given()
          .contentType("application/json")
          .body(storeRequest(updatedName, 9))
          .when()
          .patch("/store/{id}", id)
          .then()
          .statusCode(200)
          .body("id", equalTo((int) id))
          .body("name", equalTo(updatedName))
          .body("quantityProductsInStock", equalTo(9));

      verify(legacyStoreManagerGateway)
          .updateStoreOnLegacySystem(
              argThat(
                  store ->
                      store.id.equals(id)
                          && updatedName.equals(store.name)
                          && store.quantityProductsInStock == 9));
    }

    @Test
    void rejectsRequestWithoutName() {
      long id = persistStore(uniqueName("patch-no-name"), 2);

      given()
          .contentType("application/json")
          .body(Map.of("quantityProductsInStock", 9))
          .when()
          .patch("/store/{id}", id)
          .then()
          .statusCode(422)
          .body("code", equalTo(422));

      verify(legacyStoreManagerGateway, never()).updateStoreOnLegacySystem(argThat(store -> true));
    }

    @Test
    void returnsNotFoundForUnknownStore() {
      given()
          .contentType("application/json")
          .body(storeRequest(uniqueName("patch-missing"), 9))
          .when()
          .patch("/store/{id}", Long.MAX_VALUE)
          .then()
          .statusCode(404)
          .body("code", equalTo(404));

      verify(legacyStoreManagerGateway, never()).updateStoreOnLegacySystem(argThat(store -> true));
    }
  }

  @Nested
  class DeleteStore {

    @Test
    void deletesExistingStore() {
      long id = persistStore(uniqueName("delete"), 1);

      given().when().delete("/store/{id}", id).then().statusCode(204);

      given().when().get("/store/{id}", id).then().statusCode(404);
    }

    @Test
    void returnsNotFoundForUnknownStore() {
      given()
          .when()
          .delete("/store/{id}", Long.MAX_VALUE)
          .then()
          .statusCode(404)
          .body("code", equalTo(404));
    }
  }

  private Map<String, Object> storeRequest(String name, int quantityProductsInStock) {
    return Map.of("name", name, "quantityProductsInStock", quantityProductsInStock);
  }

  private long persistStore(String name, int quantityProductsInStock) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Store store = new Store(name);
              store.quantityProductsInStock = quantityProductsInStock;
              store.persistAndFlush();
              return store.id;
            });
  }

  private void deleteTestStores() {
    QuarkusTransaction.requiringNew()
        .run(() -> Store.delete("name like ?1", TEST_NAME_PREFIX + "%"));
  }

  private void deleteAllStores() {
    QuarkusTransaction.requiringNew().run(() -> Store.deleteAll());
  }

  private void restoreSeedStores() {
    persistStore("TONSTAD", 10);
    persistStore("KALLAX", 5);
    persistStore("BESTÅ", 3);
  }

  private String uniqueName(String operation) {
    return TEST_NAME_PREFIX + operation + "-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
