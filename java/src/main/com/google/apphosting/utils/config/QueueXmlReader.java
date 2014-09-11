// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.apphosting.utils.config;

import org.mortbay.xml.XmlParser.Node;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Stack;

/**
 * Creates an {@link QueueXml} instance from
 * <appdir>WEB-INF/queue.xml.  If you want to read the configuration
 * from a different file, subclass and override {@link #getFilename()}.  If you
 * want to read the configuration from something that isn't a file, subclass
 * and override {@link #getInputStream()}.
 *
 */
public class QueueXmlReader extends AbstractConfigXmlReader<QueueXml> {

  private static final String FILENAME = "WEB-INF/queue.xml";

  private static final String TOTAL_STORAGE_LIMIT_TAG = "total-storage-limit";
  private static final String QUEUEENTRIES_TAG = "queue-entries";
  private static final String QUEUE_TAG = "queue";
  private static final String NAME_TAG = "name";
  private static final String RATE_TAG = "rate";
  private static final String BUCKET_SIZE = "bucket-size";
  private static final String MAX_CONCURRENT_REQUESTS = "max-concurrent-requests";
  private static final String MODE_TAG = "mode";

  private static final String RETRY_PARAMETERS_TAG = "retry-parameters";
  private static final String TASK_RETRY_LIMIT_TAG = "task-retry-limit";
  private static final String TASK_AGE_LIMIT_TAG = "task-age-limit";
  private static final String MIN_BACKOFF_SECONDS_TAG = "min-backoff-seconds";
  private static final String MAX_BACKOFF_SECONDS_TAG = "max-backoff-seconds";
  private static final String MAX_DOUBLINGS_TAG = "max-doublings";
  private static final String TARGET_TAG = "target";

  private static final String ACL_TAG = "acl";
  private static final String USER_EMAIL_TAG = "user-email";
  private static final String WRITER_EMAIL_TAG = "writer-email";

  /**
   * Constructs the reader for {@code queue.xml} in a given application directory.
   * @param appDir the application directory
   */
  public QueueXmlReader(String appDir) {
    super(appDir, false);
  }

  /**
   * Parses the config file.
   * @return A {@link QueueXml} object representing the parsed configuration.
   */
  public QueueXml readQueueXml() {
    return readConfigXml();
  }

