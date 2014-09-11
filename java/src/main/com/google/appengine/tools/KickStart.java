// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appengine.tools;

import com.google.appengine.tools.admin.OutputPump;
import com.google.appengine.tools.development.DevAppServerMain;
import com.google.appengine.tools.info.SdkInfo;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Launches a process in an operating-system agnostic way. Helps us avoid
 * idiosyncrasies in scripts for different platforms. Currently this only
 * works for DevAppServerMain.
 *
 * Takes a command line invocation like:
 *
 * <pre>
 * java -cp ../lib/appengine-tools-api.jar com.google.appengine.tools.KickStart \
 *   --jvm_flag="-Dlog4j.configuration=log4j.props"
 *   com.google.appengine.tools.development.DevAppServerMain \
 *   --jvm_flag="-agentlib:jdwp=transport=dt_socket,server=y,address=7000"
 *   --address=localhost --port=5005 appDir
 * </pre>
 *
 * and turns it into:
 *
 * <pre>
 * java -cp &lt;an_absolute_path&gt;/lib/appengine-tools-api.jar \
 *   -Dlog4j.configuration=log4j.props \
 *   -agentlib:jdwp=transport=dt_socket,server=y,address=7000 \
 *   com.google.appengine.tools.development.DevAppServerMain \
 *   --address=localhost --port=5005 &lt;an_absolute_path&gt;/appDir
 * </pre>
 *
 * while also setting its working directory (if appropriate).
 * <p>
 * All arguments between {@code com.google.appengine.tools.KickStart} and
 * {@code com.google.appengine.tools.development.DevAppServerMain}, as well as
 * all {@code --jvm_flag} arguments after {@code DevAppServerMain}, are consumed
 * by KickStart. The remaining options after {@code DevAppServerMain} are
 * given as arguments to DevAppServerMain, without interpretation by
 * KickStart.
 *
 * At present, the only valid option to KickStart itself is:
 * <DL>
 * <DT>--jvm_flag=&lt;vm_arg&gt;</DT><DD>Passes &lt;vm_arg&gt; as a JVM
 * argument for the child JVM.  May be repeated.</DD>
 * </DL>
 * Additionally, if the --external_resource_dir argument is specified, we use it
 * to set the working directory instead of the application war directory.
 *
 */
public class KickStart {

  private static final Logger logger = Logger.getLogger(KickStart.class.getName());

  private static final String EXTERNAL_RESOURCE_DIR_FLAG =
      "--" + DevAppServerMain.EXTERNAL_RESOURCE_DIR_ARG;
  private static final String EXTERNAL_RESOURCE_DIR_ERROR_MESSAGE =
      EXTERNAL_RESOURCE_DIR_FLAG + "=<path> expected.";

  private static final String GENERATE_WAR_FLAG = "--" + DevAppServerMain.GENERATE_WAR_ARG;

  private static final String GENERATED_WAR_DIR_FLAG =
      "--" + DevAppServerMain.GENERATED_WAR_DIR_ARG;

  private static final String NO_JAVA_AGENT_FLAG = "--no_java_agent";

  private static final String JVM_FLAG = "--jvm_flag";
  private static final String JVM_FLAG_ERROR_MESSAGE =
      JVM_FLAG + "=<flag> expected.\n" + JVM_FLAG + " may be repeated to supply multiple flags";

  private static final String START_ON_FIRST_THREAD_FLAG = "--startOnFirstThread";
  private static final String START_ON_FIRST_THREAD_ERROR_MESSAGE =
      START_ON_FIRST_THREAD_FLAG + "=<boolean> expected";

  private static final String SDK_ROOT_FLAG = "--sdk_root";
  private static final String SDK_ROOT_ERROR_MESSAGE = SDK_ROOT_FLAG + "=<path> expected";

  private static final String ENABLE_JACOCO_FLAG = "--enable_jacoco";
  private static final String ENABLE_JACOCO_ERROR_MESSAGE =
      ENABLE_JACOCO_FLAG + "=true|false expected.";
  private static final String JACOCO_AGENT_JAR_FLAG = "--jacoco_agent_jar";
  private static final String JACOCO_AGENT_JAR_ERROR_MESSAGE =
      JACOCO_AGENT_JAR_FLAG + "=<path> expected.";
  private static final String JACOCO_AGENT_ARGS_FLAG = "--jacoco_agent_args";
  private static final String JACOCO_AGENT_ARGS_ERROR_MESSAGE =
      JACOCO_AGENT_ARGS_FLAG + "=<jacoco agent args> expected.";
  private static final String JACOCO_EXEC_FLAG = "--jacoco_exec";
  private static final String JACOCO_EXEC_ERROR_MESSAGE =
      JACOCO_EXEC_FLAG + "=<path> expected.";

  private Process serverProcess = null;

  public static void main(String[] args) {
    new KickStart(args);
  }

