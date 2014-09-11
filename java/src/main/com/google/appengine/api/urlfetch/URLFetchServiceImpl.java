// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.urlfetch;

import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest.RequestMethod;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchResponse;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchResponse.Header;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchServiceError.ErrorCode;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.net.ssl.SSLHandshakeException;

class URLFetchServiceImpl implements URLFetchService {
  static final String PACKAGE = "urlfetch";

  private static final Logger logger = Logger.getLogger(URLFetchServiceImpl.class.getName());

  @Override
  public HTTPResponse fetch(URL url) throws IOException {
    return fetch(new HTTPRequest(url));
  }

  @Override
  public HTTPResponse fetch(HTTPRequest request) throws IOException {
    byte[] responseBytes;
    try {
      responseBytes = ApiProxy.makeSyncCall(
          PACKAGE, "Fetch",
          convertToPb(request).toByteArray(),
          createApiConfig(request.getFetchOptions()));
    } catch (ApiProxy.RequestTooLargeException ex) {
      throw new IOException("The request exceeded the maximum permissible size");
    } catch (ApiProxy.ApplicationException ex) {
      Throwable cause = convertApplicationException(request.getURL(), ex);
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else if (cause instanceof IOException) {
        throw (IOException) cause;
      } else {
        throw new RuntimeException(cause);
      }
    } catch (ApiProxy.ApiDeadlineExceededException ex) {
      throw new SocketTimeoutException("Timeout while fetching: " + request.getURL());
    }

    URLFetchResponse responseProto = URLFetchResponse.newBuilder().mergeFrom(responseBytes).build();
    if (!request.getFetchOptions().getAllowTruncate() && responseProto.getContentWasTruncated()) {
      throw new ResponseTooLargeException(request.getURL().toString());
    }
    return convertFromPb(responseProto);
  }

  @Override
  public Future<HTTPResponse> fetchAsync(URL url) {
    return fetchAsync(new HTTPRequest(url));
  }

  @Override
  public Future<HTTPResponse> fetchAsync(HTTPRequest request) {
    final FetchOptions fetchOptions = request.getFetchOptions();
    final URL url = request.getURL();

    Future<byte[]> response = ApiProxy.makeAsyncCall(PACKAGE, "Fetch",
        convertToPb(request).toByteArray(), createApiConfig(fetchOptions));

    return new FutureWrapper<byte[], HTTPResponse>(response) {
      @Override
      protected HTTPResponse wrap(byte[] responseBytes) throws IOException {
        URLFetchResponse responseProto =
            URLFetchResponse.newBuilder().mergeFrom(responseBytes).build();
        if (!fetchOptions.getAllowTruncate() && responseProto.getContentWasTruncated()) {
          throw new ResponseTooLargeException(url.toString());
        }
        return convertFromPb(responseProto);
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        if (cause instanceof ApiProxy.ApplicationException) {
          return convertApplicationException(url, (ApiProxy.ApplicationException) cause);
        } else if (cause instanceof ApiProxy.ApiDeadlineExceededException) {
          return new SocketTimeoutException("Timeout while fetching: " + url);
        }
        return cause;
      }
    };
  }

  private ApiProxy.ApiConfig createApiConfig(FetchOptions options) {
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    apiConfig.setDeadlineInSeconds(options.getDeadline());
    return apiConfig;
  }

  private String getURLExceptionMessage(String formatString, String url, String errorDetail) {
    if (errorDetail == null || errorDetail.trim().isEmpty()) {
      return String.format(formatString, url);
    } else {
      return String.format(formatString + ", error: %s", url, errorDetail);
    }
  }

