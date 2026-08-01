package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArchiveWarehouseUseCaseTest {

  private WarehouseStore warehouseStore;
  private ArchiveWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    warehouseStore = mock(WarehouseStore.class);
    useCase = new ArchiveWarehouseUseCase(warehouseStore);
  }

  @Nested
  class ArchiveWarehouse {

    @Test
    void archivesAnExistingWarehouse() {
      Warehouse warehouse = warehouse("MWH.001");
      when(warehouseStore.findByBusinessUnitCode("MWH.001")).thenReturn(warehouse);

      useCase.archive(warehouse);

      assertNotNull(warehouse.archivedAt);
      verify(warehouseStore).update(warehouse);
    }

    @Test
    void rejectsUnknownWarehouse() {
      Warehouse warehouse = warehouse("MWH.404");
      when(warehouseStore.findByBusinessUnitCode("MWH.404")).thenReturn(null);

      WarehouseValidationException exception =
          assertThrows(WarehouseValidationException.class, () -> useCase.archive(warehouse));

      assertEquals(
          "Warehouse with business unit code MWH.404 does not exist.", exception.getMessage());
      assertNull(warehouse.archivedAt);
      verify(warehouseStore, never()).update(warehouse);
    }
  }

  private Warehouse warehouse(String businessUnitCode) {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = businessUnitCode;
    return warehouse;
  }
}
