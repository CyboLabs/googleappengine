package com.google.apphosting.utils.config;

import org.mortbay.xml.XmlParser;
import org.mortbay.xml.XmlParser.Node;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Constructs an {@link ApplicationXml} from an xml document
 * corresponding to http://java.sun.com/xml/ns/javaee/application_5.xsd.
 *
 * We use Jetty's {@link XmlParser} utility to match other Appengine XML
 * parsing code.
 *
 */
public class ApplicationXmlReader {
  /**
   * Construct an {@link ApplicationXml} from the xml document
   * within the provided {@link InputStream}.
   *
   * @param is The InputStream containing the xml we want to parse and process.
   *
   * @return Object representation of the xml document.
   * @throws AppEngineConfigException If the input stream cannot be parsed.
   */
  public ApplicationXml processXml(InputStream is) {
    ApplicationXml.Builder builder = ApplicationXml.builder();
    HashSet<String> contextRoots = new HashSet<String>();
    for (Object o : XmlUtils.parse(is)) {
      if (!(o instanceof XmlParser.Node)) {
        continue;
      }
      XmlParser.Node node = (XmlParser.Node) o;
      if ("icon".equals(node.getTag())) {
      } else if ("display-name".equals(node.getTag())) {
      } else if ("description".equals(node.getTag())) {
      } else if ("module".equals(node.getTag())) {
        handleModuleNode(node, builder.getModulesBuilder(), contextRoots);
      } else if ("security-role".equals(node.getTag())) {
      } else if ("library-directory".equals(node.getTag())) {
      } else {
        reportUnrecognizedTag(node.getTag());
      }
    }
    return builder.build();
  }

  private void handleModuleNode(Node module, ApplicationXml.Modules.Builder builder,
      Set<String> contextRoots) {
    for (Object o : module) {
      if (!(o instanceof XmlParser.Node)) {
        continue;
      }
      XmlParser.Node node = (XmlParser.Node) o;
      if ("alt-dd".equals(node.getTag())) {
      } else if ("connector".equals(node.getTag())) {
      } else if ("ejb".equals(node.getTag())) {
      } else if ("java".equals(node.getTag())) {
      } else if ("web".equals(node.getTag())) {
        handleWebNode(node, builder, contextRoots);
      } else {
        reportUnrecognizedTag(node.getTag());
      }
    }
  }

  private void handleWebNode(Node web, ApplicationXml.Modules.Builder builder,
      Set<String> contextRoots) {
    String contextRoot = null;
    String webUri = null;
    for (Object o : web) {
      if (!(o instanceof XmlParser.Node)) {
        continue;
      }
      XmlParser.Node node = (XmlParser.Node) o;
      if ("web-uri".equals(node.getTag())) {
        if (webUri != null) {
          throw new AppEngineConfigException(
              "web-uri multiply defined in application.xml web module.");
        }
        webUri = XmlUtils.getText(node);
        if (webUri.isEmpty()) {
          throw new AppEngineConfigException(
              "web-uri is empty in application.xml web module.");
        }
      } else if ("context-root".equals(node.getTag())) {
        if (contextRoot != null) {
          throw new AppEngineConfigException(
              "context-root multiply defined in application.xml web module.");
        }
        contextRoot = XmlUtils.getText(node);
        if (contextRoot.isEmpty()) {
          throw new AppEngineConfigException(
              "context-root is empty in application.xml web module.");
        }
        if (contextRoots.contains(contextRoot)) {
          throw new AppEngineConfigException(
              "context-root value '" + contextRoot + "' is not unique.");
        }
        contextRoots.add(contextRoot);
      } else {
        reportUnrecognizedTag(node.getTag());
      }
    }
    if (null == webUri) {
      throw new AppEngineConfigException(
          "web-uri not defined in application.xml web module.");
    }
    if (null == contextRoot) {
      throw new AppEngineConfigException(
          "context-root not defined in application.xml web module.");
    }
    builder.addWeb(new ApplicationXml.Modules.Web(webUri, contextRoot));
  }

  private void reportUnrecognizedTag(String tag) throws AppEngineConfigException {
    throw new AppEngineConfigException("Unrecognized element <" + tag
        + "> in application.xml.");
  }
}
