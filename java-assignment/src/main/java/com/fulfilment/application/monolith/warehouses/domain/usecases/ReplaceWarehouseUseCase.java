package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class ReplaceWarehouseUseCase implements ReplaceWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  public ReplaceWarehouseUseCase(
      WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  @Override
  public void replace(Warehouse newWarehouse) {
    validateWarehouseData(newWarehouse);
    validateBusinessUnitCode(newWarehouse.businessUnitCode);

    Warehouse currentWarehouse = findCurrentWarehouse(newWarehouse.businessUnitCode);
    Location location = resolveLocation(newWarehouse.location);
    List<Warehouse> otherWarehousesAtLocation =
        findOtherActiveWarehousesAt(location, newWarehouse.businessUnitCode);

    validateWarehouseLimit(newWarehouse.location, location, otherWarehousesAtLocation);
    validateCapacity(newWarehouse.capacity);
    validateCapacityAccommodation(newWarehouse.capacity, currentWarehouse.stock);
    validateStockMatching(newWarehouse.stock, currentWarehouse.stock);
    validateStock(newWarehouse.stock, newWarehouse.capacity);
    validateLocationCapacity(newWarehouse, location, otherWarehousesAtLocation);

    archiveAndCreate(currentWarehouse, newWarehouse);
  }

  private void validateWarehouseData(Warehouse warehouse) {
    if (warehouse == null) {
      throw new WarehouseValidationException("Warehouse data is required.");
    }
  }

  private void validateBusinessUnitCode(String businessUnitCode) {
    if (businessUnitCode == null || businessUnitCode.isBlank()) {
      throw new WarehouseValidationException("Business unit code is required.");
    }
  }

  private Warehouse findCurrentWarehouse(String businessUnitCode) {
    Warehouse currentWarehouse = warehouseStore.findByBusinessUnitCode(businessUnitCode);
    if (currentWarehouse == null) {
      throw new WarehouseValidationException(
          "Warehouse with business unit code " + businessUnitCode + " does not exist.");
    }
    return currentWarehouse;
  }

  private Location resolveLocation(String locationIdentifier) {
    Location location = locationResolver.resolveByIdentifier(locationIdentifier);
    if (location == null) {
      throw new WarehouseValidationException(
          "Location " + locationIdentifier + " is not valid.");
    }
    return location;
  }

  private List<Warehouse> findOtherActiveWarehousesAt(
      Location location, String businessUnitCode) {
    return warehouseStore.getAll().stream()
        .filter(existing -> existing.archivedAt == null)
        .filter(existing -> !businessUnitCode.equals(existing.businessUnitCode))
        .filter(existing -> location.identification.equals(existing.location))
        .toList();
  }

  private void validateWarehouseLimit(
      String locationIdentifier, Location location, List<Warehouse> otherWarehousesAtLocation) {
    if (otherWarehousesAtLocation.size() >= location.maxNumberOfWarehouses) {
      throw new WarehouseValidationException(
          "The maximum number of warehouses at location "
              + locationIdentifier
              + " has been reached.");
    }
  }

  private void validateCapacity(Integer capacity) {
    if (capacity == null || capacity <= 0) {
      throw new WarehouseValidationException("Warehouse capacity must be greater than zero.");
    }
  }

  private void validateCapacityAccommodation(Integer newCapacity, Integer currentStock) {
    if (currentStock != null && newCapacity < currentStock) {
      throw new WarehouseValidationException(
          "New warehouse capacity cannot accommodate the stock from the warehouse being replaced.");
    }
  }

  private void validateStockMatching(Integer newStock, Integer currentStock) {
    if (!Objects.equals(newStock, currentStock)) {
      throw new WarehouseValidationException(
          "New warehouse stock must match the stock of the warehouse being replaced.");
    }
  }

  private void validateStock(Integer stock, Integer capacity) {
    if (stock == null || stock < 0) {
      throw new WarehouseValidationException("Warehouse stock cannot be negative.");
    }
    if (stock > capacity) {
      throw new WarehouseValidationException("Warehouse capacity cannot be lower than its stock.");
    }
  }

  private void validateLocationCapacity(
      Warehouse newWarehouse,
      Location location,
      List<Warehouse> otherWarehousesAtLocation) {
    int allocatedCapacity =
        otherWarehousesAtLocation.stream()
            .map(existing -> existing.capacity)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
    if (allocatedCapacity + newWarehouse.capacity > location.maxCapacity) {
      throw new WarehouseValidationException(
          "Warehouse capacity exceeds the maximum capacity for location "
              + newWarehouse.location
              + ".");
    }
  }

  private void archiveAndCreate(Warehouse currentWarehouse, Warehouse newWarehouse) {
    LocalDateTime replacementTime = LocalDateTime.now();
    currentWarehouse.archivedAt = replacementTime;
    newWarehouse.archivedAt = null;
    if (newWarehouse.createdAt == null) {
      newWarehouse.createdAt = replacementTime;
    }

    warehouseStore.update(currentWarehouse);
    warehouseStore.create(newWarehouse);
  }
}
