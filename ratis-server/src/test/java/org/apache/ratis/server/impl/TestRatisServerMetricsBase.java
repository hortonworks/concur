/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ratis.server.impl;

import static org.apache.ratis.server.impl.RaftServerMetrics.RATIS_SERVER_FAILED_CLIENT_STALE_READ_COUNT;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.log4j.Level;
import org.apache.ratis.BaseTest;
import org.apache.ratis.MiniRaftCluster;
import org.apache.ratis.RaftTestUtil;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftClientRequest;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.util.Log4jUtils;
import org.junit.Assert;
import org.junit.Test;

/** Tests on Ratis server metrics. */
public abstract class TestRatisServerMetricsBase<CLUSTER extends MiniRaftCluster>
    extends BaseTest
    implements MiniRaftCluster.Factory.Get<CLUSTER> {
  {
    Log4jUtils.setLogLevel(RaftServer.Division.LOG, Level.DEBUG);
    Log4jUtils.setLogLevel(RaftClient.LOG, Level.DEBUG);
  }

  private static final int NUM_SERVERS = 3;

  @Test
  public void testClientFailedRequest() throws Exception {
    runWithNewCluster(NUM_SERVERS, this::runTestClientFailedRequest);
  }

  void runTestClientFailedRequest(CLUSTER cluster)
      throws InterruptedException, IOException, ExecutionException {
    RaftServerImpl leaderImpl = RaftTestUtil.waitForLeader(cluster);
    ClientId clientId = ClientId.randomId();
    // StaleRead with Long.MAX_VALUE minIndex will fail.
    RaftClientRequest r = new RaftClientRequest(clientId, leaderImpl.getId(), cluster.getGroupId(),
        0, Message.EMPTY, RaftClientRequest.staleReadRequestType(Long.MAX_VALUE), null);
    CompletableFuture<RaftClientReply> f = leaderImpl.submitClientRequestAsync(r);
    Assert.assertTrue(!f.get().isSuccess());
    assertEquals(1L,
        leaderImpl.getRaftServerMetrics().getRegistry().counter(RATIS_SERVER_FAILED_CLIENT_STALE_READ_COUNT).getCount());
  }
}
