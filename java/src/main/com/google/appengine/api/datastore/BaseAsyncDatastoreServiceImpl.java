// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.api.datastore;

import static com.google.appengine.api.datastore.FutureHelper.quietGet;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.appengine.api.datastore.Batcher.IndexedItem;
import com.google.appengine.api.datastore.DatastoreAttributes.DatastoreType;
import com.google.appengine.api.datastore.EntityCachingStrategy.NoOpEntityCachingStrategy;
import com.google.appengine.api.datastore.EntityCachingStrategy.PreGetCachingResult;
import com.google.appengine.api.datastore.EntityCachingStrategy.PreMutationCachingResult;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * State and behavior that is common to all asynchronous Datastore API implementations.
 *
 */
abstract class BaseAsyncDatastoreServiceImpl
    implements AsyncDatastoreServiceInternal, CurrentTransactionProvider {
  /**
   * It doesn't actually matter what this value is, the back end will set its
   * own deadline.  All that matters is that we set a value.
   */
  static final long ARBITRARY_FAILOVER_READ_MS = -1;

  /**
   * User-provided config options.
   */
  final DatastoreServiceConfig datastoreServiceConfig;

  /**
   * Knows which transaction to use when the user does not explicitly provide
   * one.
   */
  final TransactionStack defaultTxnProvider;

  EntityCachingStrategy entityCachingStrategy;

  final Logger logger = Logger.getLogger(getClass().getName());

  private DatastoreType datastoreType;

  private final QueryRunner queryRunner;

  /**
   * A base batcher for operations executed in the context of a {@link DatastoreService}.
   * @param <S> the response message type
   * @param <R> the request message type
   * @param <F> the Java specific representation of a value
   * @param <T> the proto representation of value
   */
  abstract class BaseRpcBatcher<S extends Message, R extends MessageLiteOrBuilder, F,
      T extends MessageLite> extends Batcher<R, F, T> {
    abstract Future<S> makeCall(R batch);

    @Override
    final int getMaxSize() {
      return datastoreServiceConfig.maxRpcSizeBytes;
    }

    @Override
    final int getMaxGroups() {
      return datastoreServiceConfig.maxEntityGroupsPerRpc;
    }

    final List<Future<S>> makeCalls(Iterator<R> batches) {
      List<Future<S>> futures = new ArrayList<Future<S>>();
      while (batches.hasNext()) {
        futures.add(makeCall(batches.next()));
      }
      return futures;
    }
  }

  BaseAsyncDatastoreServiceImpl(DatastoreServiceConfig datastoreServiceConfig,
      TransactionStack defaultTxnProvider, QueryRunner queryRunner) {
    this.datastoreServiceConfig = datastoreServiceConfig;
    this.defaultTxnProvider = defaultTxnProvider;
    this.queryRunner = queryRunner;
    if (datastoreServiceConfig.getEntityCacheConfig() == null) {
      entityCachingStrategy = NoOpEntityCachingStrategy.INSTANCE;
    } else {
      entityCachingStrategy = EntityCachingStrategy.createStrategy(datastoreServiceConfig);
    }
  }

  protected abstract TransactionImpl.InternalTransaction
      doBeginTransaction(TransactionOptions options);

  protected abstract Future<Map<Key, Entity>> doBatchGet( Transaction txn,
      final Set<Key> keysToGet, final Map<Key, Entity> resultMap);

  protected abstract Future<List<Key>> doBatchPut( Transaction txn,
      final List<Entity> entities);

  protected abstract Future<Void> doBatchDelete( Transaction txn,
      Collection<Key> keys);

  @SuppressWarnings("deprecation")
  static void validateQuery(Query query) {
    checkArgument(query.getFilterPredicates().isEmpty() || query.getFilter() == null,
        "A query cannot have both a filter and filter predicates set.");
    checkArgument(query.getProjections().isEmpty() || !query.isKeysOnly(),
        "A query cannot have both projections and keys-only set.");
  }

  /**
   * Return the current transaction if one already exists, otherwise create
   * a new transaction or throw an exception according to the
   * {@link ImplicitTransactionManagementPolicy}.
   */
  GetOrCreateTransactionResult getOrCreateTransaction() {
    Transaction currentTxn = getCurrentTransaction(null);
    if (currentTxn != null) {
      return new GetOrCreateTransactionResult(false, currentTxn);
    }

    switch(datastoreServiceConfig.getImplicitTransactionManagementPolicy()) {
      case NONE:
        return new GetOrCreateTransactionResult(false, null);
      case AUTO:
        return new GetOrCreateTransactionResult(true, createTransaction(
            TransactionOptions.Builder.withDefaults(), false));
      default:
        final String msg = "Unexpected Transaction Creation Policy: "
            + datastoreServiceConfig.getImplicitTransactionManagementPolicy();
        logger.severe(msg);
        throw new IllegalArgumentException(msg);
    }
  }

  @Override
  public Transaction getCurrentTransaction() {
    return defaultTxnProvider.peek();
  }

  @Override
  public Transaction getCurrentTransaction(Transaction returnedIfNoTxn) {
    return defaultTxnProvider.peek(returnedIfNoTxn);
  }

  DatastoreServiceConfig getDatastoreServiceConfig() {
    return datastoreServiceConfig;
  }

  @Override
  public Future<Entity> get(Key key) {
    if (key == null) {
      throw new NullPointerException("key cannot be null");
    }
    return wrapSingleGet(key, get(Arrays.asList(key)));
  }

  @Override
  public Future<Entity> get( Transaction txn, final Key key) {
    if (key == null) {
      throw new NullPointerException("key cannot be null");
    }
    return wrapSingleGet(key, get(txn, Arrays.asList(key)));
  }

  private Future<Entity> wrapSingleGet(final Key key, Future<Map<Key, Entity>> futureEntities) {
    return new FutureWrapper<Map<Key, Entity>, Entity>(futureEntities) {
      @Override
      protected Entity wrap(Map<Key, Entity> entities) throws Exception {
        Entity entity = entities.get(key);
        if (entity == null) {
          throw new EntityNotFoundException(key);
        }
        return entity;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  @Override
  public Future<Map<Key, Entity>> get(final Iterable<Key> keys) {
    return new TransactionRunner<Map<Key, Entity>>(getOrCreateTransaction()) {
      @Override
      protected Future<Map<Key, Entity>> runInternal(Transaction txn) {
        return get(txn, keys);
      }
    }.runReadInTransaction();
  }

  @Override
  public Future<Map<Key, Entity>> get(Transaction txn, Iterable<Key> keys) {
    if (keys == null) {
      throw new NullPointerException("keys cannot be null");
    }

    List<Key> keyList = Lists.newArrayList(keys);

    Map<Key, Entity> resultMap = new HashMap<Key, Entity>();
    PreGetContext preGetContext = new PreGetContext(this, keyList, resultMap);
    datastoreServiceConfig.getDatastoreCallbacks().executePreGetCallbacks(preGetContext);

    keyList.removeAll(resultMap.keySet());

    PreGetCachingResult preGetCachingResult =
        entityCachingStrategy.preGet(this, keyList, resultMap);
    keyList.removeAll(preGetCachingResult.getKeysToSkipLoading());

    Future<Map<Key, Entity>> result = doBatchGet(txn, Sets.newLinkedHashSet(keyList), resultMap);

    result = entityCachingStrategy.createPostGetFuture(result, preGetCachingResult);
    return new PostLoadFuture(result, datastoreServiceConfig.getDatastoreCallbacks(), this);
  }

  @Override
  public Future<Key> put(Entity entity) {
    return wrapSinglePut(put(Arrays.asList(entity)));
  }

  @Override
  public Future<Key> put(Transaction txn, Entity entity) {
    return wrapSinglePut(put(txn, Arrays.asList(entity)));
  }

  private Future<Key> wrapSinglePut(Future<List<Key>> futureKeys) {
    return new FutureWrapper<List<Key>, Key>(futureKeys) {
      @Override
      protected Key wrap(List<Key> keys) throws Exception {
        return keys.get(0);
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  @Override
  public Future<List<Key>> put(final Iterable<Entity> entities) {
    return new TransactionRunner<List<Key>>(getOrCreateTransaction()) {
      @Override
      protected Future<List<Key>> runInternal(Transaction txn) {
        return put(txn, entities);
      }
    }.runWriteInTransaction();
  }

  @Override
  public Future<List<Key>> put( Transaction txn, Iterable<Entity> entities) {
    List<Entity> entityList = entities instanceof List
        ? (List<Entity>) entities : Lists.newArrayList(entities);
    PutContext prePutContext = new PutContext(this, entityList);
    datastoreServiceConfig.getDatastoreCallbacks().executePrePutCallbacks(prePutContext);
    PreMutationCachingResult preMutationCachingResult =
        entityCachingStrategy.prePut(this, entityList);

    List<IndexedItem<Key>> indexedKeysToSkip = Lists.newArrayList();
    Set<Key> mutationKeysToSkip = preMutationCachingResult.getMutationKeysToSkip();
    List<Entity> entitiesToPut;
    if (!mutationKeysToSkip.isEmpty()) {
      entitiesToPut = Lists.newArrayList();
      int index = 0;
      for (Entity entity : entityList) {
        if (mutationKeysToSkip.contains(entity.getKey())) {
          indexedKeysToSkip.add(new IndexedItem<Key>(index++, entity.getKey()));
        } else {
          entitiesToPut.add(entity);
          ++index;
        }
      }
    } else {
      entitiesToPut = ImmutableList.copyOf(entities);
    }

    Future<List<Key>> result = combinePutResult(doBatchPut(txn, entitiesToPut), indexedKeysToSkip);

    if (txn == null) {
      result =
          entityCachingStrategy.createPostMutationFuture(result, preMutationCachingResult);
      PutContext postPutContext = new PutContext(this, entityList);
      result = new PostPutFuture(result, datastoreServiceConfig.getDatastoreCallbacks(),
          postPutContext);
    } else {
      defaultTxnProvider.addPutEntities(txn, entityList);
    }
    return result;
  }

  private Future<List<Key>> combinePutResult(Future<List<Key>> rpcResult,
      final List<IndexedItem<Key>> skippedKeys) {
    if (skippedKeys.isEmpty()) {
      return rpcResult;
    }

    return new FutureWrapper<List<Key>, List<Key>>(rpcResult) {
      @Override
      protected List<Key> wrap(List<Key> result) throws Exception {
        List<Key> combined = Lists.newLinkedList(result);
        for (IndexedItem<Key> indexedKey : skippedKeys) {
          combined.add(indexedKey.index, indexedKey.item);
        }
        return combined;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  @Override
  public Future<Void> delete(Key... keys) {
    return delete(Arrays.asList(keys));
  }

  @Override
  public Future<Void> delete(Transaction txn, Key... keys) {
    return delete(txn, Arrays.asList(keys));
  }

  @Override
  public Future<Void> delete(final Iterable<Key> keys) {
    return new TransactionRunner<Void>(getOrCreateTransaction()) {
      @Override
      protected Future<Void> runInternal(Transaction txn) {
        return delete(txn, keys);
      }
    }.runWriteInTransaction();
  }

  @Override
  public Future<Void> delete(Transaction txn, Iterable<Key> keys) {
    List<Key> allKeys = keys instanceof List
        ? (List<Key>) keys : ImmutableList.copyOf(keys);
    DeleteContext preDeleteContext = new DeleteContext(this, allKeys);
    datastoreServiceConfig.getDatastoreCallbacks().executePreDeleteCallbacks(preDeleteContext);
    PreMutationCachingResult preMutationCachingResult =
        entityCachingStrategy.preDelete(this, allKeys);
    Future<Void> result = null;
    Collection<Key> keysToDelete;
    Set<Key> keysToSkip = preMutationCachingResult.getMutationKeysToSkip();
    if (keysToSkip.isEmpty()) {
      keysToDelete = allKeys;
    } else {
      Set<Key> keySet = Sets.newHashSet(allKeys);
      keySet.removeAll(keysToSkip);
      keysToDelete = keySet;
    }
    result = doBatchDelete(txn, keysToDelete);

    if (txn == null) {
      result = entityCachingStrategy.createPostMutationFuture(result, preMutationCachingResult);
      result = new PostDeleteFuture(
          result, datastoreServiceConfig.getDatastoreCallbacks(),
          new DeleteContext(this, allKeys));
    } else {
      defaultTxnProvider.addDeletedKeys(txn, allKeys);
    }
    return result;
  }

  @Override
  public Collection<Transaction> getActiveTransactions() {
    return defaultTxnProvider.getAll();
  }

  /**
   * Register the provided future with the provided txn so that we know to
   * perform a {@link java.util.concurrent.Future#get()} before the txn is
   * committed.
   *
   * @param txn The txn with which the future must be associated.
   * @param future The future to associate with the txn.
   * @param <T> The type of the Future
   * @return The same future that was passed in, for caller convenience.
   */
  protected final <T> Future<T> registerInTransaction( Transaction txn,
      Future<T> future) {
    if (txn != null) {
      defaultTxnProvider.addFuture(txn, future);
      return new FutureHelper.TxnAwareFuture<T>(future, txn, defaultTxnProvider);
    }
    return future;
  }

  @Override
  public Future<Transaction> beginTransaction() {
    return beginTransaction(TransactionOptions.Builder.withDefaults());
  }

  @Override
  public Future<Transaction> beginTransaction(TransactionOptions options) {
    Transaction txn = createTransaction(options, true);

    defaultTxnProvider.push(txn);

    return new FutureHelper.FakeFuture<Transaction>(txn);
  }

  private Transaction createTransaction(TransactionOptions options, boolean isExplicit) {
    return new TransactionImpl(
        datastoreServiceConfig.getAppIdNamespace().getAppId(), defaultTxnProvider,
        datastoreServiceConfig.getDatastoreCallbacks(), entityCachingStrategy, isExplicit,
        doBeginTransaction(options));
  }

  @Override
  public PreparedQuery prepare(Query query) {
    return prepare(null, query);
  }

  @SuppressWarnings("deprecation")
  @Override
  public PreparedQuery prepare(Transaction txn, Query query) {
    PreQueryContext context = new PreQueryContext(this, query);
    datastoreServiceConfig.getDatastoreCallbacks().executePreQueryCallbacks(context);

    query = context.getElements().get(0);
    validateQuery(query);
    List<MultiQueryBuilder> queriesToRun = QuerySplitHelper.splitQuery(query);
    query.setFilter(null);
    query.getFilterPredicates().clear();
    if (queriesToRun.size() == 1 && queriesToRun.get(0).isSingleton()) {
      query.getFilterPredicates().addAll(queriesToRun.get(0).getBaseFilters());
      return new PreparedQueryImpl(query, txn, queryRunner);
    }
    return new PreparedMultiQuery(query, queriesToRun, txn, queryRunner);
  }

  @Override
  public Future<KeyRange> allocateIds(String kind, long num) {
    return allocateIds(null, kind, num);
  }

  protected DatastoreType getDatastoreType() {
    if (datastoreType == null) {
      datastoreType = quietGet(getDatastoreAttributes()).getDatastoreType();
    }
    return datastoreType;
  }

  @Override
  public Future<DatastoreAttributes> getDatastoreAttributes() {
    String appId = datastoreServiceConfig.getAppIdNamespace().getAppId();
    DatastoreAttributes attributes = new DatastoreAttributes(appId);
    return new FutureHelper.FakeFuture<DatastoreAttributes>(attributes);
  }
}
