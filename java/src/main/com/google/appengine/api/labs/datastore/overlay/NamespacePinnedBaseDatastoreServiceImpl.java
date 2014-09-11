package com.google.appengine.api.labs.datastore.overlay;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.BaseDatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyRange;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Projection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortPredicate;
import com.google.appengine.api.datastore.Transaction;
import com.google.apphosting.datastore.DatastoreV4;
import com.google.apphosting.datastore.EntityV4;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A thin wrapper around {@link BaseDatastoreService}, where all operations are redirected to use an
 * alternate namespace.
 *
 * <p>This is a base class that contains some operations common to both the synchronous and
 * asynchronous implementations.
 *
 * <p>For operations which use the ambient namespace from the {@link NamespaceManager} (such as
 * {@code allocateIds}), {@code namespacePrefix} is applied to the current namespace
 * before completing the operation.
 *
 * <p>For operations whose arguments contain namespaces in some form (such as keys),
 * {@code namespacePrefix} is applied to the relevant namespaces before completing the
 * operation.
 *
 * <p>In all cases, there are no lasting effects on the ambient namespace; it is restored to the
 * original value after the operation is done.
 */
abstract class NamespacePinnedBaseDatastoreServiceImpl implements BaseDatastoreService {
  protected final String namespacePrefix;
  protected final boolean shouldReserveIds;

  /**
   * Constructs a new {@link NamespacePinnedBaseDatastoreServiceImpl}.
   *
   * @param namespacePrefix the prefix to apply to all namespaces
   * @param shouldReserveIds a flag that indicates whether calling {@code put()} should explicitly
   *        reserve (not allocate) the numeric IDs used in any completed keys before storing them
   */
  NamespacePinnedBaseDatastoreServiceImpl(String namespacePrefix, boolean shouldReserveIds) {
    this.namespacePrefix = checkNotNull(namespacePrefix);
    this.shouldReserveIds = shouldReserveIds;
  }

  @Override
  public Collection<Transaction> getActiveTransactions() {
    return getUnderlyingBaseDatastoreService().getActiveTransactions();
  }

  @Override
  public Transaction getCurrentTransaction() {
    return getUnderlyingBaseDatastoreService().getCurrentTransaction();
  }

  @Override
  public Transaction getCurrentTransaction( Transaction returnedIfNoTxn) {
    return getUnderlyingBaseDatastoreService().getCurrentTransaction(returnedIfNoTxn);
  }

  @Override
  public PreparedQuery prepare(Query query) {
    checkNotNull(query);
    return prepareImpl(getUnderlyingBaseDatastoreService(), query);
  }

  @Override
  public PreparedQuery prepare( Transaction txn, Query query) {
    checkNotNull(query);
    return prepareImpl(getTxnBaseDatastore(txn), query);
  }

  private PreparedQuery prepareImpl(BaseDatastoreService datastore, Query query) {
    checkNotNull(datastore);
    checkNotNull(query);
    Query alternateQuery = getAlternateNamespaceQuery(query);
    return new NamespacePinnedPreparedQueryImpl(this, datastore.prepare(alternateQuery));
  }

  /**
   * Returns the {@link BaseDatastoreService} that underlies this Datastore.
   */
  protected abstract BaseDatastoreService getUnderlyingBaseDatastoreService();

  /**
   * Returns {@code namespace} combined with the namespace prefix for this instance.
   */
  @VisibleForTesting
  String getAlternateNamespace( String namespace) {
    return namespacePrefix + Strings.nullToEmpty(namespace);
  }

  /**
   * Returns {@code namespace} with the prefix removed.
   */
  @VisibleForTesting
  String getOriginalNamespace(String namespace) {
    checkNotNull(namespace);
    checkArgument(namespace.startsWith(namespacePrefix),
        "%s must start with %s", namespace, namespacePrefix);
    return namespace.substring(namespacePrefix.length());
  }

  /**
   * Creates a copy of {@code entities}, where each entity is replaced with a copy whose key is in
   * the alternate namespace.
   */
  protected List<Entity> getAlternateNamespaceEntities(Iterable<Entity> entities) {
    checkNotNull(entities);
    List<Entity> newEntities = Lists.newArrayList();
    for (Entity entity : entities) {
      newEntities.add(getAlternateNamespaceEntity(entity));
    }
    return newEntities;
  }

