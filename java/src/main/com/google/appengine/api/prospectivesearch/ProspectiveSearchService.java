// Copyright 2011 Google Inc. All rights reserved.

package com.google.appengine.api.prospectivesearch;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.taskqueue.QueueFactory;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * The Prospective Search App Engine API exposes the real-time and
 * highly scalable Google Prospective Search Infrastructure as an App
 * Engine service. The ProspectiveSearch API allows an app to register
 * a set of queries (in a simple query format) to match against
 * documents that are presented. For every document presented, the
 * matcher will return the ids of all of the matching queries. To
 * allow the app to handle a potentially large number of matched
 * queries, the matched ids are enqueued as tasks on the TaskQueue.
 * The target for these match notifications is defined in the member
 * DEFAULT_RESULT_RELATIVE_URL.
 *
 * @see <a href="
 * http://developers.google.com/appengine/docs/java/prospectivesearch/overview#Query_Language_Overview
 * ">Query Language Overview</a> in the Developer Guide.
 * @see <a href="
 * http://developers.google.com/appengine/docs/java/prospectivesearch/overview#Handling_Matches
 * ">Handling Matches</a> in the Developer Guide.
 */
public interface ProspectiveSearchService {

  /**
   * The default lease duration value of zero means no expiration.
   */
  public static final int DEFAULT_LEASE_DURATION_SEC = 0;

  /**
   * Set to the backend service default batch size.
   */
  public static final int DEFAULT_LIST_SUBSCRIPTIONS_MAX_RESULTS = 1000;

  /**
   * Set to the backend service default batch size.
   */
  public static final int DEFAULT_LIST_TOPICS_MAX_RESULTS = 1000;

  /**
   * Set to a small size of 100 to allow quick processing in a
   * TaskQueue task.
   */
  public static final int DEFAULT_RESULT_BATCH_SIZE = 100;

  /**
   * The default URI path to which matches will be POSTed.  Your
   * application should install a handler at this location.  The _ah
   * prefix ensures that this path can only be accessed by your
   * application and not via public HTTP connections.
   */
  public static final String DEFAULT_RESULT_RELATIVE_URL = "/_ah/prospective_search";

  /**
   * Uses the default task queue.  Equivalent to:
   *
   * <pre>
   * com.google.appengine.api.taskqueue.QueueFactory.getDefaultQueue().getQueueName()
   * </pre>
   *
   */
  public static final String DEFAULT_RESULT_TASK_QUEUE_NAME =
      QueueFactory.getDefaultQueue().getQueueName();

  /**
   * The subscribe call is used to register subscriptions, which
   * comprise of a subscription id and a query. A delay of a few
   * seconds is expected between subscribe returning successfully and
   * the subscription being registered.
   *
   * @param topic the subscription group to which this subscription
   *     will be added. Only {@link #match} calls with the same topic
   *     will match this subscription
   * @param subId the unique string for this subscription; subscribe
   *     will overwrite subscriptions with the same subId
   * @param query the query in simple query format
   * @param leaseDurationSeconds time before the subscription is
   *     automatically removed or a value of 0 for no expiration
   * @param schema the map of field names to their corresponding
   *     types
   * @throws QuerySyntaxException if the query is invalid or does not
   *     match schema
   * @throws ApiProxy.ApplicationException if the backend call failed.
   *     See the message detail for the reason
   */
  void subscribe(String topic,
      String subId,
      long leaseDurationSeconds,
      String query,
      Map<String, FieldType> schema);

  /**
   * Subscriptions are removed from the system using the unsubscribe
   * call. A successful unsubscribe call guarantees that the
   * subscription will eventually be removed. A delay of a few seconds
   * is expected between the unsubscribe returning successfully and
   * the subscription being removed. Once the last subscription for a
   * given topic is removed, the topic also no longer exists.
   *
   * @param topic the subscription group of the subscription
   * @param subId the id of the subscription to remove, as specified
   *     during {@link #subscribe}
   * @throws IllegalArgumentException if the given topic does not
   *     exist or has no subscription with the given subId
   * @throws ApiProxy.ApplicationException if the backend call failed.
   *     See the message detail for the reason
   */
  void unsubscribe(String topic, String subId);

