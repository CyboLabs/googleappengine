// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.apphosting.utils.config;

import com.google.cron.GrocTimeSpecification;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed cron.xml file.
 *
 * Any additions to this class should also be made to the YAML
 * version in CronYamlReader.java.
 *
 */
public class CronXml {

  /**
   * Describes a single cron entry.
   *
   */
  public static class Entry {

    private static final String TZ_GMT = "UTC";

    String url;
    String desc;
    String tz;
    String schedule;
    String target;

    /** Create an empty cron entry. */
    public Entry() {
      desc = "";
      tz = TZ_GMT;
      url = null;
      schedule = null;
      target = null;
    }

    /** Records the human-readable description of this cron entry. */
    public void setDescription(String description) {
      this.desc = description.replace('\n', ' ');
    }

    /** Records the URL of this cron entry */
    public void setUrl(String url) {
      this.url = url.replace('\n', ' ');
    }

    /**
     * Records the schedule of this cron entry.  May throw
     * {@link AppEngineConfigException} if the schedule does not parse
     * correctly.
     *
     * @param schedule the schedule to save
     */
    public void setSchedule(String schedule) {
      schedule = schedule.replace('\n', ' ');
      this.schedule = schedule;
    }

    /**
     * Sets the timezone for this cron entry's schedule.  Defaults to "GMT"
     * @param timezone timezone for the cron entry's {@code schedule}.
     */
    public void setTimezone(String timezone) {
      this.tz = timezone.replace('\n', ' ');
    }

    public void setTarget(String target) {
      this.target = target;
    }

    public String getUrl() {
      return url;
    }

    public String getDescription() {
      return desc;
    }

    public String getSchedule() {
      return schedule;
    }

    public String getTimezone() {
      return tz;
    }

    public String getTarget() {
      return target;
    }

  }

  private List<Entry> entries;

  /** Create an empty configuration object. */
  public CronXml() {
    entries = new ArrayList<Entry>();
  }

  /**
   * Puts a new entry into the list defined by the config file.
   *
   * @throws AppEngineConfigException if the previously-last entry is still
   *    incomplete.
   * @return the new entry
   */
  public Entry addNewEntry() {
    validateLastEntry();
    Entry entry = new Entry();
    entries.add(entry);
    return entry;
  }

  /**
   * Puts an entry into the list defined by the config file.
   *
   * @throws AppEngineConfigException if the entry is still incomplete.
   */
  public void addEntry(Entry entry) {
    validateLastEntry();
    entries.add(entry);
    validateLastEntry();
  }

  /**
   * Get the entries. Used for testing.
   */
  public List<Entry> getEntries() {
    return entries;
  }

  /**
   * Check that the last entry defined is complete.
   * @throws AppEngineConfigException if it is not.
   */
  public void validateLastEntry() {
    if (entries.size() == 0) {
      return;
    }
    Entry last = entries.get(entries.size() - 1);
    if (last.getUrl() == null) {
      throw new AppEngineConfigException("no URL for cronentry");
    }
    if (last.getSchedule() == null) {
      throw new AppEngineConfigException("no schedule for cronentry " + last.getUrl());
    }
    try {
      GrocTimeSpecification parsedSchedule =
          GrocTimeSpecification.create(last.schedule);
    } catch (IllegalArgumentException iae) {
      throw new AppEngineConfigException("schedule " + last.schedule + " failed to parse",
                                         iae.getCause());
    }
  }

  /**
   * Get the YAML equivalent of this cron.xml file.
   *
   * @return contents of an equivalent {@code cron.yaml} file.
   */
  public String toYaml() {
    StringBuilder builder = new StringBuilder("cron:\n");
    for (Entry ent : entries) {
      builder.append("- description: '" + ent.getDescription().replace("'", "''") + "'\n");
      builder.append("  url: " + ent.getUrl() + "\n");
      builder.append("  schedule: " + ent.getSchedule() + "\n");
      builder.append("  timezone: " + ent.getTimezone() + "\n");
      String target = ent.getTarget();
      if (target != null) {
        builder.append("  target: " + target + "\n");
      }
    }
    return builder.toString();
  }
}
