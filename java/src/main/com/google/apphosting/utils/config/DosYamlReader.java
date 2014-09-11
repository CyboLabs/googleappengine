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
 * Class to parse dos.yaml into a DosXml object.
 *
 */
public class DosYamlReader {

  /**
   * Wrapper around DosXml to make the JavaBeans properties match the YAML file syntax.
   */
  public static class DosYaml {
    private List<DosXml.BlacklistEntry> entries;

    public List<DosXml.BlacklistEntry> getBlacklist() {
      return entries;
    }

    public void setBlacklist(List<DosXml.BlacklistEntry> entries) {
      this.entries = entries;
    }

    public DosXml toXml() {
      DosXml xml = new DosXml();
      if (entries != null) {
        for (DosXml.BlacklistEntry entry : entries) {
          xml.addBlacklistEntry(entry);
        }
      }
      return xml;
    }
  }

  private static final String FILENAME = "dos.yaml";
  private String appDir;

  public DosYamlReader(String appDir) {
    if (appDir.length() > 0 && appDir.charAt(appDir.length() - 1) != File.separatorChar) {
      appDir += File.separatorChar;
    }
    this.appDir = appDir;
  }

  public String getFilename() {
    return appDir + DosYamlReader.FILENAME;
  }

  public DosXml parse() {
    if (new File(getFilename()).exists()) {
      try {
        return parse(new FileReader(getFilename()));
      } catch (FileNotFoundException ex) {
        throw new AppEngineConfigException("Cannot find file " + getFilename(), ex);
      }
    }
    return null;
  }

  public static DosXml parse(Reader yaml) {
    YamlReader reader = new YamlReader(yaml);
    reader.getConfig().setPropertyElementType(DosYaml.class,
                                              "blacklist",
                                              DosXml.BlacklistEntry.class);
    try {
      DosYaml dosYaml = reader.read(DosYaml.class);
      if (dosYaml == null) {
        throw new AppEngineConfigException("Empty dos configuration.");
      }
      return dosYaml.toXml();
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
  }

  public static DosXml parse(String yaml) {
    return parse(new StringReader(yaml));
  }
}
