// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.apphosting.utils.config;

import org.mortbay.xml.XmlParser;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.Iterator;

/**
 * Creates a {@link BackendsXml} instance from
 * <appdir>WEB-INF/backends.xml.  If you want to read the
 * configuration from a different file, subclass and override
 * {@link #getFilename()}.  If you want to read the configuration from
 * something that isn't a file, subclass and override
 * {@link #getInputStream()}.
 *
 */
public class BackendsXmlReader extends AbstractConfigXmlReader<BackendsXml> {

  private static final String FILENAME = "WEB-INF/backends.xml";

  /**
   * Constructs the reader for {@code backends.xml} in a given application directory.
   * @param appDir the application directory
   */
  public BackendsXmlReader(String appDir) {
    super(appDir, false);
  }

  /**
   * Parses the config file.
   * @return A {@link BackendsXml} object representing the parsed configuration.
   */
  public BackendsXml readBackendsXml() {
    return readConfigXml();
  }

  @Override
  protected BackendsXml processXml(InputStream is) {
    BackendsXml backendsXml = new BackendsXml();

    XmlParser.Node rootNode = getTopLevelNode(is);
    Iterator<?> backends = rootNode.iterator("backend");
    while (backends.hasNext()) {
      XmlParser.Node node = (XmlParser.Node) backends.next();
      backendsXml.addBackend(convertBackendNode(node));
    }

    return backendsXml;
  }

  @Override
  protected String getRelativeFilename() {
    return FILENAME;
  }

  private BackendsXml.Entry convertBackendNode(XmlParser.Node node) {
    String name = trim(node.getAttribute("name"));
    Integer instances = null;
    String instanceClass = node.getString("class", false, true);
    Integer maxConcurrentRequests = null;
    EnumSet<BackendsXml.Option> options = EnumSet.noneOf(BackendsXml.Option.class);
    {
      XmlParser.Node subNode = node.get("instances");
      if (subNode != null) {
        instances = Integer.valueOf(getTextNode(subNode));
      }
    }
    {
      XmlParser.Node subNode = node.get("max-concurrent-requests");
      if (subNode != null) {
        maxConcurrentRequests = Integer.valueOf(getTextNode(subNode));
      }
    }
    {
      XmlParser.Node subNode = node.get("options");
      if (subNode != null) {
        XmlParser.Node failFastNode = subNode.get("fail-fast");
        if (failFastNode != null) {
          if (getBooleanValue(failFastNode)) {
            options.add(BackendsXml.Option.FAIL_FAST);
          }
        }
        XmlParser.Node dynamicNode = subNode.get("dynamic");
        if (dynamicNode != null) {
          if (getBooleanValue(dynamicNode)) {
            options.add(BackendsXml.Option.DYNAMIC);
          }
        }
        XmlParser.Node publicNode = subNode.get("public");
        if (publicNode != null) {
          if (getBooleanValue(publicNode)) {
            options.add(BackendsXml.Option.PUBLIC);
          }
        }
      }
    }
    return new BackendsXml.Entry(name, instances, instanceClass,
                                 maxConcurrentRequests, options, null);
  }

  private boolean getBooleanValue(XmlParser.Node node) {
    String value = getTextNode(node);
    value = value.trim();
    return (value.equalsIgnoreCase("true") || value.equals("1"));
  }

  private String getTextNode(XmlParser.Node node) {
    String value = (String) node.get(0);
    if (value == null) {
      value = "";
    }
    return value;
  }

  private String trim(String attribute) {
    return attribute == null ? null : attribute.trim();
  }
}
