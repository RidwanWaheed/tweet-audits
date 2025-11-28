package com.ridwan.tweetaudit.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.ridwan.tweetaudit.config.AlignmentCriteria;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigValidatorTest {

  private ConfigValidator validator;
  private AlignmentCriteria validCriteria;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    validator = new ConfigValidator();

    validCriteria = new AlignmentCriteria();
    validCriteria.setForbiddenWords(List.of("kill", "bum"));
    validCriteria.setCheckProfessionalism(true);
    validCriteria.setContext("Test context");
    validCriteria.setDesiredTone("Professional");
  }

  @Test
  void shouldPassValidationWithValidConfiguration() throws IOException {
    Path validFile = tempDir.resolve("tweets.js");
    Files.writeString(validFile, "window.YTD.tweets.part0 = []");

    String apiKey = "a".repeat(35);
    String archivePath = validFile.toString();
    String outputPath = tempDir.resolve("output.csv").toString();
    int batchSize = 10;

    assertDoesNotThrow(
        () -> validator.validateConfiguration(apiKey, archivePath, outputPath, validCriteria, batchSize));
  }

  @Test
  void shouldFailWhenApiKeyIsNull() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> validator.validateConfiguration(null, "path", "output", validCriteria, 10));

    assertTrue(exception.getMessage().contains("GEMINI_API_KEY"));
    assertTrue(exception.getMessage().contains("not set"));
  }

  @Test
  void shouldFailWhenApiKeyIsEmpty() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> validator.validateConfiguration("", "path", "output", validCriteria, 10));

    assertTrue(exception.getMessage().contains("GEMINI_API_KEY"));
    assertTrue(exception.getMessage().contains("not set"));
  }

  @Test
  void shouldFailWhenApiKeyIsTooShort() {
    String shortKey = "abc123";

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> validator.validateConfiguration(shortKey, "path", "output", validCriteria, 10));

    assertTrue(exception.getMessage().contains("invalid"));
    assertTrue(exception.getMessage().contains("too short"));
  }

  @Test
  void shouldFailWhenArchivePathIsNull() {
    String validApiKey = "a".repeat(35);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> validator.validateConfiguration(validApiKey, null, "output", validCriteria, 10));

    assertTrue(exception.getMessage().contains("Archive path"));
    assertTrue(exception.getMessage().contains("not configured"));
  }

  @Test
  void shouldFailWhenArchiveFileDoesNotExist() {
    String validApiKey = "a".repeat(35);
    String nonExistentPath = "/path/that/does/not/exist/tweets.js";

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> validator.validateConfiguration(validApiKey, nonExistentPath, "output", validCriteria, 10));

    assertTrue(exception.getMessage().contains("not found"));
    assertTrue(exception.getMessage().contains(nonExistentPath));
  }

  @Test
  void shouldFailWhenArchiveFileIsEmpty() throws IOException {
    String validApiKey = "a".repeat(35);
    Path emptyFile = tempDir.resolve("empty.js");
    Files.createFile(emptyFile);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                validator.validateConfiguration(
                    validApiKey, emptyFile.toString(), "output", validCriteria, 10));

    assertTrue(exception.getMessage().contains("empty"));
  }

  @Test
  void shouldFailWhenForbiddenWordsListIsEmpty() throws IOException {
    String validApiKey = "a".repeat(35);
    Path validFile = tempDir.resolve("tweets.js");
    Files.writeString(validFile, "content");

    AlignmentCriteria invalidCriteria = new AlignmentCriteria();
    invalidCriteria.setForbiddenWords(List.of());
    invalidCriteria.setContext("Context");
    invalidCriteria.setDesiredTone("Tone");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                validator.validateConfiguration(
                    validApiKey, validFile.toString(), "output", invalidCriteria, 10));

    assertTrue(exception.getMessage().contains("forbidden words"));
    assertTrue(exception.getMessage().contains("empty"));
  }

  @Test
  void shouldFailWhenContextIsEmpty() throws IOException {
    String validApiKey = "a".repeat(35);
    Path validFile = tempDir.resolve("tweets.js");
    Files.writeString(validFile, "content");

    AlignmentCriteria invalidCriteria = new AlignmentCriteria();
    invalidCriteria.setForbiddenWords(List.of("test"));
    invalidCriteria.setContext("");
    invalidCriteria.setDesiredTone("Tone");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                validator.validateConfiguration(
                    validApiKey, validFile.toString(), "output", invalidCriteria, 10));

    assertTrue(exception.getMessage().contains("context"));
    assertTrue(exception.getMessage().contains("not set"));
  }

  @Test
  void shouldFailWhenDesiredToneIsEmpty() throws IOException {
    String validApiKey = "a".repeat(35);
    Path validFile = tempDir.resolve("tweets.js");
    Files.writeString(validFile, "content");

    AlignmentCriteria invalidCriteria = new AlignmentCriteria();
    invalidCriteria.setForbiddenWords(List.of("test"));
    invalidCriteria.setContext("Context");
    invalidCriteria.setDesiredTone("");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                validator.validateConfiguration(
                    validApiKey, validFile.toString(), "output", invalidCriteria, 10));

    assertTrue(exception.getMessage().contains("desired tone"));
    assertTrue(exception.getMessage().contains("not set"));
  }

  @Test
  void shouldFailWhenBatchSizeIsZero() throws IOException {
    String validApiKey = "a".repeat(35);
    Path validFile = tempDir.resolve("tweets.js");
    Files.writeString(validFile, "content");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> validator.validateConfiguration(validApiKey, validFile.toString(), "output", validCriteria, 0));

    assertTrue(exception.getMessage().contains("Batch size"));
    assertTrue(exception.getMessage().contains("greater than 0"));
  }

  @Test
  void shouldFailWhenBatchSizeIsNegative() throws IOException {
    String validApiKey = "a".repeat(35);
    Path validFile = tempDir.resolve("tweets.js");
    Files.writeString(validFile, "content");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> validator.validateConfiguration(validApiKey, validFile.toString(), "output", validCriteria, -5));

    assertTrue(exception.getMessage().contains("Batch size"));
    assertTrue(exception.getMessage().contains("greater than 0"));
  }
}
