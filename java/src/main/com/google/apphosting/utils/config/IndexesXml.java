// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.apphosting.utils.config;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Parsed datastore-indexes.xml file.
 *
 * Any additions to this class should also be made to the YAML
 * version in IndexYamlReader.java.
 *
 */
public class IndexesXml implements Iterable<IndexesXml.Index>{

  public class PropertySort {
    private String propName;
    private boolean ascending;

    public PropertySort (String propName, boolean ascending) {
      this.propName = propName;
      this.ascending = ascending;
    }

    public String getPropertyName() {
      return propName;
    }

    public boolean isAscending() {
      return ascending;
    }
  }

  /**
   */
  public class Index {
    private String kind;
    private boolean ancestors;
    private List<PropertySort> properties;

    public Index (String kind, boolean ancestors) {
      this.kind = kind;
      this.ancestors = ancestors;
      this.properties = new ArrayList<PropertySort>();
    }

    public void addNewProperty(String name, boolean ascending) {
      properties.add(new PropertySort(name, ascending));
    }

    public String getKind() {
      return kind;
    }

    public boolean doIndexAncestors() {
      return ancestors;
    }

    public List<PropertySort> getProperties() {
      return properties;
    }

    /**
     * Builds a Yaml String representing this index, using the style of Yaml
     * generation appropriate for a local indexes.yaml files.
     * @return A Yaml String
     */
    private String toLocalStyleYaml(){
      StringBuilder builder = new StringBuilder(50 * (1 + properties.size()));
      builder.append("- kind: \"" + kind + "\"\n");
      if (ancestors) {
        builder.append("  ancestor: yes\n");
      }
      if (!properties.isEmpty()) {
        builder.append("  properties:\n");
        for (PropertySort prop : properties) {
          builder.append("  - name: \"" + prop.getPropertyName() + "\"\n");
          builder.append("    direction: " + (prop.isAscending() ? "asc" : "desc") + "\n");
        }
      }
      return builder.toString();
    }

    /**
     * Builds a Yaml string representing this index, mimicking the style of Yaml
     * generation used on the admin server. Since the admin server is written in
     * python, it generates a slightly different style of yaml. This method is
     * useful only for testing that the client-side code is able to parse this
     * style of yaml.
     *
     * @return An admin-server-style Yaml String.
     */
    private String toServerStyleYaml() {
      StringBuilder builder = new StringBuilder(50 * (1 + properties.size()));
      builder.append("- ").append(IndexYamlReader.INDEX_TAG).append("\n");
      builder.append("  kind: " + kind + "\n");
      if (ancestors) {
        builder.append("  ancestor: yes\n");
      }
      if (!properties.isEmpty()) {
        builder.append("  properties:\n");
        for (PropertySort prop : properties) {
          builder.append("  - ");
          builder.append(IndexYamlReader.PROPERTY_TAG);
          builder.append(" {direction: ");
          builder.append((prop.isAscending() ? "asc" : "desc"));
          builder.append(",\n");
          builder.append("    ");
          builder.append("name: " + prop.getPropertyName());
          builder.append("}\n");
        }
      }
      return builder.toString();
    }

    public String toXmlString() {
      StringBuilder builder = new StringBuilder(100 * (1 + properties.size()));
      builder.append("<datastore-index kind=\"" + kind + "\" ancestor=\"" + ancestors + "\">\n");
      for (PropertySort prop : properties) {
        String direction = (prop.isAscending() ? "asc" : "desc");
        builder.append(
            "    <property name=\"" + prop.getPropertyName() + "\" direction=\"" + direction
                + "\"/>\n");
      }
      builder.append("</datastore-index>\n");
      return builder.toString();
    }
  }

  private List<Index> indexes;

  public IndexesXml() {
    indexes = new ArrayList<Index>();
  }

  @Override
  public Iterator<Index> iterator() {
    return indexes.iterator();
  }

  public int size(){
    return indexes.size();
  }

  public Index addNewIndex(String kind, boolean ancestors) {
    Index index = new Index(kind, ancestors);
    indexes.add(index);
    return index;
  }

  /**
   * Adds the given {@link Index} to the collection
   * contained in this object. Note that given {@link Index}
   * is not cloned. The provided object instance will become
   * incorporated into this object's collection.
   * @param index
   */
  public void addNewIndex(Index index){
    indexes.add(index);
  }

  public String toYaml() {
   return toYaml(false);
  }

  /**
   * Builds yaml string representing the indexes
   *
   * @param serverStyle Use the admin server style of yaml generation. Since the
   *        admin server is written in python, it generates a slightly different
   *        style of yaml. Setting this parameter to {@code true} is useful only
   *        for testing that the client-side code is able to parse this style of
   *        yaml.
   * @return A Yaml string.
   */
  public String toYaml(boolean serverStyle) {
    StringBuilder builder = new StringBuilder(1024);
    if (serverStyle) {
      builder.append(IndexYamlReader.INDEX_DEFINITIONS_TAG).append("\n");
    }
    builder.append("indexes:");
    int numIndexes = (null == indexes ? 0 : indexes.size());
    if (0 == numIndexes && serverStyle) {
      builder.append(" []");
    }
    builder.append("\n");
    for (Index index : indexes) {
      String indexYaml = (serverStyle ? index.toServerStyleYaml() : index.toLocalStyleYaml());
      builder.append(indexYaml);
    }
    return builder.toString();
  }

}
