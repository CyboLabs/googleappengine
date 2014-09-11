// Copyright 2010 Google. All Rights Reserved.
package com.google.apphosting.utils.config;

import com.google.common.base.Preconditions;
import com.google.common.xml.XmlEscapers;

import net.sourceforge.yamlbeans.YamlConfig;
import net.sourceforge.yamlbeans.YamlException;
import net.sourceforge.yamlbeans.YamlReader;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaBean representation of the Java app.yaml file.
 *
 */
public class AppYaml {

  /**
   * Plugin service to modify app.yaml with runtime-specific defaults.
   */
  public static interface Plugin {
    AppYaml process(AppYaml yaml);
  }

  /**
   * A {@code Handler} element from app.yaml. Maps to {@code servlet}, {@code servlet-mapping},
   * {@code filter}, and {@code filter-mapping} elements in web.xml
   *
   */
  public static class Handler {

    public static enum Type  {SERVLET, JSP, FILTER, NONE}

    private String url;
    private String jsp;
    private String servlet;
    private String filter;
    private LoginType login;
    private Security secure;
    private Map<String, String> init_params;
    private String name;
    private boolean load_on_startup;

    public enum LoginType { admin, required }
    public enum Security { always, optional, never }
    private boolean api_endpoint = false;

    private String script;

    private static final String MULTIPLE_HANDLERS = "Cannot set both %s and %s for the same url.";

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      YamlUtils.validateUrl(url);
      this.url = url;
    }

    public String getJsp() {
      return jsp;
    }

    public void setJsp(String jsp) {
      this.jsp = jsp;
      checkHandlers();
    }

    public String getServlet() {
      return servlet;
    }

    public void setServlet(String servlet) {
      this.servlet = servlet;
      checkHandlers();
    }

    public String getFilter() {
      return filter;
    }

    public void setFilter(String filter) {
      this.filter = filter;
      checkHandlers();
    }

    public Type getType() {
      if (servlet != null) {
        return Type.SERVLET;
      }
      if (filter != null) {
        return Type.FILTER;
      }
      if (jsp != null) {
        return Type.JSP;
      }
      return Type.NONE;
    }

    public String getTarget() {
      if (servlet != null) {
        return servlet;
      }
      if (filter != null) {
        return filter;
      }
      if (jsp != null) {
        return jsp;
      }
      return null;
    }

    public void setScript(String script) {
      this.script = script;
    }

    public String getScript() {
      return this.script;
    }

    public LoginType getLogin() {
      return login;
    }

    public void setLogin(LoginType login) {
      this.login = login;
    }

    public Security getSecure() {
      return secure;
    }

    public void setSecure(Security secure) {
      if (secure == Security.never) {
        throw new AppEngineConfigException("Java does not support secure: never");
      }
      this.secure = secure;
    }

    public Map<String, String> getInit_params() {
      return init_params;
    }

    public void setInit_params(Map<String, String> init_params) {
      this.init_params = init_params;
    }

    public String getName() {
      return (name == null ? getTarget() : name);
    }

    public void setLoad_on_startup(boolean loadOnStartup) {
      this.load_on_startup = loadOnStartup;
    }

