// Copyright 2008 Google Inc. All Rights Reserved.
package com.google.appengine.api.datastore;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.datastore.DatastoreV3Pb.DatastoreService_3;
import com.google.apphosting.datastore.DatastoreV3Pb.Error;
import com.google.apphosting.datastore.DatastoreV4.DatastoreV4Service;
import com.google.io.protocol.ProtocolMessage;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.util.ConcurrentModificationException;
import java.util.concurrent.Future;

/**
 * Helper methods and constants shared by classes that implement the java api
 * on top of the datastore.
 * Note: user should not access this class directly.
 *
 */
public final class DatastoreApiHelper {

  static final String PACKAGE = "datastore_v3";
  static final String V4_PACKAGE = "datastore_v4";

  /**
   * Key to put in {@link ApiProxy.Environment#getAttributes()} to override the app id used by the
   * datastore api.  If absent, {@link ApiProxy.Environment#getAppId()} will be used.
   */
  @SuppressWarnings("javadoc")
  static final String APP_ID_OVERRIDE_KEY = "com.google.appengine.datastore.AppIdOverride";

  private DatastoreApiHelper() {}

  public static RuntimeException translateError(ApiProxy.ApplicationException exception) {
    Error.ErrorCode errorCode = Error.ErrorCode.valueOf(exception.getApplicationError());
    if (errorCode == null) {
      return new DatastoreFailureException(exception.getErrorDetail());
    }
    switch (errorCode) {
      case BAD_REQUEST:
        return new IllegalArgumentException(exception.getErrorDetail());

      case CONCURRENT_TRANSACTION:
        return new ConcurrentModificationException(exception.getErrorDetail());

      case NEED_INDEX:
        return new DatastoreNeedIndexException(exception.getErrorDetail());

      case TIMEOUT:
      case BIGTABLE_ERROR:
        return new DatastoreTimeoutException(exception.getErrorDetail());

      case COMMITTED_BUT_STILL_APPLYING:
        return new CommittedButStillApplyingException(exception.getErrorDetail());

      case INTERNAL_ERROR:
      default:
        return new DatastoreFailureException(exception.getErrorDetail());
    }
  }

  abstract static class AsyncCallWrapper<S, T> extends FutureWrapper<S, T> {
    AsyncCallWrapper(Future<S> response) {
      super(response);
    }

    @Override
    protected Throwable convertException(Throwable cause) {
      if (cause instanceof ApiProxy.ApplicationException) {
        return translateError((ApiProxy.ApplicationException) cause);
      }
      return cause;
    }
  }

  static <T extends ProtocolMessage<T>> Future<T> makeAsyncCall(ApiConfig apiConfig,
      DatastoreService_3.Method method, ProtocolMessage<?> request, final T responseProto) {
    Future<byte[]> response =
        ApiProxy.makeAsyncCall(PACKAGE, method.name(), request.toByteArray(), apiConfig);
    return new AsyncCallWrapper<byte[], T>(response) {
      @Override
      protected T wrap(byte[] responseBytes) {
        if (responseBytes != null) {
          responseProto.parseFrom(responseBytes);
        }
        return responseProto;
      }
    };
  }

  static <T extends MessageLite> Future<T> makeAsyncCall(ApiProxy.ApiConfig apiConfig,
      DatastoreV4Service.Method method, MessageLite request, Parser<T> responseParser) {
    return makeAsyncCall(apiConfig, method, request.toByteArray(), responseParser);
  }

  static <T extends MessageLite> Future<T> makeAsyncCall(ApiProxy.ApiConfig apiConfig,
      DatastoreV4Service.Method method, byte[] request, final Parser<T> responseParser) {
    Future<byte[]> response = ApiProxy.makeAsyncCall(V4_PACKAGE, method.name(), request, apiConfig);
    return new AsyncCallWrapper<byte[], T>(response) {
      @Override
      protected T wrap(byte[] responseBytes) throws Exception {
        if (responseBytes != null) {
          return responseParser.parseFrom(responseBytes);
        }
        return responseParser.parsePartialFrom(new byte[0]);
      }
    };
  }

  static String getCurrentAppId() {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment == null) {
      throw new NullPointerException("No API environment is registered for this thread.");
    }

    Object appIdOverride = environment.getAttributes().get(APP_ID_OVERRIDE_KEY);
    if (appIdOverride != null) {
      return (String) appIdOverride;
    }

    return environment.getAppId();
  }

  /**
   * Returns a new {@link AppIdNamespace} with the current appId and the namespace
   * registered with the {@link NamespaceManager}
   */
  static AppIdNamespace getCurrentAppIdNamespace() {
    return getCurrentAppIdNamespace(getCurrentAppId());
  }

  /**
   * Returns a new {@link AppIdNamespace} with the namespace currently
   * registered with the {@link NamespaceManager} for a given appid.
   */
  static AppIdNamespace getCurrentAppIdNamespace(String appId) {
    String namespace = NamespaceManager.get();
    namespace = namespace == null ? "" : namespace;
    return new AppIdNamespace(appId, namespace);
  }
}
