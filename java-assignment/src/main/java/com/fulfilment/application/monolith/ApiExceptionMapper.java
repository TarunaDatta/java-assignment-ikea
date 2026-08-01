package com.fulfilment.application.monolith;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.jboss.logging.Logger;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {

  private static final Logger LOGGER = Logger.getLogger(ApiExceptionMapper.class);
  private static final String INTERNAL_ERROR_MESSAGE = "An unexpected error occurred.";

  @Override
  public Response toResponse(Exception exception) {
    if (exception instanceof WebApplicationException webApplicationException) {
      int status = webApplicationException.getResponse().getStatus();
      String message = clientMessage(webApplicationException, status);
      LOGGER.warnf("Request rejected with status %d: %s", status, message);
      return errorResponse(status, message);
    }

    LOGGER.error("Unexpected error while handling request", exception);
    return errorResponse(
        Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), INTERNAL_ERROR_MESSAGE);
  }

  private String clientMessage(WebApplicationException exception, int status) {
    if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
      return exception.getMessage();
    }

    Response.Status responseStatus = Response.Status.fromStatusCode(status);
    return responseStatus == null ? "Request failed." : responseStatus.getReasonPhrase();
  }

  private Response errorResponse(int status, String message) {
    return Response.status(status).entity(Map.of("code", status, "error", message)).build();
  }
}
