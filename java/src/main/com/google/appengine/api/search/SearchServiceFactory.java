// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.api.search;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.spi.ServiceFactoryFactory;

/**
 * An factory that creates default implementation of {@link SearchService}.
 *
 * <pre>
 *   SearchService search = SearchServiceFactory.getSearchService();
 * </pre>
 *
 * Optionally, you may pass a {@link SearchServiceConfig} instance to customize
 * the search service. e.g, setting deadline and namespace:
 *
 * <pre>
 *   SearchServiceFactory.getSearchService(
 *       SearchServiceConfig.newBuilder().setDeadline(10.0).setNamespace("acme").build())
 * </pre>
 *
 */
public final class SearchServiceFactory {

  /**
   * Returns an instance of the {@link SearchService}.  The instance
   * will exist in the user provided namespace. The namespace must be
   * valid, as per {@link NamespaceManager#validateNamespace(String)}
   * method.
   *
   * @param namespace a namespace to be assigned to the returned
   * search service.
   * @return the default implementation of {@link SearchService}.
   * @throws IllegalArgumentException if the namespace is invalid
   * @deprecated Use {@link SearchServiceFactory#getSearchService(SearchServiceConfig)}
   */
  @Deprecated
  public static SearchService getSearchService(String namespace) {
    return getFactory().getSearchService(namespace);
  }

  /**
   * Returns an instance of the {@link SearchService} with the given config.
   *
   * @param config a {@link SearchServiceConfig} instance that describes the
   *   requested search service. If no namespace provided in config,
   *   NamespaceManager.get() will be used.
   * @return the default implementation of {@link SearchService}.
   */
  public static SearchService getSearchService(SearchServiceConfig config) {
    return getFactory().getSearchService(config);
  }

  /**
   * Equivalent to
   * {@link SearchServiceFactory#getSearchService(SearchServiceConfig)
   *   getSearchService(SearchServiceConfig.newBuilder().build())}.
   */
  public static SearchService getSearchService() {
    return getSearchService(SearchServiceConfig.newBuilder().build());
  }

  /**
   * No instances of this class may be created.
   */
  private SearchServiceFactory() {}

  private static ISearchServiceFactory getFactory() {
    return ServiceFactoryFactory.getFactory(ISearchServiceFactory.class);
  }
}
