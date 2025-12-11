package com.ridwan.tweetaudit.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridwan.tweetaudit.model.ProcessingCheckpoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CheckpointManager {

  private final ObjectMapper objectMapper;
  private final String checkpointPath;

  public CheckpointManager(
      ObjectMapper objectMapper, @Value("${checkpoint.file-path}") String checkpointPath) {
    this.objectMapper = objectMapper;
    this.checkpointPath = checkpointPath;
  }

  public void saveCheckpoint(ProcessingCheckpoint checkpoint) throws IOException {
    checkpoint.setTimestamp(Instant.now());
    log.info(
        "Saving checkpoint: {}/{} tweets processed ({} flagged, {} errors)",
        checkpoint.getTotalProcessed(),
        checkpoint.getTotalTweets(),
        checkpoint.getFlaggedCount(),
        checkpoint.getErrorCount());

    Path path = Paths.get(checkpointPath);
    Files.createDirectories(path.getParent());

    objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), checkpoint);
    log.debug("Checkpoint saved to: {}", checkpointPath);
  }

  public ProcessingCheckpoint loadCheckpoint() throws IOException {
    Path path = Paths.get(checkpointPath);

    if (!Files.exists(path)) {
      log.info("No checkpoint found at: {}", checkpointPath);
      return null;
    }

    log.info("Loading checkpoint from: {}", checkpointPath);
    ProcessingCheckpoint checkpoint = objectMapper.readValue(path.toFile(), ProcessingCheckpoint.class);
    log.info(
        "Checkpoint loaded: {}/{} tweets already processed ({} flagged, {} errors)",
        checkpoint.getTotalProcessed(),
        checkpoint.getTotalTweets(),
        checkpoint.getFlaggedCount(),
        checkpoint.getErrorCount());

    return checkpoint;
  }

  public boolean checkpointExists() {
    return Files.exists(Paths.get(checkpointPath));
  }

  public void deleteCheckpoint() throws IOException {
    Path path = Paths.get(checkpointPath);
    if (Files.exists(path)) {
      Files.delete(path);
      log.info("Checkpoint deleted: {}", checkpointPath);
    }
  }
}
