// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.tools.appstats;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.DeadlineExceededException;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet filter that will time RPCs going to the server and collect statistics.
 * Add this filter to any servlet that you would like to monitor.
 *
 * The simplest way to configure an application for appstats collection is this:
 *
 * <p><pre>{@code
 *  <filter>
 *   <filter-name>appstats</filter-name>
 *   <filter-class>com.google.appengine.tools.appstats.AppstatsFilter</filter-class>
 *  </filter>
 *  <filter-mapping>
 *   <filter-name>appstats</filter-name>
 *   <url-pattern>/*</url-pattern>
 *  </filter-mapping>
 *  }</pre>
 *
 */
public class AppstatsFilter implements Filter {

  /**
   * Visible for testing
   */
  static final String DEADLINE_MESSAGE = "Deadline exceeded; cannot log app stats";

  /**
   * Visible for testing.
   */
  static Logger log = Logger.getLogger(AppstatsFilter.class.getName());

  /**
   * Name of the HTTP header that will be included in the response.
   */
  static final String TRACE_HEADER_NAME = "X-TraceUrl";

  /**
   * The default values for the basePath init parameter.
   */
  private static final String DEFAULT_BASE_PATH = "/appstats/";

  /**
   * Threadsafe utility to manage appstats writes.
   *
   */
  private Recording recording;

  /**
   * The delegate that was wrapped when the WRITER was created.
   * Visible for testing.
   */
  static Delegate<?> delegate;

  /**
   * The base path where the AppStats dashboard can be found. This is provided
   * as an init parameter.
   */
  private String basePath;

  /**
   * A log messsage that may be used to store a link back to app stats.
   * The ID is referred to as {ID}.
   */
  private String logMessage;

  private Recorder recorder;

  public AppstatsFilter() {
  }

  /**
   * Visible for testing
   */
  AppstatsFilter(String basePath, String logMessage, Recording recording) {
    this.basePath = basePath;
    this.logMessage = logMessage;
    this.recording = recording;
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filters)
      throws IOException, ServletException {
    Preconditions.checkNotNull(recording, "recording shouldn't be null");
    Environment environment = getCurrentEnvironment();
    Long id = recording.begin(delegate, environment, (HttpServletRequest) request);
    final HttpServletResponse innerResponse = (HttpServletResponse) response;
    final int[] responseCode = {0};

    innerResponse.addHeader(TRACE_HEADER_NAME,
        basePath + "details?time=" + id + "&type=json");

    InvocationHandler invocationHandler =  new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("sendError") || method.getName().equals("setStatus")) {
          responseCode[0] = ((int) args[0]);
        } else if (method.getName().equals("sendRedirect")) {
          responseCode[0] = HttpServletResponse.SC_TEMPORARY_REDIRECT;
        }
        return call(method, innerResponse, args);
      }
    };
    final HttpServletResponse outerResponse = (HttpServletResponse) Proxy.newProxyInstance(
        AppstatsFilter.class.getClassLoader(),
        new Class<?>[] {HttpServletResponse.class},
        invocationHandler);
    try {
      filters.doFilter(request, outerResponse);
    } catch (DeadlineExceededException e) {
      id = null;
      log.warning(DEADLINE_MESSAGE);
      throw e;
    } finally {
      if (id != null) {
        if (recorder != null) {
          recorder.processAsyncRpcs(environment);
        }
        recording.finishCustomRecordings();
        boolean didCommit = recording.commit(delegate, environment, responseCode[0]);
        if (logMessage != null && didCommit) {
          log.info(logMessage.replace("{ID}", "" + id));
        }
      }
    }
  }

  private static String getAppStatsPathFromConfig(FilterConfig config) {
    String path = config.getInitParameter("basePath");
    if (path == null) {
      return DEFAULT_BASE_PATH;
    } else {
      return path.endsWith("/") ? path : path + "/";
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public synchronized void init(FilterConfig config) {
    if (recording == null) {
      AppstatsSettings settings = initializeSettings(config);
      recording = new Recording(settings);
      getCurrentEnvironment().getAttributes().put(Recording.RECORDING_KEY, recording);
      Recorder.RecordWriter newWriter = recording.getWriter();
      delegate = ApiProxy.getDelegate();

      recorder = new Recorder(delegate, newWriter, settings);
      ApiProxy.setDelegate(wrapPartially(delegate, recorder));
    }
    basePath = getAppStatsPathFromConfig(config);
    logMessage = config.getInitParameter("logMessage");
  }

  /**
   * Create a proxy that implements all the interfaces that the original
   * implements. Whenever a method is called that the wrapper supports,
   * the wrapper will be called. Otherwise, the method will be invoked on
   * the original object.
   */
  static <S, T extends S> S wrapPartially(final S original, final T wrapper) {

    if (!original.getClass().getName().contains("Local")) {
      return wrapper;
    }

    Class<?>[] interfaces = original.getClass().getInterfaces();
    InvocationHandler handler = new InvocationHandler(){
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Method wrapperMethod = null;
        try {
          wrapperMethod = wrapper.getClass().getMethod(
              method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
          return call(method, original, args);
        }
        return call(wrapperMethod, wrapper, args);
      }};
    @SuppressWarnings("unchecked")
    S ret = (S) Proxy.newProxyInstance(original.getClass().getClassLoader(), interfaces, handler);
    return ret;
  }

  /**
   * Invoke a method and unwrap exceptions the invoked method may throw.
   */
  private static Object call(Method m, Object o, Object[] args) throws Throwable {
    try {
      return m.invoke(o, args);
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    }
  }

  /**
   * Visible for testing
   */
  Environment getCurrentEnvironment() {
    return ApiProxy.getCurrentEnvironment();
  }

  static AppstatsSettings initializeSettings(FilterConfig config) {
    Preconditions.checkNotNull(config, "FilterConfig can not be null.");
    AppstatsSettings settings = AppstatsSettings.withDefault();
    settings.setMaxLinesOfStackTrace(
        getPositiveInt(config, "maxLinesOfStackTrace", Integer.MAX_VALUE));
    if (config.getInitParameter("payloadRenderer") != null) {
      settings.setPayloadRenderer(config.getInitParameter("payloadRenderer"));
    }
    if (config.getInitParameter("onPendingAsyncCall") != null) {
      settings.setUnprocessedFutureStrategy(config.getInitParameter("onPendingAsyncCall"));
    }
    if (config.getInitParameter("calculateRpcCosts") != null) {
      settings.setCalculateRpcCosts(config.getInitParameter("calculateRpcCosts"));
    }
    if (config.getInitParameter("datastoreDetails") != null) {
      settings.setDatastoreDetails(config.getInitParameter("datastoreDetails"));
    }

    return settings;
  }

  static int getPositiveInt(FilterConfig config, String key, int defaultValue) {
    int result = defaultValue;
    String stringValue = config.getInitParameter(key);
    if (stringValue != null) {
      result = Integer.parseInt(stringValue);
      if (result <= 0) {
        throw new IllegalArgumentException(key + " must be a positive value");
      }
    }
    return result;
  }
}
