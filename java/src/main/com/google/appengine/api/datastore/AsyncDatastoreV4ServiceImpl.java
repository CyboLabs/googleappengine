package com.google.appengine.api.datastore;

import static com.google.appengine.api.datastore.DatastoreAttributes.DatastoreType.MASTER_SLAVE;
import static com.google.appengine.api.datastore.ReadPolicy.Consistency.EVENTUAL;

import com.google.appengine.api.datastore.Batcher.ReorderingMultiFuture;
import com.google.appengine.api.datastore.DatastoreService.KeyRangeState;
import com.google.appengine.api.datastore.DatastoreServiceConfig.ApiVersion;
import com.google.appengine.api.datastore.FutureHelper.MultiFuture;
import com.google.appengine.api.datastore.Index.IndexState;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.datastore.DatastoreV4.AllocateIdsRequest;
import com.google.apphosting.datastore.DatastoreV4.AllocateIdsResponse;
import com.google.apphosting.datastore.DatastoreV4.BeginTransactionRequest;
import com.google.apphosting.datastore.DatastoreV4.BeginTransactionResponse;
import com.google.apphosting.datastore.DatastoreV4.CommitRequest;
import com.google.apphosting.datastore.DatastoreV4.CommitResponse;
import com.google.apphosting.datastore.DatastoreV4.EntityResult;
import com.google.apphosting.datastore.DatastoreV4.LookupRequest;
import com.google.apphosting.datastore.DatastoreV4.LookupResponse;
import com.google.apphosting.datastore.DatastoreV4.Mutation;
import com.google.apphosting.datastore.DatastoreV4.MutationResult;
import com.google.apphosting.datastore.DatastoreV4.ReadOptions;
import com.google.apphosting.datastore.EntityV4;
import com.google.apphosting.datastore.EntityV4.Key.PathElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.Message;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An implementation of AsyncDatastoreService using the DatastoreV4 API.
 */
class AsyncDatastoreV4ServiceImpl extends BaseAsyncDatastoreServiceImpl {

  /**
   * A base batcher for DatastoreV4 operations executed in the context of an {@link
   * AsyncDatastoreV4ServiceImpl}.
   * @param <S> the response message type
   * @param <R> the request message builder type
   * @param <F> the Java specific representation of a value
   * @param <T> the proto representation of value
   */
  private abstract class V4Batcher<S extends Message, R extends Message.Builder, F,
          T extends Message> extends BaseRpcBatcher<S, R, F, T> {
    @Override
    @SuppressWarnings("unchecked")
    final R newBatch(R baseBatch) {
      return (R) baseBatch.clone();
    }
  }

  private final V4Batcher<CommitResponse, CommitRequest.Builder, Key, Mutation> deleteBatcher =
      new V4Batcher<CommitResponse, CommitRequest.Builder, Key, Mutation>() {
        @Override
        void addToBatch(Mutation mutation, CommitRequest.Builder batch) {
          batch.addMutation(mutation);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchWriteEntities;
        }

        @Override
        protected Future<CommitResponse> makeCall(CommitRequest.Builder batch) {
          return datastoreProxy.commit(batch.build());
        }

        @Override
        final Object getGroup(Key key) {
          return key.getRootKey();
        }

        @Override
        final Mutation toPb(Key value) {
          return Mutation.newBuilder()
              .setOp(Mutation.Operation.DELETE)
              .setKey(DataTypeTranslator.toV4Key(value))
              .build();
        }
      };

  private final V4Batcher<LookupResponse, LookupRequest.Builder, Key, EntityV4.Key>
      lookupByKeyBatcher =
      new V4Batcher<LookupResponse, LookupRequest.Builder, Key, EntityV4.Key>() {
        @Override
        void addToBatch(EntityV4.Key key, LookupRequest.Builder batch) {
          batch.addKey(key);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchReadEntities;
        }

        @Override
        protected Future<LookupResponse> makeCall(LookupRequest.Builder batch) {
          return datastoreProxy.lookup(batch.build());
        }

        @Override
        final Object getGroup(Key key) {
          return key.getRootKey();
        }

        @Override
        final EntityV4.Key toPb(Key value) {
          return DataTypeTranslator.toV4Key(value).build();
        }
      };

