/* Licensed under Apache-2.0 */
package work.smoke_code.logback_s3;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.core.rolling.helper.CompressionMode;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Slf4j
class S3FileAppenderTest {
  @ParameterizedTest
  @CsvSource({"test.log, NONE", "test.gz, GZ", "test.zip, ZIP"})
  void determineCompressionMode(String file, CompressionMode expected) {
    S3FileAppender target = new S3FileAppender();
    target.setFile(file);

    assertEquals(expected, target.determineCompressionMode());
  }

  public static void main(String[] args) throws Exception {
    log.info("test");
  }
}
