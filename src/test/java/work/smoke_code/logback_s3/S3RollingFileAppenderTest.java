/* Licensed under Apache-2.0 */
package work.smoke_code.logback_s3;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@Slf4j
class S3RollingFileAppenderTest {
  File activeLogFile;
  File nextLogFile;

  @AfterEach
  void tearDown() {
    if (Objects.nonNull(activeLogFile) && activeLogFile.exists()) activeLogFile.delete();
    if (Objects.nonNull(nextLogFile) && nextLogFile.exists()) nextLogFile.delete();
  }

  @Test
  void isRollover() throws Exception {
    activeLogFile = File.createTempFile("", "");

    S3RollingFileAppender target = new S3RollingFileAppender();

    assertThrows(
        IllegalArgumentException.class, () -> target.isRollover(activeLogFile.getParentFile()));

    assertFalse(target.isRollover(activeLogFile));

    nextLogFile = File.createTempFile("", "", activeLogFile.getParentFile());
    assertTrue(target.isRollover(activeLogFile));
  }

  public static void main(String[] args) throws Exception {
    while (true) {
      log.info(String.valueOf(System.currentTimeMillis()));

      Thread.sleep(1000);
    }
  }
}
