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
package org.apache.hadoop.hdds.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.apache.hadoop.metrics2.MetricsInfo;
import org.apache.hadoop.metrics2.MetricsSource;
import org.apache.hadoop.metrics2.MetricsSystem;
import org.apache.hadoop.metrics2.MetricsTag;
import org.apache.hadoop.metrics2.annotation.Metric;
import org.apache.hadoop.metrics2.annotation.Metrics;
import org.apache.hadoop.metrics2.lib.DefaultMetricsSystem;
import org.apache.hadoop.metrics2.lib.MutableCounterLong;

import static java.nio.charset.StandardCharsets.UTF_8;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test prometheus Sink.
 */
public class TestPrometheusMetricsSink {

  @Test
  public void testPublish() throws IOException {
    //GIVEN
    MetricsSystem metrics = DefaultMetricsSystem.instance();

    metrics.init("test");
    PrometheusMetricsSink sink = new PrometheusMetricsSink();
    metrics.register("Prometheus", "Prometheus", sink);
    TestMetrics testMetrics = metrics
        .register("TestMetrics", "Testing metrics", new TestMetrics());

    metrics.start();
    testMetrics.numBucketCreateFails.incr();
    metrics.publishMetricsNow();
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    OutputStreamWriter writer = new OutputStreamWriter(stream, UTF_8);

    //WHEN
    sink.writeMetrics(writer);
    writer.flush();

    //THEN
    String writtenMetrics = stream.toString(UTF_8.name());
    Assert.assertTrue(
        "The expected metric line is missing from prometheus metrics output",
        writtenMetrics.contains(
            "test_metrics_num_bucket_create_fails{context=\"dfs\"")
    );

    metrics.stop();
    metrics.shutdown();
  }

  @Test
  public void testPublishWithSameName() throws IOException {
    //GIVEN
    MetricsSystem metrics = DefaultMetricsSystem.instance();

    metrics.init("test");
    PrometheusMetricsSink sink = new PrometheusMetricsSink();
    metrics.register("Prometheus", "Prometheus", sink);
    metrics.register("FooBar", "fooBar", (MetricsSource) (collector, all) -> {
      collector.addRecord("RpcMetrics").add(new MetricsTag(PORT_INFO, "1234"))
          .addGauge(COUNTER_INFO, 123).endRecord();

      collector.addRecord("RpcMetrics").add(new MetricsTag(
          PORT_INFO, "2345")).addGauge(COUNTER_INFO, 234).endRecord();
    });

    metrics.start();
    metrics.publishMetricsNow();

    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    OutputStreamWriter writer = new OutputStreamWriter(stream, UTF_8);

    //WHEN
    sink.writeMetrics(writer);
    writer.flush();

    //THEN
    String writtenMetrics = stream.toString(UTF_8.name());
    Assert.assertTrue(
        "The expected metric line is missing from prometheus metrics output",
        writtenMetrics.contains(
            "rpc_metrics_counter{port=\"2345\""));

    Assert.assertTrue(
        "The expected metric line is missing from prometheus metrics "
            + "output",
        writtenMetrics.contains(
            "rpc_metrics_counter{port=\"1234\""));

    metrics.stop();
    metrics.shutdown();
  }

  @Test
  public void testNamingCamelCase() {
    PrometheusMetricsSink sink = new PrometheusMetricsSink();

    Assert.assertEquals("rpc_time_some_metrics",
        sink.prometheusName("RpcTime", "SomeMetrics"));

    Assert.assertEquals("om_rpc_time_om_info_keys",
        sink.prometheusName("OMRpcTime", "OMInfoKeys"));

    Assert.assertEquals("rpc_time_small",
        sink.prometheusName("RpcTime", "small"));
  }

  @Test
  public void testNamingRocksDB() {
    //RocksDB metrics are handled differently.
    PrometheusMetricsSink sink = new PrometheusMetricsSink();
    Assert.assertEquals("rocksdb_om.db_num_open_connections",
        sink.prometheusName("Rocksdb_om.db", "num_open_connections"));
  }

  @Test
  public void testNamingPipeline() {
    PrometheusMetricsSink sink = new PrometheusMetricsSink();

    String recordName = "SCMPipelineMetrics";
    String metricName = "NumBlocksAllocated-"
        + "RATIS-THREE-47659e3d-40c9-43b3-9792-4982fc279aba";
    Assert.assertEquals(
        "scm_pipeline_metrics_"
            + "num_blocks_allocated_"
            + "ratis_three_47659e3d_40c9_43b3_9792_4982fc279aba",
        sink.prometheusName(recordName, metricName));
  }

  @Test
  public void testNamingSpaces() {
    PrometheusMetricsSink sink = new PrometheusMetricsSink();

    String recordName = "JvmMetrics";
    String metricName = "GcTimeMillisG1 Young Generation";
    Assert.assertEquals(
        "jvm_metrics_gc_time_millis_g1_young_generation",
        sink.prometheusName(recordName, metricName));
  }

  /**
   * Example metric pojo.
   */
  @Metrics(about = "Test Metrics", context = "dfs")
  private static class TestMetrics {

    @Metric
    private MutableCounterLong numBucketCreateFails;
  }

  public static final MetricsInfo PORT_INFO = new MetricsInfo() {
    @Override
    public String name() {
      return "PORT";
    }

    @Override
    public String description() {
      return "port";
    }
  };

  public static final MetricsInfo COUNTER_INFO = new MetricsInfo() {
    @Override
    public String name() {
      return "COUNTER";
    }

    @Override
    public String description() {
      return "counter";
    }
  };

}
