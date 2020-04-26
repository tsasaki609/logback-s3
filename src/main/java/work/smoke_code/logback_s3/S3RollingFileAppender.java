/* Licensed under Apache-2.0 */
package work.smoke_code.logback_s3;

import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.helper.CompressionMode;
import ch.qos.logback.core.rolling.helper.Compressor;
import ch.qos.logback.core.rolling.helper.FileNamePattern;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.io.FilenameUtils;

/** RollingFileAppender extension for AWS S3 */
@Data
@EqualsAndHashCode(callSuper = true)
public class S3RollingFileAppender extends RollingFileAppender {

  private S3Config config;

  private S3Uploader uploader;

  private ExecutorService executorService = Executors.newSingleThreadExecutor();

  protected File getUploadLogFile() throws IOException {
    CompressionMode compressionMode = determineCompressionMode();

    Compressor compressor = new Compressor(compressionMode);
    compressor.setContext(this.context);

    String compressedLogFilePath;
    File uploadLogFile;
    switch (compressionMode) {
      case NONE:
        uploadLogFile = new File(getRollingPolicy().getActiveFileName());
        break;
      case GZ:
        compressedLogFilePath =
                new StringBuilder(getRollingPolicy().getActiveFileName().replaceAll("(\\..+$)", ".gz"))
                        .toString();
        compressor.compress(getRollingPolicy().getActiveFileName(), compressedLogFilePath, null);
        uploadLogFile = new File(compressedLogFilePath);
        break;
      case ZIP:
        compressedLogFilePath =
                new StringBuilder(getRollingPolicy().getActiveFileName().replaceAll("(\\..+$)", ".zip"))
                        .toString();
        compressor.compress(
            getRollingPolicy().getActiveFileName(),
            compressedLogFilePath,
            new FileNamePattern(getRollingPolicy().getActiveFileName(), this.context)
                .convert(new Date()));
        uploadLogFile = new File(compressedLogFilePath);
        break;
      default:
        uploadLogFile = new File(getRollingPolicy().getActiveFileName());
        break;
    }

    return uploadLogFile;
  }

  /**
   * determine compression mode for uploading log file
   *
   * @return CompressionMode
   */
  protected CompressionMode determineCompressionMode() {
    return getRollingPolicy().getCompressionMode();
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
                    uploadLogFile = getUploadLogFile();
                    final File _uploadLogFile = uploadLogFile;
                    final String key =
                        new StringBuilder()
                            .append(config.getKeyPrefix())
                            .append(uploadLogFile.getName())
                            .toString();
                    executorService.execute(() -> {
                      uploader.upload(config.getBucket(), key, _uploadLogFile);
                      addInfo(String.format("Uploded %s", _uploadLogFile.getName()));
                    });
                  } catch (IOException e) {
                    addError("Upload failed", e);
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
              new StringBuilder().append(config.getKeyPrefix()).append(getFile()).toString();

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
