package com.fulfilment.application.monolith.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoreResourceErrorMapperTest {

  private StoreResource.ErrorMapper errorMapper;

  @BeforeEach
  void setUp() {
    errorMapper = new StoreResource.ErrorMapper();
    errorMapper.objectMapper = new ObjectMapper();
  }

  @Test
  void includesErrorWhenExceptionHasMessage() {
    Response response = errorMapper.toResponse(new RuntimeException("Store operation failed"));

    ObjectNode body = (ObjectNode) response.getEntity();
    assertEquals(500, response.getStatus());
    assertEquals(RuntimeException.class.getName(), body.get("exceptionType").asText());
    assertEquals(500, body.get("code").asInt());
    assertEquals("Store operation failed", body.get("error").asText());
  }

  @Test
  void omitsErrorWhenExceptionMessageIsNull() {
    Response response = errorMapper.toResponse(new RuntimeException());

    ObjectNode body = (ObjectNode) response.getEntity();
    assertEquals(500, response.getStatus());
    assertEquals(RuntimeException.class.getName(), body.get("exceptionType").asText());
    assertEquals(500, body.get("code").asInt());
    assertFalse(body.has("error"));
  }
}
