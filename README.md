# S3FileAppender
ログファイルをS3へアップロードするためのシンプルなFileAppender拡張です。

JVMプロセスが終了するときにログファイルをS3へアップロードします。

Kubernetes上で実行したアプリケーションのログをダイレクトにS3へエクスポートしたかったので作成しました。

# Quick Start
ログバックの設定を追加します。
```
    <timestamp key="bySecond" datePattern="yyyyMMdd'T'HHmmss"/>
    <appender name="FILE" class="S3FileAppender">
        <file>log-${bySecond}.zip</file>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
        <region>replace here</region>
        <endpoint>replace here</endpoint>
        <accessKeyId>replace here</accessKeyId>
        <secretAccessKey>replace here</secretAccessKey>
        <bucket>replace here</bucket>
        <keyPrefix>replace here</keyPrefix>
    </appender>
```
