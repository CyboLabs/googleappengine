// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.apphosting.utils.config;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed queue.xml file.
 *
 * Any additions to this class should also be made to the YAML
 * version in QueueYamlReader.java.
 *
 */
public class QueueXml {

  static final String RATE_REGEX = "([0-9]+(\\.[0-9]+)?)/([smhd])";
  static final Pattern RATE_PATTERN = Pattern.compile(RATE_REGEX);

  static final String TOTAL_STORAGE_LIMIT_REGEX = "^([0-9]+(\\.[0-9]*)?[BKMGT]?)";
  static final Pattern TOTAL_STORAGE_LIMIT_PATTERN = Pattern.compile(TOTAL_STORAGE_LIMIT_REGEX);

  private static final int MAX_QUEUE_NAME_LENGTH = 100;
  private static final String QUEUE_NAME_REGEX = "[a-zA-Z\\d-]{1," + MAX_QUEUE_NAME_LENGTH + "}";
  private static final Pattern QUEUE_NAME_PATTERN = Pattern.compile(QUEUE_NAME_REGEX);

  private static final String TASK_AGE_LIMIT_REGEX =
      "([0-9]+(?:\\.?[0-9]*(?:[eE][\\-+]?[0-9]+)?)?)([smhd])";
  private static final Pattern TASK_AGE_LIMIT_PATTERN = Pattern.compile(TASK_AGE_LIMIT_REGEX);

  private static final String MODE_REGEX = "push|pull";
  private static final Pattern MODE_PATTERN = Pattern.compile(MODE_REGEX);

  private static final int MAX_TARGET_LENGTH = 100;
  private static final String TARGET_REGEX = "[a-z\\d\\-]{1," + MAX_TARGET_LENGTH + "}";
  private static final Pattern TARGET_PATTERN = Pattern.compile(TARGET_REGEX);

  /**
   * The default queue name.  Keep this in sync with
   * {@link com.google.appengine.api.taskqueue.Queue#DEFAULT_QUEUE}.
   */
  private static final String DEFAULT_QUEUE = "default";

  /**
   * Enumerates the allowed units for Queue rate.
   */
  public enum RateUnit {
    SECOND('s', 1),
    MINUTE('m', SECOND.getSeconds() * 60),
    HOUR('h', MINUTE.getSeconds() * 60),
    DAY('d', HOUR.getSeconds() * 24);

    final char ident;
    final int seconds;

    RateUnit(char ident, int seconds) {
      this.ident = ident;
      this.seconds = seconds;
    }

    static RateUnit valueOf(char unit) {
      switch (unit) {
        case 's' : return SECOND;
        case 'm' : return MINUTE;
        case 'h' : return HOUR;
        case 'd' : return DAY;
      }
      throw new AppEngineConfigException("Invalid rate was specified.");
    }

    public char getIdent() {
      return ident;
    }

    public int getSeconds() {
      return seconds;
    }
  }

  /**
   * Access control list for a queue.
   */
  public static class AclEntry {
    private String userEmail;
    private String writerEmail;

    public AclEntry() {
      userEmail = null;
      writerEmail = null;
    }

    public void setUserEmail(String userEmail) {
      this.userEmail = userEmail;
    }

    public String getUserEmail() {
      return userEmail;
    }

    public void setWriterEmail(String writerEmail) {
      this.writerEmail = writerEmail;
    }

    public String getWriterEmail() {
      return writerEmail;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((userEmail == null) ? 0 : userEmail.hashCode());
      result = prime * result + ((writerEmail == null) ? 0 : writerEmail.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      AclEntry other = (AclEntry) obj;
      if (userEmail == null) {
        if (other.userEmail != null) return false;
      } else if (!userEmail.equals(other.userEmail)) return false;
      if (writerEmail == null) {
        if (other.writerEmail != null) return false;
      } else if (!writerEmail.equals(other.writerEmail)) return false;
      return true;
    }
  }

