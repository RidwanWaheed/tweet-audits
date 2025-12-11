package com.ridwan.tweetaudit.validation;

import com.ridwan.tweetaudit.config.AlignmentCriteria;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConfigValidator {

  private static final int MIN_API_KEY_LENGTH = 30;
  private static final int MAX_BATCH_SIZE = 100;

  public void validateConfiguration(
      String apiKey, String archivePath, String outputPath, AlignmentCriteria criteria, int batchSize) {

    log.info("Validating configuration...");

    validateApiKey(apiKey);
    validateArchivePath(archivePath);
    validateOutputPath(outputPath);
    validateAlignmentCriteria(criteria);
    validateBatchSize(batchSize);

    log.info("Configuration validation passed");
  }

  private void validateApiKey(String apiKey) {
    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new IllegalStateException(
          "GEMINI_API_KEY environment variable is not set.\n"
              + "Please set your API key: export GEMINI_API_KEY=your_key_here\n"
              + "Get your key at: https://aistudio.google.com/app/apikey");
    }

    if (apiKey.length() < MIN_API_KEY_LENGTH) {
      throw new IllegalStateException(
          "GEMINI_API_KEY appears to be invalid (too short).\n"
              + "Expected length: at least "
              + MIN_API_KEY_LENGTH
              + " characters, got: "
              + apiKey.length()
              + "\n"
              + "Please verify your API key at: https://aistudio.google.com/app/apikey");
    }

    log.debug("API key validation passed");
  }

  private void validateArchivePath(String archivePath) {
    if (archivePath == null || archivePath.trim().isEmpty()) {
      throw new IllegalStateException(
          "Archive path is not configured.\n"
              + "Please set TWITTER_ARCHIVE_PATH or check application.properties");
    }

    Path path = Paths.get(archivePath);

    if (!Files.exists(path)) {
      throw new IllegalStateException(
          "Twitter archive file not found: "
              + archivePath
              + "\n"
              + "Please ensure the file exists or download your archive at:\n"
              + "https://x.com/settings/download_your_data");
    }

    if (!Files.isReadable(path)) {
      throw new IllegalStateException(
          "Twitter archive file is not readable: " + archivePath + "\n" + "Please check file permissions");
    }

    if (!archivePath.endsWith(".js")) {
      log.warn(
          "Archive file does not have .js extension: {}. This might not be a valid Twitter archive file.",
          archivePath);
    }

    try {
      long fileSize = Files.size(path);
      if (fileSize == 0) {
        throw new IllegalStateException("Twitter archive file is empty: " + archivePath);
      }
      log.debug("Archive file size: {} bytes", fileSize);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read archive file: " + archivePath, e);
    }

    log.debug("Archive path validation passed: {}", archivePath);
  }

  private void validateOutputPath(String outputPath) {
    if (outputPath == null || outputPath.trim().isEmpty()) {
      throw new IllegalStateException("Output path is not configured");
    }

    Path path = Paths.get(outputPath);
    Path parentDir = path.getParent();

    if (parentDir != null && Files.exists(parentDir)) {
      if (!Files.isWritable(parentDir)) {
        throw new IllegalStateException(
            "Output directory is not writable: " + parentDir + "\n" + "Please check directory permissions");
      }
    }

    log.debug("Output path validation passed: {}", outputPath);
  }

  private void validateAlignmentCriteria(AlignmentCriteria criteria) {
    if (criteria.getForbiddenWords() == null || criteria.getForbiddenWords().isEmpty()) {
      throw new IllegalStateException(
          "Alignment criteria: forbidden words list is empty.\n"
              + "Please configure alignment.forbidden-words in application.properties");
    }

    if (criteria.getContext() == null || criteria.getContext().trim().isEmpty()) {
      throw new IllegalStateException(
          "Alignment criteria: context is not set.\n"
              + "Please configure alignment.context in application.properties");
    }

    if (criteria.getDesiredTone() == null || criteria.getDesiredTone().trim().isEmpty()) {
      throw new IllegalStateException(
          "Alignment criteria: desired tone is not set.\n"
              + "Please configure alignment.desired-tone in application.properties");
    }

    log.debug(
        "Alignment criteria validation passed: {} forbidden words, professionalism check: {}",
        criteria.getForbiddenWords().size(),
        criteria.isCheckProfessionalism());
  }

  private void validateBatchSize(int batchSize) {
    if (batchSize <= 0) {
      throw new IllegalStateException(
          "Batch size must be greater than 0, got: "
              + batchSize
              + "\n"
              + "Please configure tweet.processing.batch-size in application.properties");
    }

    if (batchSize > MAX_BATCH_SIZE) {
      log.warn(
          "Batch size is very large: {}. This might cause memory issues. Recommended max: {}",
          batchSize,
          MAX_BATCH_SIZE);
    }

    log.debug("Batch size validation passed: {}", batchSize);
  }
}
