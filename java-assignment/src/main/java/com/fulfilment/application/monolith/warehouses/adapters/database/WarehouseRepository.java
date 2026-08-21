package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

  @Override
  public void lockCreationConstraints(String businessUnitCode, String location) {
    // Transaction-scoped PostgreSQL advisory locks also work when no warehouse row exists yet.
    // Namespacing the keys and always locking code before location avoids lock-order cycles.
    acquireTransactionLock("warehouse-code:" + businessUnitCode);
    acquireTransactionLock("warehouse-location:" + location);
  }

  private void acquireTransactionLock(String key) {
    getEntityManager()
        .createNativeQuery("select pg_advisory_xact_lock(hashtextextended(?1, 0))", Object.class)
        .setParameter(1, key)
        .getSingleResult();
  }

  @Override
  public List<Warehouse> getAll() {
    return find("archivedAt is null").stream().map(DbWarehouse::toWarehouse).toList();
  }

  @Override
  public void create(Warehouse warehouse) {
    var dbWarehouse = new DbWarehouse();
    dbWarehouse.businessUnitCode = warehouse.businessUnitCode;
    dbWarehouse.location = warehouse.location;
    dbWarehouse.capacity = warehouse.capacity;
    dbWarehouse.stock = warehouse.stock;
    dbWarehouse.createdAt = warehouse.createdAt;
    dbWarehouse.archivedAt = warehouse.archivedAt;
    persistAndFlush(dbWarehouse);
    warehouse.id = dbWarehouse.id;
  }

  @Override
  public void update(Warehouse warehouse) {
    update(
        "location = ?1, capacity = ?2, stock = ?3, createdAt = ?4, archivedAt = ?5 "
            + "where businessUnitCode = ?6 and archivedAt is null",
        warehouse.location,
        warehouse.capacity,
        warehouse.stock,
        warehouse.createdAt,
        warehouse.archivedAt,
        warehouse.businessUnitCode);
  }

  @Override
  public void remove(Warehouse warehouse) {
    delete("businessUnitCode = ?1 and archivedAt is null", warehouse.businessUnitCode);
  }

  @Override
  public Warehouse findByBusinessUnitCode(String buCode) {
    DbWarehouse warehouse =
        find("businessUnitCode = ?1 and archivedAt is null", buCode).firstResult();
    return warehouse == null ? null : warehouse.toWarehouse();
  }
}
