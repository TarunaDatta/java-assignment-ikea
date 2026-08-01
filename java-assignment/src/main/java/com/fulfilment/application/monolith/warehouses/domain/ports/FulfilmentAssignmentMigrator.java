package com.fulfilment.application.monolith.warehouses.domain.ports;

public interface FulfilmentAssignmentMigrator {

  void moveAssignmentsToReplacement(String businessUnitCode);
}