    public boolean getLoad_on_startup() {
      return this.load_on_startup;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getApi_endpoint() {
      return "" + this.api_endpoint;
    }

    public void setApi_endpoint(String api_endpoint) {
      this.api_endpoint = YamlUtils.parseBoolean(api_endpoint);
    }

    public boolean isApiEndpoint() {
      return this.api_endpoint;
    }

    private void checkHandlers() {
      if (jsp != null && servlet != null) {
        throw new AppEngineConfigException(String.format(MULTIPLE_HANDLERS, "jsp", "servlet"));
      }
      if (jsp != null && filter != null) {
        throw new AppEngineConfigException(String.format(MULTIPLE_HANDLERS, "jsp", "filter"));
      }
      if (filter != null && servlet != null) {
        throw new AppEngineConfigException(String.format(MULTIPLE_HANDLERS, "filter", "servlet"));
      }
    }

    /**
     * Generates the {@code servlet} or {@code filter} element of web.xml
     * corresponding to this handler.
     */
    private void generateDefinitionXml(XmlWriter xml) {
      if (getServlet() != null || getJsp() != null) {
        generateServletDefinition(xml);
      } else if (getFilter() != null) {
        generateFilterDefintion(xml);
      }
    }

    private void generateMappingXml(XmlWriter xml) {
      if (getServlet() != null || getJsp() != null) {
        generateServletMapping(xml);
      } else if (getFilter() != null) {
        generateFilterMapping(xml);
      }
      generateSecurityConstraints(xml);
    }

    private void generateSecurityConstraints(XmlWriter xml) {
      if (secure == Security.always || login == LoginType.required || login == LoginType.admin) {
        xml.startElement("security-constraint");
        xml.startElement("web-resource-collection");
        xml.simpleElement("web-resource-name", "aname");
        xml.simpleElement("url-pattern", getUrl());
        xml.endElement("web-resource-collection");
        if (login == LoginType.required) {
          securityConstraint(xml, "auth", "role-name", "*");
        } else if (login == LoginType.admin) {
          securityConstraint(xml, "auth", "role-name", "admin");
        }
        if (secure == Security.always) {
          securityConstraint(xml, "user-data", "transport-guarantee", "CONFIDENTIAL");
        }
        xml.endElement("security-constraint");
      }
    }

    private void securityConstraint(XmlWriter xml, String type, String name, String value)  {
      type = type + "-constraint";
      xml.startElement(type);
      xml.simpleElement(name, value);
      xml.endElement(type);
    }

    /**
     * Generates a {@code filter} element of web.xml corresponding to this handler.
     */
    private void generateFilterDefintion(XmlWriter xml) {
      xml.startElement("filter");
      xml.simpleElement("filter-name", getName());
      xml.simpleElement("filter-class", getFilter());
      generateInitParams(xml);
      xml.endElement("filter");
    }

    /**
     *  Generates a {@code filter-mapping} element of web.xml corresponding to this handler.
     */
    private void generateFilterMapping(XmlWriter xml) {
      xml.startElement("filter-mapping");
      xml.simpleElement("filter-name", getName());
      xml.simpleElement("url-pattern", getUrl());
      xml.endElement("filter-mapping");
    }

    /**
     * Generates a {@code servlet} or {@code jsp-file} element of web.xml corresponding to this
     * handler.
     */
    private void generateServletDefinition(XmlWriter xml) {
      xml.startElement("servlet");
      xml.simpleElement("servlet-name", getName());
      if (getJsp() == null) {
        xml.simpleElement("servlet-class", getServlet());
      } else {
        xml.simpleElement("jsp-file", getJsp());
      }
      generateInitParams(xml);
      if (load_on_startup) {
        xml.simpleElement("load-on-startup", "1");
      }
      xml.endElement("servlet");
    }

    /**
     * Merges another handler into this handler, assuming that the other handler
     * has the same name, type and target. This operation is intended to be
     * used for generating a Servlet or Filter *definition* as opposed to a
     * mapping, and therefore the urls of this handler and the other handler
     * are not involved in the merge operation. The load_on_startup values
     * of the two handlers will be OR'd and the init_params will be unioned.
     */
    public void mergeDefinitions(Handler otherHandler) {
      Preconditions.checkArgument(this.getName().equals(otherHandler.getName()),
          "Cannot merge handler named " + this.getName() + " with handler named " +
           otherHandler.getName());
      Preconditions.checkArgument(this.getType() == otherHandler.getType(),
          "Cannot merge handler of type " + this.getType() + " with handler of type "
          + otherHandler.getType());
      Preconditions.checkArgument(this.getTarget().equals(otherHandler.getTarget()),
          "Cannont merge handler with target " + this.getTarget() + " with handler with target "
          + otherHandler.getTarget());
      this.load_on_startup = this.load_on_startup || otherHandler.load_on_startup;
      Map<String, String> mergedInitParams = new LinkedHashMap<String, String>();
      if (this.init_params != null) {
        mergedInitParams.putAll(this.init_params);
      }
      if (otherHandler.init_params != null) {
        for (String key : otherHandler.init_params.keySet()) {
          String thisValue = mergedInitParams.get(key);
          String otherValue = otherHandler.init_params.get(key);
          if (thisValue == null) {
            mergedInitParams.put(key, otherValue);
          } else if (!thisValue.equals(otherValue)) {
            throw new IllegalArgumentException(
                "Cannot merge handlers with conflicting values for the init_param: " + key + " : "
                + thisValue + " vs " + otherValue);
          }
        }
      }
      if (mergedInitParams.size() != 0) {
        this.init_params = mergedInitParams;
      }
    }

    /**
     * Generates a {@code servlet-mapping} element of web.xml corresponding to this handler.
     */
    private void generateServletMapping(XmlWriter xml) {
      if (isApiEndpoint()) {
        xml.startElement("servlet-mapping", "id", xml.nextApiEndpointId());
      } else {
        xml.startElement("servlet-mapping");
      }
      xml.simpleElement("servlet-name", getName());
      xml.simpleElement("url-pattern", getUrl());
      xml.endElement("servlet-mapping");
    }

    private void generateInitParams(XmlWriter xml) {
      if (init_params != null) {
        for (Map.Entry<String, String> param : init_params.entrySet()) {
          xml.startElement("init-param");
          xml.simpleElement("param-name", param.getKey());
          xml.simpleElement("param-value", param.getValue());
          xml.endElement("init-param");
        }
      }
    }

    private void generateEndpointServletMappingId(XmlWriter xml) {
      if (isApiEndpoint()) {
        xml.simpleElement("endpoint-servlet-mapping-id", xml.nextApiEndpointId());
      }
    }
  }

