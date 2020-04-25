/* Licensed under Apache-2.0 */
package work.smoke_code.logback_s3;

import ch.qos.logback.core.rolling.RollingFileAppender;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.Data;
import org.apache.commons.io.FilenameUtils;

/** RollingFileAppender extension for AWS S3 */
@Data
public class S3RollingFileAppender extends RollingFileAppender {

  private S3Config config;

  private S3Uploader uploader;

  private ExecutorService executorService = Executors.newSingleThreadExecutor();

  protected File getUploadLogFile() throws IOException {
    return new File(getRollingPolicy().getActiveFileName());
  }

  @Override
  public void start() {
    super.start();

    uploader = S3Uploader.create(config);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  // upload log file to specified bucket on AWS S3
                  File uploadLogFile = null;
                  try {
                    final String key =
                        new StringBuilder()
                            .append(config.getKeyPrefix())
                            .append(getRollingPolicy().getActiveFileName())
                            .toString();
                    uploadLogFile = getUploadLogFile();
                    uploader.upload(config.getBucket(), key, uploadLogFile);
                  } catch (IOException e) {
                    addError("Upload failed", e);
                  } finally {
                    if (Objects.nonNull(uploadLogFile)) uploadLogFile.deleteOnExit();
                  }

                  ExecutorUtil.gracefulShutdown(executorService, e -> addError(e));
                }));
  }

  @Override
  public void rollover() {
    String logFileName = getRollingPolicy().getActiveFileName();
    File logFile = new File(logFileName);

    super.rollover();

    executorService.execute(
        () -> {
          while (true) {
            if (isRollover(logFile)) break;

            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }

          final String key =
                  new StringBuilder()
                          .append(config.getKeyPrefix())
                          .append(getFile())
                          .toString();

          uploader.upload(config.getBucket(), key, logFile);
        });
  }

  protected boolean isRollover(File logFile) {
    if (!logFile.isFile()) throw new IllegalArgumentException("logFile is not file");

    if (!logFile.exists()) return false;

    String logFileExtension = FilenameUtils.getExtension(logFile.getName());
    Optional<File> latestLogFile =
        Arrays.stream(
                logFile
                    .getParentFile()
                    .listFiles(
                        (dir, name) -> logFileExtension.equals(FilenameUtils.getExtension(name))))
            .sorted(Comparator.comparingLong(File::lastModified).reversed())
            .findFirst();

    if (!latestLogFile.isPresent()) return false;

    return !latestLogFile.equals(logFile);
  }
}
