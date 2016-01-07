package com.github.kristofa.brave.sampler;

import com.github.kristofa.brave.TraceSampler;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;

import static com.github.kristofa.brave.internal.Util.checkNotBlank;
import static java.lang.String.format;

public final class ZooKeeperTraceSampler extends TraceSampler implements Watcher, Closeable {

  private final static Logger LOGGER = Logger.getLogger(ZooKeeperTraceSampler.class.getName());
  private final static float DEFAULT_SAMPLE_RATE = 0.0f;

  private final CuratorFramework zkCurator;
  private final CountDownLatch connectionEstablished = new CountDownLatch(1);
  private final String sampleRateZNode;

  private volatile TraceSampler delegate;

  @Override
  public boolean test(long traceId) {
    return delegate.test(traceId);
  }

  /**
   * Creates a new instance. If the initial connection with ZooKeeper can't be established within 2
   * seconds an unchecked exception will be thrown as this will probably indicate wrong
   * configuration.
   *
   * @param connectionString ZooKeeper connection string. Should not be <code>null</code> or empty.
   * @param sampleRateZNode The znode that contains sample rate. Should not be <code>null</code> or
   * empty.
   * @throws IOException In case we can't connect with ZooKeeper.
   * @throws InterruptedException In case we can't connect with ZooKeeper.
   */
  public ZooKeeperTraceSampler(final String connectionString, final String sampleRateZNode)
      throws InterruptedException {
    checkNotBlank(connectionString, "Null or blank connectionString");
    this.sampleRateZNode = checkNotBlank(sampleRateZNode, "Null or blank sampleRateZNode");

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
    delegate = TraceSampler.create(getSampleRate());
  }

  @Override
  public void process(final WatchedEvent event) {

    if (event.getType().equals(Event.EventType.NodeDataChanged) || event.getType().equals(Event.EventType.NodeCreated)
        || event.getType().equals(Event.EventType.NodeDeleted)) {

      final String path = event.getPath();

      if (sampleRateZNode.equals(path)) {
        float newRate = getSampleRate();
        delegate = TraceSampler.create(newRate);
        LOGGER.info(format("SampleRate znode [%s] changed. New value: %s", sampleRateZNode, newRate));
      }
    }
  }

  /**
   * {@inheritDoc}
   */
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

  private float getSampleRate() {
    final byte[] data = getData(sampleRateZNode);
    if (data == null) {
      return DEFAULT_SAMPLE_RATE;
    }
    return Float.valueOf(new String(data));
  }

  private byte[] getData(final String znode) {
    try {
      final Stat stat = zkCurator.checkExists().usingWatcher(this).forPath(znode);
      if (stat != null) {
        return zkCurator.getData().usingWatcher(this).forPath(znode);
      }
    } catch (final Exception e) {
      LOGGER.log(Level.WARNING, "Zookeeper exception.", e);
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