  public static class ResourceFile {
    private static final String EMPTY_MESSAGE = "Missing include or exclude.";
    private static final String BOTH_MESSAGE = "Cannot specify both include and exclude.";

    protected String include;
    protected String exclude;
    protected Map<String, String> httpHeaders;

    public String getInclude() {
      if (exclude == null && include == null) {
        throw new AppEngineConfigException(EMPTY_MESSAGE);
      }
      return include;
    }

    public void setInclude(String include) {
      if (exclude != null) {
        throw new AppEngineConfigException(BOTH_MESSAGE);
      }
      this.include = include;
    }

    public String getExclude() {
      if (exclude == null && include == null) {
        throw new AppEngineConfigException(EMPTY_MESSAGE);
      }
      return exclude;
    }

    public void setExclude(String exclude) {
      if (include != null) {
        throw new AppEngineConfigException(BOTH_MESSAGE);
      }
      this.exclude = exclude;
    }

    public Map<String, String> getHttp_headers() {
      if (include == null) {
        throw new AppEngineConfigException("Missing include.");
      }

      return httpHeaders;
    }

    public void setHttp_headers(Map<String, String> httpHeaders) {
      if (include == null) {
        throw new AppEngineConfigException("Missing include.");
      }

      this.httpHeaders = httpHeaders;
    }
  }

  public static class StaticFile extends ResourceFile {
    private static final String NO_INCLUDE = "Missing include.";
    private static final String INCLUDE_ONLY = "Expiration can only be specified with include.";
    private String expiration;

    public String getExpiration() {
      if (expiration != null && include == null) {
        throw new AppEngineConfigException(NO_INCLUDE);
      }
      return expiration;
    }

    public void setExpiration(String expiration) {
      if (exclude != null) {
        throw new AppEngineConfigException(INCLUDE_ONLY);
      }
      this.expiration = expiration;
    }

    @Override
    public void setExclude(String exclude) {
      if (expiration != null) {
        throw new AppEngineConfigException(INCLUDE_ONLY);
      }
      super.setExclude(exclude);
    }
  }

  public static class AdminConsole {
    private List<AdminPage> pages;

    public List<AdminPage> getPages() {
      return pages;
    }

    public void setPages(List<AdminPage> pages) {
      this.pages = pages;
    }
  }

