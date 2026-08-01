package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateWarehouseUseCaseTest {

  private WarehouseStore warehouseStore;
  private LocationResolver locationResolver;
  private CreateWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    warehouseStore = mock(WarehouseStore.class);
    locationResolver = mock(LocationResolver.class);
    useCase = new CreateWarehouseUseCase(warehouseStore, locationResolver);
  }

  @Test
  void createsAValidWarehouse() {
    Warehouse warehouse = warehouse("MWH.100", "EINDHOVEN-001", 40, 20);
    allowLocation("EINDHOVEN-001", 2, 70, List.of());

    useCase.create(warehouse);

    verify(warehouseStore).create(warehouse);
  }

  @Test
  void rejectsMissingWarehouseData() {
    assertValidation("Warehouse data is required.", () -> useCase.create(null));

    verify(warehouseStore, never()).create(null);
  }

  @Test
  void rejectsMissingBusinessUnitCode() {
    Warehouse warehouse = warehouse(" ", "EINDHOVEN-001", 40, 20);

    assertValidation("Business unit code is required.", () -> useCase.create(warehouse));

    verify(warehouseStore, never()).create(warehouse);
  }

  @Test
  void rejectsDuplicateBusinessUnitCode() {
    Warehouse warehouse = warehouse("MWH.001", "EINDHOVEN-001", 40, 20);
    when(warehouseStore.findByBusinessUnitCode("MWH.001")).thenReturn(new Warehouse());

    assertValidation(
        "A warehouse with business unit code MWH.001 already exists.",
        () -> useCase.create(warehouse));

    verify(warehouseStore, never()).create(warehouse);
  }

  @Test
  void rejectsUnknownLocation() {
    Warehouse warehouse = warehouse("MWH.100", "UNKNOWN-001", 40, 20);

    assertValidation("Location UNKNOWN-001 is not valid.", () -> useCase.create(warehouse));

    verify(warehouseStore, never()).create(warehouse);
  }

  @Test
  void rejectsLocationAtItsWarehouseLimit() {
    Warehouse warehouse = warehouse("MWH.100", "ZWOLLE-001", 20, 10);
    Warehouse existing = warehouse("MWH.001", "ZWOLLE-001", 20, 5);
    allowLocation("ZWOLLE-001", 1, 40, List.of(existing));

    assertValidation(
        "The maximum number of warehouses at location ZWOLLE-001 has been reached.",
        () -> useCase.create(warehouse));

    verify(warehouseStore, never()).create(warehouse);
  }

  @Test
  void rejectsCapacityAboveRemainingLocationCapacity() {
    Warehouse warehouse = warehouse("MWH.100", "AMSTERDAM-001", 25, 10);
    Warehouse existing = warehouse("MWH.012", "AMSTERDAM-001", 30, 5);
    allowLocation("AMSTERDAM-001", 5, 50, List.of(existing));

    assertValidation(
        "Warehouse capacity exceeds the maximum capacity for location AMSTERDAM-001.",
        () -> useCase.create(warehouse));

    verify(warehouseStore, never()).create(warehouse);
  }

  @Test
  void ignoresArchivedWarehousesWhenCheckingLocationLimits() {
    Warehouse warehouse = warehouse("MWH.100", "HELMOND-001", 40, 10);
    Warehouse archived = warehouse("MWH.099", "HELMOND-001", 40, 10);
    archived.archivedAt = LocalDateTime.now();
    allowLocation("HELMOND-001", 1, 45, List.of(archived));

    useCase.create(warehouse);

    verify(warehouseStore).create(warehouse);
  }

  @Test
  void rejectsNonPositiveCapacity() {
    Warehouse warehouse = warehouse("MWH.100", "EINDHOVEN-001", 0, 0);
    allowLocation("EINDHOVEN-001", 2, 70, List.of());

    assertValidation(
        "Warehouse capacity must be greater than zero.", () -> useCase.create(warehouse));
  }

  @Test
  void rejectsNegativeStock() {
    Warehouse warehouse = warehouse("MWH.100", "EINDHOVEN-001", 40, -1);
    allowLocation("EINDHOVEN-001", 2, 70, List.of());

    assertValidation("Warehouse stock cannot be negative.", () -> useCase.create(warehouse));
  }

  @Test
  void rejectsStockAboveWarehouseCapacity() {
    Warehouse warehouse = warehouse("MWH.100", "EINDHOVEN-001", 20, 21);
    allowLocation("EINDHOVEN-001", 2, 70, List.of());

    assertValidation(
        "Warehouse capacity cannot be lower than its stock.", () -> useCase.create(warehouse));
  }

  private void allowLocation(
      String identifier, int warehouseLimit, int capacityLimit, List<Warehouse> warehouses) {
    when(locationResolver.resolveByIdentifier(identifier))
        .thenReturn(new Location(identifier, warehouseLimit, capacityLimit));
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
