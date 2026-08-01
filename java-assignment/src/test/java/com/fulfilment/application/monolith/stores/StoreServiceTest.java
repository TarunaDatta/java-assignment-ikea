package com.fulfilment.application.monolith.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
class StoreServiceTest {

  private static final String TEST_NAME_PREFIX = "SERVICE-TEST-";

  @Inject StoreService storeService;

  @BeforeEach
  @AfterEach
  void deleteTestStores() {
    QuarkusTransaction.requiringNew()
        .run(() -> Store.delete("name like ?1", TEST_NAME_PREFIX + "%"));
  }

  @Nested
  class CreateStore {

    @Test
    void createsAndPersistsStore() {
      Store store = store(uniqueName("create"), 12);

      Store result = storeService.create(store);

      assertNotNull(result.id);
      Store persistedStore = findStore(result.id);
      assertEquals(store.name, persistedStore.name);
      assertEquals(12, persistedStore.quantityProductsInStock);
    }

    @Test
    void failsWhenStoreNameAlreadyExists() {
      String name = uniqueName("duplicate");
      storeService.create(store(name, 1));

      assertThrows(RuntimeException.class, () -> storeService.create(store(name, 2)));
      assertEquals(1L, countStoresNamed(name));
    }
  }

  @Nested
  class UpdateStore {

    @Test
    void updatesExistingStore() {
      long id = persistStore(uniqueName("update-before"), 3);
      Store update = store(uniqueName("update-after"), 15);

      Store result = storeService.update(id, update);

      assertEquals(id, result.id);
      Store persistedStore = findStore(id);
      assertEquals(update.name, persistedStore.name);
      assertEquals(15, persistedStore.quantityProductsInStock);
    }

    @Test
    void failsWhenStoreDoesNotExist() {
      assertStoreNotFound(
          Long.MAX_VALUE,
          () -> storeService.update(Long.MAX_VALUE, store(uniqueName("missing-update"), 8)));
    }
  }

  @Nested
  class PatchStore {

    @Test
    void patchesExistingStore() {
      long id = persistStore(uniqueName("patch-before"), 2);
      Store patch = store(uniqueName("patch-after"), 9);

      Store result = storeService.patch(id, patch);

      assertEquals(id, result.id);
      Store persistedStore = findStore(id);
      assertEquals(patch.name, persistedStore.name);
      assertEquals(9, persistedStore.quantityProductsInStock);
    }

    @Test
    void doesNotPatchEmptyFieldsOnExistingStore() {
      long id = persistStore(null, 0);
      Store patch = store(uniqueName("patch-empty"), 9);

      try {
        Store result = storeService.patch(id, patch);

        assertNull(result.name);
        assertEquals(0, result.quantityProductsInStock);
        Store persistedStore = findStore(id);
        assertNull(persistedStore.name);
        assertEquals(0, persistedStore.quantityProductsInStock);
      } finally {
        deleteStore(id);
      }
    }

    @Test
    void failsWhenStoreDoesNotExist() {
      assertStoreNotFound(
          Long.MAX_VALUE,
          () -> storeService.patch(Long.MAX_VALUE, store(uniqueName("missing-patch"), 9)));
    }
  }

  private void assertStoreNotFound(long id, Runnable operation) {
    WebApplicationException exception = assertThrows(WebApplicationException.class, operation::run);

    assertEquals(404, exception.getResponse().getStatus());
    assertEquals("Store with id of " + id + " does not exist.", exception.getMessage());
  }

  private Store store(String name, int quantityProductsInStock) {
    Store store = new Store(name);
    store.quantityProductsInStock = quantityProductsInStock;
    return store;
  }

  private long persistStore(String name, int quantityProductsInStock) {
    Store store = store(name, quantityProductsInStock);
    storeService.create(store);
    return store.id;
  }

  private Store findStore(long id) {
    return QuarkusTransaction.requiringNew().call(() -> Store.findById(id));
  }

  private long countStoresNamed(String name) {
    return QuarkusTransaction.requiringNew().call(() -> Store.count("name", name));
  }

  private void deleteStore(long id) {
    QuarkusTransaction.requiringNew().run(() -> Store.deleteById(id));
  }

  private String uniqueName(String operation) {
    return TEST_NAME_PREFIX + operation + "-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
