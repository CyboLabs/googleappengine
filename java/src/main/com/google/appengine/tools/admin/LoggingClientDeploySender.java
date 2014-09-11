package com.google.appengine.tools.admin;

import com.google.appengine.tools.admin.AppVersionUpload.FileInfo;
import com.google.apphosting.utils.config.ClientDeployYamlMaker;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link ClientDeploySender} implementation that collects and logs client deploy yaml.
 */
class LoggingClientDeploySender  implements ClientDeploySender {
  private static final Logger logger =
      Logger.getLogger(LoggingClientDeploySender.class.getName());

  private final ClientDeployYamlMaker maker;
  private final ServerConnection connection;

  LoggingClientDeploySender(ServerConnection connection, String sdkVersion) {
    long startTimeUsec = getCurrentTimeUsec();
    maker = new ClientDeployYamlMaker(startTimeUsec, sdkVersion);
    this.connection = connection;
  }

  @Override
  public String send(String url, File payload, String mimeType, String... args)
      throws IOException {
    long startTimeUsec = getCurrentTimeUsec();
    try {
      String result = connection.post(url, payload, mimeType, args);
      registerHttpRequestForLogging(url, 200, startTimeUsec, payload.length());
      return result;
    } catch (HttpIoException e) {
      registerHttpRequestForLogging(url, e.getResponseCode(), startTimeUsec, payload.length());
      throw e;
    }
  }

  @Override
  public String send(String url, String payload, String... args)
      throws IOException {
    long startTimeUsec = getCurrentTimeUsec();
    try {
      String result = connection.post(url, payload, args);
      registerHttpRequestForLogging(url, 200, startTimeUsec, payload.length());
      return result;
    } catch (HttpIoException e) {
      registerHttpRequestForLogging(url, e.getResponseCode(), startTimeUsec, payload.length());
      throw e;
    }
  }

  @Override
  public void sendBatch(String batchUrl, List<FileInfo> batch, long batchSize,
      String... args) throws IOException {
    long startTimeUsec = getCurrentTimeUsec();
    try {
      connection.post(batchUrl, batch, args);
      registerHttpRequestForLogging(batchUrl, 200, startTimeUsec, batchSize);
    } catch (HttpIoException e) {
      registerHttpRequestForLogging(batchUrl, e.getResponseCode(), startTimeUsec, batchSize);
      throw e;
    }
  }

  @Override
  public void logClientDeploy(boolean success, String ...args) {
    try {
      String yaml = maker.make(getCurrentTimeUsec(), success);
      logger.log(Level.FINE, "client deploy yaml={0}", yaml);
      send("/api/logclientdeploy", yaml, args);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error logging client deployment, continuing", e);
    }
  }

  @Override
  public String getRuntime() {
    return maker.getRuntime();
  }

  @Override
  public void setRuntime(String runtime) {
    maker.setRuntime(runtime);
  }

  @Override
  public String getSdkVersion() {
    return maker.getSdkVersion();
  }

  private void registerHttpRequestForLogging(String url, int httpResponseCode,
      long startTimeUsec, long requestLengthBytes) {
    maker.addRequest(url, httpResponseCode, startTimeUsec, getCurrentTimeUsec(),
        requestLengthBytes);
  }

  /**
   * Returns the current time in microseconds.
   */
  static long getCurrentTimeUsec() {
    return System.currentTimeMillis() * 1000;
  }
}
