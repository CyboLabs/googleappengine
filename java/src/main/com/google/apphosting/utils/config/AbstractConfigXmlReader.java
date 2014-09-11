// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.apphosting.utils.config;

import org.mortbay.xml.XmlParser;
import org.mortbay.xml.XmlParser.Node;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract class for reading the XML files to configure an application.
 *
 * @param <T> the type of the configuration object returned
 *
 *
 */
public abstract class AbstractConfigXmlReader<T> {

  /**
   * Callback notified as nodes are traversed in the parsed XML.
   */
  public interface ParserCallback {
    /**
     * Node handling callback.
     *
     * @param node the newly-entered node
     * @param ancestors a possibly-empty (but not null) stack of parent nodes
     *           of {@code node}.
     * @throws AppEngineConfigException if something is wrong in the XML
     */
    public void newNode(XmlParser.Node node, Stack<XmlParser.Node> ancestors);
  }

  /** The path to the top level directory of the application. */
  protected final String appDir;

  /** Whether the config file must exist in a correct application. */
  protected final boolean required;

  /** A logger for messages. */
  protected Logger logger;

  /**
   * Initializes the generic attributes of all our configuration XML readers.
   *
   * @param appDir pathname to the application directory
   * @param required {@code true} if is an error for the config file not to exist.
   */
  public AbstractConfigXmlReader(String appDir, boolean required) {
    if (appDir.length() > 0 && appDir.charAt(appDir.length() - 1) != File.separatorChar) {
      appDir += File.separatorChar;
    }
    this.appDir = appDir;
    this.required = required;
    logger = Logger.getLogger(this.getClass().getName());
  }

  /**
   * Gets the absolute filename for the configuration file.
   *
   * @return concatenation of {@link #appDir} and {@link #getRelativeFilename()}
   */
  public String getFilename() {
    return appDir + getRelativeFilename();
  }

  /**
   * Fetches the name of the configuration file processed by this instance,
   * relative to the application directory.
   *
   * @return relative pathname for a configuration file
   */
  protected abstract String getRelativeFilename();

  /**
   * Parses the input stream to compute an instance of {@code T}.
   *
   * @return the parsed config file
   * @throws AppEngineConfigException if there is an error.
   */
  protected abstract T processXml(InputStream is);

  /**
   * Does the work of reading the XML file, processing it, and either returning
   * an object representing the result or throwing error information.
   *
   * @return A {@link AppEngineWebXml} config object derived from the
   * contents of the config xml, or {@code null} if no such file is defined and
   * the config file is optional.
   *
   * @throws AppEngineConfigException If the file cannot be parsed properly
   */
  protected T readConfigXml() {
    InputStream is = null;
    T configXml;
    if (!required && !fileExists()) {
      return null;
    }
    try {
      is = getInputStream();
      configXml = processXml(is);
      logger.info("Successfully processed " + getFilename());
    } catch (Exception e) {
      String msg = "Received exception processing " + getFilename();
      logger.log(Level.SEVERE, msg, e);
      if (e instanceof AppEngineConfigException) {
        throw (AppEngineConfigException) e;
      }
      throw new AppEngineConfigException(msg, e);
    } finally {
      close(is);
    }
    return configXml;
  }

  /**
   * Tests for file existence.  Test clases will often override this, to lie
   * (and thus stay small by avoiding needing the real filesystem).
   */
  protected boolean fileExists() {
    return (new File(getFilename())).exists();
  }

  /**
   * Tests for file existence.  Test clases will often override this, to lie
   * (and thus stay small by avoiding needing the real filesystem).
   */
  protected boolean generatedFileExists() {
    return getGeneratedFile().exists();
  }

