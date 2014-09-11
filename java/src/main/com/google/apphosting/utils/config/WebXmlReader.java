// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.apphosting.utils.config;

import com.google.apphosting.utils.config.WebXml.SecurityConstraint;

import org.mortbay.xml.XmlParser;
import org.mortbay.xml.XmlParser.Node;

import java.io.InputStream;
import java.net.URL;
import java.util.Stack;

/**
 * This reads {@code web.xml}.
 *
 *
 */
public class WebXmlReader extends AbstractConfigXmlReader<WebXml> {

  private static final String[] DEFAULT_WELCOME_FILES = new String[] {
    "index.html",
    "index.jsp",
  };

  public static final String DEFAULT_RELATIVE_FILENAME = "WEB-INF/web.xml";

  private static final String URLPATTERN_TAG = "url-pattern";
  private static final String SERVLETMAP_TAG = "servlet-mapping";
  private static final String FILTERMAP_TAG = "filter-mapping";
  private static final String SECURITYCONST_TAG = "security-constraint";
  private static final String AUTHCONST_TAG = "auth-constraint";
  private static final String ROLENAME_TAG = "role-name";
  private static final String USERDATACONST_TAG = "user-data-constraint";
  private static final String TRANSGUARANTEE_TAG = "transport-guarantee";
  private static final String WELCOME_FILE_LIST_TAG = "welcome-file-list";
  private static final String WELCOME_FILE_TAG = "welcome-file";
  private static final String EXTENSION_TAG = "extension";
  private static final String MIME_TYPE_TAG = "mime-type";
  private static final String ERROR_CODE_TAG = "error-code";

  private final String relativeFilename;

  /**
   * Creates a reader for web.xml.
   *
   * @param appDir The directory in which the config file resides.
   * @param relativeFilename The path to the config file, relative to
   * {@code appDir}.
   */
  public WebXmlReader(String appDir, String relativeFilename) {
    super(appDir, true);
    this.relativeFilename = relativeFilename;
  }

  /**
   * Creates a reader for web.xml.
   *
   * @param appDir The directory in which the web.xml config file resides.  The
   * path to the config file relative to the directory is assumed to be
   * {@link #DEFAULT_RELATIVE_FILENAME}.
   */
  public WebXmlReader(String appDir) {
    this(appDir, DEFAULT_RELATIVE_FILENAME);
  }

  @Override
  protected String getRelativeFilename() {
    return relativeFilename;
  }

  /**
   * Parses the config file.
   * @return A {@link WebXml} object representing the parsed configuration.
   */
  public WebXml readWebXml() {
    return readConfigXml();
  }

  /**
   * Instead of creating a default {@link XmlParser}, use the same
   * logic as Jetty's {@code WebXmlConfiguration} to create one that
   * is aware of web.xml files.  Specifically, this method registers
   * static versions of all known web.xml DTD's and schemas to avoid
   * URL retrieval at parse time.
   */
  @Override
  protected XmlParser createXmlParser() {

    XmlParser xmlParser = new XmlParser();
    URL dtd22 = getClass().getResource("/javax/servlet/resources/web-app_2_2.dtd");
    URL dtd23 = getClass().getResource("/javax/servlet/resources/web-app_2_3.dtd");
    URL jsp20xsd = getClass().getResource("/javax/servlet/resources/jsp_2_0.xsd");
    URL jsp21xsd = getClass().getResource("/javax/servlet/resources/jsp_2_1.xsd");
    URL j2ee14xsd = getClass().getResource("/javax/servlet/resources/j2ee_1_4.xsd");
    URL webapp24xsd = getClass().getResource("/javax/servlet/resources/web-app_2_4.xsd");
    URL webapp25xsd = getClass().getResource("/javax/servlet/resources/web-app_2_5.xsd");
    URL schemadtd = getClass().getResource("/javax/servlet/resources/XMLSchema.dtd");
    URL xmlxsd = getClass().getResource("/javax/servlet/resources/xml.xsd");
    URL webservice11xsd =
        getClass().getResource("/javax/servlet/resources/j2ee_web_services_client_1_1.xsd");
    URL webservice12xsd =
        getClass().getResource("/javax/servlet/resources/javaee_web_services_client_1_2.xsd");
    URL datatypesdtd = getClass().getResource("/javax/servlet/resources/datatypes.dtd");
    xmlParser.redirectEntity("web-app_2_2.dtd",dtd22);
    xmlParser.redirectEntity("-//Sun Microsystems, Inc.//DTD Web Application 2.2//EN",dtd22);
    xmlParser.redirectEntity("web.dtd",dtd23);
    xmlParser.redirectEntity("web-app_2_3.dtd",dtd23);
    xmlParser.redirectEntity("-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN",dtd23);
    xmlParser.redirectEntity("XMLSchema.dtd",schemadtd);
    xmlParser.redirectEntity("http://www.w3.org/2001/XMLSchema.dtd",schemadtd);
    xmlParser.redirectEntity("-//W3C//DTD XMLSCHEMA 200102//EN",schemadtd);
    xmlParser.redirectEntity("jsp_2_0.xsd",jsp20xsd);
    xmlParser.redirectEntity("http://java.sun.com/xml/ns/j2ee/jsp_2_0.xsd",jsp20xsd);
    xmlParser.redirectEntity("jsp_2_1.xsd",jsp21xsd);
    xmlParser.redirectEntity("http://java.sun.com/xml/ns/javaee/jsp_2_1.xsd",jsp21xsd);
    xmlParser.redirectEntity("j2ee_1_4.xsd",j2ee14xsd);
    xmlParser.redirectEntity("http://java.sun.com/xml/ns/j2ee/j2ee_1_4.xsd",j2ee14xsd);
    xmlParser.redirectEntity("web-app_2_4.xsd",webapp24xsd);
    xmlParser.redirectEntity("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd",webapp24xsd);
    xmlParser.redirectEntity("web-app_2_5.xsd",webapp25xsd);
    xmlParser.redirectEntity("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd",webapp25xsd);
    xmlParser.redirectEntity("xml.xsd",xmlxsd);
    xmlParser.redirectEntity("http://www.w3.org/2001/xml.xsd",xmlxsd);
    xmlParser.redirectEntity("datatypes.dtd",datatypesdtd);
    xmlParser.redirectEntity("http://www.w3.org/2001/datatypes.dtd",datatypesdtd);
    xmlParser.redirectEntity("j2ee_web_services_client_1_1.xsd",webservice11xsd);
    xmlParser.redirectEntity("http://www.ibm.com/webservices/xsd/j2ee_web_services_client_1_1.xsd",
                             webservice11xsd);
    xmlParser.redirectEntity("javaee_web_services_client_1_2.xsd",webservice12xsd);
    xmlParser.redirectEntity("http://www.ibm.com/webservices/xsd/javaee_web_services_client_1_2.xsd",
                             webservice12xsd);

    return xmlParser;
  }

