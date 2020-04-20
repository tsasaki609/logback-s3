/* Licensed under Apache-2.0 */
package work.smoke_code.logback_s3;

import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.helper.CompressionMode;
import ch.qos.logback.core.rolling.helper.Compressor;
import ch.qos.logback.core.rolling.helper.FileNamePattern;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** FileAppender extension for AWS S3 */
@Data
@EqualsAndHashCode(callSuper = true)
public class S3FileAppender extends FileAppender {

  private S3Config config;

  private S3Uploader uploader;

  private ExecutorService executorService = Executors.newSingleThreadExecutor();

  protected File getUploadLogFile() throws IOException {
    CompressionMode compressionMode = determineCompressionMode();

    Compressor compressor = new Compressor(compressionMode);
    compressor.setContext(this.context);

    String compressedLogFilePath =
        new StringBuilder(getFile().replaceAll("(\\..+$)", "-tmp$1")).toString();

    File uploadLogFile;
    switch (compressionMode) {
      case NONE:
        uploadLogFile = new File(getFile());
        break;
      case GZ:
        compressor.compress(getFile(), compressedLogFilePath, null);
        uploadLogFile = new File(compressedLogFilePath);
        break;
      case ZIP:
        compressor.compress(
            getFile(),
            compressedLogFilePath,
            new FileNamePattern(getFile(), this.context).convert(new Date()));
        uploadLogFile = new File(compressedLogFilePath);
        break;
      default:
        uploadLogFile = new File(getFile());
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
    if (getFile().endsWith(".gz")) {
      addInfo("Will use gz compression");
      return CompressionMode.GZ;
    } else if (getFile().endsWith(".zip")) {
      addInfo("Will use zip compression");
      return CompressionMode.ZIP;
    } else {
      addInfo("No compression will be used");
      return CompressionMode.NONE;
    }
  }

  @Override
  public void start() {
    super.start();

    //TODO move to shutdownhook and change local var
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
                            .append(getFile())
                            .toString();
                    uploadLogFile = getUploadLogFile();
                    uploader.upload(config.getBucket(), key, uploadLogFile);
                  } catch (IOException e) {
                    addError("Upload failed", e);
                  } finally {
                    if (Objects.nonNull(uploadLogFile)) uploadLogFile.deleteOnExit();
                  }

                  //TODO この辺りの定型的な処理は共通化したい gracefulShutdownとか
                  executorService.shutdown();
                  try {
                    if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                      // timeout
                      executorService.shutdownNow();
                      if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                        addError("Executor did not terminate");
                      }
                    }
                  } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                  }
                }));
  }
}
