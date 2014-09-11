package com.google.apphosting.utils.config;

import static com.google.common.base.Preconditions.checkNotNull;

import net.sourceforge.yamlbeans.YamlConfig;
import net.sourceforge.yamlbeans.YamlException;
import net.sourceforge.yamlbeans.YamlWriter;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;
import java.io.StringWriter;
import java.util.ArrayList;

/**
 * Utility for making client deploy yaml.
 * <p>
 * Keep in sync with apphosting/api/client_deployinfo.py.
 */
public class ClientDeployYamlMaker {
  public static final String UNKNOWN_RUNTIME = "unknown";

  private String runtime = UNKNOWN_RUNTIME;
  private final long startTimeUsec;
  private final ArrayList<Request> requests = new ArrayList<Request>();
  private final String sdkVersion;

  /**
   * Constructs a {@link ClientDeployYamlMaker} for logging an attempted deployment of
   * a module version to appengine.
   *
   * @param startTimeUsec The start time of the deployment in micro seconds.
   * @param sdkVersion The version of the client SDK.
   */
  public ClientDeployYamlMaker(long startTimeUsec, String sdkVersion) {
    this.startTimeUsec = startTimeUsec;
    this.sdkVersion = checkNotNull(sdkVersion, "sdkVersion may not be null");
  }

  /**
   * Sets the runtime.
   */
  public void setRuntime(String runtime) {
    this.runtime = checkNotNull(runtime, "runtime may not be null");
  }

  /**
   * Adds a record of an HTTP request to include in the yaml returned by
   * {@link #make(long, boolean)}
   *
   * @param path The path for the request.
   * @param responseCode The HTTP response code for the request.
   * @param startTimeUsec The start time for the request in micro seconds.
   * @param endTimeUsec The end time for the request in micro seconds.
   * @param requestSizeBytes The size of the payload for the request.
   */
  public void addRequest(String path, int responseCode, long startTimeUsec, long endTimeUsec,
      long requestSizeBytes) {
    requests.add(new Request(checkNotNull(path, "path may not be null"), responseCode,
        startTimeUsec, endTimeUsec, requestSizeBytes));
  }

  /**
   * Returns the runtime.
   */
  public String getRuntime() {
    return runtime;
  }

  /**
   * Returns the sdk version.
   */
  public String getSdkVersion() {
    return sdkVersion;
  }

  /**
   * Returns client deploy yaml suitable for logging a deployment attempt for
   * a single module version.
   *
   * @param endTimeUsec The time in micro seconds when the deployment completed.
   * @param success True iff the deployment succeeds
   * @return A {@link String} with the requested yaml.
   * @throws YamlException If yaml formatting fails.
   */
  public String make(long endTimeUsec, boolean success) throws YamlException {
    StringWriter stringWriter = new StringWriter();
    YamlConfig yamlConfig = new YamlConfig();
    yamlConfig.writeConfig.setIndentSize(2);
    yamlConfig.writeConfig.setWriteRootTags(false);
    yamlConfig.writeConfig.setWriteRootElementTags(false);
    yamlConfig.writeConfig.setWriteDefaultValues(true);
    yamlConfig.setPropertyElementType(ClientDeploy.class, "requests", Request.class);
    YamlWriter yamlWriter = new YamlWriter(stringWriter, yamlConfig);
    ClientDeploy clientDeploy = new ClientDeploy(runtime, startTimeUsec, endTimeUsec, requests,
        success, sdkVersion);

    yamlWriter.write(clientDeploy);
    yamlWriter.close();
    return stringWriter.toString();
  }

  /**
   * A holder class for representing a http request within a client deployment for use with
   * {@link YamlWriter}.
   * <p>
   * Please do not reference {@link ClientDeploy} objects directly. This class is public
   * to meet the needs of {@link YamlWriter} but should not be used outside its containing class.
   */
  public static class Request {
    private String path;
    private int responseCode;
    private long startTimeUsec;
    private long endTimeUsec;
    private long requestSizeBytes;

    public Request() {
    }

    public Request(String path, int responseCode, long startTimeUsec,
        long endTimeUsec, long requestSizeBytes) {
      this.path = path;
      this.responseCode = responseCode;
      this.startTimeUsec = startTimeUsec;
      this.endTimeUsec = endTimeUsec;
      this.requestSizeBytes = requestSizeBytes;
    }

    public String getPath() {
      return path;
    }

    public void setPath(@SuppressWarnings("unused") String path) {
      throw new UnsupportedOperationException();
    }

