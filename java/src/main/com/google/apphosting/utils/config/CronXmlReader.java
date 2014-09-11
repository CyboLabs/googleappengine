// Copyright 2008 Google Inc. All Rights Reserved.
package com.google.apphosting.utils.config;

import org.mortbay.xml.XmlParser.Node;

import java.io.InputStream;
import java.util.Stack;

/**
 * Creates an {@link CronXml} instance from
 * <appdir>WEB-INF/cron.xml.  If you want to read the configuration
 * from a different file, subclass and override {@link #getFilename()}.  If you
 * want to read the configuration from something that isn't a file, subclass
 * and override {@link #getInputStream()}.
 *
 */
public class CronXmlReader extends AbstractConfigXmlReader<CronXml> {

  private static final String FILENAME = "WEB-INF/cron.xml";

  private static final String CRONENTRIES_TAG = "cronentries";
  private static final String CRON_TAG = "cron";
  private static final String DESCRIPTION_TAG = "description";
  private static final String SCHEDULE_TAG = "schedule";
  private static final String TARGET_TAG = "target";
  private static final String TIMEZONE_TAG = "timezone";
  private static final String URL_TAG = "url";

  /**
   * Constructs the reader for {@code cron.xml} in a given application directory.
   * @param appDir the application directory
   */
  public CronXmlReader(String appDir) {
    super(appDir, false);
  }

  /**
   * Parses the config file.
   * @return A {@link CronXml} object representing the parsed configuration.
   */
  public CronXml readCronXml() {
    return readConfigXml();
  }

  @Override
  protected CronXml processXml(InputStream is) {
    final CronXml cronXml = new CronXml();
    parse(new ParserCallback() {
      boolean first = true;
      CronXml.Entry entry;

      @Override
      public void newNode(Node node, Stack<Node> ancestors) {
        switch (ancestors.size()) {
          case 0:
            if (!CRONENTRIES_TAG.equalsIgnoreCase(node.getTag())) {
              throw new AppEngineConfigException(getFilename() + " does not contain <"
                  + CRONENTRIES_TAG + ">");
            }
            if (!first) {
              throw new AppEngineConfigException(getFilename() + " contains multiple <"
                  + CRONENTRIES_TAG + ">");
            }
            first = false;
            break;

          case 1:
            if (CRON_TAG.equalsIgnoreCase(node.getTag())) {
              entry = cronXml.addNewEntry();
            } else {
              throw new AppEngineConfigException(getFilename() + " contains <"
                  + node.getTag() + "> instead of <" + CRON_TAG + "/>");
            }
            break;

          case 2:
            assert(entry != null);
            if (DESCRIPTION_TAG.equalsIgnoreCase(node.getTag())) {
              if (node.size() == 1 && node.get(0) instanceof String) {
                entry.setDescription((String) node.get(0));
              } else {
                throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                    + DESCRIPTION_TAG + ">");
              }
            } else if (URL_TAG.equalsIgnoreCase(node.getTag())) {
                if (node.size() == 1 && node.get(0) instanceof String) {
                  entry.setUrl((String) node.get(0));
                } else {
                  throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                      + URL_TAG + ">");
                }
            } else if (SCHEDULE_TAG.equalsIgnoreCase(node.getTag())) {
              if (node.size() == 1 && node.get(0) instanceof String) {
                entry.setSchedule((String) node.get(0));
              } else {
                throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                    + SCHEDULE_TAG + ">");
              }
            } else if (TIMEZONE_TAG.equalsIgnoreCase(node.getTag())) {
              if (node.size() == 1 && node.get(0) instanceof String) {
                entry.setTimezone((String) node.get(0));
              } else {
                throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                    + TIMEZONE_TAG + ">");
              }
            } else if (TARGET_TAG.equalsIgnoreCase(node.getTag())) {
              if (node.size() == 1 && node.get(0) instanceof String) {
                entry.setTarget((String) node.get(0));
              } else {
                throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                    + TARGET_TAG + ">");
              }
            } else {
              throw new AppEngineConfigException(getFilename() + " contains unknown <"
                  + node.getTag() + "> inside <" + CRON_TAG + "/>");
            }
            break;

          default:
            throw new AppEngineConfigException(getFilename()
                + " has a syntax error; node <"
                + node.getTag() + "> is too deeply nested to be valid.");
        }
      }
    }, is);
    cronXml.validateLastEntry();
    return cronXml;
  }

  @Override
  protected String getRelativeFilename() {
    return FILENAME;
  }

}