  /**
   * Describes a queue's optional retry parameters.
   */
  public static class RetryParameters {
    private Integer retryLimit;
    private Integer ageLimitSec;
    private Double minBackoffSec;
    private Double maxBackoffSec;
    private Integer maxDoublings;

    public RetryParameters() {
      retryLimit = null;
      ageLimitSec = null;
      minBackoffSec = null;
      maxBackoffSec = null;
      maxDoublings = null;
    }

    public Integer getRetryLimit() {
      return retryLimit;
    }

    public void setRetryLimit(int retryLimit) {
      this.retryLimit = retryLimit;
    }

    public void setRetryLimit(String retryLimit) {
      this.retryLimit = Integer.valueOf(retryLimit);
    }

    public Integer getAgeLimitSec() {
      return ageLimitSec;
    }

    public void setAgeLimitSec(String ageLimitString) {
      Matcher matcher = TASK_AGE_LIMIT_PATTERN.matcher(ageLimitString);
      if (!matcher.matches() || matcher.groupCount() != 2) {
        throw new AppEngineConfigException("Invalid task age limit was specified.");
      }
      double rateUnitSec = RateUnit.valueOf(matcher.group(2).charAt(0)).getSeconds();
      Double ageLimit = Double.valueOf(matcher.group(1)) * rateUnitSec;
      this.ageLimitSec = ageLimit.intValue();
    }

    public Double getMinBackoffSec() {
      return minBackoffSec;
    }

    public void setMinBackoffSec(double minBackoffSec) {
      this.minBackoffSec = minBackoffSec;
    }

    public void setMinBackoffSec(String minBackoffSec) {
      this.minBackoffSec = Double.valueOf(minBackoffSec);
    }

    public Double getMaxBackoffSec() {
      return maxBackoffSec;
    }

    public void setMaxBackoffSec(double maxBackoffSec) {
      this.maxBackoffSec = maxBackoffSec;
    }

    public void setMaxBackoffSec(String maxBackoffSec) {
      this.maxBackoffSec = Double.valueOf(maxBackoffSec);
    }

    public Integer getMaxDoublings() {
      return maxDoublings;
    }

    public void setMaxDoublings(int maxDoublings) {
      this.maxDoublings = maxDoublings;
    }

    public void setMaxDoublings(String maxDoublings) {
      this.maxDoublings = Integer.valueOf(maxDoublings);
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((ageLimitSec == null) ? 0 : ageLimitSec.hashCode());
      result = prime * result + ((maxBackoffSec == null) ? 0 : maxBackoffSec.hashCode());
      result = prime * result + ((maxDoublings == null) ? 0 : maxDoublings.hashCode());
      result = prime * result + ((minBackoffSec == null) ? 0 : minBackoffSec.hashCode());
      result = prime * result + ((retryLimit == null) ? 0 : retryLimit.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      RetryParameters other = (RetryParameters) obj;
      if (ageLimitSec == null) {
        if (other.ageLimitSec != null) return false;
      } else if (!ageLimitSec.equals(other.ageLimitSec)) return false;
      if (maxBackoffSec == null) {
        if (other.maxBackoffSec != null) return false;
      } else if (!maxBackoffSec.equals(other.maxBackoffSec)) return false;
      if (maxDoublings == null) {
        if (other.maxDoublings != null) return false;
      } else if (!maxDoublings.equals(other.maxDoublings)) return false;
      if (minBackoffSec == null) {
        if (other.minBackoffSec != null) return false;
      } else if (!minBackoffSec.equals(other.minBackoffSec)) return false;
      if (retryLimit == null) {
        if (other.retryLimit != null) return false;
      } else if (!retryLimit.equals(other.retryLimit)) return false;
      return true;
    }
  }

  /**
   * Describes a single queue entry.
   */
  public static class Entry {
    private String name;
    private Double rate;
    private RateUnit rateUnit;
    private Integer bucketSize;
    private Integer maxConcurrentRequests;
    private RetryParameters retryParameters;
    private String target;
    private String mode;
    private List<AclEntry> acl;

