package com.github.kristofa.brave.sampler;

import java.io.IOException;
import java.util.Random;
import java.util.stream.LongStream;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

public class ZooKeeperTraceSamplerTest {
  /**
   * Zipkin trace ids are random 64bit numbers. This creates a relatively large input to avoid
   * flaking out due to PRNG nuance.
   */
  long[] traceIds = new Random().longs(100000).toArray();

  private final static String SAMPLE_RATE_NODE = "/zipkin/sampleRate";

  private TestingServer zooKeeperTestServer;
  private ZooKeeperTraceSampler sampler;

  @Before
  public void setup() throws Exception {
    zooKeeperTestServer = new TestingServer();
    sampler = new ZooKeeperTraceSampler(zooKeeperTestServer.getConnectString(), SAMPLE_RATE_NODE);
  }

  @After
  public void tearDown() throws IOException {
    sampler.close();
    zooKeeperTestServer.close();
  }

  @Test
  public void dropsWhenZNodeIsAbsent() throws Exception {
    assertThat(LongStream.of(traceIds).filter(sampler::test).toArray())
        .isEmpty();
  }

  @Test
  public void retain10Percent() throws Exception {
    float sampleRate = 0.1f;
    setRate(sampleRate);

    long passCount = LongStream.of(traceIds).filter(sampler::test).count();

    assertThat(passCount)
        .isCloseTo((long) (traceIds.length * sampleRate), withPercentage(3));
  }

  @Test
  public void zeroMeansDropAllTraces() throws Exception {
    setRate(0.0f);

    assertThat(LongStream.of(traceIds).filter(sampler::test).findAny())
        .isEmpty();
  }

  @Test
  public void oneMeansKeepAllTraces() throws Exception {
    setRate(1.0f);

    assertThat(LongStream.of(traceIds).filter(sampler::test).toArray())
        .containsExactly(traceIds);
  }

  private void setRate(float rate) throws Exception {
    sampler.getZkCurator().create().creatingParentsIfNeeded()
        .forPath(SAMPLE_RATE_NODE, String.valueOf(rate).getBytes());
    Thread.sleep(100);
  }
}
