package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplaceWarehouseUseCaseTest {

  private WarehouseStore warehouseStore;
  private LocationResolver locationResolver;
  private ReplaceWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    warehouseStore = mock(WarehouseStore.class);
    locationResolver = mock(LocationResolver.class);
    useCase = new ReplaceWarehouseUseCase(warehouseStore, locationResolver);
  }

  @Test
  void archivesCurrentWarehouseBeforeCreatingItsReplacement() {
    Warehouse current = warehouse("MWH.001", "ZWOLLE-001", 100, 25);
    Warehouse replacement = warehouse("MWH.001", "EINDHOVEN-001", 40, 25);
    Warehouse other = warehouse("MWH.099", "EINDHOVEN-001", 20, 5);
    allowReplacement(current, "EINDHOVEN-001", 2, 60, List.of(current, other));

    useCase.replace(replacement);

    var persistenceOrder = inOrder(warehouseStore);
    persistenceOrder.verify(warehouseStore).update(current);
    persistenceOrder.verify(warehouseStore).create(replacement);
    assertNotNull(current.archivedAt);
    assertNotNull(replacement.createdAt);
  }

  @Test
  void excludesCurrentWarehouseFromLocationLimits() {
    Warehouse current = warehouse("MWH.001", "ZWOLLE-001", 100, 25);
    Warehouse replacement = warehouse("MWH.001", "ZWOLLE-001", 50, 25);
    allowReplacement(current, "ZWOLLE-001", 1, 50, List.of(current));

    useCase.replace(replacement);

    verify(warehouseStore).create(replacement);
  }

  @Test
  void rejectsUnknownWarehouse() {
    Warehouse replacement = warehouse("MWH.404", "EINDHOVEN-001", 40, 20);

    assertValidation(
        "Warehouse with business unit code MWH.404 does not exist.",
        () -> useCase.replace(replacement));

    verify(warehouseStore, never()).create(replacement);
  }

  @Test
  void rejectsCapacityThatCannotAccommodateCurrentStock() {
    Warehouse current = warehouse("MWH.001", "ZWOLLE-001", 100, 25);
    Warehouse replacement = warehouse("MWH.001", "EINDHOVEN-001", 20, 25);
    allowReplacement(current, "EINDHOVEN-001", 2, 60, List.of(current));

    assertValidation(
        "New warehouse capacity cannot accommodate the stock from the warehouse being replaced.",
        () -> useCase.replace(replacement));

    verify(warehouseStore, never()).update(current);
    verify(warehouseStore, never()).create(replacement);
  }

  @Test
  void rejectsStockThatDoesNotMatchCurrentWarehouse() {
    Warehouse current = warehouse("MWH.001", "ZWOLLE-001", 100, 25);
    Warehouse replacement = warehouse("MWH.001", "EINDHOVEN-001", 40, 20);
    allowReplacement(current, "EINDHOVEN-001", 2, 60, List.of(current));

    assertValidation(
        "New warehouse stock must match the stock of the warehouse being replaced.",
        () -> useCase.replace(replacement));

    verify(warehouseStore, never()).update(current);
    verify(warehouseStore, never()).create(replacement);
  }

  @Test
  void rejectsInvalidLocation() {
    Warehouse current = warehouse("MWH.001", "ZWOLLE-001", 100, 25);
    Warehouse replacement = warehouse("MWH.001", "UNKNOWN-001", 40, 25);
    when(warehouseStore.findByBusinessUnitCode("MWH.001")).thenReturn(current);

    assertValidation(
        "Location UNKNOWN-001 is not valid.", () -> useCase.replace(replacement));

    verify(warehouseStore, never()).update(current);
    verify(warehouseStore, never()).create(replacement);
  }

  private void allowReplacement(
      Warehouse current,
      String locationIdentifier,
      int warehouseLimit,
      int capacityLimit,
      List<Warehouse> warehouses) {
    when(warehouseStore.findByBusinessUnitCode(current.businessUnitCode)).thenReturn(current);
    when(locationResolver.resolveByIdentifier(locationIdentifier))
        .thenReturn(new Location(locationIdentifier, warehouseLimit, capacityLimit));
    when(warehouseStore.getAll()).thenReturn(warehouses);
  }

  private Warehouse warehouse(String businessUnitCode, String location, int capacity, int stock) {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = businessUnitCode;
    warehouse.location = location;
    warehouse.capacity = capacity;
    warehouse.stock = stock;
    return warehouse;
  }

  private void assertValidation(String message, Runnable operation) {
    WarehouseValidationException exception =
        assertThrows(WarehouseValidationException.class, operation::run);
    assertEquals(message, exception.getMessage());
  }
}
