package com.google.appengine.api.labs.datastore.overlay;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.AsyncDatastoreService;
import com.google.appengine.api.datastore.BaseDatastoreService;
import com.google.appengine.api.datastore.DatastoreAttributes;
import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.Entity;
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
import java.util.concurrent.Future;

/**
 * A thin wrapper around {@link AsyncDatastoreService}, where all operations are redirected to use
 * an alternate namespace.
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
final class NamespacePinnedAsyncDatastoreServiceImpl extends NamespacePinnedBaseDatastoreServiceImpl
    implements AsyncDatastoreService {
  private final AsyncDatastoreService datastore;

  /**
   * Constructs a new {@link NamespacePinnedAsyncDatastoreServiceImpl}.
   *
   * @param datastore the underlying {@link AsyncDatastoreService}
   * @param namespacePrefix the prefix to apply to all namespaces
   * @param shouldReserveIds a flag that indicates whether calling {@code put()} should explicitly
   *        reserve (not allocate) the numeric IDs used in any completed keys before storing them
   */
  NamespacePinnedAsyncDatastoreServiceImpl(AsyncDatastoreService datastore, String namespacePrefix,
      boolean shouldReserveIds) {
    super(checkNotNull(namespacePrefix), shouldReserveIds);
    this.datastore = checkNotNull(datastore);
  }

  @Override
  public Future<Entity> get(Key key) {
    checkNotNull(key);
    return getImpl(datastore, key);
  }

  @Override
  public Future<Entity> get( Transaction txn, Key key) {
    checkNotNull(key);
    return getImpl(getTxnAsyncDatastore(txn), key);
  }

  private Future<Entity> getImpl(AsyncDatastoreService datastore, Key key) {
    checkNotNull(datastore);
    checkNotNull(key);
    return new RethrowingFutureWrapper<Entity, Entity>(
        datastore.get(getAlternateNamespaceKey(key))) {
      @Override
      protected Entity wrap(Entity entity) throws Exception {
        return getOriginalNamespaceEntity(entity);
      }
    };
  }

  @Override
  public Future<Map<Key, Entity>> get(Iterable<Key> keys) {
    checkNotNull(keys);
    return getImpl(datastore, keys);
  }

  @Override
  public Future<Map<Key, Entity>> get( Transaction txn, Iterable<Key> keys) {
    checkNotNull(keys);
    return getImpl(getTxnAsyncDatastore(txn), keys);
  }

  private Future<Map<Key, Entity>> getImpl(AsyncDatastoreService datastore, Iterable<Key> keys) {
    checkNotNull(datastore);
    checkNotNull(keys);
    return new RethrowingFutureWrapper<Map<Key, Entity>, Map<Key, Entity>>(
        datastore.get(getAlternateNamespaceKeys(keys))) {
      @Override
      protected Map<Key, Entity> wrap(Map<Key, Entity> entityMap) throws Exception {
        return getOriginalNamespaceEntityMap(entityMap);
      }
    };
  }

  @Override
  public Future<Key> put(Entity entity) {
    checkNotNull(entity);
    return putImpl(datastore, entity);
  }

  @Override
  public Future<Key> put( Transaction txn, Entity entity) {
    checkNotNull(entity);
    return putImpl(getTxnAsyncDatastore(txn), entity);
  }

  private Future<Key> putImpl(final AsyncDatastoreService datastore, Entity entity) {
    checkNotNull(datastore);
    checkNotNull(entity);
    final Entity alternateEntity = getAlternateNamespaceEntity(entity);
    if (shouldReserveIds) {
      return new RethrowingFutureWrapper<Void, Key>(reserveId(alternateEntity)) {
        @Override
        protected Key wrap(Void v) throws Exception {
          Key key = datastore.put(alternateEntity).get();
          return getOriginalNamespaceKey(key);
        }
      };
    } else {
      return new RethrowingFutureWrapper<Key, Key>(datastore.put(alternateEntity)) {
        @Override
        protected Key wrap(Key key) throws Exception {
          return getOriginalNamespaceKey(key);
        }
      };
    }
  }

  @Override
  public Future<List<Key>> put(Iterable<Entity> entities) {
    checkNotNull(entities);
    return putImpl(datastore, entities);
  }

  @Override
  public Future<List<Key>> put( Transaction txn, Iterable<Entity> entities) {
    checkNotNull(entities);
    return putImpl(getTxnAsyncDatastore(txn), entities);
  }

  private Future<List<Key>> putImpl(final AsyncDatastoreService datastore,
      Iterable<Entity> entities) {
    checkNotNull(datastore);
    checkNotNull(entities);
    final List<Entity> alternateEntities = getAlternateNamespaceEntities(entities);
    if (shouldReserveIds) {
      return new RethrowingFutureWrapper<Void, List<Key>>(reserveIds(alternateEntities)) {
        @Override
        protected List<Key> wrap(Void v) throws Exception {
          List<Key> keys = datastore.put(alternateEntities).get();
          return getOriginalNamespaceKeys(keys);
        }
      };
    } else {
      return new RethrowingFutureWrapper<List<Key>, List<Key>>(datastore.put(alternateEntities)) {
        @Override
        protected List<Key> wrap(List<Key> keys) throws Exception {
          return getOriginalNamespaceKeys(keys);
        }
      };
    }
  }

  @Override
  public Future<Void> delete(Key... keys) {
    checkNotNull(keys);
    return delete(ImmutableList.copyOf(keys));
  }

  @Override
  public Future<Void> delete( Transaction txn, Key... keys) {
    checkNotNull(keys);
    return delete(txn, ImmutableList.copyOf(keys));
  }

  @Override
  public Future<Void> delete(Iterable<Key> keys) {
    checkNotNull(keys);
    return datastore.delete(getAlternateNamespaceKeys(keys));
  }

  @Override
  public Future<Void> delete( Transaction txn, Iterable<Key> keys) {
    checkNotNull(keys);
    return datastore.delete(txn, getAlternateNamespaceKeys(keys));
  }

  @Override
  public Future<Transaction> beginTransaction() {
    return datastore.beginTransaction();
  }

  @Override
  public Future<Transaction> beginTransaction(TransactionOptions options) {
    checkNotNull(options);
    return datastore.beginTransaction(options);
  }

  @Override
  public Future<KeyRange> allocateIds(String kind, long num) {
    checkNotNull(kind);
    String currentNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set(getAlternateNamespace(currentNamespace));
      return new RethrowingFutureWrapper<KeyRange, KeyRange>(datastore.allocateIds(kind, num)) {
        @Override
        protected KeyRange wrap(KeyRange range) throws Exception {
          return getOriginalNamespaceKeyRange(range);
        }
      };
    } finally {
      NamespaceManager.set(currentNamespace);
    }
  }

  @Override
  public Future<KeyRange> allocateIds(Key parent, String kind, long num) {
    checkNotNull(parent);
    checkNotNull(kind);
    String currentNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set(getAlternateNamespace(parent.getNamespace()));
      return new RethrowingFutureWrapper<KeyRange, KeyRange>(
          datastore.allocateIds(getAlternateNamespaceKey(parent), kind, num)) {
        @Override
        protected KeyRange wrap(KeyRange range) throws Exception {
          return getOriginalNamespaceKeyRange(range);
        }
      };
    } finally {
      NamespaceManager.set(currentNamespace);
    }
  }

  @Override
  public Future<DatastoreAttributes> getDatastoreAttributes() {
    return datastore.getDatastoreAttributes();
  }

  @Override
  public Future<Map<Index, Index.IndexState>> getIndexes() {
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
  private static Future<Void> reserveId(Entity entity) {
    checkNotNull(entity);
    return reserveIds(ImmutableList.of(entity));
  }

  /**
   * Issues an RPC to reserve the keys of {@code entities}. This is used when encountering
   * completed keys that have possibly been assigned IDs by the parent Datastore, but that the
   * overlay may not have seen yet.
   */
  private static Future<Void> reserveIds(Iterable<Entity> entities) {
    checkNotNull(entities);
    DatastoreV4.AllocateIdsRequest req = makeReserveIdsRequest(entities);
    return new RethrowingFutureWrapper<byte[], Void>(
        ApiProxy.makeAsyncCall("datastore_v4", "AllocateIds", req.toByteArray())) {
      @Override
      protected Void wrap(byte[] respBytes) throws Exception {
        try {
          DatastoreV4.AllocateIdsResponse.parseFrom(respBytes);
        } catch (InvalidProtocolBufferException e) {
          throw new DatastoreFailureException("error reserving IDs", e);
        }
        return null;
      }
    };
  }

  @Override
  protected BaseDatastoreService getTxnBaseDatastore( Transaction txn) {
    return getTxnAsyncDatastore(txn);
  }

  /**
   * Gets a version of the Datastore that is linked to {@code txn}.
   */
  private AsyncDatastoreService getTxnAsyncDatastore( Transaction txn) {
    return new TransactionLinkedAsyncDatastoreServiceImpl(datastore, txn);
  }
}
