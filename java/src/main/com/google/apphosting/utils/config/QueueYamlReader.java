// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.apphosting.utils.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import net.sourceforge.yamlbeans.YamlException;
import net.sourceforge.yamlbeans.YamlReader;

/**
 * Class to parse queue.yaml into a QueueXml object.
 *
 */
public class QueueYamlReader {

  /**
   * Wrapper around QueueXml to match JavaBean properties to
   * the yaml file syntax.
   */
  public static class QueueYaml {

    /**
     * Wrapper around QueueXml.RetryParameters to match the JavaBean
     * properties to the yaml file syntax.
     */
    public static class RetryParameters {
      private QueueXml.RetryParameters retryParameters = new QueueXml.RetryParameters();

      public RetryParameters() {}

      public void setTask_retry_limit(int limit) {
        retryParameters.setRetryLimit(limit);
      }
      public int getTask_retry_limit() {
        return retryParameters.getRetryLimit();
      }
      public void setTask_age_limit(String ageLimit) {
        retryParameters.setAgeLimitSec(ageLimit);
      }
      public String getTask_age_limit() {
        return retryParameters.getAgeLimitSec().toString() + "s";
      }
      public void setMin_backoff_seconds(double backoff) {
        retryParameters.setMinBackoffSec(backoff);
      }
      public double getMin_backoff_seconds() {
        return retryParameters.getMinBackoffSec();
      }
      public void setMax_backoff_seconds(double backoff) {
        retryParameters.setMaxBackoffSec(backoff);
      }
      public double getMax_backoff_seconds() {
        return retryParameters.getMaxBackoffSec();
      }
      public void setMax_doublings(int doublings) {
        retryParameters.setMaxDoublings(doublings);
      }
      public int getMax_doublings() {
        return retryParameters.getMaxDoublings();
      }
      public QueueXml.RetryParameters toXml() {
        return retryParameters;
      }
    }

    public static class AclEntry {
      QueueXml.AclEntry acl = new QueueXml.AclEntry();

      public void setUser_email(String userEmail) {
        acl.setUserEmail(userEmail);
      }

      public String getUser_email() {
        return acl.getUserEmail();
      }

      public void setWriter_email(String writerEmail) {
        acl.setWriterEmail(writerEmail);
      }

      public String getWriter_email() {
        return acl.getWriterEmail();
      }

      public QueueXml.AclEntry toXml() {
        return acl;
      }
    }

    /**
     * Wrapper around QueueXml.Entry to match the JavaBean properties to
     * the yaml file syntax.
     */
    public static class Entry {
      private QueueXml.Entry entry = new QueueXml.Entry();
      private RetryParameters retryParameters;
      private List<AclEntry> acl;
      public void setName(String name) {
        entry.setName(name);
      }
      public String getName() {
        return entry.getName();
      }
      public void setRate(String rate) {
        entry.setRate(rate);
      }
      public String getRate() {
        return entry.getRate() + "/" + entry.getRateUnit().getIdent();
      }
      public void setBucket_size(int size) {
        entry.setBucketSize(size);
      }
      public int getBucket_size() {
        return entry.getBucketSize();
      }
      public void setMax_concurrent_requests(int size) {
        entry.setMaxConcurrentRequests(size);
      }
      public int getMax_concurrent_requests() {
        return entry.getMaxConcurrentRequests();
      }
      public void setRetry_parameters(RetryParameters retryParameters) {
        this.retryParameters = retryParameters;
        if (retryParameters != null) {
          entry.setRetryParameters(retryParameters.toXml());
        } else {
          entry.setRetryParameters(new QueueXml.RetryParameters());
        }
      }
      public RetryParameters getRetry_parameters() {
        return retryParameters;
      }
      public void setTarget(String target) {
        entry.setTarget(target);
      }
      public String getTarget() {
        return entry.getTarget();
      }
      public void setMode(String mode) {
        entry.setMode(mode);
      }
      public String getMode() {
        return entry.getMode();
      }
      public void setAcl(List<AclEntry> acl) {
        this.acl = acl;
        entry.setAcl(new ArrayList<QueueXml.AclEntry>());
        if (acl != null) {
          for (int i = 0; i < acl.size(); ++i) {
            AclEntry aclEntry = acl.get(i);
            entry.addAcl(aclEntry.toXml());
          }
        }
      }
      public List<AclEntry> getAcl() {
        return acl;
      }
      public QueueXml.Entry toXml() {
        return entry;
      }
    }

    private List<Entry> entries;
    public String total_storage_limit;

    public List<Entry> getQueue() {
      return entries;
    }

    public void setQueue(List<Entry> entries) {
      this.entries = entries;
    }

    public QueueXml toXml() {
      QueueXml xml = new QueueXml();
      if (total_storage_limit != null) {
        xml.setTotalStorageLimit(total_storage_limit);
      }
      if (entries != null) {
        for (Entry entry : entries) {
          xml.addEntry(entry.toXml());
        }
      }
      return xml;
    }
  }

  private static final String FILENAME = "queue.yaml";
  private String appDir;

  public QueueYamlReader(String appDir) {
    if (appDir.length() > 0 && appDir.charAt(appDir.length() - 1) != File.separatorChar) {
      appDir += File.separatorChar;
    }
    this.appDir = appDir;
  }

  public String getFilename() {
    return appDir + QueueYamlReader.FILENAME;
  }

  public QueueXml parse() {
    if (new File(getFilename()).exists()) {
      try {
        return parse(new FileReader(getFilename()));
      } catch (FileNotFoundException ex) {
        throw new AppEngineConfigException("Cannot find file " + getFilename(), ex);
      }
    }
    return null;
  }

  public static QueueXml parse(Reader yaml) {
    YamlReader reader = new YamlReader(yaml);
    reader.getConfig().setPropertyElementType(QueueYaml.class,
                                              "queue",
                                              QueueYaml.Entry.class);

    reader.getConfig().setPropertyElementType(QueueYaml.Entry.class,
                                              "acl",
                                              QueueYaml.AclEntry.class);
    try {
      QueueYaml queueYaml = reader.read(QueueYaml.class);
      if (queueYaml == null) {
        throw new AppEngineConfigException("Empty queue configuration.");
      }
      return queueYaml.toXml();
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
  }

  public static QueueXml parse(String yaml) {
    return parse(new StringReader(yaml));
  }
}
