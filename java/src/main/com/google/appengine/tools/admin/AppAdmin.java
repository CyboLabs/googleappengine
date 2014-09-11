// Copyright 2008 Google Inc. All rights reserved.

package com.google.appengine.tools.admin;

import com.google.apphosting.utils.config.BackendsXml;
import com.google.common.base.Preconditions;

import java.io.Reader;
import java.util.List;

/**
 * The application administration interface to App Engine. Use this
 * API to update, configure, and otherwise manage an App Engine
 * application. Use {@link AppAdminFactory} to retrieve an {@code AppAdmin}
 * instance configured for a specific application.
 * <p>
 * <b>Synchronous versus Asynchronous requests:</b>
 * Some requests, such as {@link #update}, occur asynchronously
 * and must be monitored with a {@code listener}. Other requests, such as
 * {@link #updateIndexes}, are made synchronously. In either case,
 * work often continues to occur asynchronously on the remote server after the
 * request has been completed.
 * <p>
 * <b>Error handling:</b> Most configuration operations require communicating
 * to App Engine's remote administration server occur over a
 * network connection. In cases where unrecoverable failures occur (such as a
 * network failure), this API throws an
 * {@link com.google.appengine.tools.admin.AdminException}.
 * <p>
 * Application updates occur transactionally. If a failure occurs during
 * update, you must {@link #rollback} the incomplete transaction before
 * beginning another.
 *
 */
public interface AppAdmin {

  /**
   * Uploads a new version of the application and updates its indexes.
   * The update may occur asynchronously, so an {@link UpdateListener}
   * must be installed to track progress.
   *
   * @param listener The listener to be notified of updates. Must not be
   * {@code null}.
   *
   * @throws AdminException if an error occurs.
   */
  void update(UpdateListener listener);

  /**
   * Rolls back an "in-progress" transaction. This operation occurs
   * synchronously.
   *
   * @throws AdminException if an error occurs.
   */
  void rollback();

  /**
   * Rolls back an "in-progress" transaction on a backend. This operation occurs
   * synchronously.
   *
   * @param backend to rollback, or null for default app
   *
   * @throws AdminException if an error occurs.
   */
  void rollbackBackend(String backend);

  /**
   * Rolls back an "in-progress" transaction on all backends. This operation occurs
   * synchronously.
   *
   * @throws AdminException if an error occurs.
   */
  void rollbackAllBackends();

  /**
   * Sends a synchronous request to update the application's indexes. This work
   * completes asynchronously on the server.
   *
   * @throws AdminException if an error occurs.
   */
  void updateIndexes();

  /**
   * Sends a synchronous request to update the application's cron jobs. This
   * work completes synchronously on the server.
   *
   * @throws AdminException if an error occurs.
   */
  void updateCron();

  /**
   * Sends a synchronous request to update the application's task queue
   * definitions. This work completes synchronously on the server.
   *
   * @throws AdminException if an error occurs.
   */
  void updateQueues();

  /**
   * Sends a synchronous request to update the application's Dispatch configuration.
   *
   * @throws AdminException if an error occurs.
   */
  void updateDispatch();

  /**
   * Sends a synchronous request to update the application's DoS configuration.
   * This work completes synchronously on the server.
   *
   * @throws AdminException if an error occurs.
   */
  void updateDos();

  /**
   * Locally parses an application's configured cron entries and reports the
   * upcoming several execution times.
   *
   * @throws AdminException if an error occurs.
   */
  List<CronEntry> cronInfo();

  /**
   * Gets the resource limits. The values returned are a combination of
   * values reported by the adminconsole/appserver plus locally defined
   * defaults for any missing values.
   *
   * @return The ResourceLimits object.
   *
   * @throws AdminException if an error occurs.
   */
  ResourceLimits getResourceLimits();

  /**
   * Performs the following three steps:
   * <ol>
   * <li>Queries the remote administration server for existing indexes
   * that are not included in the local index configuration file. We will
   * refer to these as <b>orphaned</b> indexes.
   * <li>If {@code callback} is not null, then the {@link
   * ConfirmationCallback#confirmAction(ConfirmationCallback.Action)
   * confirmAction} method will be called once for each of the orphaned indexes
   * to confirm that it really should be deleted.
   * <li>A request will be sent to the server to delete each of the confirmed
   * indexes. This work completes asynchronously on the server.
   *</ol>
   *
   * @param callback Used to confirm deletions. May be {@code null} in which case no
   * confirmation will be done.
   *
   * @param listener The listener to be notified of updates. Must not be
   * {@code null}.
   *
   * @throws AdminException if an error occurs.
   */
  void vacuumIndexes(
      ConfirmationCallback<IndexDeleter.DeleteIndexAction> callback, UpdateListener listener);

