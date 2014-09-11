package com.google.appengine.api.datastore;

import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.datastore.DatastoreV4;
import com.google.apphosting.datastore.DatastoreV4.BeginTransactionResponse;
import com.google.apphosting.datastore.DatastoreV4.CommitRequest;
import com.google.apphosting.datastore.DatastoreV4.Mutation;
import com.google.apphosting.datastore.DatastoreV4.RollbackRequest;
import com.google.apphosting.datastore.EntityV4;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of the V4-specific logic to handle a {@link Transaction}.
 *
 * In V4, puts and gets are stored on the client until commit. This class serializes
 * mutations as they are received to avoid memory penalties associated with the full
 * proto objects.
 *
 * This object is subclassed to manage the two mutation styles.
 *
 * @param <T> Internal storage format of each mutation.
 */
abstract class BaseInternalTransactionV4<T> implements TransactionImpl.InternalTransaction {

  /**
   * Generates a unique identifier (for a given runtime) which can be used for later
   * lookup of the instance.
   */
  private static final AtomicLong clientIdGenerator = new AtomicLong();

  /**
   * Used to store {@link BaseInternalTransactionV4} objects for reidentification when a
   * potentially wrapped Transaction object is passed back to the SDK in a future call.
   * Each {@link BaseInternalTransactionV4} instance is wrapped in a {@link TransactionImpl}.
   * We use weak references in this static map because this object's purpose is tied to
   * the lifetime of the wrapper.
   */
  private static final Map<String, BaseInternalTransactionV4<?>> internalTransactionRegister =
      new MapMaker().weakValues().makeMap();

  /**
   * The ID reported through {@link #getId()}. This ID is also used for instance lookup, see
   * {@link #getById(String)}.
   */
  private final String clientId = Long.toString(clientIdGenerator.getAndIncrement());

  /**
   * The list of mutations (deferred Put/Delete operations) that will be sent to the server as part
   * of the Commit RPC.  A linked map is used to generate consistent results for unit tests;
   * however iteration order shouldn't affect correctness.
   */
  private final Map<EntityV4.Key, T> mutationMap = Maps.newLinkedHashMap();

  /**
   * The {@link Future} associated with the BeginTransaction RPC we sent to the
   * datastore server.
   */
  private final Future<BeginTransactionResponse> beginTxnFuture;

  protected final DatastoreV4Proxy dsApiProxy;

  private boolean isWritable = true;

  BaseInternalTransactionV4(DatastoreV4Proxy dsApiProxy,
      Future<BeginTransactionResponse> beginTxnFuture) {
    this.dsApiProxy = dsApiProxy;
    this.beginTxnFuture = beginTxnFuture;
  }

  /**
   * Convert a mutation to a format suitable for committing later.
   */
  abstract T serializeMutation(Mutation mutation);

  /**
   * Convert the partial proto segments into a serialized {@link CommitRequest}.
   */
  abstract Future<?> sendCommit(Collection<T> mutations);

  /**
   * Register a new transaction on the internal roaster.
   * @return The txn, for chaining.
   */
  static <T> BaseInternalTransactionV4<T> registerTxn(BaseInternalTransactionV4<T> txn) {
    internalTransactionRegister.put(txn.clientId, txn);
    return txn;
  }

  /**
   * Provides the unique identifier for the txn.
   * Blocks on the future since the handle comes back from the datastore
   * server.
   */
  ByteString getHandle() {
    return FutureHelper.quietGet(beginTxnFuture).getTransaction();
  }

  /**
   * Schedules a put operation for when this transaction is committed.
   */
  void deferPut(Entity entity) {
    deferPut(DataTypeTranslator.toV4Entity(entity));
  }

  void deferPut(EntityV4.Entity.Builder entityProto) {
    checkWritable();
    mutationMap.put(entityProto.getKey(),
        serializeMutation(Mutation.newBuilder()
            .setOp(DatastoreV4.Mutation.Operation.UPSERT)
            .setEntity(entityProto)
            .build()));
  }

  void deferDelete(Key key) {
    checkWritable();
    EntityV4.Key v4Key = DataTypeTranslator.toV4Key(key).build();
    mutationMap.put(v4Key,
        serializeMutation(Mutation.newBuilder()
        .setOp(DatastoreV4.Mutation.Operation.DELETE)
        .setKey(v4Key)
        .build()));
  }

  @Override
  public Future<Void> doCommitAsync() {
    isWritable = false;
    Future<Void> result = new VoidFutureWrapper<>(sendCommit(mutationMap.values()));
    mutationMap.clear();
    return result;
  }

  @Override
  public Future<Void> doRollbackAsync() {
    isWritable = false;
    mutationMap.clear();
    return new VoidFutureWrapper<>(dsApiProxy.rollback(
        RollbackRequest.newBuilder().setTransaction(getHandle()).build()));
  }

  @Override
  public String getId() {
    return clientId;
  }

  private void checkWritable() {
    if (!isWritable) {
      throw new IllegalStateException("Transaction is not writable.");
    }
  }

  /**
   * Locates the {@link BaseInternalTransactionV4} object associated with a
   * {@link Transaction} by looking up the ID in an static, threadsafe map.
   * @throws IllegalArgumentException If a txn object is not found.
   * @return Internal transaction object associated with the given ID.
   */
  static BaseInternalTransactionV4<?> getById(String txnClientId) {
    BaseInternalTransactionV4<?> txnImpl = internalTransactionRegister.get(txnClientId);
    if (txnImpl == null) {
      throw new IllegalArgumentException("Transaction not found with ID: " + txnClientId);
    }
    return txnImpl;
  }

  private static class VoidFutureWrapper<T> extends FutureWrapper<T, Void> {
    public VoidFutureWrapper(Future<T> parent) {
      super(parent);
    }

    @Override
    protected Void wrap(T ignore) throws Exception {
      return null;
    }

    @Override
    protected Throwable convertException(Throwable cause) {
      return cause;
    }
  }
}
