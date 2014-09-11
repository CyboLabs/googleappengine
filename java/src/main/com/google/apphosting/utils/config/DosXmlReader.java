// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.apphosting.utils.config;

import org.mortbay.xml.XmlParser.Node;

import java.io.InputStream;
import java.util.Stack;

/**
 * Creates an {@link DosXml} instance from
 * <appdir>WEB-INF/dos.xml.  If you want to read the configuration
 * from a different file, subclass and override {@link #getFilename()}.  If you
 * want to read the configuration from something that isn't a file, subclass
 * and override {@link #getInputStream()}.
 *
 */
public class DosXmlReader extends AbstractConfigXmlReader<DosXml> {

  private static final String FILENAME = "WEB-INF/dos.xml";

  private static final String BLACKLISTENTRIES_TAG = "blacklistentries";
  private static final String BLACKLIST_TAG = "blacklist";
  private static final String DESCRIPTION_TAG = "description";
  private static final String SUBNET_TAG = "subnet";

  /**
   * Constructs the reader for {@code dos.xml} in a given application directory.
   * @param appDir the application directory
   */
  public DosXmlReader(String appDir) {
    super(appDir, false);
  }

  /**
   * Parses the config file.
   * @return A {@link DosXml} object representing the parsed configuration.
   */
  public DosXml readDosXml() {
    return readConfigXml();
  }

  @Override
  protected DosXml processXml(InputStream is) {
    final DosXml dosXml = new DosXml();
    parse(new ParserCallback() {
      boolean first = true;
      DosXml.BlacklistEntry blacklistEntry;

      @Override
      public void newNode(Node node, Stack<Node> ancestors) {
        switch (ancestors.size()) {
          case 0:
            if (!BLACKLISTENTRIES_TAG.equalsIgnoreCase(node.getTag())) {
              throw new AppEngineConfigException(getFilename() + " does not contain <"
                  + BLACKLISTENTRIES_TAG + ">");
            }
            if (!first) {
              throw new AppEngineConfigException(getFilename() + " contains multiple <"
                  + BLACKLISTENTRIES_TAG + ">");
            }
            first = false;
            break;

          case 1:
            if (BLACKLIST_TAG.equalsIgnoreCase(node.getTag())) {
              blacklistEntry = dosXml.addNewBlacklistEntry();
            } else {
              throw new AppEngineConfigException(getFilename() + " contains <"
                  + node.getTag() + "> instead of <" + BLACKLIST_TAG + "/>");
            }
            break;

          case 2:
            assert(blacklistEntry != null);
            if (DESCRIPTION_TAG.equalsIgnoreCase(node.getTag())) {
              if (node.size() == 1 && node.get(0) instanceof String) {
                blacklistEntry.setDescription((String) node.get(0));
              } else {
                throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                    + DESCRIPTION_TAG + ">");
              }
            } else if (SUBNET_TAG.equalsIgnoreCase(node.getTag())) {
                if (node.size() == 1 && node.get(0) instanceof String) {
                  blacklistEntry.setSubnet((String) node.get(0));
                } else {
                  throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                      + SUBNET_TAG + ">");
                }
            } else {
              throw new AppEngineConfigException(getFilename() + " contains unknown <"
                  + node.getTag() + "> inside <" + BLACKLIST_TAG + "/>");
            }
            break;

          default:
            throw new AppEngineConfigException(getFilename()
                + " has a syntax error; node <"
                + node.getTag() + "> is too deeply nested to be valid.");
        }
      }
    }, is);
    dosXml.validateLastEntry();
    return dosXml;
  }

  @Override
  protected String getRelativeFilename() {
    return FILENAME;
  }
}
