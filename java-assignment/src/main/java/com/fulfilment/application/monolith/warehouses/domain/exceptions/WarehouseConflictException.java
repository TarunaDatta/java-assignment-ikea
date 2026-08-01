package com.fulfilment.application.monolith.warehouses.domain.exceptions;

public class WarehouseConflictException extends RuntimeException {

  public WarehouseConflictException(String message) {
    super(message);
  }
}