  @Override
  protected WebXml processXml(InputStream is) {
    final WebXml webXml = new WebXml();
    parse(new ParserCallback() {

      private WebXml.SecurityConstraint  security;
      private String extension;

      @Override
      public void newNode(Node node, Stack<Node> ancestors) {
        String thisTag = node.getTag().toLowerCase();
        String parentTag = null;
        if (ancestors.size() > 0) {
          parentTag = ancestors.get(ancestors.size() - 1).getTag().toLowerCase();
        }

        if (URLPATTERN_TAG.equals(thisTag)) {
          String pattern = getString(node);
          if (SERVLETMAP_TAG.equals(parentTag) || FILTERMAP_TAG.equals(parentTag)) {
            String id = node.getAttribute("id");
            webXml.addServletPattern(pattern, id);
          } else if (security != null) {
            security.addUrlPattern(pattern);
          }
        } else if (ROLENAME_TAG.equals(thisTag) && AUTHCONST_TAG.equals(parentTag)) {
          if (security == null) {
            throw new AppEngineConfigException(getFilename() + ": <" + ROLENAME_TAG +
                "> in <" + AUTHCONST_TAG + "> in unrecognized context");
          }
          security.setRequiredRole(parseRequiredRole(getString(node)));
        } else if (TRANSGUARANTEE_TAG.equals(thisTag) && USERDATACONST_TAG.equals(parentTag)) {
          if (security == null) {
            throw new AppEngineConfigException(getFilename() + ": <" + TRANSGUARANTEE_TAG +
                "> in <" + USERDATACONST_TAG + "> in unrecognized context");
          }
          security.setTransportGuarantee(parseTransportGuarantee(getString(node)));
        } else if (SECURITYCONST_TAG.equals(thisTag)) {
          security = webXml.addSecurityConstraint();
        } else if (WELCOME_FILE_TAG.equals(thisTag)) {
          if (!WELCOME_FILE_LIST_TAG.equals(parentTag)) {
            throw new AppEngineConfigException(getFilename() + ": <" + WELCOME_FILE_TAG +
                "> in unrecognized context");
          }
          webXml.addWelcomeFile(getString(node));
        } else if (EXTENSION_TAG.equals(thisTag)) {
          extension = getString(node);
        } else if (MIME_TYPE_TAG.equals(thisTag)) {
          if (extension == null) {
            throw new AppEngineConfigException(getFilename() + ": <" + MIME_TYPE_TAG +
                                               "> without <extension>.");
          }
          String mimeType = getString(node);
          webXml.addMimeMapping(extension, mimeType);
          extension = null;
        } else if (ERROR_CODE_TAG.equals(thisTag)) {
          String code = getString(node);
          if ("404".equals(code)) {
            webXml.setFallThroughToRuntime(true);
          }
        }
      }
    }, is);

    if (webXml.getWelcomeFiles().isEmpty()) {
      for (String welcomeFile : DEFAULT_WELCOME_FILES) {
        webXml.addWelcomeFile(welcomeFile);
      }
    }

    return webXml;
  }

  private SecurityConstraint.RequiredRole parseRequiredRole(String role) {
    if ("*".equals(role)) {
      return SecurityConstraint.RequiredRole.ANY_USER;
    } else if ("admin".equals(role)) {
      return SecurityConstraint.RequiredRole.ADMIN;
    } else {
      throw new AppEngineConfigException(getFilename() + ": " +
                                         "Unknown role-name: must be '*' or 'admin'");
    }
  }

  private SecurityConstraint.TransportGuarantee parseTransportGuarantee(String transportGuarantee) {
    if ("NONE".equalsIgnoreCase(transportGuarantee)) {
      return SecurityConstraint.TransportGuarantee.NONE;
    } else if ("INTEGRAL".equalsIgnoreCase(transportGuarantee)) {
      return SecurityConstraint.TransportGuarantee.INTEGRAL;
    } else if ("CONFIDENTIAL".equalsIgnoreCase(transportGuarantee)) {
      return SecurityConstraint.TransportGuarantee.CONFIDENTIAL;
    } else {
      throw new AppEngineConfigException(getFilename() + ": " +
                                         "Unknown transport-guarantee: must be " +
                                         "NONE, INTEGRAL, or CONFIDENTIAL.");
    }
  }
}