  /**
   * Creates a copy of {@code entity}, where the key is in the alternate namespace.
   */
  protected Entity getAlternateNamespaceEntity(Entity entity) {
    checkNotNull(entity);
    return cloneEntityWithNewKey(entity, getAlternateNamespaceKey(entity.getKey()));
  }

  /**
   * Creates a copy of {@code entity}, where the key is in the original namespace.
   */
  protected Entity getOriginalNamespaceEntity(Entity entity) {
    checkNotNull(entity);
    return cloneEntityWithNewKey(entity, getOriginalNamespaceKey(entity.getKey()));
  }

  /**
   * Creates a copy of {@code entity}, where the key is replaced with {@code newKey}.
   */
  private Entity cloneEntityWithNewKey(Entity entity, Key newKey) {
    Entity newEntity = new Entity(newKey);
    newEntity.setPropertiesFrom(entity);
    return newEntity;
  }

  /**
   * Creates a copy of {@code entityMap}, where all the keys are replaced with the equivalent keys
   * in the original namespace.
   */
  protected Map<Key, Entity> getOriginalNamespaceEntityMap(Map<Key, Entity> entityMap) {
    checkNotNull(entityMap);
    Map<Key, Entity> newMap = Maps.newHashMapWithExpectedSize(entityMap.size());
    for (Map.Entry<Key, Entity> entry : entityMap.entrySet()) {
      newMap.put(getOriginalNamespaceKey(entry.getKey()),
          getOriginalNamespaceEntity(entry.getValue()));
    }
    return newMap;
  }

