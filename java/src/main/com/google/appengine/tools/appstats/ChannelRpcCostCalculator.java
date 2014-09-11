// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.appengine.tools.appstats;

import static com.google.appengine.tools.appstats.StatsProtos.BilledOpProto.BilledOp.CHANNEL_OPEN;

import java.util.Arrays;
import java.util.Map;

/**
 * {@link RpcCostCalculator} implementation for the Channel API.
 *
 */
class ChannelRpcCostCalculator implements RpcCostCalculator {

  private static final String PKG = "channel";

  private final StatsProtos.BilledOpProto billedOpProto =
      StatsProtos.BilledOpProto.newBuilder().setNumOps(1).setOp(CHANNEL_OPEN).build();

  private final RpcCost channelOpenCostMicropennies;

  ChannelRpcCostCalculator(long channelOpenCostMicropennies) {
    this.channelOpenCostMicropennies =
        new RpcCost(channelOpenCostMicropennies, Arrays.asList(billedOpProto));
  }

  @Override
  public RpcCost determineCost(String methodName, byte[] request, byte[] response) {
    if (methodName.equals("CreateChannel")) {
      return channelOpenCostMicropennies;
    }
    return FREE;
  }

  static void register(
      Map<String, RpcCostCalculator> costCalculatorMap, RpcOperationCostManager opCostMgr) {
    costCalculatorMap.put(PKG, new ChannelRpcCostCalculator(opCostMgr.costOf(CHANNEL_OPEN)));
  }
}
