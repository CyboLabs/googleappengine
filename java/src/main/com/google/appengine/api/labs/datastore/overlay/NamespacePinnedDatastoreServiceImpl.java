package com.google.appengine.api.labs.datastore.overlay;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.BaseDatastoreService;
import com.google.appengine.api.datastore.DatastoreAttributes;
import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Index;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyRange;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.datastore.DatastoreV4;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.List;
import java.util.Map;

/**
 * A thin wrapper around {@link DatastoreService}, where all operations are redirected to use an
 * alternate namespace.
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
final class NamespacePinnedDatastoreServiceImpl extends NamespacePinnedBaseDatastoreServiceImpl
    implements DatastoreService {
  private final DatastoreService datastore;

  /**
   * Constructs a new {@link NamespacePinnedDatastoreServiceImpl}.
   *
   * @param datastore the underlying {@link DatastoreService}
   * @param namespacePrefix the prefix to apply to all namespaces
   * @param shouldReserveIds a flag that indicates whether calling {@code put()} should explicitly
   *        reserve (not allocate) the numeric IDs used in any completed keys before storing them
   */
  NamespacePinnedDatastoreServiceImpl(DatastoreService datastore, String namespacePrefix,
      boolean shouldReserveIds) {
    super(checkNotNull(namespacePrefix), shouldReserveIds);
    this.datastore = checkNotNull(datastore);
  }

  @Override
  public Entity get(Key key) throws EntityNotFoundException {
    checkNotNull(key);
    return getOriginalNamespaceEntity(datastore.get(getAlternateNamespaceKey(key)));
  }

  @Override
  public Entity get( Transaction txn, Key key) throws EntityNotFoundException {
    checkNotNull(key);
    return getOriginalNamespaceEntity(datastore.get(txn, getAlternateNamespaceKey(key)));
  }

  @Override
  public Map<Key, Entity> get(Iterable<Key> keys) {
    checkNotNull(keys);
    return getOriginalNamespaceEntityMap(datastore.get(getAlternateNamespaceKeys(keys)));
  }

  @Override
  public Map<Key, Entity> get( Transaction txn, Iterable<Key> keys) {
    checkNotNull(keys);
    return getOriginalNamespaceEntityMap(datastore.get(txn, getAlternateNamespaceKeys(keys)));
  }

  @Override
  public Key put(Entity entity) {
    checkNotNull(entity);
    return putImpl(datastore, entity);
  }

  @Override
  public Key put( Transaction txn, Entity entity) {
    checkNotNull(entity);
    return putImpl(getTxnDatastore(txn), entity);
  }

  private Key putImpl(DatastoreService datastore, Entity entity) {
    checkNotNull(datastore);
    checkNotNull(entity);
    Entity alternateEntity = getAlternateNamespaceEntity(entity);
    if (shouldReserveIds) {
      reserveId(alternateEntity);
    }
    return getOriginalNamespaceKey(datastore.put(alternateEntity));
  }

  @Override
  public List<Key> put(Iterable<Entity> entities) {
    checkNotNull(entities);
    return putImpl(datastore, entities);
  }

  @Override
  public List<Key> put( Transaction txn, Iterable<Entity> entities) {
    checkNotNull(entities);
    return putImpl(getTxnDatastore(txn), entities);
  }

  private List<Key> putImpl(DatastoreService datastore, Iterable<Entity> entities) {
    checkNotNull(datastore);
    checkNotNull(entities);
    List<Entity> alternateEntities = getAlternateNamespaceEntities(entities);
    if (shouldReserveIds) {
      reserveIds(alternateEntities);
    }
    return getOriginalNamespaceKeys(datastore.put(alternateEntities));
  }

  @Override
  public void delete(Key... keys) {
    checkNotNull(keys);
    delete(ImmutableList.copyOf(keys));
  }

  @Override
  public void delete( Transaction txn, Key... keys) {
    checkNotNull(keys);
    delete(txn, ImmutableList.copyOf(keys));
  }

  @Override
  public void delete(Iterable<Key> keys) {
    checkNotNull(keys);
    datastore.delete(getAlternateNamespaceKeys(keys));
  }

  @Override
  public void delete( Transaction txn, Iterable<Key> keys) {
    checkNotNull(keys);
    datastore.delete(txn, getAlternateNamespaceKeys(keys));
  }

  @Override
  public Transaction beginTransaction() {
    return datastore.beginTransaction();
  }

  @Override
  public Transaction beginTransaction(TransactionOptions options) {
    checkNotNull(options);
    return datastore.beginTransaction(options);
  }

  @Override
  public KeyRange allocateIds(String kind, long num) {
    checkNotNull(kind);
    String currentNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set(getAlternateNamespace(currentNamespace));
      return getOriginalNamespaceKeyRange(datastore.allocateIds(kind, num));
    } finally {
      NamespaceManager.set(currentNamespace);
    }
  }

  @Override
  public KeyRange allocateIds(Key parent, String kind, long num) {
    checkNotNull(parent);
    checkNotNull(kind);
    String currentNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set(getAlternateNamespace(parent.getNamespace()));
      return getOriginalNamespaceKeyRange(
          datastore.allocateIds(getAlternateNamespaceKey(parent), kind, num));
    } finally {
      NamespaceManager.set(currentNamespace);
    }
  }

  @Override
  public KeyRangeState allocateIdRange(KeyRange range) {
    checkNotNull(range);
    KeyRange alternateRange = getAlternateNamespaceKeyRange(range);
    String currentNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set(alternateRange.getStart().getNamespace());
      return datastore.allocateIdRange(alternateRange);
    } finally {
      NamespaceManager.set(currentNamespace);
    }
  }

  @Override
  public DatastoreAttributes getDatastoreAttributes() {
    return datastore.getDatastoreAttributes();
  }

  @Override
  public Map<Index, Index.IndexState> getIndexes() {
    return datastore.getIndexes();
  }

  @Override
  protected BaseDatastoreService getUnderlyingBaseDatastoreService() {
    return datastore;
  }

  /**
   * Issues an RPC to reserve the key of {@code entity}. This is used when encountering
   * completed keys that have possibly been assigned IDs by the parent Datastore, but that the
   * overlay may not have seen yet.
   */
  private static void reserveId(Entity entity) {
    checkNotNull(entity);
    reserveIds(ImmutableList.of(entity));
  }

  /**
   * Issues an RPC to reserve the keys of {@code entities}. This is used when encountering
   * completed keys that have possibly been assigned IDs by the parent Datastore, but that the
   * overlay may not have seen yet.
   */
  private static void reserveIds(Iterable<Entity> entities) {
    checkNotNull(entities);
    DatastoreV4.AllocateIdsRequest req = makeReserveIdsRequest(entities);
    try {
      DatastoreV4.AllocateIdsResponse.parseFrom(
          ApiProxy.makeSyncCall("datastore_v4", "AllocateIds", req.toByteArray()));
    } catch (InvalidProtocolBufferException e) {
      throw new DatastoreFailureException("error reserving IDs", e);
    }
  }

  @Override
  protected BaseDatastoreService getTxnBaseDatastore( Transaction txn) {
    return getTxnDatastore(txn);
  }

  /**
   * Gets a version of the Datastore that is linked to {@code txn}.
   */
  private DatastoreService getTxnDatastore( Transaction txn) {
    return new TransactionLinkedDatastoreServiceImpl(datastore, txn);
  }
}
