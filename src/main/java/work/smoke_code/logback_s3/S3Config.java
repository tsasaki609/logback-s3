/* Licensed under Apache-2.0 */
package work.smoke_code.logback_s3;

import lombok.Data;

@Data
public class S3Config {
  private String region;
  private String endpoint;
  private String accessKeyId;
  private String secretAccessKey;
  private String bucket;
  private String keyPrefix;
}