  private final V4Batcher<LookupResponse, LookupRequest.Builder, EntityV4.Key, EntityV4.Key>
      lookupByPbBatcher =
      new V4Batcher<LookupResponse, LookupRequest.Builder, EntityV4.Key, EntityV4.Key>() {
        @Override
        void addToBatch(EntityV4.Key key, LookupRequest.Builder batch) {
          batch.addKey(key);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchReadEntities;
        }

        @Override
        protected Future<LookupResponse> makeCall(LookupRequest.Builder batch) {
          return datastoreProxy.lookup(batch.build());
        }

        @Override
        final Object getGroup(EntityV4.Key key) {
          return key.getPathElement(0);
        }

        @Override
        final EntityV4.Key toPb(EntityV4.Key value) {
          return value;
        }
      };

  private final V4Batcher<CommitResponse, CommitRequest.Builder, Entity, Mutation>
      putBatcher = new V4Batcher<CommitResponse, CommitRequest.Builder, Entity, Mutation>() {
        @Override
        void addToBatch(Mutation mutation, CommitRequest.Builder batch) {
          batch.addMutation(mutation);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchWriteEntities;
        }

        @Override
        protected Future<CommitResponse> makeCall(CommitRequest.Builder batch) {
          return datastoreProxy.commit(batch.build());
        }

        @Override
        final Object getGroup(Entity value) {
          return value.getKey().getRootKey();
        }

        @Override
        final Mutation toPb(Entity value) {
          return Mutation.newBuilder()
              .setOp(Mutation.Operation.UPSERT)
              .setEntity(DataTypeTranslator.toV4Entity(value))
              .build();
        }
      };

  private final V4Batcher<AllocateIdsResponse, AllocateIdsRequest.Builder, Key, EntityV4.Key>
      allocateIdsBatcher =
      new V4Batcher<AllocateIdsResponse, AllocateIdsRequest.Builder, Key, EntityV4.Key>() {
        @Override
        void addToBatch(EntityV4.Key key, AllocateIdsRequest.Builder batch) {
          batch.addAllocate(key);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchAllocateIdKeys;
        }

        @Override
        protected Future<AllocateIdsResponse> makeCall(AllocateIdsRequest.Builder batch) {
          return datastoreProxy.allocateIds(batch.build());
        }

        @Override
        final Object getGroup(Key key) {
          Key parent = key.getParent();
          if (parent == null) {
            return EntityV4.Key.PathElement.getDefaultInstance();
          } else {
            return DataTypeTranslator.toV4Key(parent).getPathElement(0);
          }
        }

        @Override
        final EntityV4.Key toPb(Key value) {
          return DataTypeTranslator.toV4Key(value).build();
        }
      };

  private final DatastoreV4Proxy datastoreProxy;

  public AsyncDatastoreV4ServiceImpl(
      DatastoreServiceConfig datastoreServiceConfig, DatastoreV4Proxy datastoreProxy,
      TransactionStack defaultTxnProvider) {
    super(datastoreServiceConfig, defaultTxnProvider,
        new QueryRunnerV4(datastoreServiceConfig, datastoreProxy));
    this.datastoreProxy = datastoreProxy;
  }

  @Override
  protected TransactionImpl.InternalTransaction doBeginTransaction(TransactionOptions options) {
    BeginTransactionRequest.Builder request = BeginTransactionRequest.newBuilder();
    request.setCrossGroup(options.isXG());

    Future<BeginTransactionResponse> future = datastoreProxy.beginTransaction(request.build());

    ApiVersion apiVersion = datastoreServiceConfig.getApiVersion();
    switch (apiVersion) {
      case CLOUD_DATASTORE:
        return InternalTransactionCloudDatastore.create(datastoreProxy, future);
      case V4:
        return InternalTransactionV4.create(datastoreProxy, future);
      default:
        throw new IllegalStateException("Unsupported api version: " + apiVersion);
    }
  }

  @Override
  protected Future<Map<Key, Entity>> doBatchGet( Transaction txn,
      final Set<Key> keysToGet, final Map<Key, Entity> resultMap) {
    final LookupRequest.Builder baseReq = LookupRequest.newBuilder();
    ReadOptions.Builder readOptionsBuilder = baseReq.getReadOptionsBuilder();
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      readOptionsBuilder.setTransaction(InternalTransactionV4.getById(txn.getId()).getHandle());
    } else if (datastoreServiceConfig.getReadPolicy().getConsistency() == EVENTUAL) {
      readOptionsBuilder.setReadConsistency(ReadOptions.ReadConsistency.EVENTUAL);
    } else {
      baseReq.clearReadOptions();
    }

