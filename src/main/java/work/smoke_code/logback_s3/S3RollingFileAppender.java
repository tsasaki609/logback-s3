/* Licensed under Apache-2.0 */
package work.smoke_code.logback_s3;

import ch.qos.logback.core.rolling.RollingFileAppender;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** RollingFileAppender extension for AWS S3 */
public class S3RollingFileAppender extends RollingFileAppender {

  private S3Config config;

  private ExecutorService executorService = Executors.newSingleThreadExecutor();

  protected File getUploadLogFile() throws IOException {
    return null;
    //TODO OSS実装解析して圧縮ファイルを簡単に取得できる方法調べる（FileAppenderみたいな独自実装はしたくない）
    //親クラスでは圧縮してないっぽい　ローリングするときのポリシーでやってる？
  }

  @Override
  public void start() {
    super.start();

    //TODO シャットダウンフックを登録する
  }

  @Override
  public void rollover() {
    super.rollover();
  }
}
