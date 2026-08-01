package com.fulfilment.application.monolith.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import org.junit.jupiter.api.Test;

public class LocationGatewayTest {

  @Test
  public void shouldReturnLocationWhenIdentifierExists() {
    LocationGateway locationGateway = new LocationGateway();

    Location location = locationGateway.resolveByIdentifier("ZWOLLE-001");

    assertEquals("ZWOLLE-001", location.identification);
    assertEquals(1, location.maxNumberOfWarehouses);
    assertEquals(40, location.maxCapacity);
  }

  @Test
  public void shouldReturnNullWhenIdentifierDoesNotExist() {
    LocationGateway locationGateway = new LocationGateway();

    Location location = locationGateway.resolveByIdentifier("UNKNOWN-001");

    assertNull(location);
  }

  @Test
  public void shouldReturnNullWhenIdentifierIsNull() {
    LocationGateway locationGateway = new LocationGateway();

    Location location = locationGateway.resolveByIdentifier(null);

    assertNull(location);
  }

  @Test
  public void shouldReturnNullWhenIdentifierIsEmpty() {
    LocationGateway locationGateway = new LocationGateway();

    Location location = locationGateway.resolveByIdentifier("");

    assertNull(location);
  }
}
