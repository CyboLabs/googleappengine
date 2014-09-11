// Copyright 2010 Google Inc. All rights reserved.

package com.google.appengine.api.datastore;

import static com.google.appengine.api.datastore.DatastoreApiHelper.makeAsyncCall;
import static com.google.appengine.api.datastore.DatastoreAttributes.DatastoreType.MASTER_SLAVE;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import static com.google.appengine.api.datastore.ReadPolicy.Consistency.EVENTUAL;

import com.google.appengine.api.datastore.Batcher.ReorderingMultiFuture;
import com.google.appengine.api.datastore.DatastoreService.KeyRangeState;
import com.google.appengine.api.datastore.FutureHelper.MultiFuture;
import com.google.appengine.api.datastore.Index.IndexState;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiBasePb.StringProto;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.datastore.DatastoreV3Pb;
import com.google.apphosting.datastore.DatastoreV3Pb.AllocateIdsRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.AllocateIdsResponse;
import com.google.apphosting.datastore.DatastoreV3Pb.CompositeIndices;
import com.google.apphosting.datastore.DatastoreV3Pb.DatastoreService_3.Method;
import com.google.apphosting.datastore.DatastoreV3Pb.DeleteRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.DeleteResponse;
import com.google.apphosting.datastore.DatastoreV3Pb.GetRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.GetResponse;
import com.google.apphosting.datastore.DatastoreV3Pb.PutRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.PutResponse;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.io.protocol.ProtocolMessage;
import com.google.storage.onestore.v3.OnestoreEntity.CompositeIndex;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * An implementation of AsyncDatastoreService using the DatastoreV3 API.
 *
 */
class AsyncDatastoreServiceImpl extends BaseAsyncDatastoreServiceImpl {

  /**
   * A base batcher for DatastoreV3 operations executed in the context of an {@link
   * AsyncDatastoreServiceImpl}.
   * @param <S> the response message type
   * @param <R> the request message type
   * @param <F> the Java specific representation of a value
   * @param <T> the proto representation of value
   */
  private abstract class V3Batcher<S extends ProtocolMessage<S>, R extends ProtocolMessage<R>,
      F, T extends ProtocolMessage<T>> extends BaseRpcBatcher<S, R, F, T> {
    @Override
    final R newBatch(R baseBatch) {
      return baseBatch.clone();
    }
  }

  /**
   * A base batcher for operations that operate on {@link Key}s.
   * @param <S> the response message type
   * @param <R> the request message type
   */
  private abstract class V3KeyBatcher<S extends ProtocolMessage<S>, R extends ProtocolMessage<R>>
      extends V3Batcher<S, R, Key, Reference> {
    @Override
    final Object getGroup(Key value) {
      return value.getRootKey();
    }

    @Override
    final Reference toPb(Key value) {
      return KeyTranslator.convertToPb(value);
    }
  }

  private final V3KeyBatcher<DeleteResponse, DeleteRequest> deleteBatcher =
      new V3KeyBatcher<DeleteResponse, DeleteRequest>() {
        @Override
        void addToBatch(Reference value, DeleteRequest batch) {
          batch.addKey(value);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchWriteEntities;
        }

        @Override
        protected Future<DeleteResponse> makeCall(DeleteRequest batch) {
          return makeAsyncCall(apiConfig, Method.Delete, batch, new DeleteResponse());
        }
      };

  private final V3KeyBatcher<GetResponse, GetRequest> getByKeyBatcher =
      new V3KeyBatcher<GetResponse, GetRequest>() {
        @Override
        void addToBatch(Reference value, GetRequest batch) {
          batch.addKey(value);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchReadEntities;
        }

        @Override
        protected Future<GetResponse> makeCall(GetRequest batch) {
          return makeAsyncCall(apiConfig, Method.Get, batch, new GetResponse());
        }
      };

  private final V3Batcher<GetResponse, GetRequest, Reference, Reference> getByReferenceBatcher =
      new V3Batcher<GetResponse, GetRequest, Reference, Reference>() {
        @Override
        final Object getGroup(Reference value) {
          return value.getPath().getElement(0);
        }

        @Override
        final Reference toPb(Reference value) {
          return value;
        }

        @Override
        void addToBatch(Reference value, GetRequest batch) {
          batch.addKey(value);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchReadEntities;
        }

        @Override
        protected Future<GetResponse> makeCall(GetRequest batch) {
          return makeAsyncCall(apiConfig, Method.Get, batch, new GetResponse());
        }
      };

