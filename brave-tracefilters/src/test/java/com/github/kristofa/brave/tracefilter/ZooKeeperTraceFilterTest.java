package com.github.kristofa.brave.tracefilter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ZooKeeperTraceFilterTest {

    private final static String SAMPLE_RATE_NODE = "/zipkin/sampleRate";

    private TestingServer zooKeeperTestServer;
    private ZooKeeperTraceFilter traceFilter;

    @Before
    public void setup() throws Exception {
        zooKeeperTestServer = new TestingServer();
        traceFilter = new ZooKeeperTraceFilter(zooKeeperTestServer.getConnectString(), SAMPLE_RATE_NODE);

    }

    @After
    public void tearDown() throws IOException {
        traceFilter.close();
        zooKeeperTestServer.close();
    }

    @Test
    public void testShouldTrace() throws Exception {

        final CuratorFramework zkCurator = traceFilter.getZkCurator();
        assertFalse("znode does not exist.", traceFilter.shouldTrace(null));
        zkCurator.create().creatingParentsIfNeeded().forPath(SAMPLE_RATE_NODE, new String("1").getBytes());
        Thread.sleep(100);
        assertTrue(traceFilter.shouldTrace(null));
        assertTrue(traceFilter.shouldTrace(null));
        assertTrue(traceFilter.shouldTrace(null));
        setValue(zkCurator, 0);
        assertFalse(traceFilter.shouldTrace(null));
        assertFalse(traceFilter.shouldTrace(null));
        assertFalse(traceFilter.shouldTrace(null));
        setValue(zkCurator, 3);
        assertFalse(traceFilter.shouldTrace(null));
        assertFalse(traceFilter.shouldTrace(null));
        assertTrue(traceFilter.shouldTrace(null));
        assertFalse(traceFilter.shouldTrace(null));
        assertFalse(traceFilter.shouldTrace(null));
        assertTrue(traceFilter.shouldTrace(null));
        setValue(zkCurator, -1);
        assertFalse(traceFilter.shouldTrace(null));
        assertFalse(traceFilter.shouldTrace(null));
        assertFalse(traceFilter.shouldTrace(null));
        zkCurator.delete().forPath(SAMPLE_RATE_NODE);
        Thread.sleep(100);
        assertFalse(traceFilter.shouldTrace(null));
        assertFalse(traceFilter.shouldTrace(null));
        assertFalse(traceFilter.shouldTrace(null));

    }

    private void setValue(final CuratorFramework zkCurator, final int value) throws Exception {
        zkCurator.setData().forPath(SAMPLE_RATE_NODE, String.valueOf(value).getBytes());
        Thread.sleep(100);
    }

}
