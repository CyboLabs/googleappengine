package com.google.appengine.api.search;

import com.google.appengine.api.NamespaceManager;

/**
 * Configuration options for Search API.
 */
public final class SearchServiceConfig {

  private final Double deadline;
  private final String namespace;

  private SearchServiceConfig(SearchServiceConfig.Builder builder) {
    deadline = builder.deadline;
    namespace = builder.namespace;
  }

  /**
   * Builder for {@link SearchServiceConfig}.
   */
  public static final class Builder {

    private Double deadline;
    private String namespace;

    private Builder(SearchServiceConfig config) {
      deadline = config.deadline;
      namespace = config.namespace;
    }

    private Builder() {
    }

    public SearchServiceConfig.Builder setDeadline(Double deadlineInSeconds)
        throws SearchServiceException {
      if (deadlineInSeconds != null && deadlineInSeconds <= 0.0) {
        throw new IllegalArgumentException("Invalid Deadline. Must be a positive number.");
      }
      this.deadline = deadlineInSeconds;
      return this;
    }

    public SearchServiceConfig.Builder setNamespace(String namespace) {
      if (namespace != null) {
        NamespaceManager.validateNamespace(namespace);
      }
      this.namespace = namespace;
      return this;
    }

    /**
     * Builds a configuration.
     *
     * @return the configuration built by this builder
     */
    public SearchServiceConfig build() {
      return new SearchServiceConfig(this);
    }
  }

  /**
   * Creates a new {@link SearchServiceConfig.Builder}.
   *
   * @return the newly created {@link SearchServiceConfig.Builder} instance
   */
  public static SearchServiceConfig.Builder newBuilder() {
    return new SearchServiceConfig.Builder();
  }

  /**
   * Converts this config instance to a builder.
   *
   * @return the newly created {@link SearchServiceConfig.Builder} instance
   */
  public SearchServiceConfig.Builder toBuilder() {
    return new SearchServiceConfig.Builder(this);
  }

  /**
   * Returns the API deadline in seconds.
   *
   * @return the deadline in seconds or null if no deadline has been set
   */
  public Double getDeadline() {
    return deadline;
  }

  public String getNamespace() {
    return namespace;
  }
}