  private final V3Batcher<PutResponse, PutRequest, Entity, EntityProto> putBatcher =
      new V3Batcher<PutResponse, PutRequest, Entity, EntityProto>() {
        @Override
        Object getGroup(Entity value) {
          return value.getKey().getRootKey();
        }

        @Override
        void addToBatch(EntityProto value, PutRequest batch) {
          batch.addEntity(value);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchWriteEntities;
        }

        @Override
        protected Future<PutResponse> makeCall(PutRequest batch) {
          return makeAsyncCall(apiConfig, Method.Put, batch, new PutResponse());
        }

        @Override
        EntityProto toPb(Entity value) {
          return EntityTranslator.convertToPb(value);
        }
      };

  private final ApiConfig apiConfig;

  public AsyncDatastoreServiceImpl(DatastoreServiceConfig datastoreServiceConfig,
      ApiConfig apiConfig, TransactionStack defaultTxnProvider) {
    super(datastoreServiceConfig, defaultTxnProvider,
        new QueryRunnerV3(datastoreServiceConfig, apiConfig));
    this.apiConfig = apiConfig;
  }

  @Override
  protected TransactionImpl.InternalTransaction doBeginTransaction(TransactionOptions options) {
    DatastoreV3Pb.Transaction remoteTxn = new DatastoreV3Pb.Transaction();
    DatastoreV3Pb.BeginTransactionRequest request = new DatastoreV3Pb.BeginTransactionRequest();
    request.setApp(datastoreServiceConfig.getAppIdNamespace().getAppId());
    request.setAllowMultipleEg(options.isXG());

    Future<DatastoreV3Pb.Transaction> future =
        DatastoreApiHelper.makeAsyncCall(apiConfig, Method.BeginTransaction, request, remoteTxn);

    return new InternalTransactionV3(apiConfig, request.getApp(), future);
  }

  @Override
  protected final Future<Map<Key, Entity>> doBatchGet( Transaction txn, final Set<Key> keysToGet, final Map<Key, Entity> resultMap) {
    final GetRequest baseReq = new GetRequest();
    baseReq.setAllowDeferred(true);
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      baseReq.setTransaction(InternalTransactionV3.localTxnToRemoteTxn(txn));
    }
    if (datastoreServiceConfig.getReadPolicy().getConsistency() == EVENTUAL) {
      baseReq.setFailoverMs(ARBITRARY_FAILOVER_READ_MS);
      baseReq.setStrong(false);
    }

    final boolean shouldUseMultipleBatches = getDatastoreType() != MASTER_SLAVE && txn == null
        && datastoreServiceConfig.getReadPolicy().getConsistency() != EVENTUAL;

    Iterator<GetRequest> batches = getByKeyBatcher.getBatches(keysToGet, baseReq,
        baseReq.getSerializedSize(), shouldUseMultipleBatches);
    List<Future<GetResponse>> futures = getByKeyBatcher.makeCalls(batches);

