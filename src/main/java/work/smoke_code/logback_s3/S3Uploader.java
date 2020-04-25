/* Licensed under Apache-2.0 */
package work.smoke_code.logback_s3;

import java.io.File;
import java.net.URI;
import lombok.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@RequiredArgsConstructor
class S3Uploader {
  public static S3Uploader create(S3Config config) {
    S3Client client =
        S3Client.builder()
            .region(Region.of(config.getRegion()))
            .endpointOverride(URI.create(config.getEndpoint()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        config.getAccessKeyId(), config.getSecretAccessKey())))
            .build();

    return new S3Uploader(client);
  }

  @NonNull private S3Client client;

  protected void upload(@NonNull String bucket, @NonNull String key, @NonNull File file) {
    try {
      client.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromFile(file));
    } catch (Exception e) {
      // addError("Executor did not upload", e);
    }
  }
}