  private Throwable convertApplicationException(URL requestUrl,
                                                ApiProxy.ApplicationException ex) {
    String urlString = requestUrl.toString();
    ErrorCode errorCode = ErrorCode.valueOf(ex.getApplicationError());
    String errorDetail = ex.getErrorDetail();
    switch (errorCode) {
      case INVALID_URL:
        return new MalformedURLException(
            getURLExceptionMessage("Invalid URL specified: %s", urlString, errorDetail));
      case CLOSED:
        return new IOException(getURLExceptionMessage(
            "Connection closed unexpectedly by server at URL: %s", urlString, null));
      case TOO_MANY_REDIRECTS:
        return new IOException(getURLExceptionMessage(
            "Too many redirects at URL: %s with redirect=true", urlString, null));
      case MALFORMED_REPLY:
        return new IOException(getURLExceptionMessage(
            "Malformed HTTP reply received from server at URL: %s", urlString, errorDetail));
      case RESPONSE_TOO_LARGE:
        return new ResponseTooLargeException(urlString);
      case DNS_ERROR:
        return new UnknownHostException(getURLExceptionMessage(
            "DNS host lookup failed for URL: %s", urlString, null));
      case FETCH_ERROR:
        return new IOException(getURLExceptionMessage(
            "Could not fetch URL: %s", urlString, errorDetail));
      case INTERNAL_TRANSIENT_ERROR:
        return new InternalTransientException(urlString);
      case DEADLINE_EXCEEDED:
        return new SocketTimeoutException(getURLExceptionMessage(
            "Timeout while fetching URL: %s", urlString, null));
      case SSL_CERTIFICATE_ERROR:
        return new SSLHandshakeException(getURLExceptionMessage(
            "Could not verify SSL certificate for URL: %s", urlString, null));
      case UNSPECIFIED_ERROR:
      default:
        return new IOException(ex.getErrorDetail());
    }
  }

  private URLFetchRequest convertToPb(HTTPRequest request) {
    URLFetchRequest.Builder requestProto = URLFetchRequest.newBuilder();
    requestProto.setUrl(request.getURL().toExternalForm());

    byte[] payload = request.getPayload();
    if (payload != null) {
      requestProto.setPayload(ByteString.copyFrom(payload));
    }

    switch(request.getMethod()) {
      case GET:
        requestProto.setMethod(RequestMethod.GET);
        break;
      case POST:
        requestProto.setMethod(RequestMethod.POST);
        break;
      case HEAD:
        requestProto.setMethod(RequestMethod.HEAD);
        break;
      case PUT:
        requestProto.setMethod(RequestMethod.PUT);
        break;
      case DELETE:
        requestProto.setMethod(RequestMethod.DELETE);
        break;
      case PATCH:
        requestProto.setMethod(RequestMethod.PATCH);
        break;
      default:
        throw new IllegalArgumentException("unknown method: " + request.getMethod());
    }

    for (HTTPHeader header : request.getHeaders()) {
      URLFetchRequest.Header.Builder headerProto = URLFetchRequest.Header.newBuilder();
      headerProto.setKey(header.getName());
      headerProto.setValue(header.getValue());
      requestProto.addHeader(headerProto);
    }

    requestProto.setFollowRedirects(
        request.getFetchOptions().getFollowRedirects());

    switch (request.getFetchOptions().getCertificateValidationBehavior()) {
      case VALIDATE:
        requestProto.setMustValidateServerCertificate(true);
        break;
      case DO_NOT_VALIDATE:
        requestProto.setMustValidateServerCertificate(false);
        break;
      default:
    }

    return requestProto.build();
  }

  private HTTPResponse convertFromPb(URLFetchResponse responseProto) {
    HTTPResponse response = new HTTPResponse(responseProto.getStatusCode());
    if (responseProto.hasContent()) {
      response.setContent(responseProto.getContent().toByteArray());
    }

    for (Header header : responseProto.getHeaderList()) {
      response.addHeader(header.getKey(), header.getValue());
    }

    if (responseProto.hasFinalUrl() &&
        responseProto.getFinalUrl().length() > 0) {
      try {
        response.setFinalUrl(new URL(responseProto.getFinalUrl()));
      } catch (MalformedURLException e) {
        logger.severe("malformed final URL: " + e);
      }
    }

    return response;
  }
}