    final boolean shouldUseMultipleBatches = getDatastoreType() != MASTER_SLAVE && txn == null
        && datastoreServiceConfig.getReadPolicy().getConsistency() != EVENTUAL;

    Iterator<LookupRequest.Builder> batches = lookupByKeyBatcher.getBatches(keysToGet, baseReq,
        baseReq.build().getSerializedSize(), shouldUseMultipleBatches);
    List<Future<LookupResponse>> futures = lookupByKeyBatcher.makeCalls(batches);

    return registerInTransaction(txn, new MultiFuture<LookupResponse, Map<Key, Entity>>(futures) {
      /**
       * A Map from an EntityV4.Key without an App Id specified to the corresponding Key that the
       * user requested.  This is a workaround for the Remote API to support matching requested
       * Keys to Entities that may be from a different App Id.
       */
      private Map<EntityV4.Key, Key> keyMapIgnoringAppId;

      @Override
      public Map<Key, Entity> get() throws InterruptedException, ExecutionException {
        try {
          aggregate(futures, null, null);
        } catch (TimeoutException e) {
          throw new RuntimeException(e);
        }
        return resultMap;
      }

      @Override
      public Map<Key, Entity> get(long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        aggregate(futures, timeout, unit);
        return resultMap;
      }

      /**
       * Aggregates the results of the given Futures and issues (synchronous) followup requests if
       * any results were deferred.
       *
       * @param currentFutures the Futures corresponding to the batches of the initial
       *        LookupRequests.
       * @param timeout the timeout to use while waiting on the Future, or null for none.
       * @param timeoutUnit the unit of the timeout, or null for none.
       */
      private void aggregate(
          Iterable<Future<LookupResponse>> currentFutures, Long timeout, TimeUnit timeoutUnit)
          throws ExecutionException, InterruptedException, TimeoutException {
        while (true) {
          List<EntityV4.Key> deferredKeys = Lists.newArrayList();

          for (Future<LookupResponse> currentFuture : currentFutures) {
            LookupResponse resp = getFutureWithOptionalTimeout(currentFuture, timeout, timeoutUnit);
            addEntitiesToResultMap(resp);
            deferredKeys.addAll(resp.getDeferredList());
          }

          if (deferredKeys.isEmpty()) {
            break;
          }

          Iterator<LookupRequest.Builder> followupBatches = lookupByPbBatcher.getBatches(
              deferredKeys, baseReq, baseReq.build().getSerializedSize(), shouldUseMultipleBatches);
          currentFutures = lookupByPbBatcher.makeCalls(followupBatches);
        }
      }

      /**
       * Convenience method to get the result of a Future and optionally specify a timeout.
       *
       * @param future the Future to get.
       * @param timeout the timeout to use while waiting on the Future, or null for none.
       * @param timeoutUnit the unit of the timeout, or null for none.
       * @return the result of the Future.
       * @throws TimeoutException will only ever be thrown if a timeout is provided.
       */
      private LookupResponse getFutureWithOptionalTimeout(
          Future<LookupResponse> future, Long timeout, TimeUnit timeoutUnit)
          throws ExecutionException, InterruptedException, TimeoutException {
        if (timeout == null) {
          return future.get();
        } else {
          return future.get(timeout, timeoutUnit);
        }
      }

      /**
       * Adds the Entities from the LookupResponse to the resultMap.  Will omit Keys that were
       * missing. Handles Keys with different App Ids from the Entity.Key.  See
       * {@link #findKeyFromRequestIgnoringAppId(EntityV4.Key)}
       */
      private void addEntitiesToResultMap(LookupResponse response) {
        for (EntityResult entityResult : response.getFoundList()) {
          Entity responseEntity = DataTypeTranslator.toEntity(entityResult.getEntity());
          Key responseKey = responseEntity.getKey();

          if (!keysToGet.contains(responseKey)) {
            responseKey = findKeyFromRequestIgnoringAppId(entityResult.getEntity().getKey());
          }
          resultMap.put(responseKey, responseEntity);
        }
      }

      /**
       * This is a hack to support calls going through the Remote API.  The problem is:
       *
       * The requested Key may have a local app id.
       * The returned Entity may have a remote app id.
       *
       * In this case, we want to return a Map.Entry with {LocalKey, RemoteEntity}.  This way, the
       * user can always do map.get(keyFromRequest).
       *
       * This method will find the corresponding requested LocalKey for a RemoteKey by ignoring the
       * AppId field.
       *
       * Note that we used to be able to rely on the order of the Response Entities matching the
       * order of Request Keys.  We can no longer do so with the addition of Deferred results.
       *
       * @param keyFromResponse the key from the Response that did not match any of the requested
       *        Keys.
       * @return the Key from the request that corresponds to the given Key from the Response
       *         (ignoring AppId.)
       */
      private Key findKeyFromRequestIgnoringAppId(EntityV4.Key keyFromResponse) {
        if (keyMapIgnoringAppId == null) {
          keyMapIgnoringAppId = Maps.newHashMap();
          for (Key requestKey : keysToGet) {
            EntityV4.Key.Builder requestKeyAsRefWithoutApp = DataTypeTranslator.toV4Key(requestKey);
            requestKeyAsRefWithoutApp.getPartitionIdBuilder().clearDatasetId();
            keyMapIgnoringAppId.put(requestKeyAsRefWithoutApp.build(), requestKey);
          }
        }

        EntityV4.Key.Builder keyBuilder = keyFromResponse.toBuilder();
        keyBuilder.getPartitionIdBuilder().clearDatasetId();
        Key result = keyMapIgnoringAppId.get(keyBuilder.build());
        if (result == null) {
          throw new DatastoreFailureException("Internal error");
        }
        return result;
      }
    });
  }

  @Override
  protected Future<List<Key>> doBatchPut( final Transaction txn,
      final List<Entity> entities) {
    if (txn == null) {
      CommitRequest.Builder baseReq = CommitRequest.newBuilder();
      baseReq.setMode(CommitRequest.Mode.NON_TRANSACTIONAL);
      List<Integer> order = Lists.newArrayListWithCapacity(entities.size());
      Iterator<CommitRequest.Builder> batches = putBatcher.getBatches(entities, baseReq,
          baseReq.build().getSerializedSize(), true, order);
      List<Future<CommitResponse>> futures = putBatcher.makeCalls(batches);

      return new ReorderingMultiFuture<CommitResponse, List<Key>>(futures, order) {
        @Override
        protected List<Key> aggregate(CommitResponse intermediateResult, Iterator<Integer> indexItr,
            List<Key> result) {
          for (MutationResult mutationResult : intermediateResult.getMutationResultList()) {
            int index = indexItr.next();
            Key key = entities.get(index).getKey();
            if (mutationResult.hasKey()) {
              List<EntityV4.Key.PathElement> pathElements =
                  mutationResult.getKey().getPathElementList();
              key.setId(pathElements.get(pathElements.size() - 1).getId());
            }
            result.set(index, key);
          }
          return result;
        }

        @Override
        protected List<Key> initResult(int size) {
          List<Key> keyList = Lists.newArrayListWithCapacity(size);
          keyList.addAll(Collections.<Key>nCopies(size, null));
          return keyList;
        }
      };
    }

    TransactionImpl.ensureTxnActive(txn);
    final BaseInternalTransactionV4<?> v4txn = InternalTransactionV4.getById(txn.getId());

    ImmutableList.Builder<Key> keyListBuilder = ImmutableList.builder();
    final List<Key> incompleteKeys = Lists.newArrayList();
    final List<EntityV4.Entity.Builder> incompleteEntityBldrs = Lists.newArrayList();
    for (Entity entity : entities) {
      Key key = entity.getKey();
      keyListBuilder.add(key);
      if (key.isComplete()) {
        v4txn.deferPut(entity);
      } else {
        EntityV4.Entity.Builder v4Entity = EntityV4.Entity.newBuilder();
        DataTypeTranslator.addPropertiesToPb(entity.getPropertyMap(), v4Entity);
        incompleteEntityBldrs.add(v4Entity);
        incompleteKeys.add(key);
      }
    }
    final List<Key> allKeys = keyListBuilder.build();

    if (incompleteKeys.isEmpty()) {
      return new FutureHelper.FakeFuture<List<Key>>(allKeys);
    }
    return registerInTransaction(txn,
        new FutureWrapper<List<EntityV4.Key>, List<Key>>(allocateIds(incompleteKeys)) {
          @Override
          protected List<Key> wrap(List<EntityV4.Key> completedKeyPbs) {
            Iterator<EntityV4.Entity.Builder> entityPbBldrIt = incompleteEntityBldrs.iterator();
            Iterator<Key> incompleteKeysIt = incompleteKeys.iterator();
            for (EntityV4.Key v4Key : completedKeyPbs) {
              updateKey(v4Key, incompleteKeysIt.next());
              v4txn.deferPut(entityPbBldrIt.next().setKey(v4Key));
            }
            return allKeys;
          }

          @Override
          protected Throwable convertException(Throwable cause) {
            return cause;
          }
        });
  }

  @Override
  protected Future<Void> doBatchDelete( Transaction txn, Collection<Key> keys) {
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      BaseInternalTransactionV4<?> v4txn = InternalTransactionV4.getById(txn.getId());
      for (Key key : keys) {
        v4txn.deferDelete(key);
      }
      return new FutureHelper.FakeFuture<Void>(null);
    }

    CommitRequest.Builder baseReq = CommitRequest.newBuilder();
    baseReq.setMode(CommitRequest.Mode.NON_TRANSACTIONAL);
    Iterator<CommitRequest.Builder> batches = deleteBatcher.getBatches(keys, baseReq,
        baseReq.build().getSerializedSize(), true);
    List<Future<CommitResponse>> futures = deleteBatcher.makeCalls(batches);
    return new MultiFuture<CommitResponse, Void>(futures) {
      @Override
      public Void get() throws InterruptedException, ExecutionException {
        for (Future<CommitResponse> future : futures) {
          future.get();
        }
        return null;
      }

      @Override
      public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
          TimeoutException {
        for (Future<CommitResponse> future : futures) {
          future.get(timeout, unit);
        }
        return null;
      }
    };
  }

  /**
   * This API is specific to sequential IDs, which V4 does not support.
   */
  @Override
  public Future<KeyRange> allocateIds(final Key parent, final String kind, long num) {
    throw new UnsupportedOperationException();
  }

  /**
   * This API is specific to sequential IDs, which V4 does not support.
   */
  @Override
  public Future<KeyRangeState> allocateIdRange(final KeyRange range) {
    throw new UnsupportedOperationException();
  }

  /**
   * Allocates scattered IDs for a list of incomplete keys.
   */
  protected Future<List<EntityV4.Key>> allocateIds(List<Key> keyList) {
    List<Integer> order = Lists.newArrayListWithCapacity(keyList.size());
    Iterator<AllocateIdsRequest.Builder> batches = allocateIdsBatcher.getBatches(keyList,
        AllocateIdsRequest.newBuilder(), 0, true, order);
    List<Future<AllocateIdsResponse>> futures = allocateIdsBatcher.makeCalls(batches);

    return new ReorderingMultiFuture<AllocateIdsResponse, List<EntityV4.Key>>(futures, order) {
      @Override
      protected List<EntityV4.Key> aggregate(AllocateIdsResponse batch, Iterator<Integer> indexItr,
          List<EntityV4.Key> result) {
        for (EntityV4.Key key : batch.getAllocatedList()) {
          result.set(indexItr.next(), key);
        }
        return result;
      }

      @Override
      protected List<com.google.apphosting.datastore.EntityV4.Key> initResult(int size) {
        return Arrays.asList(new EntityV4.Key[size]);
      }
    };
  }

  @Override
  public Future<Map<Index, IndexState>> getIndexes() {
    throw new UnsupportedOperationException();
  }

  /**
   * Update a key object with the id in the proto, if one exists.
   */
  static void updateKey(EntityV4.Key v4Key, Key key) {
    List<EntityV4.Key.PathElement> pathElements = v4Key.getPathElementList();
    if (!pathElements.isEmpty()) {
      PathElement lastElement = pathElements.get(pathElements.size() - 1);
      if (lastElement.hasId()) {
        key.setId(lastElement.getId());
      }
    }
  }
}