    /** Create an empty queue entry. */
    public Entry() {
      name = null;
      rate = null;
      rateUnit = RateUnit.SECOND;
      bucketSize = null;
      maxConcurrentRequests = null;
      retryParameters = null;
      target = null;
      mode = null;
      acl = null;
    }

    public Entry(String name, double rate, RateUnit rateUnit, int bucketSize,
                 Integer maxConcurrentRequests, String target) {
      this.name = name;
      this.rate = rate;
      this.rateUnit = rateUnit;
      this.bucketSize = bucketSize;
      this.maxConcurrentRequests = maxConcurrentRequests;
      this.target = target;
    }

    public String getName() {
      return name;
    }

    public void setName(String queueName) {
      if (queueName == null || queueName.length() == 0 ||
          !QUEUE_NAME_PATTERN.matcher(queueName).matches()) {
        throw new AppEngineConfigException(
            "Queue name does not match expression " + QUEUE_NAME_PATTERN +
            "; found '" + queueName + "'");
      }
      this.name = queueName;
    }

    public void setMode(String mode) {
      if (mode == null || mode.length() == 0 ||
          !MODE_PATTERN.matcher(mode).matches()) {
        throw new AppEngineConfigException(
            "mode must be either 'push' or 'pull'");
      }
      this.mode = mode;
    }

    public String getMode() {
      return mode;
    }

    public List<AclEntry> getAcl() {
      return acl;
    }

    public void setAcl(List<AclEntry> acl) {
      this.acl = acl;
    }

    public void addAcl(AclEntry aclEntry) {
      this.acl.add(aclEntry);
    }

    public Double getRate() {
      return rate;
    }

    public void setRate(double rate) {
      this.rate = rate;
    }

    /**
     * Set rate and units based on a "number/unit" formatted string.
     * @param rateString My be "0" or "number/unit" where unit is 's|m|h|d'.
     */
    public void setRate(String rateString) {
      if (rateString.equals("0")) {
        rate = 0.0;
        rateUnit = RateUnit.SECOND;
        return;
      }
      Matcher matcher = RATE_PATTERN.matcher(rateString);
      if (!matcher.matches()) {
        throw new AppEngineConfigException("Invalid queue rate was specified.");
      }
      String digits = matcher.group(1);
      rateUnit = RateUnit.valueOf(matcher.group(3).charAt(0));
      rate = Double.valueOf(digits);
    }

    public RateUnit getRateUnit() {
      return rateUnit;
    }

    public void setRateUnit(RateUnit rateUnit) {
      this.rateUnit = rateUnit;
    }

    public Integer getBucketSize() {
      return bucketSize;
    }

    public void setBucketSize(int bucketSize) {
      this.bucketSize = bucketSize;
    }

    public void setBucketSize(String bucketSize) {
      try {
        this.bucketSize = Integer.valueOf(bucketSize);
      } catch (NumberFormatException exception) {
        throw new AppEngineConfigException("Invalid bucket-size was specified.", exception);
      }
    }