  /**
   * Equivalent to:
   *
   * <pre>
   *   match(entity, topic, "");
   * </pre>
   *
   * @see #match(Entity, String, String)
   */
  void match(Entity document, String topic);

  /**
   * Equivalent to:
   *
   * <pre>
   *   match(entity, topic, resultKey,
   *         DEFAULT_RESULT_RELATIVE_URL,
   *         DEFAULT_RESULT_TASK_QUEUE_NAME,
   *         DEFAULT_RESULT_BATCH_SIZE,
   *         true);
   * </pre>
   *
   * @see #match(Entity, String, String, String, String, int, bool)
   */
  void match(Entity document, String topic, String resultKey);

  /**
   * The match call is used to present a document for matching against
   * all registered subscriptions of the same topic.  A match results
   * message is delivered asynchronously via TaskQueue POST to the
   * given {@code resultRelativeURL}.
   *
   * @see <a href="
   * http://developers.google.com/appengine/docs/java/prospectivesearch/overview#Handling_Matches
   * ">Handling Matches</a> in the Developer Guide.
   * @param document the document to match against registered subscriptions
   * @param topic the subscription group to match
   * @param resultKey a user defined key returned with the results
   *     message that can be used to associate the results message
   *     with this call
   * @param resultRelativeUrl the relative URL to which the results
   *     message will be delivered
   * @param resultTaskQueue the name of the TaskQueue to use for
   *     delivering the results message
   * @param resultBatchSize the maximum number of subscription IDs per
   *     results message
   * @throws ApiProxy.ApplicationException if the backend call failed.
   *     See the message detail for the reason
   */
  void match(Entity document,
      String topic,
      String resultKey,
      String resultRelativeUrl,
      String resultTaskQueueName,
      int resultBatchSize,
      boolean resultReturnDocument);

  /**
   * Equivalent to:
   * <pre>
   *   listSubscriptions(topic, "",
   *                     DEFAULT_LIST_SUBSCRIPTIONS_MAX_RESULTS,
   *                     0);
   * </pre>
   * @see #listSubscriptions(String, String, int, long)
   */
  List<Subscription> listSubscriptions(String topic);

  /**
   * The listSubscriptions call lists subscriptions that are currently
   * active.
   *
   * @param topic the subscription group to list
   * @param subIdStart subscriptions which are lexicographically
   *     greater or equal to the given value should be returned.
   *     NOTE: The empty string precedes all others
   * @param maxResults sets the maximum number of subscriptions that
   *     should be returned
   * @param expiresBefore limits the returned subscriptions to those
   *     that expire before the given time in seconds since epoch, or
   *     0 for no expiration
   * @return a list of subscriptions
   * @throws ApiProxy.ApplicationException if the backend call failed.
   *     See the message detail for the reason
   */
  List<Subscription> listSubscriptions(String topic,
      String subIdStart,
      int maxResults,
      long expiresBefore);

  /**
   * Get the {@link Subscription} with the given topic and id.
   *
   * @param topic the subscription group to which the subscription
   *     belongs
   * @param subId the id of the subscription
   * @return the subscription
   * @throws IllegalArgumentExcpetion if no such subscription exists
   * @throws ApiProxy.ApplicationException if the backend call failed.  See the
   *     message detail for the reason
   */
  Subscription getSubscription(String topic, String subId);

  /**
   * List topics in lexicographic order.
   *
   * @param topicStart the topic from which to start listing, or the
   *     empty string to start from the beginning
   * @param maxResults the maximum number of topics to return.  A good
   *     default is 1000
   * @return the list of topic names
   * @throws ApiProxy.ApplicationException if the backend call failed.
   *     See the message detail for the reason
   */
  List<String> listTopics(String topicStart, long maxResults);

  /**
   * Decodes document from {@link #match(Entity, String, String,
   * String, String, int, bool)} result POST request.
   *
   * @param matchCallbackPost the received POST request
   * @return the Entity that matched a subscription as the result of a
   *     match call, or null if it cannot be decoded
   * @throws com.google.common.util.Base64DecoderException if the
   *     document could not be decoded
   */
  Entity getDocument(HttpServletRequest matchCallbackPost);
}