  /**
   * Creates a copy of {@code key}, but in the alternate namespace.
   */
  protected Key getAlternateNamespaceKey(Key key) {
    checkNotNull(key);
    String currentNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set(getAlternateNamespace(key.getNamespace()));
      return cloneKey(key);
    } finally {
      NamespaceManager.set(currentNamespace);
    }
  }

  /**
   * Creates a copy of {@code key} in the original namespace. {@code key} must be in the alternate
   * namespace to begin with.
   */
  protected Key getOriginalNamespaceKey(Key key) {
    checkNotNull(key);
    String currentNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set(getOriginalNamespace(key.getNamespace()));
      return cloneKey(key);
    } finally {
      NamespaceManager.set(currentNamespace);
    }
  }

  /**
   * Creates a copy of {@code key}, where the copy (and each of its components) takes its namespace
   * from the {@link NamespaceManager}.
   */
  protected static Key cloneKey(Key key) {
    checkNotNull(key);
    Key parentKey = key.getParent();
    String name = key.getName();
    long id = key.getId();
    if (parentKey == null) {
      if (name != null) {
        return new Entity(key.getKind(), name).getKey();
      } else if (id != 0L) {
        return new Entity(key.getKind(), key.getId()).getKey();
      } else {
        return new Entity(key.getKind()).getKey();
      }
    } else {
      if (name != null) {
        return new Entity(key.getKind(), name, cloneKey(parentKey)).getKey();
      } else if (id != 0L) {
        return new Entity(key.getKind(), key.getId(), cloneKey(parentKey)).getKey();
      } else {
        return new Entity(key.getKind(), cloneKey(parentKey)).getKey();
      }
    }
  }

  /**
   * Creates a copy of {@code keys}, where each key is replaced with a copy that is in the alternate
   * namespace.
   */
  protected List<Key> getAlternateNamespaceKeys(Iterable<Key> keys) {
    checkNotNull(keys);
    List<Key> newKeys = Lists.newArrayList();
    for (Key key : keys) {
      newKeys.add(getAlternateNamespaceKey(key));
    }
    return newKeys;
  }

  /**
   * Creates a copy of {@code keys}, where each key is replaced with a copy that is in the original
   * namespace. The elements of {@code keys} must be in the alternate namespace to begin with.
   */
  protected List<Key> getOriginalNamespaceKeys(Iterable<Key> keys) {
    checkNotNull(keys);
    List<Key> newKeys = Lists.newArrayList();
    for (Key key : keys) {
      newKeys.add(getOriginalNamespaceKey(key));
    }
    return newKeys;
  }

  /**
   * Creates a {@link EntityV4.Key} that is equivalent to {@code key}.
   */
  protected static EntityV4.Key convertKeyToV4Pb(Key key) {
    checkNotNull(key);
    EntityV4.Key.Builder keyV4 = EntityV4.Key.newBuilder();

    EntityV4.PartitionId.Builder partitionId = EntityV4.PartitionId.newBuilder();
    partitionId.setDatasetId(key.getAppId());
    String namespace = key.getNamespace();
    if (namespace.length() != 0) {
      partitionId.setNamespace(namespace);
    }
    keyV4.setPartitionId(partitionId);

    List<EntityV4.Key.PathElement> pathElements = Lists.newArrayList();
    while (key != null) {
      EntityV4.Key.PathElement.Builder pathElement = EntityV4.Key.PathElement.newBuilder();
      pathElement.setKind(key.getKind());
      if (key.isComplete()) {
        String name = key.getName();
        if (name != null) {
          pathElement.setName(name);
        } else {
          pathElement.setId(key.getId());
        }
      }
      pathElements.add(0, pathElement.build());
      key = key.getParent();
    }
    for (EntityV4.Key.PathElement pathElement : pathElements) {
      keyV4.addPathElement(pathElement);
    }

    return keyV4.build();
  }

  /**
   * Builds a request to reserve the keys of {@code entities}. This is used when encountering
   * completed keys that have possibly been assigned IDs by the parent Datastore, but that the
   * overlay may not have seen yet.
   */
  protected static DatastoreV4.AllocateIdsRequest makeReserveIdsRequest(Iterable<Entity> entities) {
    checkNotNull(entities);
    DatastoreV4.AllocateIdsRequest.Builder reqBuilder = DatastoreV4.AllocateIdsRequest.newBuilder();
    for (Entity entity : entities) {
      Key key = entity.getKey();
      while (key != null && !key.isComplete()) {
        key = key.getParent();
      }
      if (key != null) {
        reqBuilder.addReserve(convertKeyToV4Pb(key));
      }
    }
    return reqBuilder.build();
  }

  /**
   * Creates a copy of {@code query}, where each component key is replaced with the equivalent key
   * in the alternate namespace.
   */
  protected Query getAlternateNamespaceQuery(Query query) {
    checkNotNull(query);
    Query alternateQuery;
    String currentNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set(getAlternateNamespace(query.getNamespace()));
      alternateQuery = new Query(query.getKind());
    } finally {
      NamespaceManager.set(currentNamespace);
    }

    Key ancestor = query.getAncestor();
    if (ancestor != null) {
      alternateQuery.setAncestor(getAlternateNamespaceKey(ancestor));
    }
    for (SortPredicate sp : query.getSortPredicates()) {
      alternateQuery.addSort(sp.getPropertyName(), sp.getDirection());
    }
    alternateQuery.setFilter(query.getFilter());
    for (FilterPredicate fp : query.getFilterPredicates()) {
      alternateQuery.addFilter(fp.getPropertyName(), fp.getOperator(), fp.getValue());
    }
    if (query.isKeysOnly()) {
      alternateQuery.setKeysOnly();
    }
    for (Projection p : query.getProjections()) {
      alternateQuery.addProjection(p);
    }
    alternateQuery.setDistinct(query.getDistinct());

    return alternateQuery;
  }

  /**
   * Creates a copy of {@code keyRange}, where the returned keys are in the alternate namespace.
   */
  protected KeyRange getAlternateNamespaceKeyRange(KeyRange keyRange) {
    Key originalParent = keyRange.getStart().getParent();
    Key alternateParent = originalParent == null ? null : getAlternateNamespaceKey(originalParent);
    String currentNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set(getAlternateNamespace(keyRange.getStart().getNamespace()));
      return new KeyRange(alternateParent, keyRange.getStart().getKind(),
          keyRange.getStart().getId(), keyRange.getEnd().getId());
    } finally {
      NamespaceManager.set(currentNamespace);
    }
  }

  /**
   * Creates a copy of {@code keyRange}, where the returned keys are in the original namespace,
   * rather than the alternate namespace.
   */
  protected KeyRange getOriginalNamespaceKeyRange(KeyRange keyRange) {
    Key alternateParent = keyRange.getStart().getParent();
    Key originalParent = alternateParent == null ? null : getOriginalNamespaceKey(alternateParent);
    String currentNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set(getOriginalNamespace(keyRange.getStart().getNamespace()));
      return new KeyRange(originalParent, keyRange.getStart().getKind(),
          keyRange.getStart().getId(), keyRange.getEnd().getId());
    } finally {
      NamespaceManager.set(currentNamespace);
    }
  }

  /**
   * Gets a version of the Datastore that is linked to {@code txn}.
   */
  protected abstract BaseDatastoreService getTxnBaseDatastore( Transaction txn);
}