  @Override
  protected QueueXml processXml(InputStream is) {
    final QueueXml queueXml = new QueueXml();
    parse(new ParserCallback() {
      boolean firstQueueEntriesTag = true;
      boolean firstTotalStorageLimitTag = true;
      boolean insideRetryParametersTag = false;
      boolean insideAclTag = false;
      QueueXml.Entry entry;

      @Override
      public void newNode(Node node, Stack<Node> ancestors) {
        switch (ancestors.size()) {
          case 0:
            if (QUEUEENTRIES_TAG.equalsIgnoreCase(node.getTag())) {
              if (!firstQueueEntriesTag) {
                throw new AppEngineConfigException(getFilename() + " contains multiple <"
                    + QUEUEENTRIES_TAG + ">");
              }
              firstQueueEntriesTag = false;
            }
            break;

          case 1:
            if (firstQueueEntriesTag) {
              throw new AppEngineConfigException(getFilename() + " does not contain <"
                  + QUEUEENTRIES_TAG + ">");
            }
            if (TOTAL_STORAGE_LIMIT_TAG.equalsIgnoreCase(node.getTag())) {
              if (!firstTotalStorageLimitTag) {
                throw new AppEngineConfigException(getFilename() + " contains multiple <"
                    + TOTAL_STORAGE_LIMIT_TAG + ">");
              }
              if (node.size() == 1 && node.get(0) instanceof String) {
                queueXml.setTotalStorageLimit(getString(node));
              } else {
                throw new AppEngineConfigException(getFilename() + "has invalid <"
                    +TOTAL_STORAGE_LIMIT_TAG + ">");
              }
              firstTotalStorageLimitTag = false;
            } else if (QUEUE_TAG.equalsIgnoreCase(node.getTag())) {
              entry = queueXml.addNewEntry();
            } else {
              throw new AppEngineConfigException(getFilename() + " contains <"
                  + node.getTag() + "> instead of <" + QUEUE_TAG + "/> or <" +
                  TOTAL_STORAGE_LIMIT_TAG + ">");
            }
            break;

          case 2:
            assert(entry != null);
            if (NAME_TAG.equalsIgnoreCase(node.getTag())) {
              if (node.size() == 1 && node.get(0) instanceof String) {
                entry.setName(getString(node));
              } else {
                throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                    + NAME_TAG + ">");
              }
            } else if (BUCKET_SIZE.equalsIgnoreCase(node.getTag())) {
                if (node.size() == 1 && node.get(0) instanceof String) {
                  entry.setBucketSize(getString(node));
                } else {
                  throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                      + BUCKET_SIZE + ">");
                }
            } else if (RATE_TAG.equalsIgnoreCase(node.getTag())) {
                if (node.size() == 1 && node.get(0) instanceof String) {
                  entry.setRate(getString(node));
                } else {
                  throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                      + RATE_TAG + ">");
                }
            } else if (MAX_CONCURRENT_REQUESTS.equalsIgnoreCase(node.getTag())) {
                if (node.size() == 1 && node.get(0) instanceof String) {
                  entry.setMaxConcurrentRequests(getString(node));
                } else {
                  throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                      + MAX_CONCURRENT_REQUESTS + ">");
                }
            } else if (MODE_TAG.equalsIgnoreCase(node.getTag())) {
                if (node.size() == 1 && node.get(0) instanceof String) {
                  entry.setMode(getString(node));
                } else {
                  throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                      + MODE_TAG + ">");
                }
            } else if (TARGET_TAG.equalsIgnoreCase(node.getTag())) {
                if (node.size() == 1 && node.get(0) instanceof String) {
                  entry.setTarget(getString(node));
                } else {
                  throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                      + TARGET_TAG + ">");
                }
            } else if (RETRY_PARAMETERS_TAG.equalsIgnoreCase(node.getTag())) {
                entry.setRetryParameters(new QueueXml.RetryParameters());
                insideRetryParametersTag = true;
                insideAclTag = false;
            } else if (ACL_TAG.equalsIgnoreCase(node.getTag())) {
                entry.setAcl(new ArrayList<QueueXml.AclEntry>());
                insideAclTag = true;
                insideRetryParametersTag = false;
            } else {
              throw new AppEngineConfigException(getFilename() + " contains unknown <"
                  + node.getTag() + "> inside <" + QUEUE_TAG + "/>");
            }
            break;

          case 3:
            assert(insideRetryParametersTag ^ insideAclTag);
            assert(entry != null);
            boolean brokenTag = !(node.size() == 1 && node.get(0) instanceof String);

            if (insideRetryParametersTag) {
              assert(entry.getRetryParameters() != null);
              QueueXml.RetryParameters retryParameters = entry.getRetryParameters();

              if (TASK_RETRY_LIMIT_TAG.equalsIgnoreCase(node.getTag())) {
                if (!brokenTag) {
                  retryParameters.setRetryLimit(getString(node));
                }
              } else if (TASK_AGE_LIMIT_TAG.equalsIgnoreCase(node.getTag())) {
                if (!brokenTag) {
                  retryParameters.setAgeLimitSec(getString(node));
                }
              } else if (MIN_BACKOFF_SECONDS_TAG.equalsIgnoreCase(node.getTag())) {
                if (!brokenTag) {
                  retryParameters.setMinBackoffSec(getString(node));
                }
              } else if (MAX_BACKOFF_SECONDS_TAG.equalsIgnoreCase(node.getTag())) {
                if (!brokenTag) {
                  retryParameters.setMaxBackoffSec(getString(node));
                }
              } else if (MAX_DOUBLINGS_TAG.equalsIgnoreCase(node.getTag())) {
                if (!brokenTag) {
                  retryParameters.setMaxDoublings(getString(node));
                }
              } else {
                throw new AppEngineConfigException(getFilename() + " contains unknown <"
                    + node.getTag() + "> inside <" + RETRY_PARAMETERS_TAG + "/>");
              }
            }

            if (insideAclTag) {
              assert(entry.getAcl() != null);
              if (USER_EMAIL_TAG.equalsIgnoreCase(node.getTag())) {
                if (!brokenTag) {
                  QueueXml.AclEntry acl = new QueueXml.AclEntry();
                  acl.setUserEmail(getString(node));
                  entry.getAcl().add(acl);
                }
              } else if (WRITER_EMAIL_TAG.equalsIgnoreCase(node.getTag())) {
                if (!brokenTag) {
                  QueueXml.AclEntry acl = new QueueXml.AclEntry();
                  acl.setWriterEmail(getString(node));
                  entry.getAcl().add(acl);
                }
              } else {
                throw new AppEngineConfigException(getFilename() + " contains unknown <"
                    + node.getTag() + "> inside <" + ACL_TAG + "/>");
              }
            }

            if (brokenTag) {
              throw new AppEngineConfigException(getFilename() + " has bad contents in <"
                  + node.getTag() + ">");
            }
            break;

          default:
            throw new AppEngineConfigException(getFilename()
                + " has a syntax error; node <"
                + node.getTag() + "> is too deeply nested to be valid.");
        }
      }
    }, is);
    queueXml.validateLastEntry();
    return queueXml;
  }

  @Override
  protected String getRelativeFilename() {
    return FILENAME;
  }

}
