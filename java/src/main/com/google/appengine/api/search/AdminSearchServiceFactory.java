package com.google.appengine.api.search;

import com.google.apphosting.api.AppEngineInternal;

/**
 * Builds {@link SearchService} instances that are pinned to a specific application and namespace
 * regardless of the "current" appId provided by {@code ApiProxy.getCurrentEnvironment().getAppId()}
 * and the "current" namespace provided by {@code NamespaceManager.get()}.
 * <p>
 * Note: users should not access this class directly.
 */
@AppEngineInternal
public final class AdminSearchServiceFactory {

  /**
   * Returns a {@link SearchService} that is pinned to a specific application and namespace. This
   * implementation ignores the "current" appId provided by
   * {@code ApiProxy.getCurrentEnvironment().getAppId()} and the "current" namespace provided by
   * {@code NamespaceManager.get()}.
   */
  public SearchService getSearchService(final String appId, SearchServiceConfig config) {
    if (appId == null) {
      throw new IllegalArgumentException();
    }
    if (config.getNamespace() == null) {
      throw new IllegalArgumentException();
    }
    SearchApiHelper helper = new SearchApiHelper(appId);
    return new SearchServiceImpl(helper, config);
  }
}
