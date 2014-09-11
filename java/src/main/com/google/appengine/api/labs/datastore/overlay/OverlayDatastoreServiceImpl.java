package com.google.appengine.api.labs.datastore.overlay;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.datastore.BaseDatastoreService;
import com.google.appengine.api.datastore.DatastoreAttributes;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreService.KeyRangeState;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Index;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyRange;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An implementation of {@link DatastoreService} using an overlay model. Conceptually, an overlay
 * Datastore is based on some other Datastore (the "parent"). The overlay allows developers to
 * effectively update or delete entities on the parent, but without actually modifying the data that
 * the parent stores. (There is one exception: ID allocation is forwarded to the parent Datastore.)
 */
final class OverlayDatastoreServiceImpl extends OverlayBaseDatastoreServiceImpl
    implements DatastoreService {
  private final DatastoreService datastore;
  private final DatastoreService parent;

  OverlayDatastoreServiceImpl(DatastoreService datastore, DatastoreService parent) {
    this.datastore = checkNotNull(datastore);
    this.parent = checkNotNull(parent);
  }

  @Override
  public Entity get(Key key) throws EntityNotFoundException {
    checkNotNull(key);
    return getImpl(datastore, key);
  }

  @Override
  public Entity get( Transaction txn, Key key) throws EntityNotFoundException {
    checkNotNull(key);
    return getImpl(getTxnDatastore(txn), key);
  }

  private Entity getImpl(DatastoreService datastore, Key key) throws EntityNotFoundException {
    checkNotNull(datastore);
    checkNotNull(key);
    Entity result = getImpl(datastore, ImmutableList.of(key)).get(key);
    if (result == null) {
      throw new EntityNotFoundException(key);
    }
    return result;
  }

  @Override
  public Map<Key, Entity> get(Iterable<Key> keys) {
    checkNotNull(keys);
    return getImpl(datastore, keys);
  }

  @Override
  public Map<Key, Entity> get( Transaction txn, Iterable<Key> keys) {
    checkNotNull(keys);
    return getImpl(getTxnDatastore(txn), keys);
  }

  private Map<Key, Entity> getImpl(DatastoreService datastore, Iterable<Key> keys) {
    checkNotNull(datastore);
    checkNotNull(keys);

    Map<Key, Entity> results = datastore.get(OverlayUtils.getKeysAndTombstoneKeys(keys));

    Set<Key> remainingKeys = Sets.newHashSet(keys);
    Iterator<Map.Entry<Key, Entity>> entryIterator = results.entrySet().iterator();
    while (entryIterator.hasNext()) {
      Map.Entry<Key, Entity> entry = entryIterator.next();
      if (OverlayUtils.isTombstone(entry.getValue())) {
        entryIterator.remove();
        remainingKeys.remove(OverlayUtils.getKeyFromTombstoneKey(entry.getKey()));
      } else {
        remainingKeys.remove(entry.getKey());
      }
    }

    results.putAll(parent.get(null, remainingKeys));

    return results;
  }

  @Override
  public Key put(Entity entity) {
    checkNotNull(entity);
    return put(ImmutableList.of(entity)).get(0);
  }

  @Override
  public Key put( Transaction txn, Entity entity) {
    checkNotNull(entity);
    return put(txn, ImmutableList.of(entity)).get(0);
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

    Map<IncompleteKey, Integer> idsNeededMap = OverlayUtils.getIdsNeededMap(entities);

    Map<IncompleteKey, Iterator<Key>> idsAllocatedMap = Maps.newHashMap();
    for (Map.Entry<IncompleteKey, Integer> entry : idsNeededMap.entrySet()) {
      IncompleteKey incompleteKey = entry.getKey();
      idsAllocatedMap.put(incompleteKey, incompleteKey.parent == null
          ? allocateIds(incompleteKey.kind, entry.getValue()).iterator()
          : allocateIds(incompleteKey.parent, incompleteKey.kind, entry.getValue()).iterator());
    }

    List<Entity> completedEntities = OverlayUtils.completeEntities(entities, idsAllocatedMap);

    List<Key> keys = datastore.put(completedEntities);

    datastore.delete(OverlayUtils.getTombstoneKeys(keys));

    return keys;
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
    deleteImpl(datastore, keys);
  }

  @Override
  public void delete( Transaction txn, Iterable<Key> keys) {
    checkNotNull(keys);
    deleteImpl(getTxnDatastore(txn), keys);
  }

  private void deleteImpl(DatastoreService datastore, Iterable<Key> keys) {
    checkNotNull(datastore);
    checkNotNull(keys);

    datastore.put(OverlayUtils.getTombstonesForKeys(keys));

    datastore.delete(keys);
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
    return parent.allocateIds(kind, num);
  }

  @Override
  public KeyRange allocateIds(Key parent, String kind, long num) {
    checkNotNull(parent);
    checkNotNull(kind);
    return this.parent.allocateIds(parent, kind, num);
  }

  @Override
  public KeyRangeState allocateIdRange(KeyRange range) {
    checkNotNull(range);
    return this.parent.allocateIdRange(range);
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
  public Map<Key, Entity> getFromOverlayOnly( Transaction txn, Iterable<Key> keys) {
    checkNotNull(keys);
    return datastore.get(txn, keys);
  }

  @Override
  protected BaseDatastoreService getParentBaseDatastoreService() {
    return parent;
  }

  @Override
  protected BaseDatastoreService getUnderlyingBaseDatastoreService() {
    return datastore;
  }

  /**
   * Gets a version of the Datastore that is linked to {@code txn}.
   */
  private DatastoreService getTxnDatastore( Transaction txn) {
    return new TransactionLinkedDatastoreServiceImpl(datastore, txn);
  }
}
