package com.github.kristofa.brave.tracefilter;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PreDestroy;

import org.apache.commons.lang.Validate;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kristofa.brave.TraceFilter;

/**
 * {@link TraceFilter} that gets sample rate from ZooKeeper. It watches a ZooKeeper znode which contains the sample rate. If
 * the value changes our TraceFilter is notified and adapts the sample rate accordingly.
 * <ul>
 * <li>If sample rate value <= 0 tracing is disabled. In that case we won't trace any request.</li>
 * <li>If sample rate value = 1 we will trace every request.</li>
 * <li>If sample rate value > 1, for example 5, we will trace every 5 requests.</li>
 * <li>If the znode does not exist, gets deleted or has no value tracing will be disabled.</li>
 * </ul>
 * 
 * @author kristof
 */
public class ZooKeeperSamplingTraceFilter implements TraceFilter, Watcher {

    private final static Logger LOGGER = LoggerFactory.getLogger(ZooKeeperSamplingTraceFilter.class);
    private final static int DEFAULT_SAMPLE_RATE = 0;

    private final CuratorFramework zkCurator;
    private final CountDownLatch connectionEstablished = new CountDownLatch(1);
    private final String sampleRateZNode;

    private int sampleRate;
    private final AtomicInteger counter = new AtomicInteger();

    /**
     * Creates a new instance. If the initial connection with ZooKeeper can't be established within 2 seconds an unchecked
     * exception will be thrown as this will probably indicate wrong configuration.
     * 
     * @param connectionString ZooKeeper connection string. Should not be <code>null</code> or empty.
     * @param sampleRateZNode The znode that contains sample rate. Should not be <code>null</code> or empty.
     * @throws IOException In case we can't connect with ZooKeeper.
     * @throws InterruptedException In case we can't connect with ZooKeeper.
     */
    public ZooKeeperSamplingTraceFilter(final String connectionString, final String sampleRateZNode)
        throws InterruptedException {
        Validate.notEmpty(connectionString);
        Validate.notEmpty(sampleRateZNode);
        this.sampleRateZNode = sampleRateZNode;

        final RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        zkCurator = CuratorFrameworkFactory.newClient(connectionString, retryPolicy);
        final ConnectionStateListener initialConnectionState = new InitialConnectionStateListener();
        zkCurator.getConnectionStateListenable().addListener(initialConnectionState);
        zkCurator.start();

        if (connectionEstablished.await(2, TimeUnit.SECONDS) == false) {
            zkCurator.close();
            throw new IllegalStateException("Connection with ZooKeeper failed.");
        }
        zkCurator.getConnectionStateListenable().removeListener(initialConnectionState);
        sampleRate = getSampleRate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean trace(final String requestName) {
        if (sampleRate <= 0) {
            return false;
        } else if (sampleRate == 1) {
            return true;
        }

        final int value = counter.incrementAndGet();
        if (value >= sampleRate) {
            synchronized (counter) {
                if (counter.get() >= sampleRate) {
                    counter.set(0);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final WatchedEvent event) {

        if (event.getType().equals(EventType.NodeDataChanged) || event.getType().equals(EventType.NodeCreated)
            || event.getType().equals(EventType.NodeDeleted)) {

            final String path = event.getPath();

            if (sampleRateZNode.equals(path)) {
                sampleRate = getSampleRate();
                LOGGER.info("SampleRate znode [" + sampleRateZNode + "] changed. New value: " + sampleRate);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @PreDestroy
    @Override
    public void close() {
        zkCurator.close();
    }

    /**
     * Gets ZooKeeper Curator instance.
     * 
     * @return ZooKeeper Curator.
     */
    CuratorFramework getZkCurator() {
        return zkCurator;
    }

    private int getSampleRate() {
        final byte[] data = getData(sampleRateZNode);
        if (data == null) {
            return DEFAULT_SAMPLE_RATE;
        }
        final int value = Integer.valueOf(new String(data));
        return value;

    }

    private byte[] getData(final String znode) {
        try {
            final Stat stat = zkCurator.checkExists().usingWatcher(this).forPath(znode);
            if (stat != null) {
                return zkCurator.getData().usingWatcher(this).forPath(znode);
            }
        } catch (final Exception e) {
            LOGGER.error("Zookeeper exception.", e);
        }
        return null;
    }

    private class InitialConnectionStateListener implements ConnectionStateListener {

        @Override
        public void stateChanged(final CuratorFramework client, final ConnectionState newState) {
            if (ConnectionState.CONNECTED.equals(newState) || ConnectionState.RECONNECTED.equals(newState)) {
                LOGGER.info("Connected with ZooKeeper.");
                connectionEstablished.countDown();
            }
        }
    }

}
