// Copyright 2011 Google. All Rights Reserved.

package com.google.apphosting.utils.config;

import com.google.common.base.Joiner;

import net.sourceforge.yamlbeans.YamlException;
import net.sourceforge.yamlbeans.YamlReader;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

/**
 * JavaBean representation of the Java backends.yaml file.
 *
 */
public class BackendsYamlReader {

  public static class BackendsYaml {

    private List<Entry> backends;

    public List<Entry> getBackends() {
      return backends;
    }

    public void setBackends(List<Entry> backends) {
      this.backends = backends;
    }

    public BackendsXml toXml() {
      BackendsXml xml = new BackendsXml();
      for (Entry backend : backends) {
        xml.addBackend(backend.toXml());
      }
      return xml;
    }

    public static class Entry {
      private String name;
      private Integer instances;
      private String instanceClass;
      private BackendsXml.State state;
      private Integer maxConcurrentRequests;
      private Set<BackendsXml.Option> options = EnumSet.noneOf(BackendsXml.Option.class);

      public String getName() {
        return name;
      }

      public void setName(String name) {
        this.name = name;
      }

      public Integer getInstances() {
        return instances;
      }

      public void setInstances(Integer instances) {
        this.instances = instances;
      }

      public String getInstanceClass() {
        return instanceClass;
      }

      public void setInstanceClass(String instanceClass) {
        this.instanceClass = instanceClass;
      }

      public Integer getMax_concurrent_requests() {
        return maxConcurrentRequests;
      }

      public void setMax_concurrent_requests(Integer maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
      }

      public String getOptions() {
        List<String> optionNames = new ArrayList<String>();
        for (BackendsXml.Option option : options) {
          optionNames.add(option.getYamlValue());
        }
        return Joiner.on(", ").useForNull("null").join(optionNames);
      }

      public void setOptions(String optionString) {
        options.clear();
        for (String optionName : optionString.split(" *, *")) {
          options.add(BackendsXml.Option.fromYamlValue(optionName));
        }
      }

      public String getState() {
        return (state != null) ? state.getYamlValue() : null;
      }

      public void setState(String state) {
        this.state = (state != null) ? BackendsXml.State.fromYamlValue(state) : null;
      }

      public BackendsXml.Entry toXml() {
        return new BackendsXml.Entry(
            name,
            instances,
            instanceClass,
            maxConcurrentRequests,
            options,
            state);
      }
    }

    public static class EntryBeanInfo extends SimpleBeanInfo {
      @Override
      public PropertyDescriptor[] getPropertyDescriptors() {
        try {
          return new PropertyDescriptor[] {
            new PropertyDescriptor("name", Entry.class),
            new PropertyDescriptor("instances", Entry.class),
            new PropertyDescriptor("class", Entry.class, "getInstanceClass", "setInstanceClass"),
            new PropertyDescriptor("state", Entry.class),
            new PropertyDescriptor("max_concurrent_requests", Entry.class),
            new PropertyDescriptor("options", Entry.class),
          };
        } catch (IntrospectionException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
  }

  private static final String FILENAME = "backends.yaml";
  private String appDir;

  public BackendsYamlReader(String appDir) {
    if (appDir.length() > 0 && appDir.charAt(appDir.length() - 1) != File.separatorChar) {
      appDir += File.separatorChar;
    }
    this.appDir = appDir;
  }

  public String getFilename() {
    return appDir + FILENAME;
  }

  public BackendsXml parse() {
    if (new File(getFilename()).exists()) {
      try {
        return parse(new FileReader(getFilename()));
      } catch (FileNotFoundException ex) {
        throw new AppEngineConfigException("Cannot find file " + getFilename(), ex);
      }
    }
    return null;
  }

  public static BackendsXml parse(Reader yaml) {
    YamlReader reader = new YamlReader(yaml);
    reader.getConfig().setPropertyElementType(BackendsYaml.class,
                                              "backends",
                                              BackendsYaml.Entry.class);

    try {
      BackendsYaml backendsYaml = reader.read(BackendsYaml.class);
      if (backendsYaml == null) {
        throw new AppEngineConfigException("Empty backends configuration.");
      }
      return backendsYaml.toXml();
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
  }

  public static BackendsXml parse(String yaml) {
    return parse(new StringReader(yaml));
  }
}
