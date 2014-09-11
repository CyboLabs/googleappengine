// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.apphosting.utils.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import net.sourceforge.yamlbeans.YamlException;
import net.sourceforge.yamlbeans.YamlReader;

/**
 * Class to parse queue.yaml into a QueueXml object.
 *
 */
public class CronYamlReader {

  /**
   * Wrapper around CronXml to make the JavaBeans properties match the YAML file syntax.
   */
  public static class CronYaml {
    private List<CronXml.Entry> entries;

    public List<CronXml.Entry> getCron() {
      return entries;
    }

    public void setCron(List<CronXml.Entry> entries) {
      this.entries = entries;
    }

    public CronXml toXml() {
      CronXml xml = new CronXml();
      if (entries != null) {
        for (CronXml.Entry entry : entries) {
          xml.addEntry(entry);
        }
      }
      return xml;
    }
  }

  private static final String FILENAME = "cron.yaml";
  private String appDir;

  public CronYamlReader(String appDir) {
    if (appDir.length() > 0 && appDir.charAt(appDir.length() - 1) != File.separatorChar) {
      appDir += File.separatorChar;
    }
    this.appDir = appDir;
  }

  public String getFilename() {
    return appDir + CronYamlReader.FILENAME;
  }

  public CronXml parse() {
    if (new File(getFilename()).exists()) {
      try {
        return parse(new FileReader(getFilename()));
      } catch (FileNotFoundException ex) {
        throw new AppEngineConfigException("Cannot find file " + getFilename(), ex);
      }
    }
    return null;
  }

  public static CronXml parse(Reader yaml) {
    YamlReader reader = new YamlReader(yaml);
    reader.getConfig().setPropertyElementType(CronYaml.class,
                                              "cron",
                                              CronXml.Entry.class);
    try {
      CronYaml cronYaml = reader.read(CronYaml.class);
      if (cronYaml == null) {
        throw new AppEngineConfigException("Empty cron configuration.");
      }
      return cronYaml.toXml();
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
  }

  public static CronXml parse(String yaml) {
    return parse(new StringReader(yaml));
  }
}
