package com.google.appengine.api.labs.datastore.overlay;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.datastore.AsyncDatastoreService;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceConfig;
import com.google.appengine.api.datastore.DatastoreServiceFactory;

/**
 * A factory for creating overlay-based {@link DatastoreService} implementations.
 */
public final class OverlayDatastoreServiceFactory {
  /**
   * Creates an overlay-based {@link DatastoreService} using {@code config}, where a single
   * Datastore holds both original and overlay data. The data is segregated using namespaces.
   */
  public static DatastoreService getNamespaceBasedOverlayDatastoreService(
      DatastoreServiceConfig config) {
    checkNotNull(config);
    return getNamespaceBasedOverlayDatastoreService(
        DatastoreServiceFactory.getDatastoreService(config));
  }

  /**
   * Creates an overlay-based {@link DatastoreService} using the default config, where a single
   * Datastore holds both original and overlay data. The data is segregated using namespaces.
   */
  public static DatastoreService getNamespaceBasedOverlayDatastoreService() {
    return getNamespaceBasedOverlayDatastoreService(DatastoreServiceFactory.getDatastoreService());
  }

  /**
   * Creates an overlay-based {@link DatastoreService} using {@code datastore},
   * where a single Datastore holds both original and overlay data. The data is segregated using
   * namespaces.
   */
  public static DatastoreService getNamespaceBasedOverlayDatastoreService(
      DatastoreService datastore) {
    checkNotNull(datastore);
    return new OverlayDatastoreServiceImpl(
        new NamespacePinnedDatastoreServiceImpl(datastore, OverlayUtils.OVERLAY_NAMESPACE, true),
        datastore);
  }

  /**
   * Creates an overlay-based {@link AsyncDatastoreService} using {@code config}, where a
   * single Datastore holds both original and overlay data. The data is segregated using namespaces.
   */
  public static AsyncDatastoreService getNamespaceBasedOverlayAsyncDatastoreService(
      DatastoreServiceConfig config) {
    checkNotNull(config);
    return getNamespaceBasedOverlayAsyncDatastoreService(
        DatastoreServiceFactory.getAsyncDatastoreService(config));
  }

  /**
   * Creates an overlay-based {@link AsyncDatastoreService} using the default config, where a single
   * Datastore holds both original and overlay data. The data is segregated using namespaces.
   */
  public static AsyncDatastoreService getNamespaceBasedOverlayAsyncDatastoreService() {
    return getNamespaceBasedOverlayAsyncDatastoreService(
        DatastoreServiceFactory.getAsyncDatastoreService());
  }

  /**
   * Creates an overlay-based {@link AsyncDatastoreService} using {@code datastore}, where a single
   * Datastore holds both original and overlay data. The data is segregated using namespaces.
   */
  public static AsyncDatastoreService getNamespaceBasedOverlayAsyncDatastoreService(
      AsyncDatastoreService datastore) {
    checkNotNull(datastore);
    return new OverlayAsyncDatastoreServiceImpl(new NamespacePinnedAsyncDatastoreServiceImpl(
        datastore, OverlayUtils.OVERLAY_NAMESPACE, true), datastore);
  }
}
