package com.fulfilment.application.monolith.fulfilments;

import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.FulfilmentAssignmentMigrator;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class FulfilmentAssignmentRepository
    implements PanacheRepository<FulfilmentAssignment>, FulfilmentAssignmentMigrator {

  @Inject EntityManager entityManager;

  public boolean exists(Long storeId, Long productId, Long warehouseId) {
    return count(
            "store.id = ?1 and product.id = ?2 and warehouse.id = ?3",
            storeId,
            productId,
            warehouseId)
        > 0;
  }

  public long countWarehousesForProductAtStore(Long storeId, Long productId) {
    return entityManager
        .createQuery(
            "select count(distinct assignment.warehouse.id) "
                + "from FulfilmentAssignment assignment "
                + "where assignment.store.id = :storeId "
                + "and assignment.product.id = :productId",
            Long.class)
        .setParameter("storeId", storeId)
        .setParameter("productId", productId)
        .getSingleResult();
  }

  public long countWarehousesForStore(Long storeId) {
    return entityManager
        .createQuery(
            "select count(distinct assignment.warehouse.id) "
                + "from FulfilmentAssignment assignment where assignment.store.id = :storeId",
            Long.class)
        .setParameter("storeId", storeId)
        .getSingleResult();
  }

  public boolean warehouseAlreadyFulfilsStore(Long storeId, Long warehouseId) {
    return count("store.id = ?1 and warehouse.id = ?2", storeId, warehouseId) > 0;
  }

  public long countProductsForWarehouse(Long warehouseId) {
    return entityManager
        .createQuery(
            "select count(distinct assignment.product.id) "
                + "from FulfilmentAssignment assignment where assignment.warehouse.id = :warehouseId",
            Long.class)
        .setParameter("warehouseId", warehouseId)
        .getSingleResult();
  }

  public boolean warehouseAlreadyStoresProduct(Long warehouseId, Long productId) {
    return count("warehouse.id = ?1 and product.id = ?2", warehouseId, productId) > 0;
  }

  public long countByStore(Long storeId) {
    return count("store.id", storeId);
  }

  public long countByProduct(Long productId) {
    return count("product.id", productId);
  }

  public long countByWarehouse(Long warehouseId) {
    return count("warehouse.id", warehouseId);
  }

  public List<FulfilmentAssignment> listForStore(Long storeId) {
    return list("store.id", Sort.by("id"), storeId);
  }

  public List<FulfilmentAssignment> listOrdered() {
    return listAll(Sort.by("id"));
  }

  @Override
  public void moveAssignmentsToReplacement(String businessUnitCode) {
    DbWarehouse replacement =
        entityManager
            .createQuery(
                "from DbWarehouse warehouse "
                    + "where warehouse.businessUnitCode = :businessUnitCode "
                    + "and warehouse.archivedAt is null",
                DbWarehouse.class)
            .setParameter("businessUnitCode", businessUnitCode)
            .getSingleResult();

    list(
            "warehouse.businessUnitCode = ?1 and warehouse.id <> ?2",
            businessUnitCode,
            replacement.id)
        .forEach(assignment -> assignment.warehouse = replacement);
  }
}
