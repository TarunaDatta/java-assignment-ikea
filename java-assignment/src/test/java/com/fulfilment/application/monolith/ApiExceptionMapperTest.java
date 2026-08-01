package com.fulfilment.application.monolith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiExceptionMapperTest {

  private final ApiExceptionMapper exceptionMapper = new ApiExceptionMapper();

  @Test
  void preservesStatusAndMessageForClientErrors() {
    Response response =
        exceptionMapper.toResponse(new NotFoundException("Warehouse does not exist."));

    assertEquals(404, response.getStatus());
    assertEquals(Map.of("code", 404, "error", "Warehouse does not exist."), response.getEntity());
  }

  @Test
  void usesHttpReasonWhenClientErrorHasNoMessage() {
    Response response =
        exceptionMapper.toResponse(
            new WebApplicationException((String) null, Response.Status.BAD_REQUEST));

    assertEquals(400, response.getStatus());
    assertEquals(Map.of("code", 400, "error", "Bad Request"), response.getEntity());
  }

  @Test
  void hidesUnexpectedExceptionDetailsFromClients() {
    Response response =
        exceptionMapper.toResponse(new RuntimeException("database password must not leak"));

    assertEquals(500, response.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) response.getEntity();
    assertEquals(500, body.get("code"));
    assertEquals("An unexpected error occurred.", body.get("error"));
    assertFalse(body.toString().contains("database password"));
    assertFalse(body.containsKey("exceptionType"));
  }
}
