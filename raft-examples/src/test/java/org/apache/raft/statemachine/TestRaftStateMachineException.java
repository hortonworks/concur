/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.raft.statemachine;

import org.apache.hadoop.test.GenericTestUtils;
import org.apache.log4j.Level;
import org.apache.raft.MiniRaftCluster;
import org.apache.raft.RaftTestUtil;
import org.apache.raft.client.RaftClient;
import org.apache.raft.examples.RaftExamplesTestUtil;
import org.apache.raft.grpc.MiniRaftClusterWithGRpc;
import org.apache.raft.hadooprpc.MiniRaftClusterWithHadoopRpc;
import org.apache.raft.protocol.Message;
import org.apache.raft.protocol.StateMachineException;
import org.apache.raft.server.RaftServer;
import org.apache.raft.server.simulation.MiniRaftClusterWithSimulatedRpc;
import org.apache.raft.server.simulation.RequestHandler;
import org.apache.raft.server.storage.RaftLog;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class TestRaftStateMachineException {
  static {
    GenericTestUtils.setLogLevel(RaftServer.LOG, Level.DEBUG);
    GenericTestUtils.setLogLevel(RaftLog.LOG, Level.DEBUG);
    GenericTestUtils.setLogLevel(RequestHandler.LOG, Level.DEBUG);
    GenericTestUtils.setLogLevel(RaftClient.LOG, Level.DEBUG);
  }

  protected static class StateMachineWithException extends SimpleStateMachine {
    @Override
    public CompletableFuture<Message> applyTransaction(TrxContext trx) {
      CompletableFuture<Message> future = new CompletableFuture<>();
      future.completeExceptionally(new StateMachineException("Fake Exception"));
      return future;
    }
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() throws IOException {
    return RaftExamplesTestUtil.getMiniRaftClusters(StateMachineWithException.class,
        MiniRaftClusterWithSimulatedRpc.class,
        MiniRaftClusterWithHadoopRpc.class,
        MiniRaftClusterWithGRpc.class);
    // TODO fix MiniRaftClusterWithNetty.class
  }

  @Parameterized.Parameter
  public MiniRaftCluster cluster;

  @Test
  public void testHandleStateMachineException() throws Exception {
    cluster.start();
    RaftTestUtil.waitForLeader(cluster);

    final String leaderId = cluster.getLeader().getId();

    try(final RaftClient client = cluster.createClient("client", leaderId)) {
      client.send(new RaftTestUtil.SimpleMessage("m"));
      fail("Exception expected");
    } catch (StateMachineException e) {
      Assert.assertTrue(e.getMessage().contains("Fake Exception"));
    }

    cluster.shutdown();
  }
}