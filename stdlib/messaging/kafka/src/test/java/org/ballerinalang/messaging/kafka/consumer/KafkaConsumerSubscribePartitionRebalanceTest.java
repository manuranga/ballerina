/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.messaging.kafka.consumer;

import io.debezium.kafka.KafkaCluster;
import io.debezium.util.Testing;
import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.test.util.BCompileUtil;
import org.ballerinalang.test.util.BRunUtil;
import org.ballerinalang.test.util.CompileResult;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.ballerinalang.messaging.kafka.utils.KafkaTestUtils.getFilePath;

/**
 * Tests for ballerina Kafka subscribeWithPartitionRebalance function.
 */
@Test(singleThreaded = true)
public class KafkaConsumerSubscribePartitionRebalanceTest {

    private static File dataDir;
    private static KafkaCluster kafkaCluster;

    @BeforeClass
    public void setup() throws IOException {
        Properties prop = new Properties();
        kafkaCluster = kafkaCluster().deleteDataPriorToStartup(true)
                .deleteDataUponShutdown(true).withKafkaConfiguration(prop).addBrokers(1).startup();
    }

    @Test(description = "Test functionality of subscribeWithPartitionRebalance() function", enabled = false)
    public void testKafkaConsumerSubscribeWithPartitionRebalance() {
        CompileResult result = BCompileUtil.compile(true,
                getFilePath("test-src/consumer/kafka_consumer_subscribe_with_partition_rebalance.bal"));
        BValue[] returnBValuesRevoked = BRunUtil.invoke(result, "funcKafkaGetRebalanceInvokedPartitionsCount");
        Assert.assertEquals(returnBValuesRevoked.length, 1);
        Assert.assertTrue(returnBValuesRevoked[0] instanceof BInteger);
        long revokedPartitionCount = ((BInteger) returnBValuesRevoked[0]).intValue();

        BValue[] returnBValuesAssigned = BRunUtil.invoke(result, "funcKafkaGetRebalanceAssignedPartitionsCount");
        Assert.assertEquals(returnBValuesAssigned.length, 1);
        Assert.assertTrue(returnBValuesAssigned[0] instanceof BInteger);
        long assignedPartitionCount = ((BInteger) returnBValuesAssigned[0]).intValue();
        Assert.assertEquals(revokedPartitionCount, -1);
        Assert.assertEquals(assignedPartitionCount, -1);

        kafkaCluster.createTopic("rebalance-topic-1", 3, 1);
        kafkaCluster.createTopic("rebalance-topic-2", 2, 1);

        BRunUtil.invoke(result, "funcKafkaTestSubscribeWithPartitionRebalance");
        returnBValuesRevoked = BRunUtil.invoke(result, "funcKafkaGetRebalanceInvokedPartitionsCount");
        Assert.assertEquals(returnBValuesRevoked.length, 1);
        Assert.assertTrue(returnBValuesRevoked[0] instanceof BInteger);
        revokedPartitionCount = ((BInteger) returnBValuesRevoked[0]).intValue();

        returnBValuesAssigned = BRunUtil.invoke(result, "funcKafkaGetRebalanceAssignedPartitionsCount");
        Assert.assertEquals(returnBValuesAssigned.length, 1);
        Assert.assertTrue(returnBValuesAssigned[0] instanceof BInteger);
        assignedPartitionCount = ((BInteger) returnBValuesAssigned[0]).intValue();

        Assert.assertEquals(revokedPartitionCount, 0);
        Assert.assertEquals(assignedPartitionCount, 5);
    }

    @AfterClass
    public void tearDown() {
        if (kafkaCluster != null) {
            kafkaCluster.shutdown();
            kafkaCluster = null;
            boolean delete = dataDir.delete();
            // If files are still locked and a test fails: delete on exit to allow subsequent test execution
            if (!delete) {
                dataDir.deleteOnExit();
            }
        }
    }

    private static KafkaCluster kafkaCluster() {
        if (kafkaCluster != null) {
            throw new IllegalStateException();
        }
        dataDir = Testing.Files.createTestingDirectory("cluster-kafka-consumer-subscribe-to-pattern-test");
        kafkaCluster = new KafkaCluster().usingDirectory(dataDir).withPorts(2185, 9098);
        return kafkaCluster;
    }
}