    return registerInTransaction(txn, new MultiFuture<GetResponse,  Map<Key, Entity>>(futures) {
      /**
       * A Map from a Reference without an App Id specified to the corresponding Key that the user
       * requested.  This is a workaround for the Remote API to support matching requested Keys to
       * Entities that may be from a different App Id .
       */
      private Map<Reference, Key> keyMapIgnoringAppId;

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
       * @param currentFutures the Futures corresponding to the batches of the initial GetRequests.
       * @param timeout the timeout to use while waiting on the Future, or null for none.
       * @param timeoutUnit the unit of the timeout, or null for none.
       */
      private void aggregate(
          Iterable<Future<GetResponse>> currentFutures, Long timeout, TimeUnit timeoutUnit)
          throws ExecutionException, InterruptedException, TimeoutException {
        while (true) {
          List<Reference> deferredRefs = Lists.newLinkedList();

          for (Future<GetResponse> currentFuture : currentFutures) {
            GetResponse resp = getFutureWithOptionalTimeout(currentFuture, timeout, timeoutUnit);
            addEntitiesToResultMap(resp);
            deferredRefs.addAll(resp.deferreds());
          }

          if (deferredRefs.isEmpty()) {
            break;
          }

          Iterator<GetRequest> followupBatches = getByReferenceBatcher.getBatches(deferredRefs,
              baseReq, baseReq.getSerializedSize(), shouldUseMultipleBatches);
          currentFutures = getByReferenceBatcher.makeCalls(followupBatches);
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
      private GetResponse getFutureWithOptionalTimeout(
          Future<GetResponse> future, Long timeout, TimeUnit timeoutUnit)
          throws ExecutionException, InterruptedException, TimeoutException {
        if (timeout == null) {
          return future.get();
        } else {
          return future.get(timeout, timeoutUnit);
        }
      }

      /**
       * Adds the Entities from the GetResponse to the resultMap.  Will omit Keys that were missing.
       * Handles Keys with different App Ids from the Entity.Key.  See
       * {@link #findKeyFromRequestIgnoringAppId(Reference)}
       */
      private void addEntitiesToResultMap(GetResponse response) {
        for (GetResponse.Entity entityResult : response.entitys()) {
          if (entityResult.hasEntity()) {
            Entity responseEntity = EntityTranslator.createFromPb(entityResult.getEntity());
            Key responseKey = responseEntity.getKey();

            if (!keysToGet.contains(responseKey)) {
              responseKey = findKeyFromRequestIgnoringAppId(entityResult.getEntity().getKey());
            }
            resultMap.put(responseKey, responseEntity);
          }
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
       * @param referenceFromResponse the reference from the Response that did not match any of the
       *                              requested Keys.  (May be mutated.)
       * @return the Key from the request that corresponds to the given Reference from the Response
       *         (ignoring AppId.)
       */
      private Key findKeyFromRequestIgnoringAppId(Reference referenceFromResponse) {
        if (keyMapIgnoringAppId == null) {
          keyMapIgnoringAppId = Maps.newHashMap();
          for (Key requestKey : keysToGet) {
            Reference requestKeyAsRefWithoutApp = KeyTranslator.convertToPb(requestKey).clearApp();
            keyMapIgnoringAppId.put(requestKeyAsRefWithoutApp, requestKey);
          }
        }

        Key result = keyMapIgnoringAppId.get(referenceFromResponse.clearApp());
        if (result == null) {
          throw new DatastoreFailureException("Internal error");
        }
        return result;
      }
    });
  }

  @Override
  protected Future<List<Key>> doBatchPut( Transaction txn,
      final List<Entity> entities) {
    PutRequest baseReq = new PutRequest();
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      baseReq.setTransaction(InternalTransactionV3.localTxnToRemoteTxn(txn));
    }
    boolean group = !baseReq.hasTransaction();
    List<Integer> order = Lists.newArrayListWithCapacity(entities.size());
    Iterator<PutRequest> batches = putBatcher.getBatches(entities, baseReq,
        baseReq.getSerializedSize(), group, order);
    List<Future<PutResponse>> futures = putBatcher.makeCalls(batches);

    return registerInTransaction(txn,
        new ReorderingMultiFuture<PutResponse, List<Key>>(futures, order) {
          @Override
          protected List<Key> aggregate(
              PutResponse intermediateResult, Iterator<Integer> indexItr, List<Key> result) {
            for (Reference reference : intermediateResult.keys()) {
              int index = indexItr.next();
              Key key = entities.get(index).getKey();
              KeyTranslator.updateKey(reference, key);
              result.set(index, key);
            }
            return result;
          }

          @Override
          protected List<Key> initResult(int size) {
            List<Key> result = new ArrayList<Key>(Collections.<Key>nCopies(size, null));
            return result;
          }
        });
  }

  @Override
  protected Future<Void> doBatchDelete( Transaction txn, Collection<Key> keys) {
    DeleteRequest baseReq = new DeleteRequest();
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      baseReq.setTransaction(InternalTransactionV3.localTxnToRemoteTxn(txn));
    }
    boolean group = !baseReq.hasTransaction();
    Iterator<DeleteRequest> batches = deleteBatcher.getBatches(keys, baseReq,
        baseReq.getSerializedSize(), group);
    List<Future<DeleteResponse>> futures = deleteBatcher.makeCalls(batches);
    return registerInTransaction(txn, new MultiFuture<DeleteResponse, Void>(futures) {
      @Override
      public Void get() throws InterruptedException, ExecutionException {
        for (Future<DeleteResponse> future : futures) {
          future.get();
        }
        return null;
      }

      @Override
      public Void get(long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        for (Future<DeleteResponse> future : futures) {
          future.get(timeout, unit);
        }
        return null;
      }
    });
  }

  static Reference buildAllocateIdsRef(
      Key parent, String kind, AppIdNamespace appIdNamespace) {
    if (parent != null && !parent.isComplete()) {
      throw new IllegalArgumentException("parent key must be complete");
    }
    Key key = new Key(kind, parent, Key.NOT_ASSIGNED, "ignored", appIdNamespace);
    return KeyTranslator.convertToPb(key);
  }

  @Override
  public Future<KeyRange> allocateIds(final Key parent, final String kind, long num) {
    if (num <= 0) {
      throw new IllegalArgumentException("num must be > 0");
    }

    if (num > 1000000000) {
      throw new IllegalArgumentException("num must be < 1 billion");
    }

    final AppIdNamespace appIdNamespace = datastoreServiceConfig.getAppIdNamespace();
    Reference allocateIdsRef = buildAllocateIdsRef(parent, kind, appIdNamespace);
    AllocateIdsRequest req =
        new AllocateIdsRequest().setSize(num).setModelKey(allocateIdsRef);
    AllocateIdsResponse resp = new AllocateIdsResponse();
    Future<AllocateIdsResponse> future = makeAsyncCall(apiConfig, Method.AllocateIds, req, resp);
    return new FutureWrapper<AllocateIdsResponse, KeyRange>(future) {
      @Override
      protected KeyRange wrap(AllocateIdsResponse resp) throws Exception {
        return new KeyRange(parent, kind, resp.getStart(), resp.getEnd(), appIdNamespace);
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  @Override
  public Future<KeyRangeState> allocateIdRange(final KeyRange range) {
    Key parent = range.getParent();
    final String kind = range.getKind();
    final long start = range.getStart().getId();
    long end = range.getEnd().getId();

    AllocateIdsRequest req = new AllocateIdsRequest()
        .setModelKey(AsyncDatastoreServiceImpl.buildAllocateIdsRef(parent, kind, null))
        .setMax(end);
    AllocateIdsResponse resp = new AllocateIdsResponse();
    Future<AllocateIdsResponse> future = makeAsyncCall(apiConfig, Method.AllocateIds, req, resp);
    return new FutureWrapper<AllocateIdsResponse, KeyRangeState>(future) {
      @SuppressWarnings("deprecation")
      @Override
      protected KeyRangeState wrap(AllocateIdsResponse resp) throws Exception {
        Query query = new Query(kind).setKeysOnly();
        query.addFilter(
            Entity.KEY_RESERVED_PROPERTY, FilterOperator.GREATER_THAN_OR_EQUAL, range.getStart());
        query.addFilter(
            Entity.KEY_RESERVED_PROPERTY, FilterOperator.LESS_THAN_OR_EQUAL, range.getEnd());
        List<Entity> collision = prepare(query).asList(withLimit(1));

        if (!collision.isEmpty()) {
          return KeyRangeState.COLLISION;
        }

        boolean raceCondition = start < resp.getStart();
        return raceCondition ? KeyRangeState.CONTENTION : KeyRangeState.EMPTY;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  @Override
  public Future<Map<Index, IndexState>> getIndexes() {
    StringProto req = new StringProto();
    req.setValue(datastoreServiceConfig.getAppIdNamespace().getAppId());
    return new FutureWrapper<CompositeIndices, Map<Index, IndexState>>(makeAsyncCall(apiConfig,
        Method.GetIndices, req, new CompositeIndices())) {
      @Override
      protected Map<Index, IndexState> wrap(CompositeIndices indices) throws Exception {
        Map<Index, IndexState> answer = new LinkedHashMap<Index, IndexState>();
        for (CompositeIndex ci : indices.indexs()) {
          Index index = IndexTranslator.convertFromPb(ci);
          switch (ci.getStateEnum()) {
            case DELETED:
              answer.put(index, IndexState.DELETING);
              break;
            case ERROR:
              answer.put(index, IndexState.ERROR);
              break;
            case READ_WRITE:
              answer.put(index, IndexState.SERVING);
              break;
            case WRITE_ONLY:
              answer.put(index, IndexState.BUILDING);
              break;
            default:
              logger.log(Level.WARNING, "Unrecognized index state for " + index);
              break;
          }
        }
        return answer;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }
}
