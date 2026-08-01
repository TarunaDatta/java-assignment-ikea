package com.fulfilment.application.monolith.fulfilments;

import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FulfilmentAssignmentService {

  static final int MAX_WAREHOUSES_PER_PRODUCT_AT_STORE = 2;
  static final int MAX_WAREHOUSES_PER_STORE = 3;
  static final int MAX_PRODUCTS_PER_WAREHOUSE = 5;

  private final FulfilmentAssignmentRepository assignmentRepository;
  private final ProductRepository productRepository;
  private final WarehouseRepository warehouseRepository;

  public FulfilmentAssignmentService(
      FulfilmentAssignmentRepository assignmentRepository,
      ProductRepository productRepository,
      WarehouseRepository warehouseRepository) {
    this.assignmentRepository = assignmentRepository;
    this.productRepository = productRepository;
    this.warehouseRepository = warehouseRepository;
  }

  @Transactional
  public FulfilmentAssignment create(FulfilmentAssignmentRequest request) {
    validateRequest(request);

    // Locking these owners serializes every counter affected by this assignment.
    Store store = Store.findById(request.storeId, LockModeType.PESSIMISTIC_WRITE);
    if (store == null) {
      throw notFound("Store", request.storeId);
    }

    Product product =
        productRepository.findById(request.productId, LockModeType.PESSIMISTIC_WRITE);
    if (product == null) {
      throw notFound("Product", request.productId);
    }

    DbWarehouse warehouse =
        warehouseRepository
            .find(
                "businessUnitCode = ?1 and archivedAt is null",
                request.warehouseBusinessUnitCode)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResult();
    if (warehouse == null) {
      throw new FulfilmentException(
          404,
          "Active warehouse with business unit code "
              + request.warehouseBusinessUnitCode
              + " does not exist.");
    }

    if (assignmentRepository.exists(store.id, product.id, warehouse.id)) {
      throw conflict("The fulfilment assignment already exists.");
    }
    if (assignmentRepository.countWarehousesForProductAtStore(store.id, product.id)
        >= MAX_WAREHOUSES_PER_PRODUCT_AT_STORE) {
      throw conflict("A product can be fulfilled by at most 2 warehouses per store.");
    }
    if (!assignmentRepository.warehouseAlreadyFulfilsStore(store.id, warehouse.id)
        && assignmentRepository.countWarehousesForStore(store.id) >= MAX_WAREHOUSES_PER_STORE) {
      throw conflict("A store can be fulfilled by at most 3 warehouses.");
    }
    if (!assignmentRepository.warehouseAlreadyStoresProduct(warehouse.id, product.id)
        && assignmentRepository.countProductsForWarehouse(warehouse.id)
            >= MAX_PRODUCTS_PER_WAREHOUSE) {
      throw conflict("A warehouse can store at most 5 types of products.");
    }

    var assignment = new FulfilmentAssignment();
    assignment.store = store;
    assignment.product = product;
    assignment.warehouse = warehouse;
    assignmentRepository.persistAndFlush(assignment);
    return assignment;
  }

  @Transactional
  public void delete(Long id) {
    FulfilmentAssignment assignment = assignmentRepository.findById(id);
    if (assignment == null) {
      throw notFound("Fulfilment assignment", id);
    }
    assignmentRepository.delete(assignment);
  }

  private void validateRequest(FulfilmentAssignmentRequest request) {
    if (request == null) {
      throw new FulfilmentException(400, "Request body is required.");
    }
    if (request.storeId == null) {
      throw new FulfilmentException(400, "Store id is required.");
    }
    if (request.productId == null) {
      throw new FulfilmentException(400, "Product id is required.");
    }
    if (request.warehouseBusinessUnitCode == null
        || request.warehouseBusinessUnitCode.isBlank()) {
      throw new FulfilmentException(400, "Warehouse business unit code is required.");
    }
  }

  private FulfilmentException notFound(String entity, Long id) {
    return new FulfilmentException(404, entity + " with id " + id + " does not exist.");
  }

  private FulfilmentException conflict(String message) {
    return new FulfilmentException(409, message);
  }
}
