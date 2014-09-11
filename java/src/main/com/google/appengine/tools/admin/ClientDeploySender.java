package com.google.appengine.tools.admin;

import com.google.appengine.tools.admin.AppVersionUpload.FileInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Interface for sending admin console deployment related requests with collection of
 * needed timing and error information for client deploy logging.
 */
public interface ClientDeploySender {
  /**
   * Sends an http request and records client deploy logging information for the request.
   * @param url The url path for the request.
   * @param payload The {@link File} with the payload for the request.
   * @param mimeType The mime type for the request.
   * @param args Url query arguments for the request.
   * @return The http reply body.
   * @throws IOException If the request fails.
   */
  public String send(String url, File payload, String mimeType, String... args)
      throws IOException;

  /**
   * Sends an http request and records client deploy logging information for the request.
   * @param url The url path for the request.
   * @param payload The payload for the request.
   * @param args Query arguments for the request.
   * @return The http reply body.
   * @throws IOException If the request fails.
   */
  public String send(String url, String payload, String... args)
      throws IOException;

  /**
   * Sends an multi part http request and records client deploy logging information for the request.
   * @param batchUrl The url path for the request.
   * @param batch The list of {@link FileInfo} descriptors for files to include in the batch.
   * @param batchSize The approximate aggregate byte size of the batch.
   * @param args Query arguments for the request.
   * @throws IOException If the request fails.
   */
  public void sendBatch(String batchUrl, List<FileInfo> batch, long batchSize,
      String ...args) throws IOException;

  /**
   * Logs a client deployment attempt with collected timing and completion status for included
   * http requests.
   *
   * @param success
   * @param args Query arguments for the logging request.
   */
  public void logClientDeploy(boolean success, String... args);

  /**
   * Returns the runtime.
   */
  public String getRuntime();

  /**
   * Sets the runtime.
   * @param runtime
   */
  public void setRuntime(String runtime);

  /**
   * Returns the SDK version or null is this {@link ClientDeploySender} does not have one.
   */
  public String getSdkVersion();

}