  /**
   * Opens an input stream, or fails with an AppEngineConfigException
   * containing helpful information.  Test classes will often override this.
   *
   * @return an open {@link InputStream}
   * @throws AppEngineConfigException
   */
  protected InputStream getInputStream() {
    try {
      return new FileInputStream(getFilename());
    } catch (FileNotFoundException fnfe) {
      throw new AppEngineConfigException(
          "Could not locate " + new File(getFilename()).getAbsolutePath(), fnfe);
    }
  }

  /**
   * Returns a {@code File} for the generated variant of this file, or
   * {@code null} if no generation is possible.  This is not an indication that
   * the file exists, only of where it would be if it does exist.
   *
   * @return the generated file, if there might be one; {@code null} if not.
   */
  protected File getGeneratedFile() {
    return null;
  }

  /**
   * Returns an InputStream of the generated contents, or {@code null} if no
   * generated contents are available.
   *
   * @return input stream, or {@code null}
   */
  protected InputStream getGeneratedStream() {
    File file = getGeneratedFile();
    if (file == null || !file.exists()) {
      return null;
    }
    try {
      return new FileInputStream(file);
    } catch (FileNotFoundException ex) {
      throw new AppEngineConfigException("can't find generated " + file.getPath());
    }
  }

  /**
   * Creates an {@link XmlParser} to use when parsing this file.
   */
  protected XmlParser createXmlParser() {
    return new XmlParser();
  }

  /**
   * Given an InputStream, create a Node corresponding to the top level xml
   * element.
   *
   * @throws AppEngineConfigException If the input stream cannot be parsed.
   */
  protected XmlParser.Node getTopLevelNode(InputStream is) {
    XmlParser xmlParser = createXmlParser();
    try {
      return xmlParser.parse(is);
    } catch (IOException e) {
      String msg = "Received IOException parsing the input stream for " + getFilename();
      logger.log(Level.SEVERE, msg, e);
      throw new AppEngineConfigException(msg, e);
    } catch (SAXException e) {
      String msg = "Received SAXException parsing the input stream for " + getFilename();
      logger.log(Level.SEVERE, msg, e);
      throw new AppEngineConfigException(msg, e);
    }
  }

  /**
   * Parses the nodes of an XML file.  This is <i>limited</i> XML parsing,
   * in particular skipping any TEXT element and parsing only the nodes.
   *
   * @param parseCb the ParseCallback to call for each node
   * @param is the input stream to read
   * @throws AppEngineConfigException on any error
   */
  protected void parse(ParserCallback parseCb, InputStream is) {
    Stack<XmlParser.Node> stack = new Stack<XmlParser.Node>();
    XmlParser.Node top = getTopLevelNode(is);
    parse(top, stack, parseCb);
  }

  /**
   * Recursive descent helper for {@link #parse(ParserCallback, InputStream)}, calling
   * the callback for this node and recursing for its children.
   *
   * @param node the node being visited
   * @param stack the anscestors of {@code node}
   * @param parseCb the visitor callback
   * @throws AppEngineConfigException for any configuration errors
   */
  protected void parse(XmlParser.Node node, Stack<XmlParser.Node> stack, ParserCallback parseCb) {
    parseCb.newNode(node, stack);
    stack.push(node);
    for (Object child : node) {
      if (child instanceof XmlParser.Node) {
        parse((XmlParser.Node) child, stack, parseCb);
      }
    }
    stack.pop();
  }

  /**
   * Closes the given input stream, converting any {@link IOException} thrown
   * to an {@link AppEngineConfigException} if necessary.
   *
   * @throws AppEngineConfigException if the input stream cannot close
   */
  protected void close(InputStream is) {
    if (is != null) {
      try {
        is.close();
      } catch (IOException e) {
        throw new AppEngineConfigException(e);
      }
    }
  }

  /**
   * Gets the Node's first (index zero) content value, as a trimmed string.
   *
   * @param node the node to get the string from.
   */
  protected String getString(Node node) {
    String string = (String) node.get(0);
    if (string == null) {
      return null;
    } else {
      return string.trim();
    }
  }

}
