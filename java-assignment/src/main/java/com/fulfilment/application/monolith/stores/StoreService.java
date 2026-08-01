package com.fulfilment.application.monolith.stores;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class StoreService {

  @Transactional
  public Store create(Store store) {
    store.persist();
    return store;
  }

  @Transactional
  public Store update(Long id, Store updatedStore) {
    Store entity = findById(id);

    entity.name = updatedStore.name;
    entity.quantityProductsInStock = updatedStore.quantityProductsInStock;

    return entity;
  }

  @Transactional
  public Store patch(Long id, Store updatedStore) {
    Store entity = findById(id);

    if (entity.name != null) {
      entity.name = updatedStore.name;
    }

    if (entity.quantityProductsInStock != 0) {
      entity.quantityProductsInStock = updatedStore.quantityProductsInStock;
    }

    return entity;
  }

  private Store findById(Long id) {
    Store entity = Store.findById(id);
    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }
    return entity;
  }
}
