/* Licensed under Apache-2.0 */
package work.smoke_code.logback_s3;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ExecutorUtil {
  public static void gracefulShutdown(
      ExecutorService executorService, Consumer<String> funcLogging) {
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
        // timeout
        executorService.shutdownNow();
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
          // addError("Executor did not terminate");
          funcLogging.accept("Executor did not terminate");
        }
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
