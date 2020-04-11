/* Licensed under Apache-2.0 */
package work.smoke_code.logback_s3;

import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.helper.CompressionMode;
import ch.qos.logback.core.rolling.helper.Compressor;
import ch.qos.logback.core.rolling.helper.FileNamePattern;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** FileAppender extension for AWS S3 */
@Data
@EqualsAndHashCode(callSuper = true)
public class S3FileAppender extends FileAppender {

  private String region;
  private String endpoint;
  private String accessKeyId;
  private String secretAccessKey;
  private String bucket;
  private String keyPrefix;

  private S3Client s3Client;

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

  /** upload log file to specified bucket on AWS S3 */
  protected void upload() {
    final String key = new StringBuilder().append(getKeyPrefix()).append(getFile()).toString();
    executorService.execute(
        () -> {
          File uploadLogFile = null;
          try {
            uploadLogFile = getUploadLogFile();
            s3Client.putObject(
                PutObjectRequest.builder().bucket(getBucket()).key(key).build(),
                RequestBody.fromFile(uploadLogFile));
          } catch (IOException e) {
            addError("Executor did not upload", e);
          } finally {
            if (Objects.nonNull(uploadLogFile)) {
              uploadLogFile.deleteOnExit();
            }
          }
        });
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

    s3Client =
        S3Client.builder()
            .region(Region.of(region))
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
            .build();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  upload();

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
