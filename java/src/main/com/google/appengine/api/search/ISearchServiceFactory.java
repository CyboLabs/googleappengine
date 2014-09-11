// Copyright 2012 Google Inc. All rights reserved.

package com.google.appengine.api.search;

/**
 * A factory that creates default implementation of {@link SearchService}.
 *
 */
public interface ISearchServiceFactory {

  /**
   * Returns an instance of the {@link SearchService}.  The instance
   * will exist in the user provided namespace. The namespace must be
   * valid, as per {@link NamespaceManager#validateNamespace(String)}
   * method. Equivalent to
   * <code>
   * getSearchService(SearchServiceConfig.newBuilder().setNamespace(namespace).build())
   * </code>
   *
   * @param namespace a namespace to be assigned to the returned
   * search service.
   * @return the default implementation of {@link SearchService}.
   * @throws IllegalArgumentException if the namespace is invalid
   * @deprecated Use {@link ISearchServiceFactory#getSearchService(SearchServiceConfig)}
   */
  @Deprecated
  SearchService getSearchService(String namespace);

  /**
   * Returns an instance of the {@link SearchService} with the given config.
   *
   * @param config a {@link SearchServiceConfig} instance that describes the
   *   requested search service. If no namespace provided in config,
   *   NamespaceManager.get() will be used.
   * will be used.
   * @return the default implementation of {@link SearchService}.
   */
  SearchService getSearchService(SearchServiceConfig config);
}