  /**
   * Retrieves application logs from the remote administration server.
   *
   * @param numDays The number of days to retrieve logs for. The cut-off
   * point is midnight UTC. Use 0 to get all available logs.
   *
   * @param severity The severity of app-level log messages to get. If null,
   * only request logs are returned.
   *
   * @param includeAll Include everything we know about a request, including
   * ms, cpu_ms and so on.
   *
   * @return a non-null {@code Reader} which can be used to stream the logs
   * from the remote administration server. You should
   * {@link Reader#close close} the {@code Reader} when you're finished reading
   * logs. It is ok to {@code close} the {@code Reader} before all logs have
   * been read (streaming from the server is cancelled).
   *
   * @throws AdminException if an error occurs.
   */
  Reader requestLogs(int numDays, LogSeverity severity, boolean includeAll);

  /**
   * Redeploy the backend with the specified name.
   */
  void updateBackend(String backendName, UpdateListener listener);

  /**
   * Redeploy the backends with the specified names.
   */
  void updateBackends(List<String> backendNames, UpdateListener listener);

  /**
   * Redeploy all backends.
   */
  void updateAllBackends(UpdateListener listener);

  /**
   * Retrieve a list of registered backends with their associated state.
   */
  List<BackendsXml.Entry> listBackends();

  /**
   * Update the state of the backend with the specified name to {@code newState}.
   */
  void setBackendState(String backendName, BackendsXml.State newState);

  /**
   * Delete the backend with the specified name.
   */
  void deleteBackend(String backendName);

  /**
   * Reconfigure the backend with the specified name.
   */
  void configureBackend(String backendName);

  /**
   * Start the specified module version.
   */
  void startModuleVersion();

  /**
   * Stop the specified module version.
   */
  void stopModuleVersion();

  /**
   * The severity levels for App Engine application logging.
   */
  enum LogSeverity {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    CRITICAL,
  }

  /**
   * Sends a synchronous request to update the application's default version.
   * This work completes synchronously on the server.
   *
   * @throws AdminException if an error occurs.
   */
  void setDefaultVersion();

  /**
   * Sends a synchronous request to retrieve the list of versions for each module.
   *
   * @return Returns the YAML server response as a String.
   *
   * @throws AdminException if an error occurs.
   */
  String listVersions();

  /**
   * Sends a synchronous request to delete the specified version
   *
   * @param appId The application to delete the version from.
   *
   * @param moduleId The module to delete the version from.
   *
   * @param versionId The version to delete.
   *
   * @return Returns the YAML server response as a String
   *
   * @throws AdminException if an error occurs.
   */
  String deleteVersion(String appId, String moduleId, String versionId);

  /**
   * Sends a synchronous request to turn on debugging for the specified vm version.
   *
   * @return Returns the YAML server response as a String
   *
   * @throws AdminException if an error occurs.
   */
  String debugVersion();

  /**
   * Sends a synchronous request to get the status of a debug request for the specified version.
   *
   * @return Returns the YAML server response as a String
   *
   * @throws AdminException if an error occurs.
   */
  String debugVersionState();

  /**
   * Returns the {@link UpdateOptions} for this {@link AppAdmin}.
   */
  UpdateOptions getUpdateOptions();

  /**
   * Settable options for configuring the behavior of update operations.
   */
  public static class UpdateOptions {
    /**
     * SDK version for logging in cases the SDK version is unavailable.
     */
    static final String UNKNOWN_SDK_VERSION = "Java/unknown";

    private boolean updateGlobalConfigurations = true;
    private String sdkVersion = UNKNOWN_SDK_VERSION;
    private boolean updateUsageReporting = true;

    /**
     * When set to true {@link AppAdmin#update}, {@link AppAdmin#updateBackend}
     * and {@link AppAdmin#updateAllBackends} automatically include
     * updates to global application settings. This is the default behavior and
     * preserves compatibility for these operations.
     * <p>
     * The updated settings include indexes, cron, queues, dos and pagespeed.
     * <p>
     * When called with false these these global application settings
     * are not updated. The false setting is appropriate when uploading
     * all but the first WAR in an EAR.
     */
    void setUpdateGlobalConfigurations(boolean updateGlobalConfigurations) {
      this.updateGlobalConfigurations = updateGlobalConfigurations;
    }

    /**
     * Returns true if global application configuration values that have
     * historically been automatically updated should be updated by
     * version updates.
     * <p>
     * For details see {@link #setUpdateGlobalConfigurations(boolean)}.
     */
    boolean getUpdateGlobalConfigurations() {
      return updateGlobalConfigurations;
    }

    /**
     * Set the SDK version.
     * <p>
     * This is currently used for logging purposes.
     *
     * @param sdkVersion
     */
    void setSdkVersion(String sdkVersion) {
      this.sdkVersion = Preconditions.checkNotNull(sdkVersion);
    }

    /**
     * Returns the SDK version if it has been set and a string
     * indicating an unknown version if it has not been set.
     */
    String getSdkVersion() {
      return sdkVersion;
    }

    /**
     * Enables or disables usage reporting (aka client deploy logging).
     */
    void setUpdateUsageReporting(boolean updateUsageReporting) {
      this.updateUsageReporting = updateUsageReporting;
    }

    /**
     * Returns true iff usage reporting (aka client deploy logging) is enabled.
     */
    boolean getUpdateUsageReporting() {
      return updateUsageReporting;
    }
  }
}
