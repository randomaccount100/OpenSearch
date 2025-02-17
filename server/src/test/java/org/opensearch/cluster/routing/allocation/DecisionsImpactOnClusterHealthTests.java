/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.cluster.routing.allocation;

import org.opensearch.Version;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.OpenSearchAllocationTestCase;
import org.opensearch.cluster.EmptyClusterInfoService;
import org.opensearch.cluster.health.ClusterHealthStatus;
import org.opensearch.cluster.health.ClusterStateHealth;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.routing.IndexShardRoutingTable;
import org.opensearch.cluster.routing.RoutingNode;
import org.opensearch.cluster.routing.RoutingTable;
import org.opensearch.cluster.routing.ShardRouting;
import org.opensearch.cluster.routing.allocation.AllocationService;
import org.opensearch.cluster.routing.allocation.RoutingAllocation;
import org.opensearch.cluster.routing.allocation.allocator.BalancedShardsAllocator;
import org.opensearch.cluster.routing.allocation.decider.AllocationDecider;
import org.opensearch.cluster.routing.allocation.decider.AllocationDeciders;
import org.opensearch.cluster.routing.allocation.decider.Decision;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.snapshots.EmptySnapshotsInfoService;
import org.opensearch.test.gateway.TestGatewayAllocator;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;

/**
 * This class of tests exercise various scenarios of
 * primary shard allocation and assert the cluster health
 * has the correct status based on those allocation decisions.
 */
public class DecisionsImpactOnClusterHealthTests extends OpenSearchAllocationTestCase {

    public void testPrimaryShardNoDecisionOnIndexCreation() throws IOException {
        final String indexName = "test-idx";
        Settings settings = Settings.builder()
                                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toAbsolutePath().toString())
                                .build();
        AllocationDecider decider = new TestAllocateDecision(Decision.NO);
        // if deciders say NO to allocating a primary shard, then the cluster health should be RED
        runAllocationTest(
            settings, indexName, Collections.singleton(decider), ClusterHealthStatus.RED
        );
    }

    public void testPrimaryShardThrottleDecisionOnIndexCreation() throws IOException {
        final String indexName = "test-idx";
        Settings settings = Settings.builder()
                                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toAbsolutePath().toString())
                                .build();
        AllocationDecider decider = new TestAllocateDecision(Decision.THROTTLE);
        // if deciders THROTTLE allocating a primary shard, stay in YELLOW state
        runAllocationTest(
            settings, indexName, Collections.singleton(decider), ClusterHealthStatus.YELLOW
        );
    }

    public void testPrimaryShardYesDecisionOnIndexCreation() throws IOException {
        final String indexName = "test-idx";
        Settings settings = Settings.builder()
                                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toAbsolutePath().toString())
                                .build();
        AllocationDecider decider = new TestAllocateDecision(Decision.YES) {
            @Override
            public Decision canAllocate(ShardRouting shardRouting, RoutingNode node, RoutingAllocation allocation) {
                if (node.getByShardId(shardRouting.shardId()) == null) {
                    return Decision.YES;
                } else {
                    return Decision.NO;
                }
            }
        };
        // if deciders say YES to allocating primary shards, stay in YELLOW state
        ClusterState clusterState = runAllocationTest(
            settings, indexName, Collections.singleton(decider), ClusterHealthStatus.YELLOW
        );
        // make sure primaries are initialized
        RoutingTable routingTable = clusterState.routingTable();
        for (IndexShardRoutingTable indexShardRoutingTable : routingTable.index(indexName)) {
            assertTrue(indexShardRoutingTable.primaryShard().initializing());
        }
    }

    private ClusterState runAllocationTest(final Settings settings,
                                           final String indexName,
                                           final Set<AllocationDecider> allocationDeciders,
                                           final ClusterHealthStatus expectedStatus) throws IOException {

        final String clusterName = "test-cluster";
        final AllocationService allocationService = newAllocationService(settings, allocationDeciders);

        logger.info("Building initial routing table");
        final int numShards = randomIntBetween(1, 5);
        Metadata metadata = Metadata.builder()
                                .put(IndexMetadata.builder(indexName)
                                         .settings(settings(Version.CURRENT))
                                         .numberOfShards(numShards)
                                         .numberOfReplicas(1))
                                .build();

        RoutingTable routingTable = RoutingTable.builder()
                                        .addAsNew(metadata.index(indexName))
                                        .build();

        ClusterState clusterState = ClusterState.builder(new ClusterName(clusterName))
                                        .metadata(metadata)
                                        .routingTable(routingTable)
                                        .build();

        logger.info("--> adding nodes");
        // we need at least as many nodes as shards for the THROTTLE case, because
        // once a shard has been throttled on a node, that node no longer accepts
        // any allocations on it
        final DiscoveryNodes.Builder discoveryNodes = DiscoveryNodes.builder();
        for (int i = 0; i < numShards; i++) {
            discoveryNodes.add(newNode("node" + i));
        }
        clusterState = ClusterState.builder(clusterState).nodes(discoveryNodes).build();

        logger.info("--> do the reroute");
        routingTable = allocationService.reroute(clusterState, "reroute").routingTable();
        clusterState = ClusterState.builder(clusterState).routingTable(routingTable).build();

        logger.info("--> assert cluster health");
        ClusterStateHealth health = new ClusterStateHealth(clusterState);
        assertThat(health.getStatus(), equalTo(expectedStatus));

        return clusterState;
    }

    private static AllocationService newAllocationService(Settings settings, Set<AllocationDecider> deciders) {
        return new AllocationService(new AllocationDeciders(deciders),
                                     new TestGatewayAllocator(),
                                     new BalancedShardsAllocator(settings),
                                     EmptyClusterInfoService.INSTANCE,
                                     EmptySnapshotsInfoService.INSTANCE);
    }

}
