// Copyright 2011 Google Inc. All rights reserved.

package com.google.appengine.api.prospectivesearch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.prospectivesearch.ProspectiveSearchPb.SubscriptionRecord;
import com.google.common.annotations.VisibleForTesting;

import java.util.Date;

/**
 * The Subscription class represents information about a registered
 * subscription.
 *
 * @see ProspectiveSearchService#subscribe(String, String, long, String, Map<String, FieldType>)
 */
public final class Subscription {

  /**
   * The state of the subscription in the backend system.
   */
  public enum State {
    /**
     * Subscription is active.
     */
    OK,

    /**
     * Successfully registered but not yet active.
     */
    PENDING,

    /**
     * Inactive due to an error. (See the error value for explanation.)
     */
    ERROR;

    /**
     * Mapping from internal to wrapper states.
     */
    static State lookupState(final SubscriptionRecord.State internalState) {
      switch (internalState) {
        case OK:
          return Subscription.State.OK;
        case PENDING:
          return Subscription.State.PENDING;
        default:
          return Subscription.State.ERROR;
      }
    }
  }

  private final String id;
  private final String query;
  private final long expirationTimeSec;
  private State state;
  private String errorMsg;

  @VisibleForTesting
  public Subscription(String id, String query, long expirationTimeSec) {
    this(id, query, expirationTimeSec, State.OK, "");
  }

  @VisibleForTesting
  public Subscription(String id,
      String query,
      long expirationTimeSec,
      State state,
      String errorMsg) {
    checkArgument(expirationTimeSec >= 0,
        "Lease duration must be non-negative: %s", expirationTimeSec);
    this.id = checkNotNull(id);
    this.query = checkNotNull(query);
    this.expirationTimeSec = expirationTimeSec;
    this.state = checkNotNull(state);
    this.errorMsg = checkNotNull(errorMsg);
  }

  Subscription(SubscriptionRecord sr) {
    this(sr.getId(), sr.getVanillaQuery(), (long) sr.getExpirationTimeSec(),
         State.lookupState(sr.getStateEnum()), sr.getErrorMessage());
  }

  /**
   * @return the id supplied during subscription
   */
  public String getId() {
    return id;
  }

  /**
   * @return the query supplied during subscription
   */
  public String getQuery() {
    return query;
  }

  /** @return the expiration time of this subscription, in seconds
   * since January 1, 1970 UTC */
  public long getExpirationTime() {
    return expirationTimeSec;
  }

  /** @return the current state of the subscription */
  public State getState() {
    return state;
  }

  /** @return the error message if the current state is ERROR */
  public String getErrorMessage() {
    return errorMsg;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Subscription)) {
      return false;
    }
    Subscription o = (Subscription) other;
    return id.equals(o.id) &&
        query.equals(o.query) &&
        expirationTimeSec == o.expirationTimeSec &&
        state.equals(o.state);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /**
   * @return a string of the format: %s@%d{id=%s, query=%s,
   * expires=%s, state=%s}, where the first two replacements are the
   * class name and the system identity hashcode, and the date
   * attribute may be "NEVER".
   */
  @Override
  public String toString() {
    return String.format("%s@%d{id=%s, query=%s, expires=%s, state=%s}",
        this.getClass().getName(),
        System.identityHashCode(this),
        id,
        query,
        expirationTimeSec == 0 ? "NEVER" : new Date(expirationTimeSec * 1000L),
        state);
  }
}
