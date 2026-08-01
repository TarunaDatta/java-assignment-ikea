package com.fulfilment.application.monolith.fulfilments;

public class FulfilmentException extends RuntimeException {
  private final int status;

  public FulfilmentException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