    public int getResponseCode() {
      return responseCode;
    }

    public void setResponseCode(@SuppressWarnings("unused") int responseCode) {
      throw new UnsupportedOperationException();
    }

    public long getStartTimeUsec() {
      return startTimeUsec;
    }

    public void setStartTimeUsec(@SuppressWarnings("unused") long startTimeUsec) {
      throw new UnsupportedOperationException();
    }

    public long getEndTimeUsec() {
      return endTimeUsec;
    }

    public void setEndTimeUsec(@SuppressWarnings("unused") long endTimeUsec) {
      throw new UnsupportedOperationException();
    }

    public long getRequestSizeBytes() {
      return requestSizeBytes;
    }

    public void setRequestSizeBytes(@SuppressWarnings("unused") long requestSizeBytes) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * BeanInfo to map Request field names to client deploy yaml names which happen to not be
   * conforming Google java names.
   */
  public static class RequestBeanInfo extends SimpleBeanInfo {
    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
      try {
        return new PropertyDescriptor[] {
            new PropertyDescriptor("path", Request.class),
            new PropertyDescriptor("response_code", Request.class, "getResponseCode",
                "setResponseCode"),
            new PropertyDescriptor("start_time_usec", Request.class, "getStartTimeUsec",
                "setStartTimeUsec"),
            new PropertyDescriptor("end_time_usec", Request.class, "getEndTimeUsec",
                "setEndTimeUsec"),
            new PropertyDescriptor("request_size_bytes", Request.class, "getRequestSizeBytes",
                "setRequestSizeBytes")};
      } catch (IntrospectionException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  /**
   * A holder class for representing a client deployment for use with {@link YamlWriter}.
   * <p>
   * Please do not reference {@link ClientDeploy} objects directly. This class is public
   * to meet the needs of {@link YamlWriter} but should not be used its containing class.
   */
  public static class ClientDeploy {

    private String runtime;
    private long startTimeUsec;
    private long endTimeUsec;
    private ArrayList<Request> requests;
    private boolean success;
    private String sdkVersion;

    public ClientDeploy(String runtime, long startTimeUsec, long endTimeUsec,
        ArrayList<Request> requests, boolean success, String sdkVersion) {
      this.runtime = runtime;
      this.startTimeUsec = startTimeUsec;
      this.endTimeUsec = endTimeUsec;
      this.requests = requests;
      this.success = success;
      this.sdkVersion = sdkVersion;
    }

    public ClientDeploy() {
    }

    public String getRuntime() {
      return runtime;
    }

    public void setRuntime(@SuppressWarnings("unused") String runtime) {
      throw new UnsupportedOperationException();
    }

    public long getStartTimeUsec() {
      return startTimeUsec;
    }

    public void setStartTimeUsec(@SuppressWarnings("unused") long startTimeUsec) {
      throw new UnsupportedOperationException();
    }

    public long getEndTimeUsec() {
      return endTimeUsec;
    }

    public void setEndTimeUsec(@SuppressWarnings("unused") long endTimeUsec) {
      throw new UnsupportedOperationException();
    }

    public ArrayList<Request> getRequests() {
      return requests;
    }

    public void setRequests(@SuppressWarnings("unused") ArrayList<Request> requests) {
      throw new UnsupportedOperationException();
    }

    public boolean isSuccess() {
      return success;
    }

    public void setSuccess(@SuppressWarnings("unused") boolean success) {
      throw new UnsupportedOperationException();
    }

    public String getSdkVersion() {
      return sdkVersion;
    }

    public void setSdkVersion(@SuppressWarnings("unused") String sdkVersion) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * BeanInfo to map ClientDeploy field names to client deploy yaml names which happen to not be
   * conforming Google java names.
   */
  public static class ClientDeployBeanInfo extends SimpleBeanInfo {
    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
      try {
        return new PropertyDescriptor[] {
            new PropertyDescriptor("runtime", ClientDeploy.class),
            new PropertyDescriptor("start_time_usec", ClientDeploy.class, "getStartTimeUsec",
                "setStartTimeUsec"),
            new PropertyDescriptor("end_time_usec", ClientDeploy.class, "getEndTimeUsec",
                "setEndTimeUsec"),
            new PropertyDescriptor("requests", ClientDeploy.class),
            new PropertyDescriptor("success", ClientDeploy.class),
            new PropertyDescriptor("sdk_version", ClientDeploy.class, "getSdkVersion",
                "setSdkVersion")};
      } catch (IntrospectionException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

}
