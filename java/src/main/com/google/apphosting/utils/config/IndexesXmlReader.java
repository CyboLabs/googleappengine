// Copyright 2008 Google Inc. All Rights Reserved.
package com.google.apphosting.utils.config;

import org.mortbay.xml.XmlParser.Node;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;
import java.util.Stack;
import java.util.logging.Level;

/**
 * Creates an {@link IndexesXml} instance from
 * <appdir>WEB-INF/datastore-indexes.xml.  If you want to read the
 * configuration from a different file, subclass and override
 * {@link #getFilename()}.  If you want to read the configuration from
 * something that isn't a file, subclass and override
 * {@link #getInputStream()}.
 *
 */
public class IndexesXmlReader extends AbstractConfigXmlReader<IndexesXml> {

  /**
   * Relative-to-{@code GenerationDirectory.GENERATED_DIR_PROPERTY} file for
   * generated index.
   */
  public static final String GENERATED_INDEX_FILENAME = "datastore-indexes-auto.xml";
  public static final String INDEX_FILENAME = "datastore-indexes.xml";
  public static final String INDEX_YAML_FILENAME = "WEB-INF/index.yaml";

  /** Name of the XML tag in {@code datastore-indexes.xml} for autoindexing */
  public static final String AUTOINDEX_TAG = "auto-update";

  private static final String FILENAME = "WEB-INF/datastore-indexes.xml";

  private static final String INDEXES_TAG = "datastore-indexes";
  private static final String INDEX_TAG = "datastore-index";
  private static final String KIND_PROP = "kind";
  private static final String ANCESTORS_PROP = "ancestor";
  private static final String ANCESTORS_VALUE_YES = "true";
  private static final String ANCESTORS_VALUE_NO = "false";
  private static final String PROPERTY_TAG = "property";
  private static final String NAME_PROP = "name";
  private static final String DIRECTION_PROP = "direction";
  private static final String DIRECTION_VALUE_ASC = "asc";
  private static final String DIRECTION_VALUE_DESC = "desc";

  private IndexesXml indexesXml;

  /**
   * Constructs a reader for the {@code indexes.xml} configuration of a given app.
   * @param appDir root directory of the application
   */
  public IndexesXmlReader(String appDir) {
    super(appDir, false);
  }

  /**
   * Reads the configuration file.
   * @return an {@link IndexesXml} representing the parsed configuration.
   */
  public IndexesXml readIndexesXml() {
    return readConfigXml();
  }

  /**
   * Reads the index configuration.  If neither the user-generated nor the
   * auto-generated config file exists, returns a {@code null}.  Otherwise,
   * reads both files (if available) and returns the union of both sets of
   * indexes.
   *
   * @throws AppEngineConfigException If the file cannot be parsed properly
   */
  @Override
  protected IndexesXml readConfigXml() {
    InputStream is = null;
    String filename = null;

    indexesXml = new IndexesXml();
    try {
      if (fileExists()) {
        filename = getFilename();
        is = getInputStream();
        processXml(is);
        logger.info("Successfully processed " + filename);
      }
      if (yamlFileExists()) {
        filename = getYamlFilename();
        IndexYamlReader.parse(getYamlReader(), indexesXml);
        logger.info("Successfully processed " + filename);
      }
      if (generatedFileExists()) {
        filename = getGeneratedFile().getPath();
        is = getGeneratedStream();
        processXml(is);
        logger.info("Successfully processed " + filename);
      }
    } catch (Exception e) {
      String msg = "Received exception processing " + filename;
      logger.log(Level.SEVERE, msg, e);
      if (e instanceof AppEngineConfigException) {
        throw (AppEngineConfigException) e;
      }
      throw new AppEngineConfigException(msg, e);
    } finally {
      close(is);
    }
    return indexesXml;
  }

