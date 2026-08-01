package com.fulfilment.application.monolith.stores;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_OUT)
@ResourceLock(Resources.SYSTEM_ERR)
class LegacyStoreManagerGatewayTest {

  private static final String CREATED_FILE_MESSAGE = "Temporary file created at: ";

  private final LegacyStoreManagerGateway gateway = new LegacyStoreManagerGateway();

  @Nested
  class CreateStoreOnLegacySystem {

    @Test
    void writesStoreToTemporaryFileAndDeletesIt() {
      Store store = store("TEST-CREATE", 12);

      verifySuccessfulWrite(store, () -> gateway.createStoreOnLegacySystem(store));
    }

    @Test
    void handlesFailureToCreateTemporaryFile() {
      Store store = store("invalid\0name", 12);

      verifyCreateFileFailure(() -> gateway.createStoreOnLegacySystem(store));
    }
  }

  @Nested
  class UpdateStoreOnLegacySystem {

    @Test
    void writesStoreToTemporaryFileAndDeletesIt() {
      Store store = store("TEST-UPDATE", 8);

      verifySuccessfulWrite(store, () -> gateway.updateStoreOnLegacySystem(store));
    }

    @Test
    void handlesFailureToCreateTemporaryFile() {
      Store store = store("invalid\0name", 8);

      verifyCreateFileFailure(() -> gateway.updateStoreOnLegacySystem(store));
    }
  }

  private void verifySuccessfulWrite(Store store, Runnable operation) {
    String output = captureStandardOutput(operation);

    assertTrue(output.contains("Data read from temporary file: " + expectedContent(store)));
    assertTrue(output.contains("Temporary file deleted."));

    String temporaryFilePath =
        output
            .lines()
            .filter(line -> line.startsWith(CREATED_FILE_MESSAGE))
            .map(line -> line.substring(CREATED_FILE_MESSAGE.length()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Gateway did not report the temporary file path"));
    assertFalse(Files.exists(Path.of(temporaryFilePath)));
  }

  private void verifyCreateFileFailure(Runnable operation) {
    String errorOutput = captureStandardError(() -> assertDoesNotThrow(operation::run));

    assertTrue(errorOutput.contains(InvalidPathException.class.getName()));
  }

  private String captureStandardOutput(Runnable operation) {
    PrintStream originalOutput = System.out;
    ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();

    try (PrintStream testOutput =
        new PrintStream(capturedOutput, true, StandardCharsets.UTF_8)) {
      System.setOut(testOutput);
      operation.run();
    } finally {
      System.setOut(originalOutput);
    }

    return capturedOutput.toString(StandardCharsets.UTF_8);
  }

  private String captureStandardError(Runnable operation) {
    PrintStream originalError = System.err;
    ByteArrayOutputStream capturedError = new ByteArrayOutputStream();

    try (PrintStream testError = new PrintStream(capturedError, true, StandardCharsets.UTF_8)) {
      System.setErr(testError);
      operation.run();
    } finally {
      System.setErr(originalError);
    }

    return capturedError.toString(StandardCharsets.UTF_8);
  }

  private Store store(String name, int quantityProductsInStock) {
    Store store = new Store(name);
    store.quantityProductsInStock = quantityProductsInStock;
    return store;
  }

  private String expectedContent(Store store) {
    return "Store created. [ name ="
        + store.name
        + " ] [ items on stock ="
        + store.quantityProductsInStock
        + "]";
  }
}
