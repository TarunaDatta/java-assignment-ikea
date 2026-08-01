package com.fulfilment.application.monolith.fulfilments;

public class FulfilmentAssignmentResponse {
  public Long id;
  public Long storeId;
  public Long productId;
  public Long warehouseId;
  public String warehouseBusinessUnitCode;

  public static FulfilmentAssignmentResponse from(FulfilmentAssignment assignment) {
    var response = new FulfilmentAssignmentResponse();
    response.id = assignment.id;
    response.storeId = assignment.store.id;
    response.productId = assignment.product.id;
    response.warehouseId = assignment.warehouse.id;
    response.warehouseBusinessUnitCode = assignment.warehouse.businessUnitCode;
    return response;
  }
}
