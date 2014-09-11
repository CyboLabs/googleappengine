package com.google.apphosting.utils.config;

import com.google.apphosting.utils.config.DispatchXml.DispatchEntry;
import com.google.common.annotations.VisibleForTesting;

import net.sourceforge.yamlbeans.YamlException;
import net.sourceforge.yamlbeans.YamlReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.List;

/**
 * Class to parse dispatch.yaml into a {@link DispatchXml}.
 *
 */
public class DispatchYamlReader {
  private static final String DISPATCH_FILENAME = "dispatch.yaml";
  private final String parentDirectory;

  /**
   * Constructs a {@link DispatchYamlReader}.
   * @param parentDirectory the directory containing the dispatch.yaml file.
   */
  public DispatchYamlReader(String parentDirectory) {
    if (parentDirectory.length() > 0
        && parentDirectory.charAt(parentDirectory.length() - 1) != File.separatorChar) {
      parentDirectory += File.separatorChar;
    }
    this.parentDirectory = parentDirectory;
  }

  public String getFilename() {
    return parentDirectory + DISPATCH_FILENAME;
  }

  public DispatchXml parse() {
    DispatchXml result = null;
    try {
      return parseImpl(new FileReader(getFilename()));
    } catch (FileNotFoundException ex) {
    }
    return null;
  }

  @VisibleForTesting
  static DispatchXml parseImpl(Reader yaml) {
    YamlReader reader = new YamlReader(yaml);
    reader.getConfig().setPropertyElementType(DispatchYaml.class, "dispatch",
        DispatchYamlEntry.class);
    try {
      DispatchYaml dispatchYaml = reader.read(DispatchYaml.class);
      if (dispatchYaml == null) {
        throw new AppEngineConfigException("Empty dispatch.yaml configuration.");
      }
      return dispatchYaml.toXml();
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
  }

  /**
   * Top level bean for a parsed dispatch.yaml file that meets the
   * requirements of {@link YamlReader}.
   */
  public static class DispatchYaml {
    private List<DispatchYamlEntry> dispatchEntries;

    public List<DispatchYamlEntry> getDispatch() {
      return dispatchEntries;
    }

    public void setDispatch(List<DispatchYamlEntry> entries) {
      this.dispatchEntries = entries;
    }

    public DispatchXml toXml() {
      DispatchXml.Builder builder = DispatchXml.builder();
      if (dispatchEntries != null) {
        for (DispatchYamlEntry entry : dispatchEntries) {
          builder.addDispatchEntry(entry.asDispatchEntry());
        }
      }
      return builder.build();
    }
  }

  /**
   * Bean for a parsed single uri to module mapping entry in a
   * dispatch.yaml file that meets the requirements of
   * {@link YamlReader}.
   */
  public static class DispatchYamlEntry {
    private String url;
    private String module;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getModule() {
      return module;
    }

    public void setModule(String module) {
      this.module = module;
    }

    DispatchEntry asDispatchEntry() {
      return new DispatchEntry(url, module);
    }
  }
}
