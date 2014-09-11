package com.google.appengine.tools.appstats;

import com.google.appengine.tools.appstats.Recorder.UnprocessedFutureStrategy;

/**
 * A class to store various configurable options specified via Appstats filter definition.
 * <p>
 *
 * <pre>
 * AppstatsSettings settings = AppstatsSettings.withDefault();
 * settings.setDatastoreDetails("true");
 * settings.getDatastoreDetails();
 * </pre>
 *
 */
final class AppstatsSettings {
  private static final PayloadRenderer DEFAULT_RENDERER = new NullPayloadRenderer();

  private PayloadRenderer payloadRenderer;
  private UnprocessedFutureStrategy unprocessedFutureStrategy;
  private boolean calculateRpcCosts;
  private boolean datastoreDetails;
  private int maxLinesOfStackTrace;

  private AppstatsSettings() {}

  /**
   * We don't want partial initialization of this class. So any of the parameters not specified via
   * filter config should have a default value.
   */
  static AppstatsSettings withDefault() {
    AppstatsSettings settings = new AppstatsSettings();
    settings.setPayloadRenderer(DEFAULT_RENDERER);
    settings.setDatastoreDetails(false);
    settings.setCalculateRpcCosts(false);
    settings.setUnprocessedFutureStrategy(UnprocessedFutureStrategy.DO_NOTHING);
    settings.setMaxLinesOfStackTrace(Integer.MAX_VALUE);
    return settings;
  }

  PayloadRenderer getPayloadRenderer() {
    return payloadRenderer;
  }

  UnprocessedFutureStrategy getUnprocessedFutureStrategy() {
    return unprocessedFutureStrategy;
  }

  boolean isCalculateRpcCosts() {
    return calculateRpcCosts;
  }

  boolean isDatastoreDetails() {
    return datastoreDetails;
  }

  int getMaxLinesOfStackTrace() {
    return maxLinesOfStackTrace;
  }

  /**
   * Determines how request/response data should be rendered.
   */
  void setPayloadRenderer(String payloadRenderer) {
    try {
      this.payloadRenderer = (PayloadRenderer) Class.forName(payloadRenderer).newInstance();
    } catch (ReflectiveOperationException | ClassCastException e) {
      throw new IllegalArgumentException("Cannot instantiate payloadRenderer", e);
    }
  }

  void setUnprocessedFutureStrategy(String unprocessedFutureStrategy) {
    this.unprocessedFutureStrategy = UnprocessedFutureStrategy.valueOf(unprocessedFutureStrategy);
  }

  void setCalculateRpcCosts(String calculateRpcCosts) {
    this.calculateRpcCosts = Boolean.parseBoolean(calculateRpcCosts);
  }

  void setDatastoreDetails(String datastoreDetails) {
    this.datastoreDetails = Boolean.parseBoolean(datastoreDetails);
  }

  void setPayloadRenderer(PayloadRenderer payloadRenderer) {
    this.payloadRenderer = payloadRenderer;
  }

  void setUnprocessedFutureStrategy(UnprocessedFutureStrategy unprocessedFutureStrategy) {
    this.unprocessedFutureStrategy = unprocessedFutureStrategy;
  }

  void setCalculateRpcCosts(boolean calculateRpcCosts) {
    this.calculateRpcCosts = calculateRpcCosts;
  }

  void setDatastoreDetails(boolean datastoreDetails) {
    this.datastoreDetails = datastoreDetails;
  }
  /**
   * Sets the maximum lines of the stack trace that should be recorded.
   */
  void setMaxLinesOfStackTrace(int maxLinesOfStackTrace) {
    this.maxLinesOfStackTrace = maxLinesOfStackTrace;
  }
}
