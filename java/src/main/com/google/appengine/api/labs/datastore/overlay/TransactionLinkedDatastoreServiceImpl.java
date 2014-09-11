package com.google.appengine.api.labs.datastore.overlay;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.datastore.BaseDatastoreService;
import com.google.appengine.api.datastore.DatastoreAttributes;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Index;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyRange;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;

import java.util.List;
import java.util.Map;

/**
 * A simple wrapper class that combines a {@link DatastoreService} with a {@link Transaction}. The
 * purpose of this class is to avoid code duplication between the transaction and non-transaction
 * versions of several API methods.
 */
final class TransactionLinkedDatastoreServiceImpl extends TransactionLinkedBaseDatastoreServiceImpl
    implements DatastoreService {
  private final DatastoreService datastore;

  public TransactionLinkedDatastoreServiceImpl(DatastoreService datastore, Transaction txn) {
    super(txn);
    this.datastore = datastore;
  }

  @Override
  public Entity get(Key key) throws EntityNotFoundException {
    checkNotNull(key);
    return datastore.get(txn, key);
  }

  @Override
  public Entity get( Transaction txn, Key key) throws EntityNotFoundException {
    throw new UnsupportedOperationException(
        "if you want to pass a txn explicitly, don't use this class");
  }

  @Override
  public Map<Key, Entity> get(Iterable<Key> keys) {
    checkNotNull(keys);
    return datastore.get(txn, keys);
  }

  @Override
  public Map<Key, Entity> get( Transaction txn, Iterable<Key> keys) {
    throw new UnsupportedOperationException(
        "if you want to pass a txn explicitly, don't use this class");
  }

  @Override
  public Key put(Entity entity) {
    checkNotNull(entity);
    return datastore.put(txn, entity);
  }

  @Override
  public Key put( Transaction txn, Entity entity) {
    throw new UnsupportedOperationException(
        "if you want to pass a txn explicitly, don't use this class");
  }

  @Override
  public List<Key> put(Iterable<Entity> entities) {
    checkNotNull(entities);
    return datastore.put(txn, entities);
  }

  @Override
  public List<Key> put( Transaction txn, Iterable<Entity> entities) {
    throw new UnsupportedOperationException(
        "if you want to pass a txn explicitly, don't use this class");
  }

  @Override
  public void delete(Key... keys) {
    datastore.delete(txn, keys);
  }

  @Override
  public void delete( Transaction txn, Key... keys) {
    throw new UnsupportedOperationException(
        "if you want to pass a txn explicitly, don't use this class");
  }

  @Override
  public void delete(Iterable<Key> keys) {
    checkNotNull(keys);
    datastore.delete(txn, keys);
  }

  @Override
  public void delete( Transaction txn, Iterable<Key> keys) {
    throw new UnsupportedOperationException(
        "if you want to pass a txn explicitly, don't use this class");
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
    return datastore.allocateIds(kind, num);
  }

  @Override
  public KeyRange allocateIds(Key parent, String kind, long num) {
    checkNotNull(parent);
    checkNotNull(kind);
    return datastore.allocateIds(parent, kind, num);
  }

  @Override
  public KeyRangeState allocateIdRange(KeyRange range) {
    checkNotNull(range);
    return datastore.allocateIdRange(range);
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
}
