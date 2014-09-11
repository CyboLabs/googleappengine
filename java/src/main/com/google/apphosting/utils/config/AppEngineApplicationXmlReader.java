package com.google.apphosting.utils.config;

import org.mortbay.xml.XmlParser;

import java.io.InputStream;

/**
 * Constructs an {@link AppEngineApplicationXml} from an xml document
 * corresponding to appengine-application.xsd.
 *
 * <p>We use Jetty's {@link XmlParser} utility to match other Appengine XML
 * parsing code.
 *
 */
public class AppEngineApplicationXmlReader {
  private static final String EMPTY_STRING = "";

  /**
   * Construct an {@link AppEngineApplicationXml} from the xml document
   * within the provided {@link InputStream}.
   *
   * @param is The InputStream containing the xml we want to parse and process
   *
   * @return Object representation of the xml document
   * @throws AppEngineConfigException If the input stream cannot be parsed
   */
  public AppEngineApplicationXml processXml(InputStream is) throws AppEngineConfigException {
    AppEngineApplicationXml.Builder builder = new AppEngineApplicationXml.Builder();
    String applicationId = EMPTY_STRING;
    for (Object o : XmlUtils.parse(is)) {
      if (!(o instanceof XmlParser.Node)) {
        continue;
      }
      XmlParser.Node node = (XmlParser.Node) o;
      if (node.getTag().equals("application")) {
        applicationId = XmlUtils.getText(node);
      } else {
        throw new AppEngineConfigException("Unrecognized element <" + node.getTag()
            + "> in appengine-application.xml.");
      }
    }
    if (applicationId.equals(EMPTY_STRING)) {
      throw new AppEngineConfigException(
          "Missing or empty <application> element in appengine-application.xml.");
    }
    return builder.setApplicationId(applicationId).build();
  }
}
