package com.google.appengine.tools.appstats;

import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.tools.appstats.Recorder.Clock;
import com.google.appengine.tools.appstats.Recorder.RecordWriter;
import com.google.appengine.tools.appstats.StatsProtos.IndividualRpcStatsProto;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;

/**
 * A utility class that exposes an instance of {@link Recorder.RecordWriter}. In addition
 * it also allows applications to record custom event via {@link Recording#startCustomRecording}.
 * <p>
 *
 * <pre>
 *  // Obtain the recording instance for this request thread.
 *  Recording recording = Recording.get();
 *  // Starts recording custom event into Appstats timeline for this thread.
 *  String key = recording.startCustomRecording(
 *      &quot;Class&quot;, &quot;Method&quot;, &quot;MethodParameters...&quot;);
 *  --- Execute custom method ---
 *  // Write this recording on Appstats timeline.
 *  recording.endCustomRecording(&quot;key&quot;,true);
 * </pre>
 */
public final class Recording {
  private final Recorder.RecordWriter writer;
  private final Clock clock = new Clock();
  private final AppstatsSettings settings;
  static final String RECORDING_KEY = Recording.class.getName() + ".recording_key";
  static final String CUSTOM_RECORDING_KEY = Recording.class.getName() + ".custom";

  public static Recording get() {
    Recording recording =
        (Recording) ApiProxy.getCurrentEnvironment().getAttributes().get(RECORDING_KEY);
    Preconditions.checkNotNull(recording, "Recording has not been initialized yet.");
    return recording;
  }

  Recording(AppstatsSettings settings) {
    this(settings, new MemcacheWriter(new Recorder.Clock(),
        MemcacheServiceFactory.getMemcacheService(MemcacheWriter.STATS_NAMESPACE)));
  }

  Recording(AppstatsSettings settings, Recorder.RecordWriter writer) {
    Preconditions.checkNotNull(settings);
    Preconditions.checkNotNull(writer);
    this.settings = settings;
    this.writer = writer;
  }

  Recorder.RecordWriter getWriter() {
    return writer;
  }

  long begin(Delegate<?> wrappedDelegate, Environment environment,
      HttpServletRequest request) {
    return writer.begin(wrappedDelegate, environment, request);
  }

  void write(Delegate<?> wrappedDelegate, Environment environment,
      IndividualRpcStatsProto.Builder record, long overheadWalltimeMillis,
      boolean correctStartOffset) {
    writer.write(wrappedDelegate, environment, record, overheadWalltimeMillis, correctStartOffset);
  }

  boolean commit(Delegate<?> wrappedDelegate, Environment environment, int httpResponseCode) {
    return writer.commit(wrappedDelegate, environment, httpResponseCode);
  }

  /**
   * Starts recording a custom event. It initializes a statistics proto, saves in current
   * environment and returns a key. This key can be used to finalize statistics after event has
   * finished.
   *
   * @param packageName package or class enclosing this custom method belongs to.
   * @param methodName the method being recorded.
   * @param recordParams should parameters be saved in Appstats.
   * @param params the input parameters to this method.
   * @return key that is used to store stats for this event.
   */
  public String startCustomRecording(String packageName, String methodName, boolean recordParams,
      Object... params) {
    long preNow = clock.currentTimeMillis();
    Environment environment = ApiProxy.getCurrentEnvironment();

    packageName = Strings.isNullOrEmpty(packageName) ? "custom" : packageName;
    methodName = Strings.isNullOrEmpty(methodName) ? "method" : methodName;

    RecordingData recordingData = new RecordingData();
    IndividualRpcStatsProto.Builder stats = IndividualRpcStatsProto.newBuilder();
    stats.setServiceCallName(packageName + "." + methodName);
    if (environment.getAttributes().containsKey(Recorder.CURRENT_NAMESPACE_KEY)) {
      stats.setNamespace(
            (String) environment.getAttributes().get(Recorder.CURRENT_NAMESPACE_KEY));
    }

    if (recordParams) {
      stats.setRequestDataSummary(
          settings.getPayloadRenderer().renderPayload(packageName, methodName, true, params));
    }

    Recorder.createStackTrace(2, stats, settings.getMaxLinesOfStackTrace());
    stats.setWasSuccessful(false);
    stats.setStartOffsetMilliseconds(clock.currentTimeMillis());
    recordingData.setStats(stats);
    if (!environment.getAttributes().containsKey(CUSTOM_RECORDING_KEY)) {
      environment.getAttributes().put(CUSTOM_RECORDING_KEY, Maps.newLinkedHashMap());
    }

    @SuppressWarnings("unchecked")
    Map<String, RecordingData> customRecordings =
        (Map<String, RecordingData>) environment.getAttributes().get(CUSTOM_RECORDING_KEY);
    String key = generateKey(stats.getServiceCallName(), preNow);
    recordingData.addOverhead(clock.currentTimeMillis() - preNow);
    customRecordings.put(key, recordingData);
    return key;
  }

  /**
   * Finalizes a custom recording.
   *
   * @param key the identifier for this recording event used to store stats.
   * @param wasSuccessful whether this method executed successfully.
   */
  public void endCustomRecording(String key, boolean wasSuccessful) {
    long preNow = clock.currentTimeMillis();
    Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment.getAttributes().containsKey(CUSTOM_RECORDING_KEY)) {
      @SuppressWarnings("unchecked")
      Map<String, RecordingData> customRecordings =
          (Map<String, RecordingData>) environment.getAttributes().get(CUSTOM_RECORDING_KEY);
      RecordingData recordingData = customRecordings.get(key);
      if (recordingData != null) {
        writeCustomRecording(environment, recordingData, preNow, wasSuccessful);
        customRecordings.remove(key);
      }
    }
  }

  /**
   * Writes an instance of {@link RecordingData} to {@link RecordWriter}.
   */
  private void writeCustomRecording(Environment environment, RecordingData recordingData,
      long preNow, boolean wasSuccessful) {
    Delegate<?> noDelegate = null;
    IndividualRpcStatsProto.Builder stats = recordingData.getStats();
    stats.setWasSuccessful(wasSuccessful);
    stats.setDurationMilliseconds(preNow - stats.getStartOffsetMilliseconds());
    recordingData.addOverhead(clock.currentTimeMillis() - preNow);
    write(noDelegate, environment, stats, recordingData.getOverhead(), true);
  }

  /**
   * Generates a key used to store custom stats in {@link Environment}. It appends 3 random digits
   * to timestamp in milliseconds to reduce chances of key collision.
   */
  private String generateKey(String prefix, long timeStamp) {
    prefix = Strings.isNullOrEmpty(prefix) ? "custom_key" : prefix;
    timeStamp = timeStamp * 1000 + new Random().nextInt(1000);
    return prefix + '-' + timeStamp;
  }

  /**
   * A cleanup method to write all pending custom recordings to Appstats before
   * finishing current request.
   */
  void finishCustomRecordings() {
    long preNow = clock.currentTimeMillis();
    Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment.getAttributes().containsKey(CUSTOM_RECORDING_KEY)) {
      @SuppressWarnings("unchecked")
      Map<String, RecordingData> customRecordings =
          (Map<String, RecordingData>) environment.getAttributes().get(CUSTOM_RECORDING_KEY);
      for (RecordingData recordingData : customRecordings.values()) {
        writeCustomRecording(environment, recordingData, preNow, false);
      }
      environment.getAttributes().remove(CUSTOM_RECORDING_KEY);
    }
  }
}
