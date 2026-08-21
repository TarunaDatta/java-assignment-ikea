package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseConflictException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class CreateWarehouseUseCase implements CreateWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  public CreateWarehouseUseCase(
      WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  @Override
  public void create(Warehouse warehouse) {
    validateWarehouseData(warehouse);
    validateBusinessUnitCodeIsPresent(warehouse.businessUnitCode);
    warehouseStore.lockCreationConstraints(warehouse.businessUnitCode, warehouse.location);
    validateBusinessUnitCodeIsAvailable(warehouse.businessUnitCode);

    Location location = resolveLocation(warehouse.location);
    List<Warehouse> warehousesAtLocation = findActiveWarehousesAt(location);

    validateWarehouseLimit(warehouse.location, location, warehousesAtLocation);
    validateCapacityAndStock(warehouse, location, warehousesAtLocation);

    warehouseStore.create(warehouse);
  }

  private void validateWarehouseData(Warehouse warehouse) {
    if (warehouse == null) {
      throw new WarehouseValidationException("Warehouse data is required.");
    }
  }

  private void validateBusinessUnitCodeIsPresent(String businessUnitCode) {
    if (businessUnitCode == null || businessUnitCode.isBlank()) {
      throw new WarehouseValidationException("Business unit code is required.");
    }
  }

  private void validateBusinessUnitCodeIsAvailable(String businessUnitCode) {
    if (warehouseStore.findByBusinessUnitCode(businessUnitCode) != null) {
      throw new WarehouseConflictException(
          "A warehouse with business unit code " + businessUnitCode + " already exists.");
    }
  }

  private Location resolveLocation(String locationIdentifier) {
    Location location = locationResolver.resolveByIdentifier(locationIdentifier);
    if (location == null) {
      throw new WarehouseValidationException(
          "Location " + locationIdentifier + " is not valid.");
    }
    return location;
  }

  private List<Warehouse> findActiveWarehousesAt(Location location) {
    return warehouseStore.getAll().stream()
        .filter(existing -> existing.archivedAt == null)
        .filter(existing -> location.identification.equals(existing.location))
        .toList();
  }

  private void validateWarehouseLimit(
      String locationIdentifier, Location location, List<Warehouse> warehousesAtLocation) {
    if (warehousesAtLocation.size() >= location.maxNumberOfWarehouses) {
      throw new WarehouseValidationException(
          "The maximum number of warehouses at location "
              + locationIdentifier
              + " has been reached.");
    }
  }

  private void validateCapacityAndStock(
      Warehouse warehouse, Location location, List<Warehouse> warehousesAtLocation) {
    if (warehouse.capacity == null || warehouse.capacity <= 0) {
      throw new WarehouseValidationException("Warehouse capacity must be greater than zero.");
    }
    int allocatedCapacity =
        warehousesAtLocation.stream()
            .map(existing -> existing.capacity)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
    if (allocatedCapacity + warehouse.capacity > location.maxCapacity) {
      throw new WarehouseValidationException(
          "Warehouse capacity exceeds the maximum capacity for location "
              + warehouse.location
              + ".");
    }
    if (warehouse.stock == null || warehouse.stock < 0) {
      throw new WarehouseValidationException("Warehouse stock cannot be negative.");
    }
    if (warehouse.stock > warehouse.capacity) {
      throw new WarehouseValidationException("Warehouse capacity cannot be lower than its stock.");
    }
  }
}