  @Override
  protected IndexesXml processXml(InputStream is) {
    parse(new ParserCallback() {
      boolean first = true;
      IndexesXml.Index index;

      @Override
      public void newNode(Node node, Stack<Node> ancestors) {
        switch (ancestors.size()) {
          case 0:
            if (!INDEXES_TAG.equalsIgnoreCase(node.getTag())) {
              throw new AppEngineConfigException(getFilename() + " does not contain <"
                  + INDEXES_TAG + ">");
            }
            if (!first) {
              throw new AppEngineConfigException(getFilename() + " contains multiple <"
                  + INDEXES_TAG + ">");
            }
            first = false;
            break;

          case 1:
            if (INDEX_TAG.equalsIgnoreCase(node.getTag())) {
              String kind = node.getAttribute(KIND_PROP);
              if (kind == null) {
                throw new AppEngineConfigException(getFilename() + " has <" + INDEX_TAG +
                    "> missing required attribute \"" + KIND_PROP + "\"");
              }
              String anc = node.getAttribute(ANCESTORS_PROP).toLowerCase();
              boolean ancestorProp = false;
              if (anc != null) {
                if (anc.equals(ANCESTORS_VALUE_YES)) {
                  ancestorProp = true;
                } else if (!anc.equals(ANCESTORS_VALUE_NO)) {
                  throw new AppEngineConfigException(getFilename() + " has <" + INDEX_TAG +
                      "> with attribute \"" + ANCESTORS_PROP + "\" not \"" + ANCESTORS_VALUE_YES +
                      "\" or \"" + ANCESTORS_VALUE_NO + "\"");
                }
              }
              index = indexesXml.addNewIndex(kind, ancestorProp);

            } else {
              throw new AppEngineConfigException(getFilename() + " contains <"
                  + node.getTag() + "> instead of <" + INDEX_TAG + "/>");
            }
            break;

          case 2:
            assert(index != null);
            if (PROPERTY_TAG.equalsIgnoreCase(node.getTag())) {
              String name = node.getAttribute(NAME_PROP);
              if (name == null) {
                throw new AppEngineConfigException(getFilename() + " has <" + PROPERTY_TAG +
                    "> missing required attribute \"" + NAME_PROP + "\"");
              }
              String direction = node.getAttribute(DIRECTION_PROP).toLowerCase();
              boolean ascending = true;
              if (direction != null) {
                if (direction.equals(DIRECTION_VALUE_DESC)) {
                  ascending = false;
                } else if (!direction.equals(DIRECTION_VALUE_ASC)) {
                  throw new AppEngineConfigException(getFilename() + " has <" + PROPERTY_TAG +
                      "> with attribute \"" + DIRECTION_PROP + "\" not \"" + DIRECTION_VALUE_ASC +
                      "\" or \"" + DIRECTION_VALUE_DESC + "\"");
                }
              }
              index.addNewProperty(name, ascending);
            } else {
              throw new AppEngineConfigException(getFilename() + " contains <"
                  + node.getTag() + "> instead of <" + PROPERTY_TAG + "/>");
            }
            break;

          default:
            throw new AppEngineConfigException(getFilename()
                + " has a syntax error; node <"
                + node.getTag() + "> is too deeply nested to be valid.");
        }
      }
    }, is);
    return indexesXml;
  }

  @Override
  protected String getRelativeFilename() {
    return FILENAME;
  }

  @Override
  protected File getGeneratedFile() {
    File genFile = new File(GenerationDirectory.getGenerationDirectory(new File(appDir)),
                    GENERATED_INDEX_FILENAME);
    return genFile;
  }

  protected String getYamlFilename() {
    return appDir + INDEX_YAML_FILENAME;
  }

  protected boolean yamlFileExists() {
    return new File(getYamlFilename()).exists();
  }

  protected Reader getYamlReader() {
    try {
      return new FileReader(getYamlFilename());
    } catch (FileNotFoundException ex) {
      throw new AppEngineConfigException("Cannot find file" + getYamlFilename());
    }
  }
}
