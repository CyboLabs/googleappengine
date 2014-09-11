package com.google.appengine.tools.admin;

import com.google.appengine.tools.admin.AppVersionUpload.FileInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A {@link ClientDeploySender} that does not collect or log client deploy
 * yaml.
 * <p>
 * This implementation is handy in two cases.
 * <ol>
 * <li> The user has requested client deploy logging is not enabled.
 * <li> Client deploy logging is not supported for the operation being performed.
 * </ol>
 */
class NoLoggingClientDeploySender implements ClientDeploySender {

  private final ServerConnection connection;
  private String runtime;

  NoLoggingClientDeploySender(ServerConnection connection) {
    this.connection = connection;
  }

  @Override
  public String send(String url, File payload, String mimeType, String... args) throws IOException {
    return connection.post(url, payload, mimeType, args);
  }

  @Override
  public String send(String url, String payload, String... args) throws IOException {
    return connection.post(url, payload, args);
  }

  @Override
  public void sendBatch(String batchUrl, List<FileInfo> batch, long batchSize, String... args)
      throws IOException {
    connection.post(batchUrl, batch, args);
  }

  @Override
  public void logClientDeploy(boolean success, String... args) {
  }

  @Override
  public String getRuntime() {
    return runtime;
  }

  @Override
  public void setRuntime(String runtime) {
    this.runtime = runtime;
  }

  @Override
  public String getSdkVersion() {
    return null;
  }
}