  public static class AdminPage {
    private String name;
    private String url;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }
  }

  public static class AsyncSessionPersistence {
    private boolean enabled = false;
    private String queue_name;

    public String getEnabled() {
      return "" + enabled;
    }

    public void setEnabled(String enabled) {
      this.enabled = YamlUtils.parseBoolean(enabled);
    }

    public String getQueue_name() {
      return this.queue_name;
    }

    public void setQueue_name(String queue_name) {
      this.queue_name = queue_name;
    }
  }

  public static class ErrorHandler {
    private String file;
    private String errorCode;

    public String getFile() {
      return file;
    }

    public void setFile(String file) {
      this.file = file;
    }

    public String getError_code() {
      return errorCode;
    }

    public void setError_code(String errorCode) {
      this.errorCode = errorCode;
    }
  }

  /**
   * AutomaticScaling bean.
   */
  public static class AutomaticScaling {
    private String minPendingLatency;
    private String maxPendingLatency;
    private String minIdleInstances;
    private String maxIdleInstances;
    private String maxConcurrentRequests;

    public String getMin_pending_latency() {
      return minPendingLatency;
    }

    public void setMin_pending_latency(String minPendingLatency) {
      this.minPendingLatency = minPendingLatency;
    }

    public String getMax_pending_latency() {
      return maxPendingLatency;
    }

    public void setMax_pending_latency(String maxPendingLatency) {
      this.maxPendingLatency = maxPendingLatency;
    }

    public String getMin_idle_instances() {
      return minIdleInstances;
    }

    public void setMin_idle_instances(String minIdleInstances) {
      this.minIdleInstances = minIdleInstances;
    }

    public String getMax_idle_instances() {
      return maxIdleInstances;
    }

    public void setMax_idle_instances(String maxIdleInstances) {
      this.maxIdleInstances = maxIdleInstances;
    }

    public String getMax_concurrent_requests() {
      return maxConcurrentRequests;
    }

    public void setMax_concurrent_requests(String maxConcurrentRequests) {
      this.maxConcurrentRequests = maxConcurrentRequests;
    }
  }

  /**
   * ManualScaling bean.
   */
  public static class ManualScaling {
    private String instances;

    public String getInstances() {
      return instances;
    }

    public void setInstances(String instances) {
      this.instances = instances;
    }
  }

  /**
   * BasicScaling bean.
   */
  public static class BasicScaling {
    private String maxInstances;
    private String idleTimeout;

    public String getMax_instances() {
      return maxInstances;
    }

    public void setMax_instances(String maxInstances) {
      this.maxInstances = maxInstances;
    }
    public String getIdle_timeout() {
      return idleTimeout;
    }

    public void setIdle_timeout(String idleTimeout) {
      this.idleTimeout = idleTimeout;
    }
  }

  private String application;
  private String version;
  private String source_language;
  private String module;
  private String instanceClass;
  private AutomaticScaling automatic_scaling;
  private ManualScaling manual_scaling;
  private BasicScaling basic_scaling;
  private String runtime;
  private List<Handler> handlers;
  private String public_root;
  private List<StaticFile> static_files;
  private List<ResourceFile> resource_files;
  private boolean ssl_enabled = true;
  private boolean precompilation_enabled = true;
  private boolean sessions_enabled = false;
  private AsyncSessionPersistence async_session_persistence;
  private boolean threadsafe = false;
  private String auto_id_policy;
  private boolean threadsafeWasSet = false;
  private boolean codeLock = false;
  private Map<String, String> system_properties;
  private Map<String, String> env_variables;
  private Map<String, String> context_params;
  private List<String> welcome_files;
  private List<String> listeners;
  private List<String> inbound_services;
  private AdminConsole admin_console;
  private List<ErrorHandler> error_handlers;
  private ApiConfig api_config;
  private Pagespeed pagespeed;
  private String web_xml;

  private static final String REQUIRED_FIELD = "Missing required element '%s'.";

  public String getApplication() {
    if (application == null) {
      throw new AppEngineConfigException(String.format(REQUIRED_FIELD, "application"));
    }
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public void setSource_language(String sourceLanguage) {
    this.source_language = sourceLanguage;
  }

  public String getSource_language() {
    return source_language;
  }

  public String getModule() {
    return module;
  }

  public void setModule(String module) {
    this.module = module;
  }

  public String getInstance_class() {
    return instanceClass;
  }

  public void setInstance_class(String instanceClass) {
    this.instanceClass = instanceClass;
  }

  public AutomaticScaling getAutomatic_scaling() {
    return automatic_scaling;
  }

  public void setAutomatic_scaling(AutomaticScaling automaticScaling) {
    this.automatic_scaling = automaticScaling;
  }

  public ManualScaling getManual_scaling() {
    return manual_scaling;
  }

  public void setManual_scaling(ManualScaling manualScaling) {
    this.manual_scaling = manualScaling;
  }

  public BasicScaling getBasic_scaling() {
    return basic_scaling;
  }

  public void setBasic_scaling(BasicScaling basicScaling) {
    this.basic_scaling = basicScaling;
  }

  public String getRuntime() {
    return runtime;
  }

  public void setRuntime(String runtime) {
    this.runtime = runtime;
  }

  public List<Handler> getHandlers() {
    return handlers;
  }

  public void setHandlers(List<Handler> handlers) {
    this.handlers = handlers;
    if (this.api_config != null) {
      this.api_config.setHandlers(handlers);
    }
  }

  public String getPublic_root() {
    return public_root;
  }

  public void setPublic_root(String public_root) {
    this.public_root = public_root;
  }

  public List<StaticFile> getStatic_files() {
    return static_files;
  }

  public void setStatic_files(List<StaticFile> static_files) {
    this.static_files = static_files;
  }

  public List<ResourceFile> getResource_files() {
    return resource_files;
  }

  public void setResource_files(List<ResourceFile> resource_files) {
    this.resource_files = resource_files;
  }

  public String getSsl_enabled() {
    return "" + ssl_enabled;
  }

  public void setSsl_enabled(String ssl_enabled) {
    this.ssl_enabled = YamlUtils.parseBoolean(ssl_enabled);
  }

  public boolean isSslEnabled() {
    return ssl_enabled;
  }

  public String getPrecompilation_enabled() {
    return "" + precompilation_enabled;
  }

  public boolean isPrecompilationEnabled() {
    return precompilation_enabled;
  }

  public void setPrecompilation_enabled(String precompilation_enabled) {
    this.precompilation_enabled = YamlUtils.parseBoolean(precompilation_enabled);
  }

  public String getSessions_enabled() {
    return "" + sessions_enabled;
  }

  public boolean isSessionsEnabled() {
    return sessions_enabled;
  }

  public void setSessions_enabled(String sessions_enabled) {
    this.sessions_enabled = YamlUtils.parseBoolean(sessions_enabled);
  }

  public AsyncSessionPersistence getAsync_session_persistence() {
    return async_session_persistence;
  }

  public void setAsync_session_persistence(AsyncSessionPersistence async_session_persistence) {
    this.async_session_persistence = async_session_persistence;
  }

  public String getThreadsafe() {
    return "" + threadsafe;
  }

  public boolean isThreadsafeSet() {
    return threadsafeWasSet;
  }

  public void setThreadsafe(String threadsafe) {
    this.threadsafe = YamlUtils.parseBoolean(threadsafe);
    threadsafeWasSet = true;
  }

  public String getAuto_id_policy() {
    return auto_id_policy;
  }

  public void setAuto_id_policy(String policy) {
    auto_id_policy = policy;
  }

  public String getCode_lock() {
    return "" + codeLock;
  }

  public void setCode_lock(String codeLock) {
    this.codeLock = YamlUtils.parseBoolean(codeLock);
  }

  public Map<String, String> getSystem_properties() {
    return system_properties;
  }

  public void setSystem_properties(Map<String, String> system_properties) {
    this.system_properties = system_properties;
  }

  public Map<String, String> getEnv_variables() {
    return env_variables;
  }

  public void setEnv_variables(Map<String, String> env_variables) {
    this.env_variables = env_variables;
  }

  public List<String> getWelcome_files() {
    return welcome_files;
  }

  public void setWelcome_files(List<String> welcome_files) {
    this.welcome_files = welcome_files;
  }

  public Map<String, String> getContext_params() {
    return context_params;
  }

  public void setContext_params(Map<String, String> context_params) {
    this.context_params = context_params;
  }

  public List<String> getListeners() {
    return listeners;
  }

  public void setListeners(List<String> listeners) {
    this.listeners = listeners;
  }

  public String getWeb_xml() {
    return web_xml;
  }

  public void setWeb_xml(String web_xml) {
    this.web_xml = web_xml;
  }

  public List<String> getInbound_services() {
    return inbound_services;
  }

  public void setInbound_services(List<String> inbound_services) {
    this.inbound_services = inbound_services;
  }

  public AdminConsole getAdmin_console() {
    return admin_console;
  }

  public void setAdmin_console(AdminConsole admin_console) {
    this.admin_console = admin_console;
  }

  public List<ErrorHandler> getError_handlers() {
    return error_handlers;
  }

  public void setError_handlers(List<ErrorHandler> error_handlers) {
    this.error_handlers = error_handlers;
  }

  public ApiConfig getApi_config() {
    return api_config;
  }

  public void setApi_config(ApiConfig api_config) {
    this.api_config = api_config;
    if (handlers != null) {
      this.api_config.setHandlers(handlers);
    }
  }

  public Pagespeed getPagespeed() {
    return pagespeed;
  }

  public void setPagespeed(Pagespeed pagespeed) {
    this.pagespeed = pagespeed;
  }

  public AppYaml applyPlugins() {
    AppYaml yaml = this;
    for (Plugin plugin : PluginLoader.loadPlugins(Plugin.class)) {
      AppYaml modified = plugin.process(yaml);
      if (modified != null) {
        yaml = modified;
      }
    }
    return yaml;
  }

  /**
   * Represents an api-config: top level app.yaml stanza
   * This is a singleton specifying url: and servlet: for the api config server.
   */
  public static class ApiConfig {
    private String url;
    private String servlet;
    private List<Handler> handlers;

    public void setHandlers(List<Handler> handlers) {
      this.handlers = handlers;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      YamlUtils.validateUrl(url);
      this.url = url;
    }

    public String getServlet() {
      return servlet;
    }

    public void setServlet(String servlet) {
      this.servlet = servlet;
    }

    private void generateXml(XmlWriter xml) {
      xml.startElement("api-config", "servlet-class", getServlet(), "url-pattern", getUrl());
      if (handlers != null) {
        for (Handler handler : handlers) {
          handler.generateEndpointServletMappingId(xml);
        }
      }
      xml.endElement("api-config");
    }
  }

  /**
   * Represents a &lt;pagespeed&gt; element. This is used to specify configuration for the Page
   * Speed Service, which can be used to automatically optimize the loading speed of app engine
   * sites.
   */
  public static class Pagespeed {
    private List<String> urlBlacklist;
    private List<String> domainsToRewrite;
    private List<String> enabledRewriters;
    private List<String> disabledRewriters;

    public void setUrl_blacklist(List<String> urls) {
      urlBlacklist = urls;
    }

    public List<String> getUrl_blacklist() {
      return urlBlacklist;
    }

    public void setDomains_to_rewrite(List<String> domains) {
      domainsToRewrite = domains;
    }

    public List<String> getDomains_to_rewrite() {
      return domainsToRewrite;
    }

    public void setEnabled_rewriters(List<String> rewriters) {
      enabledRewriters = rewriters;
    }

    public List<String> getEnabled_rewriters() {
      return enabledRewriters;
    }

    public void setDisabled_rewriters(List<String> rewriters) {
      disabledRewriters = rewriters;
    }

    public List<String> getDisabled_rewriters() {
      return disabledRewriters;
    }

    private void generateXml(XmlWriter xml) {
      if (!isEmpty()) {
        xml.startElement("pagespeed");
        appendElements(xml, "url-blacklist", urlBlacklist);
        appendElements(xml, "domain-to-rewrite", domainsToRewrite);
        appendElements(xml, "enabled-rewriter", enabledRewriters);
        appendElements(xml, "disabled-rewriter", disabledRewriters);
        xml.endElement("pagespeed");
      }
    }

    private void appendElements(XmlWriter xml, String name, List<String> l) {
      if (l != null) {
        for (String elt : l) {
          xml.simpleElement(name, elt);
        }
      }
    }

    private boolean isEmpty() {
      return !hasElements(urlBlacklist) && !hasElements(domainsToRewrite)
          && !hasElements(enabledRewriters) && !hasElements(disabledRewriters);
    }

    private boolean hasElements(List<?> coll) {
      return coll != null && !coll.isEmpty();
    }
  }

  private class XmlWriter {
    private static final String XML_HEADER = "<!-- Generated from app.yaml. Do not edit. -->";
    private final PrintWriter writer;
    private int indent = 0;
    private int apiEndpointId = 0;

    public XmlWriter(Writer w) {
      writer = new PrintWriter(w);
      writer.println(XML_HEADER);
    }

    public void startElement(String name, String... attributes) {
      startElement(name, false, attributes);
      writer.println();
    }

    public void startElement(String name, boolean empty, String... attributes) {
      indent();
      writer.print("<");
      writer.print(name);
      for (int i = 0; i < attributes.length; i += 2) {
        String attributeName = attributes[i];
        String value = attributes[i + 1];
        if (value != null) {
          writer.print(" ");
          writer.print(attributeName);
          writer.print("='");
          writer.print(escapeAttribute(value));
          writer.print("'");
        }
      }
      if (empty) {
        writer.println("/>");
      } else {
        writer.print(">");
        indent += 2;
      }
    }

    public void endElement(String name) {
      endElement(name, true);
    }

    public void endElement(String name, boolean needIndent) {
      indent -= 2;
      if (needIndent) {
        indent();
      }
      writer.print("</");
      writer.print(name);
      writer.println(">");
    }

    public void emptyElement(String name, String... attributes) {
      startElement(name, true, attributes);
    }

    public void simpleElement(String name, String value, String... attributes) {
      startElement(name, false, attributes);
      writer.print(escapeContent(value));
      endElement(name, false);
    }

    public void writeUnescaped(String xmlContent) {
      writer.println(xmlContent);
    }

    private void indent() {
      for (int i = 0; i < indent; i++) {
        writer.print(" ");
      }
    }

    private String escapeContent(String value) {
      if (value == null) {
        return null;
      }
      return XmlEscapers.xmlContentEscaper().escape(value);
    }

    private String escapeAttribute(String value) {
      if (value == null) {
        return null;
      }
      return XmlEscapers.xmlAttributeEscaper().escape(value);
    }

    private String nextApiEndpointId() {
      return String.format("endpoint-%1$d", ++apiEndpointId);
    }
  }

  private void addOptionalElement(XmlWriter xml, String name, String value) {
    if (value != null) {
      xml.simpleElement(name, value);
    }
  }

  public void generateAppEngineWebXml(Writer writer) {
    XmlWriter xml = new XmlWriter(writer);
    xml.startElement("appengine-web-app", "xmlns", "http://appengine.google.com/ns/1.0");
    xml.simpleElement("application", getApplication());
    addOptionalElement(xml, "version", getVersion());
    addOptionalElement(xml, "source-language", getSource_language());
    addOptionalElement(xml, "module", getModule());
    addOptionalElement(xml, "instance-class", getInstance_class());
    addOptionalElement(xml, "public-root", public_root);
    addOptionalElement(xml, "auto-id-policy", getAuto_id_policy());
    if (automatic_scaling != null) {
      xml.startElement("automatic-scaling");
      addOptionalElement(xml, "min-pending-latency", automatic_scaling.getMin_pending_latency());
      addOptionalElement(xml, "max-pending-latency", automatic_scaling.getMax_pending_latency());
      addOptionalElement(xml, "min-idle-instances", automatic_scaling.getMin_idle_instances());
      addOptionalElement(xml, "max-idle-instances", automatic_scaling.getMax_idle_instances());
      addOptionalElement(xml, "max-concurrent-requests",
          automatic_scaling.getMax_concurrent_requests());
      xml.endElement("automatic-scaling");
    }
    if (manual_scaling != null) {
      xml.startElement("manual-scaling");
      xml.simpleElement("instances", manual_scaling.getInstances());
      xml.endElement("manual-scaling");
    }
    if (basic_scaling != null) {
      xml.startElement("basic-scaling");
      xml.simpleElement("max-instances", basic_scaling.getMax_instances());
      addOptionalElement(xml, "idle-timeout", basic_scaling.getIdle_timeout());
      xml.endElement("basic-scaling");
    }
    xml.startElement("static-files");
    if (static_files != null) {
      for (StaticFile file : static_files) {
        String name, path;
        if (file.getInclude() != null) {
          generateInclude(file, xml);
        } else {
          xml.emptyElement("exclude", "path", file.getExclude());
        }
      }
    }
    xml.endElement("static-files");
    xml.startElement("resource-files");
    if (resource_files != null) {
      for (ResourceFile file : resource_files) {
        String name, path;
        if (file.getInclude() != null) {
          name = "include";
          path = file.getInclude();
        } else {
          name = "exclude";
          path = file.getExclude();
        }
        xml.emptyElement(name, "path", path);
      }
    }
    xml.endElement("resource-files");
    xml.simpleElement("ssl-enabled", getSsl_enabled());
    xml.simpleElement("precompilation-enabled", getPrecompilation_enabled());
    if (isThreadsafeSet()) {
      xml.simpleElement("threadsafe", getThreadsafe());
    }
    xml.simpleElement("code-lock", getCode_lock());
    xml.simpleElement("sessions-enabled", getSessions_enabled());
    if (async_session_persistence != null) {
      xml.simpleElement("async-session-persistence", null,
          "enabled", getAsync_session_persistence().getEnabled(),
          "queue-name", getAsync_session_persistence().getQueue_name());
    }
    if (system_properties != null) {
      xml.startElement("system-properties");
      for (Map.Entry<String, String> entry : system_properties.entrySet()) {
        xml.emptyElement("property", "name", entry.getKey(), "value", entry.getValue());
      }
      xml.endElement("system-properties");
    }
    if (env_variables != null) {
      xml.startElement("env-variables");
      for (Map.Entry<String, String> entry : env_variables.entrySet()) {
        xml.emptyElement("env-var", "name", entry.getKey(), "value", entry.getValue());
      }
      xml.endElement("env-variables");
    }
    boolean warmupService = false;
    if (inbound_services != null) {
      xml.startElement("inbound-services");
      for (String service : inbound_services) {
        if (AppEngineWebXml.WARMUP_SERVICE.equals(service)) {
          warmupService = true;
        } else {
          xml.simpleElement("service", service);
        }
      }
      xml.endElement("inbound-services");
    }
    xml.simpleElement("warmup-requests-enabled", Boolean.toString(warmupService));
    if (admin_console != null && admin_console.getPages() != null) {
      xml.startElement("admin-console");
      for (AdminPage page : admin_console.getPages()) {
        xml.emptyElement("page", "name", page.getName(), "url", page.getUrl());
      }
      xml.endElement("admin-console");
    }
    if (error_handlers != null) {
      xml.startElement("static-error-handlers");
      for (ErrorHandler handler : error_handlers) {
        xml.emptyElement("handler",
                         "file", handler.getFile(),
                         "error-code", handler.getError_code());
      }
      xml.endElement("static-error-handlers");
    }
    if (api_config != null) {
      api_config.generateXml(xml);
    }
    if (pagespeed != null) {
      pagespeed.generateXml(xml);
    }
    xml.endElement("appengine-web-app");
  }

  /**
   * Generates the {@code servlet}, {@code servlet-mapping}, {@code filter}, and
   * {@code filter-mapping} elements of web.xml corresponding to the {@link #handlers} list. There
   * may be multiple {@link Handler handlers} corresponding to the same servlet or filter, because a
   * single handler can only specify one URL pattern and the user may wish to map several URL
   * patterns to the same servlet or filter. In this case we want to have multiple
   * {@code servlet-mapping} or {@code filter-mapping} elements but only a single {@code servlet} or
   * {@code filter} element.
   */
  private void generateHandlerXml(XmlWriter xmlWriter) {
    if (handlers == null) {
      return;
    }
    Map<String, Handler> servletsByName = new LinkedHashMap<String, Handler>(handlers.size());
    Map<String, Handler> filtersByName = new LinkedHashMap<String, Handler>(handlers.size());
    for (Handler handler : handlers) {
      String name = handler.getName();
      if (name != null) {
        Handler.Type type = handler.getType();
        boolean isServlet = (type == Handler.Type.SERVLET || type == Handler.Type.JSP);
        boolean isFilter = (type == Handler.Type.FILTER);
        Handler existing = (isServlet ? servletsByName.get(name) : filtersByName.get(name));
        if (existing != null) {
          existing.mergeDefinitions(handler);
        } else {
          if (isServlet) {
            servletsByName.put(name, handler);
          }
          if (isFilter) {
            filtersByName.put(name, handler);
          }
        }
      }
    }
    for (Handler handler : servletsByName.values()) {
      handler.generateDefinitionXml(xmlWriter);
    }
    for (Handler handler : filtersByName.values()) {
      handler.generateDefinitionXml(xmlWriter);
    }
    for (Handler handler : handlers) {
      handler.generateMappingXml(xmlWriter);
    }
  }

  public void generateWebXml(Writer writer) {
    XmlWriter xml = new XmlWriter(writer);
    xml.startElement("web-app", "version", "2.5",
        "xmlns", "http://java.sun.com/xml/ns/javaee",
        "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance",
        "xsi:schemaLocation",
        "http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd"
    );
    generateHandlerXml(xml);
    if (context_params != null) {
      for (Map.Entry<String, String> entry : context_params.entrySet()) {
        xml.startElement("context-param");
        xml.simpleElement("param-name", entry.getKey());
        xml.simpleElement("param-value", entry.getValue());
        xml.endElement("context-param");
      }
    }
    if (welcome_files != null) {
      xml.startElement("welcome-file-list");
      for (String file : welcome_files) {
        xml.simpleElement("welcome-file", file);
      }
      xml.endElement("welcome-file-list");
    }
    if (listeners != null) {
      for (String listener : listeners) {
        xml.startElement("listener");
        xml.simpleElement("listener-class", listener);
        xml.endElement("listener");
      }
    }
    if (web_xml != null) {
      xml.writeUnescaped(web_xml);
    }
    xml.endElement("web-app");
  }

  public static AppYaml parse(Reader reader) {
    YamlReader yaml = new YamlReader(reader);
    prepareParser(yaml.getConfig());
    try {
      AppYaml appYaml = yaml.read(AppYaml.class);
      if (appYaml == null) {
        throw new YamlException("Unable to parse yaml file");
      }
      return appYaml.applyPlugins();
    } catch (YamlException e) {
      Throwable innerException = e.getCause();

      while (innerException != null) {
        if (innerException instanceof AppEngineConfigException) {
          throw (AppEngineConfigException) innerException;
        }
        innerException = innerException.getCause();
      }

      throw new AppEngineConfigException(e.getMessage(), e);
    }
  }

  public static AppYaml parse(String yaml) {
    return parse(new StringReader(yaml));
  }

  public static void prepareParser(YamlConfig config) {
    config.setPropertyElementType(AppYaml.class, "handlers", Handler.class);
    config.setPropertyElementType(AppYaml.class, "static_files", StaticFile.class);
    config.setPropertyElementType(AppYaml.class, "resource_files", ResourceFile.class);
    config.setPropertyElementType(AppYaml.class, "system_properties", String.class);
    config.setPropertyElementType(AppYaml.class, "context_params", String.class);
    config.setPropertyElementType(AppYaml.class, "env_variables", String.class);
    config.setPropertyElementType(AppYaml.class, "welcome_files", String.class);
    config.setPropertyElementType(AppYaml.class, "listeners", String.class);
    config.setPropertyElementType(AppYaml.class, "inbound_services", String.class);
    config.setPropertyElementType(Handler.class, "init_params", String.class);
    config.setPropertyElementType(AdminConsole.class, "pages", AdminPage.class);
    config.setPropertyElementType(AppYaml.class, "error_handlers", ErrorHandler.class);
    config.setPropertyElementType(Pagespeed.class, "url_blacklist", String.class);
    config.setPropertyElementType(Pagespeed.class, "domains_to_rewrite", String.class);
    config.setPropertyElementType(Pagespeed.class, "enabled_rewriters", String.class);
    config.setPropertyElementType(Pagespeed.class, "disabled_rewriters", String.class);
  }

  private void generateInclude(StaticFile include, XmlWriter xml) {
    String path = include.getInclude();
    Map<String, String> httpHeaders = include.getHttp_headers();
    if (httpHeaders == null || httpHeaders.isEmpty()) {
      xml.emptyElement("include",
                       "path", include.getInclude(),
                       "expiration", include.getExpiration());
    } else {
      xml.startElement("include",
                       false,
                       "path", include.getInclude(),
                       "expiration", include.getExpiration());
      for (Map.Entry<String, String> entry : httpHeaders.entrySet()) {
        xml.emptyElement("http-header",
                         "name", entry.getKey(),
                         "value", entry.getValue());
      }
      xml.endElement("include");
    }
  }
}
