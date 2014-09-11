package com.google.appengine.api.datastore;

import com.google.apphosting.datastore.DatastoreV4.BeginTransactionResponse;
import com.google.apphosting.datastore.DatastoreV4.CommitRequest;
import com.google.apphosting.datastore.DatastoreV4.Mutation;
import com.google.common.primitives.Bytes;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Collection;
import java.util.concurrent.Future;

/**
 * Implementation of the V4-specific logic to handle a {@link Transaction}.
 *
 * In V4, puts and gets are stored on the client until commit. This class serializes
 * mutations as they are received to avoid memory penalties associated with the full
 * proto objects.
 */
class InternalTransactionV4 extends BaseInternalTransactionV4<byte[]> {
  private final CommitRequest.Builder commitReqBuilder = CommitRequest.newBuilder();

  /**
   * Objects should be created with {@link #create(DatastoreV4Proxy, Future)} due
   * to post-construction manipulation.
   */
  private InternalTransactionV4(DatastoreV4Proxy dsApiProxy,
      Future<BeginTransactionResponse> beginTxnFuture) {
    super(dsApiProxy, beginTxnFuture);
  }

  static TransactionImpl.InternalTransaction create(DatastoreV4Proxy dsApiProxy,
      Future<BeginTransactionResponse> future) {
    return registerTxn(new InternalTransactionV4(dsApiProxy, future));
  }

  @Override
  byte[] serializeMutation(Mutation mutation) {
    byte[] bytes = commitReqBuilder.addMutation(mutation).buildPartial().toByteArray();
    commitReqBuilder.clearMutation();
    return bytes;
  }

  @Override
  Future<?> sendCommit(Collection<byte[]> mutations) {
    byte[][] protoSegmentsArray = new byte[mutations.size() + 1][];
    protoSegmentsArray[0] = CommitRequest.newBuilder()
        .setTransaction(getHandle())
        .build().toByteArray();
    int arrayIndex = 1;
    for (byte[] mutData : mutations) {
      protoSegmentsArray[arrayIndex++] = mutData;
    }
    try {
      return dsApiProxy.rawCommit(Bytes.concat(protoSegmentsArray));
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException("Unexpected error.", e);
    }
  }
}
