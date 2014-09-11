package com.google.apphosting.utils.config;

import org.mortbay.xml.XmlParser;
import org.mortbay.xml.XmlParser.Node;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility functions for processing XML.
 */
public class XmlUtils {
  private static final Logger logger = Logger.getLogger(XmlUtils.class.getName());

  static String getText(XmlParser.Node node) throws AppEngineConfigException{
    Object child = node.get(0);
    String value;
    if (child == null) {
      value = "";
    } else {
      if (!(child instanceof String)) {
        String msg = "Invalid XML: String content expected in node '" + node.getTag() + "'.";
        logger.log(Level.SEVERE, msg);
        throw new AppEngineConfigException(msg);
      }
      value = (String) child;
    }

    return value.trim();
  }

  /**
   * Parses the input stream and returns the {@link Node} for the root element.
   *
   * @throws AppEngineConfigException If the input stream cannot be parsed.
   */
  static Node parse(InputStream is) {
    XmlParser xmlParser = new XmlParser();
    try {
      return xmlParser.parse(is);
    } catch (IOException e) {
      String msg = "Received IOException parsing the input stream.";
      logger.log(Level.SEVERE, msg, e);
      throw new AppEngineConfigException(msg, e);
    } catch (SAXException e) {
      String msg = "Received SAXException parsing the input stream.";
      logger.log(Level.SEVERE, msg, e);
      throw new AppEngineConfigException(msg, e);
    }
  }
}
