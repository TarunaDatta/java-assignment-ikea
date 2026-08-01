package com.fulfilment.application.monolith.warehouses.domain.exceptions;

public class WarehouseValidationException extends RuntimeException {

  public WarehouseValidationException(String message) {
    super(message);
  }
}
