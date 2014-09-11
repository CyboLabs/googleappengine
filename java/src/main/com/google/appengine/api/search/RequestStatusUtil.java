package com.google.appengine.api.search;

import com.google.apphosting.api.AppEngineInternal;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.net.util.error.Codes.Code;

/**
 * Collection of utility methods for SearchServicePb.RequestStatus.
 */
@AppEngineInternal
public final class RequestStatusUtil {

  /**
   * Mapping of search service error to general Canonical Errors.
   */
  private static final ImmutableMap<SearchServicePb.SearchServiceError.ErrorCode, Code>
      REQUEST_STATUS_TO_CANONICAL_ERROR_MAPPING =
      ImmutableMap.<SearchServicePb.SearchServiceError.ErrorCode, Code>builder()
      .put(SearchServicePb.SearchServiceError.ErrorCode.OK, Code.OK)
      .put(SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST, Code.INVALID_ARGUMENT)
      .put(SearchServicePb.SearchServiceError.ErrorCode.TRANSIENT_ERROR, Code.UNAVAILABLE)
      .put(SearchServicePb.SearchServiceError.ErrorCode.INTERNAL_ERROR, Code.INTERNAL)
      .put(SearchServicePb.SearchServiceError.ErrorCode.PERMISSION_DENIED, Code.PERMISSION_DENIED)
      .put(SearchServicePb.SearchServiceError.ErrorCode.TIMEOUT, Code.DEADLINE_EXCEEDED)
      .put(SearchServicePb.SearchServiceError.ErrorCode.CONCURRENT_TRANSACTION, Code.ABORTED)
      .build();

  /**
   * Converts SearchServicePb.SearchServiceError.ErrorCode to canonical error code.
   */
  public static Code toCanonicalCode(SearchServicePb.SearchServiceError.ErrorCode appCode) {
    return Preconditions.checkNotNull(REQUEST_STATUS_TO_CANONICAL_ERROR_MAPPING.get(appCode));
  }

  /**
   * Creates a SearchServicePb.RequestStatus.Builder from the given code and message.
   */
  public static SearchServicePb.RequestStatus.Builder newStatusBuilder(
      SearchServicePb.SearchServiceError.ErrorCode code, String message) {
    SearchServicePb.RequestStatus.Builder builder = SearchServicePb.RequestStatus.newBuilder();
    builder.setCode(code).setCanonicalCode(toCanonicalCode(code).getNumber());
    if (message != null) {
      builder.setErrorDetail(message);
    }
    return builder;
  }

  /**
   * Creates a SearchServicePb.RequestStatus from the given code and message.
   */
  public static SearchServicePb.RequestStatus newStatus(
      SearchServicePb.SearchServiceError.ErrorCode code, String message) {
    return newStatusBuilder(code, message).build();
  }

  /**
   * Creates a SearchServicePb.RequestStatus from the given code.
   */
  public static SearchServicePb.RequestStatus newStatus(
      SearchServicePb.SearchServiceError.ErrorCode code) {
    return newStatusBuilder(code, null).build();
  }

  /**
   * Creates a RequestStatus message suitable for reporting an invalid request.
   */
  public static SearchServicePb.RequestStatus newInvalidRequestStatus(IllegalArgumentException e) {
    Preconditions.checkNotNull(e.getMessage());
    return newStatus(SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST, e.getMessage());
  }

  /**
   * Creates a RequestStatus message suitable for reporting an unknown index. We use
   * {@link SearchServicePb.SearchServiceError.ErrorCode#OK} because the unknown index isn't
   * an error condition but just a notice to the user.
   */
  public static SearchServicePb.RequestStatus newUnknownIndexStatus(
      SearchServicePb.IndexSpec indexSpec) {
    return newStatus(SearchServicePb.SearchServiceError.ErrorCode.OK, String.format(
        "Index '%s' in namespace '%s' does not exist",
        indexSpec.getName(), indexSpec.getNamespace()));
  }
}