    public Integer getMaxConcurrentRequests() {
      return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
      this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(String maxConcurrentRequests) {
      try {
        this.maxConcurrentRequests = Integer.valueOf(maxConcurrentRequests);
      } catch (NumberFormatException exception) {
        throw new AppEngineConfigException("Invalid max-concurrent-requests was specified: '" +
                                           maxConcurrentRequests + "'", exception);
      }
    }

    public RetryParameters getRetryParameters() {
      return retryParameters;
    }

    public void setRetryParameters(RetryParameters retryParameters) {
      this.retryParameters = retryParameters;
    }

    public String getTarget() {
      return target;
    }

    public void setTarget(String target) {
      Matcher matcher = TARGET_PATTERN.matcher(target);
      if (!matcher.matches()) {
        throw new AppEngineConfigException("Invalid queue target was specified. Target: '" +
                                           target + "'");
      }
      this.target = target;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((acl == null) ? 0 : acl.hashCode());
      result = prime * result + ((bucketSize == null) ? 0 : bucketSize.hashCode());
      result =
          prime * result + ((maxConcurrentRequests == null) ? 0 : maxConcurrentRequests.hashCode());
      result = prime * result + ((mode == null) ? 0 : mode.hashCode());
      result = prime * result + ((name == null) ? 0 : name.hashCode());
      result = prime * result + ((rate == null) ? 0 : rate.hashCode());
      result = prime * result + ((rateUnit == null) ? 0 : rateUnit.hashCode());
      result = prime * result + ((target == null) ? 0 : target.hashCode());
      result = prime * result + ((retryParameters == null) ? 0 : retryParameters.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      Entry other = (Entry) obj;
      if (acl == null) {
        if (other.acl != null) return false;
      } else if (!acl.equals(other.acl)) return false;
      if (bucketSize == null) {
        if (other.bucketSize != null) return false;
      } else if (!bucketSize.equals(other.bucketSize)) return false;
      if (maxConcurrentRequests == null) {
        if (other.maxConcurrentRequests != null) return false;
      } else if (!maxConcurrentRequests.equals(other.maxConcurrentRequests)) return false;
      if (mode == null) {
        if (other.mode != null) return false;
      } else if (!mode.equals(other.mode)) return false;
      if (name == null) {
        if (other.name != null) return false;
      } else if (!name.equals(other.name)) return false;
      if (rate == null) {
        if (other.rate != null) return false;
      } else if (!rate.equals(other.rate)) return false;
      if (rateUnit == null) {
        if (other.rateUnit != null) return false;
      } else if (!rateUnit.equals(other.rateUnit)) return false;
      if (target == null) {
        if (other.target != null) return false;
      } else if (!target.equals(other.target)) return false;
      if (retryParameters == null) {
        if (other.retryParameters != null) return false;
      } else if (!retryParameters.equals(other.retryParameters)) return false;
      return true;
    }
  }

  private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<String, Entry>();
  private Entry lastEntry;

  private String totalStorageLimit = "";

  /**
   * Return a new {@link Entry} describing the default queue.
   */
  public static Entry defaultEntry() {
    return new Entry(DEFAULT_QUEUE, 5, RateUnit.SECOND, 5, null, null);
  }

  /**
   * Puts a new entry into the list defined by the queue.xml file.
   *
   * @throws AppEngineConfigException if the previously-last entry is still
   *    incomplete.
   * @return the new entry
   */
  public Entry addNewEntry() {
    validateLastEntry();
    lastEntry = new Entry();
    return lastEntry;
  }

  public void addEntry(Entry entry) {
    validateLastEntry();
    lastEntry = entry;
    validateLastEntry();
  }

  /**
   * Get the entries.
   */
  public Collection<Entry> getEntries() {
    validateLastEntry();
    return entries.values();
  }

  /**
   * Check that the last entry defined is complete.
   * @throws AppEngineConfigException if it is not.
   */
  public void validateLastEntry() {
    if (lastEntry == null) {
      return;
    }
    if (lastEntry.getName() == null) {
      throw new AppEngineConfigException("Queue entry must have a name.");
    }
    if (entries.containsKey(lastEntry.getName())) {
      throw new AppEngineConfigException("Queue entry has duplicate name.");
    }
    if ("pull".equals(lastEntry.getMode())) {
      if (lastEntry.getRate() != null) {
        throw new AppEngineConfigException("Rate must not be specified for pull queue.");
      }
      if (lastEntry.getBucketSize() != null) {
        throw new AppEngineConfigException("Bucket size must not be specified for pull queue.");
      }
      if (lastEntry.getMaxConcurrentRequests() != null) {
        throw new AppEngineConfigException(
            "MaxConcurrentRequests must not be specified for pull queue.");
      }
      RetryParameters retryParameters = lastEntry.getRetryParameters();
      if (retryParameters != null) {
        if (retryParameters.getAgeLimitSec() != null) {
          throw new AppEngineConfigException(
              "Age limit must not be specified for pull queue.");
        }
        if (retryParameters.getMinBackoffSec() != null) {
          throw new AppEngineConfigException(
              "Min backoff must not be specified for pull queue.");
        }
        if (retryParameters.getMaxBackoffSec() != null) {
          throw new AppEngineConfigException(
              "Max backoff must not be specified for pull queue.");
        }
        if (retryParameters.getMaxDoublings() != null) {
          throw new AppEngineConfigException(
              "Max doublings must not be specified for pull queue.");
        }
      }
    } else {
      if (lastEntry.getRate() == null) {
        throw new AppEngineConfigException("A queue rate is required for push queue.");
      }
    }
    entries.put(lastEntry.getName(), lastEntry);
    lastEntry = null;
  }

  public void setTotalStorageLimit(String s) {
    totalStorageLimit = s;
  }

  public String getTotalStorageLimit() {
    return totalStorageLimit;
  }

  /**
   * Get the YAML equivalent of this queue.xml file.
   *
   * @return contents of an equivalent {@code queue.yaml} file.
   */
  public String toYaml() {
    StringBuilder builder = new StringBuilder();
    if (getTotalStorageLimit().length() > 0) {
      builder.append("total_storage_limit: " + getTotalStorageLimit() + "\n\n");
    }
    builder.append("queue:\n");
    for (Entry ent : getEntries()) {
      builder.append("- name: " + ent.getName() + "\n");
      Double rate = ent.getRate();
      if (rate != null) {
        builder.append(
            "  rate: " + rate + '/' + ent.getRateUnit().getIdent() + "\n");
      }
      Integer bucketSize = ent.getBucketSize();
      if (bucketSize != null) {
        builder.append("  bucket_size: " + bucketSize + "\n");
      }
      Integer maxConcurrentRequests = ent.getMaxConcurrentRequests();
      if (maxConcurrentRequests != null) {
        builder.append("  max_concurrent_requests: " + maxConcurrentRequests + "\n");
      }
      RetryParameters retryParameters = ent.getRetryParameters();
      if (retryParameters != null) {
        builder.append("  retry_parameters:\n");
        if (retryParameters.getRetryLimit() != null) {
          builder.append("    task_retry_limit: " + retryParameters.getRetryLimit() + "\n");
        }
        if (retryParameters.getAgeLimitSec() != null) {
          builder.append("    task_age_limit: " + retryParameters.getAgeLimitSec() + "s\n");
        }
        if (retryParameters.getMinBackoffSec() != null) {
          builder.append("    min_backoff_seconds: " + retryParameters.getMinBackoffSec() + "\n");
        }
        if (retryParameters.getMaxBackoffSec() != null) {
          builder.append("    max_backoff_seconds: " + retryParameters.getMaxBackoffSec() + "\n");
        }
        if (retryParameters.getMaxDoublings() != null) {
          builder.append("    max_doublings: " + retryParameters.getMaxDoublings() + "\n");
        }
      }
      String target = ent.getTarget();
      if (target != null) {
        builder.append("  target: " + target + "\n");
      }
      String mode = ent.getMode();
      if (mode != null) {
        builder.append("  mode: " + mode + "\n");
      }
      List<AclEntry> acl = ent.getAcl();
      if (acl != null) {
        builder.append("  acl:\n");
        for (AclEntry aclEntry : acl) {
          if (aclEntry.getUserEmail() != null) {
            builder.append("  - user_email: " + aclEntry.getUserEmail() + "\n");
          } else if (aclEntry.getWriterEmail() != null) {
            builder.append("  - writer_email: " + aclEntry.getWriterEmail() + "\n");
          }
        }
      }
    }
    return builder.toString();
  }
}
