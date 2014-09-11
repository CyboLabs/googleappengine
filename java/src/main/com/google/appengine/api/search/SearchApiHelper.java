package com.google.appengine.api.search;

import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.concurrent.Future;

/** Provides support for translation of calls between userland and appserver land. */
class SearchApiHelper {

  private static final String PACKAGE = "search";
  private final ByteString appIdOverride;

  SearchApiHelper() {
    this.appIdOverride = null;
  }

  SearchApiHelper(String appIdOverride) {
    this.appIdOverride = ByteString.copyFromUtf8(appIdOverride);
  }

  Future<SearchServicePb.DeleteDocumentResponse.Builder> makeAsyncDeleteDocumentCall(
      SearchServicePb.DeleteDocumentParams.Builder params, Double deadline) {
    SearchServicePb.DeleteDocumentRequest.Builder requestBuilder =
        SearchServicePb.DeleteDocumentRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.DeleteDocumentResponse.Builder responseBuilder =
        SearchServicePb.DeleteDocumentResponse.newBuilder();
    return makeAsyncCall("DeleteDocument", requestBuilder.build(), responseBuilder, deadline);
  }

  Future<SearchServicePb.DeleteSchemaResponse.Builder> makeAsyncDeleteSchemaCall(
      SearchServicePb.DeleteSchemaParams.Builder params, Double deadline) {
    SearchServicePb.DeleteSchemaRequest.Builder requestBuilder =
        SearchServicePb.DeleteSchemaRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.DeleteSchemaResponse.Builder responseBuilder =
        SearchServicePb.DeleteSchemaResponse.newBuilder();
    return makeAsyncCall("DeleteSchema", requestBuilder.build(), responseBuilder, deadline);
  }

  Future<SearchServicePb.IndexDocumentResponse.Builder> makeAsyncIndexDocumentCall(
      SearchServicePb.IndexDocumentParams.Builder params, Double deadline) {
    SearchServicePb.IndexDocumentRequest.Builder requestBuilder =
        SearchServicePb.IndexDocumentRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.IndexDocumentResponse.Builder responseBuilder =
        SearchServicePb.IndexDocumentResponse.newBuilder();
    return makeAsyncCall("IndexDocument", requestBuilder.build(), responseBuilder, deadline);
  }

  Future<SearchServicePb.ListDocumentsResponse.Builder> makeAsyncListDocumentsCall(
      SearchServicePb.ListDocumentsParams.Builder params, Double deadline) {
    SearchServicePb.ListDocumentsRequest.Builder requestBuilder =
        SearchServicePb.ListDocumentsRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.ListDocumentsResponse.Builder responseBuilder =
        SearchServicePb.ListDocumentsResponse.newBuilder();
    return makeAsyncCall("ListDocuments", requestBuilder.build(), responseBuilder, deadline);
  }

  Future<SearchServicePb.ListIndexesResponse.Builder> makeAsyncListIndexesCall(
      SearchServicePb.ListIndexesParams params, Double deadline) {
    SearchServicePb.ListIndexesRequest.Builder requestBuilder =
        SearchServicePb.ListIndexesRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.ListIndexesResponse.Builder responseBuilder =
        SearchServicePb.ListIndexesResponse.newBuilder();
    return makeAsyncCall("ListIndexes", requestBuilder.build(), responseBuilder, deadline);
  }

  Future<SearchServicePb.SearchResponse.Builder> makeAsyncSearchCall(
      SearchServicePb.SearchParams.Builder params, Double deadline) {
    SearchServicePb.SearchRequest.Builder requestBuilder =
        SearchServicePb.SearchRequest.newBuilder().setParams(params);
    if (appIdOverride != null) {
      requestBuilder.setAppId(appIdOverride);
    }
    SearchServicePb.SearchResponse.Builder responseBuilder =
        SearchServicePb.SearchResponse.newBuilder();
    return makeAsyncCall("Search", requestBuilder.build(), responseBuilder, deadline);
  }

  /**
   * Makes an asynchronous call.
   *
   * @param method the method on the API to call
   * @param request the request to forward to the API
   * @param responseBuilder the response builder used to fill the response
   * @param deadline the deadline of the call. if it is null, the default api deadline will be used
   */
  private <T extends GeneratedMessage.Builder<T>>
  Future<T> makeAsyncCall(String method, GeneratedMessage request, final T responseBuilder, Double deadline) {
    Future<byte[]> response;
    if (deadline == null) {
      response = ApiProxy.makeAsyncCall(PACKAGE, method, request.toByteArray());
    } else {
      ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
      apiConfig.setDeadlineInSeconds(deadline);
      response = ApiProxy.makeAsyncCall(PACKAGE, method, request.toByteArray(), apiConfig);
    }
    return new FutureWrapper<byte[], T>(response) {
      @Override
      protected T wrap(byte[] responseBytes) {
        if (responseBytes != null) {
          try {
            responseBuilder.mergeFrom(responseBytes);
          } catch (InvalidProtocolBufferException e) {
            throw new SearchServiceException(e.toString());
          }
        }
        return responseBuilder;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }
}
