package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.fulfilments.FulfilmentAssignmentRepository;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseConflictException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.Map;

@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  @Inject private WarehouseRepository warehouseRepository;
  @Inject private FulfilmentAssignmentRepository fulfilmentAssignmentRepository;
  @Inject private ArchiveWarehouseOperation archiveWarehouseOperation;
  @Inject private CreateWarehouseOperation createWarehouseOperation;
  @Inject private ReplaceWarehouseOperation replaceWarehouseOperation;

  @Override
  public List<Warehouse> listAllWarehousesUnits() {
    return warehouseRepository.getAll().stream().map(this::toWarehouseResponse).toList();
  }

  @Override
  @Transactional
  public Warehouse createANewWarehouseUnit(@NotNull Warehouse data) {
    var warehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    warehouse.businessUnitCode = data.getBusinessUnitCode();
    warehouse.location = data.getLocation();
    warehouse.capacity = data.getCapacity();
    warehouse.stock = data.getStock();
    warehouse.createdAt = java.time.LocalDateTime.now();

    createWarehouseOperation.create(warehouse);
    return toWarehouseResponse(warehouse);
  }

  @Override
  public Warehouse getAWarehouseUnitByID(String id) {
    final Long warehouseId;
    try {
      warehouseId = Long.valueOf(id);
    } catch (NumberFormatException exception) {
      throw new NotFoundException("Warehouse with id " + id + " does not exist.");
    }

    var warehouse =
        warehouseRepository
            .find("id = ?1 and archivedAt is null", warehouseId)
            .firstResult();
    if (warehouse == null) {
      throw new NotFoundException("Warehouse with id " + id + " does not exist.");
    }

    return toWarehouseResponse(warehouse.toWarehouse());
  }

  @Override
  @Transactional
  public void archiveAWarehouseUnitByID(String id) {
    final Long warehouseId;
    try {
      warehouseId = Long.valueOf(id);
    } catch (NumberFormatException exception) {
      throw new NotFoundException("Warehouse with id " + id + " does not exist.");
    }

    var warehouse =
        warehouseRepository.findById(warehouseId, LockModeType.PESSIMISTIC_WRITE);
    if (warehouse == null || warehouse.archivedAt != null) {
      throw new NotFoundException("Warehouse with id " + id + " does not exist.");
    }
    if (fulfilmentAssignmentRepository.countByWarehouse(warehouse.id) > 0) {
      throw new WarehouseConflictException(
          "Warehouse cannot be archived while it has active fulfilment assignments.");
    }

    archiveWarehouseOperation.archive(warehouse.toWarehouse());
  }

  @Override
  @Transactional
  public Warehouse replaceTheCurrentActiveWarehouse(
      String businessUnitCode, @NotNull Warehouse data) {
    if (businessUnitCode == null || businessUnitCode.isBlank()) {
      throw new NotFoundException(
          "Warehouse with business unit code " + businessUnitCode + " does not exist.");
    }
    if (data == null) {
      throw new WarehouseValidationException("Warehouse data is required.");
    }

    // Keep the same lock order as creation: logical code, logical location, then entity row.
    // This prevents a create/replacement deadlock while serializing location-capacity checks.
    warehouseRepository.lockWarehouseConstraints(businessUnitCode, data.getLocation());
    var currentWarehouse =
        warehouseRepository
            .find("businessUnitCode = ?1 and archivedAt is null", businessUnitCode)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResult();
    if (currentWarehouse == null) {
      throw new NotFoundException(
          "Warehouse with business unit code " + businessUnitCode + " does not exist.");
    }

    var warehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    warehouse.businessUnitCode = businessUnitCode;
    warehouse.location = data.getLocation();
    warehouse.capacity = data.getCapacity();
    warehouse.stock = data.getStock();
    warehouse.createdAt = java.time.LocalDateTime.now();

    replaceWarehouseOperation.replace(warehouse);
    return toWarehouseResponse(warehouse);
  }

  private Warehouse toWarehouseResponse(
      com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {
    var response = new Warehouse();
    if (warehouse.id != null) {
      response.setId(warehouse.id.toString());
    }
    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);

    return response;
  }

  @Provider
  public static class ValidationExceptionMapper
      implements ExceptionMapper<WarehouseValidationException> {

    @Override
    public Response toResponse(WarehouseValidationException exception) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("code", 400, "error", exception.getMessage()))
          .build();
    }
  }

  @Provider
  public static class ConflictExceptionMapper
      implements ExceptionMapper<WarehouseConflictException> {

    @Override
    public Response toResponse(WarehouseConflictException exception) {
      return Response.status(Response.Status.CONFLICT)
          .entity(Map.of("code", 409, "error", exception.getMessage()))
          .build();
    }
  }

  @Provider
  public static class CreateResponseStatusFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
      if ("POST".equals(request.getMethod())
          && request.getUriInfo().getPathSegments().size() == 1
          && "warehouse".equals(request.getUriInfo().getPathSegments().get(0).getPath())
          && response.getStatus() == Response.Status.OK.getStatusCode()) {
        response.setStatus(Response.Status.CREATED.getStatusCode());
      }
    }
  }
}
