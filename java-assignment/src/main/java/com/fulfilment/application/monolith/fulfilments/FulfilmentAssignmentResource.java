package com.fulfilment.application.monolith.fulfilments;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.Map;

@Path("fulfilment-assignments")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class FulfilmentAssignmentResource {

  @Inject FulfilmentAssignmentRepository assignmentRepository;
  @Inject FulfilmentAssignmentService assignmentService;

  @GET
  @Transactional
  public List<FulfilmentAssignmentResponse> list(@QueryParam("storeId") Long storeId) {
    var assignments =
        storeId == null
            ? assignmentRepository.listOrdered()
            : assignmentRepository.listForStore(storeId);
    return assignments.stream().map(FulfilmentAssignmentResponse::from).toList();
  }

  @GET
  @Path("{id}")
  @Transactional
  public FulfilmentAssignmentResponse get(@PathParam("id") Long id) {
    FulfilmentAssignment assignment = assignmentRepository.findById(id);
    if (assignment == null) {
      throw new FulfilmentException(404, "Fulfilment assignment with id " + id + " does not exist.");
    }
    return FulfilmentAssignmentResponse.from(assignment);
  }

  @POST
  public Response create(FulfilmentAssignmentRequest request) {
    FulfilmentAssignment assignment = assignmentService.create(request);
    return Response.status(Response.Status.CREATED)
        .entity(FulfilmentAssignmentResponse.from(assignment))
        .build();
  }

  @DELETE
  @Path("{id}")
  public Response delete(@PathParam("id") Long id) {
    assignmentService.delete(id);
    return Response.noContent().build();
  }

  @Provider
  public static class FulfilmentExceptionMapper implements ExceptionMapper<FulfilmentException> {
    @Override
    public Response toResponse(FulfilmentException exception) {
      return Response.status(exception.status())
          .entity(Map.of("code", exception.status(), "error", exception.getMessage()))
          .build();
    }
  }
}