  private KickStart(String[] args) {
    String entryClass = null;

    ProcessBuilder builder = new ProcessBuilder();
    String home = System.getProperty("java.home");
    String javaExe = home + File.separator + "bin" + File.separator + "java";

    List<String> jvmArgs = new ArrayList<String>();
    ArrayList<String> appServerArgs = new ArrayList<String>();
    boolean enableJacoco = false;
    String jacocoAgentJarArg = null;
    String jacocoAgentArgs = "";
    String jacocoExecArg = "jacoco.exec";

    List<String> command = builder.command();
    command.add(javaExe);

    boolean startOnFirstThread = System.getProperty("os.name").equalsIgnoreCase("Mac OS X");
    String externalResourceDirArg = null;
    boolean generateWar = false;
    boolean noJavaAgent = false;

    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith(EXTERNAL_RESOURCE_DIR_FLAG)) {
        externalResourceDirArg = extractValue(args[i], EXTERNAL_RESOURCE_DIR_ERROR_MESSAGE);
      } else if (args[i].startsWith(GENERATED_WAR_DIR_FLAG)
          || args[i].startsWith(GENERATE_WAR_FLAG)) {
        generateWar = true;
      } else if (args[i].startsWith(NO_JAVA_AGENT_FLAG)) {
        noJavaAgent = true;
      }
      if (args[i].startsWith(JVM_FLAG)) {
        jvmArgs.add(extractValue(args[i], JVM_FLAG_ERROR_MESSAGE));
      } else if (args[i].startsWith(START_ON_FIRST_THREAD_FLAG)) {
        startOnFirstThread =
            Boolean.valueOf(extractValue(args[i], START_ON_FIRST_THREAD_ERROR_MESSAGE));
      } else if (args[i].startsWith(ENABLE_JACOCO_FLAG)) {
        enableJacoco = "true".equals(extractValue(args[i], ENABLE_JACOCO_ERROR_MESSAGE));
      } else if (args[i].startsWith(JACOCO_AGENT_JAR_FLAG)) {
        jacocoAgentJarArg = extractValue(args[i], JACOCO_AGENT_JAR_ERROR_MESSAGE);
      } else if (args[i].startsWith(JACOCO_AGENT_ARGS_FLAG)) {
        jacocoAgentArgs = extractValue(args[i], JACOCO_AGENT_ARGS_ERROR_MESSAGE);
      } else if (args[i].startsWith(JACOCO_EXEC_FLAG)) {
        jacocoExecArg = extractValue(args[i], JACOCO_EXEC_ERROR_MESSAGE);
      } else if (entryClass == null) {
        if (args[i].charAt(0) == '-') {
          throw new IllegalArgumentException("Only --jvm_flag may precede classname, not "
              + args[i]);
        } else {
          entryClass = args[i];
          if (!entryClass.equals(DevAppServerMain.class.getName())) {
            throw new IllegalArgumentException("KickStart only works for DevAppServerMain");
          }
        }
      } else {
        appServerArgs.add(args[i]);
      }
    }

    if (entryClass == null) {
      throw new IllegalArgumentException("missing entry classname");
    }

    if (externalResourceDirArg == null && generateWar) {
      System.err.println(
          "Generating a war directory requires " + "--" + EXTERNAL_RESOURCE_DIR_FLAG);
      System.exit(1);
    }
    File newWorkingDir = newWorkingDir(externalResourceDirArg,
        appServerArgs.toArray(new String[appServerArgs.size()]));
    builder.directory(newWorkingDir);

    if (startOnFirstThread) {
      jvmArgs.add("-XstartOnFirstThread");
    }

    String classpath = System.getProperty("java.class.path");
    StringBuilder newClassPath = new StringBuilder();
    assert classpath != null : "classpath must not be null";
    String[] paths = classpath.split(File.pathSeparator);
    for (int i = 0; i < paths.length; ++i) {
      newClassPath.append(new File(paths[i]).getAbsolutePath());
      if (i != paths.length - 1) {
        newClassPath.append(File.pathSeparator);
      }
    }
    String sdkRoot = null;
    String appDir = null;
    List<String> absoluteAppServerArgs = new ArrayList<String>(appServerArgs.size());

    for (int i = 0; i < appServerArgs.size(); ++i) {
      String arg = appServerArgs.get(i);
      if (arg.startsWith(SDK_ROOT_FLAG)) {
        sdkRoot = new File(extractValue(arg, SDK_ROOT_ERROR_MESSAGE)).getAbsolutePath();
        arg = SDK_ROOT_FLAG + "=" + sdkRoot;
      } else if (arg.startsWith(EXTERNAL_RESOURCE_DIR_FLAG)) {
        arg = EXTERNAL_RESOURCE_DIR_FLAG + "="
            + new File(extractValue(arg, EXTERNAL_RESOURCE_DIR_ERROR_MESSAGE)).getAbsolutePath();
      } else if (i == appServerArgs.size() - 1) {
        if (!arg.startsWith("-")) {
          File file = new File(arg);
          if (file.exists()) {
            arg = new File(arg).getAbsolutePath();
            appDir = arg;
          }
        }
      }
      absoluteAppServerArgs.add(arg);
    }
    if (sdkRoot == null) {
      sdkRoot = SdkInfo.getSdkRoot().getAbsolutePath();
    }
    boolean isVM = false;
    if (appDir != null) {
      isVM = isVMRuntime(appDir);
    }
    if (isVM) {
      noJavaAgent = true;
      jvmArgs.add("-D--enable_all_permissions=true");
    }
    if (!noJavaAgent) {
      String agentJar = sdkRoot + "/lib/agent/appengine-agent.jar";
      agentJar = agentJar.replace('/', File.separatorChar);
      if (enableJacoco) {
        jvmArgs.add("-D--enable_all_permissions=true");
        String jacocoAgentJar = new File(jacocoAgentJarArg).getAbsolutePath();
        if (!jacocoAgentArgs.isEmpty()) {
          jacocoAgentArgs = jacocoAgentArgs + ",";
        }
        jacocoAgentArgs = jacocoAgentArgs + "destfile=" + new File(jacocoExecArg).getAbsolutePath();
        jvmArgs.add("-javaagent:" + jacocoAgentJar + "=" + jacocoAgentArgs);
        if (newClassPath.length() > 0) {
          newClassPath.append(File.pathSeparator);
        }
        newClassPath.append(agentJar);
      } else {
        jvmArgs.add("-javaagent:" + agentJar);
      }
    }

    String jdkOverridesJar = sdkRoot + "/lib/override/appengine-dev-jdk-overrides.jar";
    jdkOverridesJar = jdkOverridesJar.replace('/', File.separatorChar);
    jvmArgs.add("-Xbootclasspath/p:" + jdkOverridesJar);
    command.addAll(jvmArgs);
    command.add("-classpath");
    command.add(newClassPath.toString());
    command.add(entryClass);
    command.add("--property=kickstart.user.dir=" + System.getProperty("user.dir"));
    if (isVM) {
      command.add("--no_java_agent");
    }
    command.addAll(absoluteAppServerArgs);

    logger.fine("Executing " + command);
    System.out.println("Executing " + command);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        if (serverProcess != null) {
          serverProcess.destroy();
        }
      }
    });

    try {
      serverProcess = builder.start();
    } catch (IOException e) {
      throw new RuntimeException("Unable to start the process", e);
    }

    new Thread(new OutputPump(serverProcess.getInputStream(),
        new PrintWriter(System.out, true))).start();
    new Thread(new OutputPump(serverProcess.getErrorStream(),
        new PrintWriter(System.err, true))).start();

    try {
      serverProcess.waitFor();
    } catch (InterruptedException e) {
    }

    serverProcess.destroy();
    serverProcess = null;
  }

  private static String extractValue(String argument, String errorMessage) {
    int indexOfEqualSign = argument.indexOf('=');
    if (indexOfEqualSign == -1) {
      throw new IllegalArgumentException(errorMessage);
    }
    return argument.substring(argument.indexOf('=') + 1);
  }

  /**
   * Encapsulates the logic to determine the working directory that should be set for the dev
   * appserver process. If one is explicitly specified it will be used. Otherwise the last
   * command-line argument will be used.
   *
   * @param workingDirectoryArg An explicitly specified path. If not {@code null} then it will be
   *        used.
   * @param args The command-line arguments. If {@code workingDirectory} is {@code null} then the
   *        last command-line argument will be used as the working directory.
   * @return The working directory to use. If the path to an existing directory was not specified
   * then we exist with a failure.
   */
  private static File newWorkingDir(String workingDirectoryArg, String[] args) {
    File workingDir;
    if (workingDirectoryArg != null) {
      workingDir = new File(workingDirectoryArg);
      if (!workingDir.isDirectory()) {
        System.err.println(workingDirectoryArg + " is not an existing directory.");
        System.exit(1);
      }
    } else {
      if (args.length < 1 || args[args.length - 1].startsWith("-")) {
        new DevAppServerMain().printHelp(System.out);
        System.exit(1);
      }
      workingDir = new File(args[args.length - 1]);
      new DevAppServerMain().validateWarPath(workingDir);
    }
    return workingDir;
  }

  static boolean isVMRuntime(String appDir) {
    File f = new File(appDir, "WEB-INF/appengine-web.xml");
    if (!f.exists()) {
      return false;
    }

    try {
      AppEngineWebXmlReader reader = new AppEngineWebXmlReader(appDir);
      AppEngineWebXml appEngineWebXml = reader.readAppEngineWebXml();
      return appEngineWebXml.getUseVm();
    } catch (AppEngineConfigException e) {
      System.err.println("Error reading: " + f.getAbsolutePath());
      return false;
    }
  }
}
