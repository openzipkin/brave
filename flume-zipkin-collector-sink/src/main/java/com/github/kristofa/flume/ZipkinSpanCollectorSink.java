package com.github.kristofa.flume;

import java.util.ArrayList;
import java.util.List;

import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Sink;
import org.apache.flume.Source;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.instrumentation.SinkCounter;
import org.apache.flume.lifecycle.LifecycleState;
import org.apache.flume.sink.AbstractSink;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twitter.zipkin.gen.LogEntry;
import com.twitter.zipkin.gen.ZipkinCollector;

/**
 * A Flume {@link Sink} that is used to submit events to Zipkin Span Collector or a Scribe {@link Source}. </p> It expects
 * that the {@link Event events} are originally submitted by the Zipkin Span Collector and transported by The Flume Scribe
 * Source.
 * <p/>
 * You can configure:
 * <ul>
 * <li>hostname: Host name for scribe source or zipkin collector. Mandatory, no default value.</li>
 * <li>port: Port for scribe source of zipkin collector. Mandatory, no default value.</li>
 * <li>batchsize: How many event should be sent at once if there are that many available. Optional, default value = 100.</li>
 * </ul>
 * 
 * @author adriaens
 */
public class ZipkinSpanCollectorSink extends AbstractSink implements Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipkinSpanCollectorSink.class);
    private static final String PORT_CONFIG_PROP_NAME = "port";
    private static final String HOSTNAME_CONFIG_PROP_NAME = "hostname";
    private static final String BATCH_SIZE_PROP_NAME = "batchsize";
    private static final String SCRIBE_CATEGORY = "category";
    private static final int DEFAULT_BATCH_SIZE = 100;

    private TTransport transport;
    private ZipkinCollector.Client client;
    private String hostName;
    private int port;
    private LifecycleState lifeCycleState;
    private SinkCounter sinkCounter;
    private int batchSize = DEFAULT_BATCH_SIZE;

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start() {
        super.start();
        lifeCycleState = LifecycleState.START;
        sinkCounter.start();
        try {
            connect();
        } catch (final TTransportException e) {
            lifeCycleState = LifecycleState.ERROR;
            throw new IllegalStateException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop() {
        LOGGER.info("Stopping ZipkinSpanCollectorSink.");
        transport.close();
        lifeCycleState = LifecycleState.STOP;
        sinkCounter.incrementConnectionClosedCount();
        sinkCounter.stop();
        super.stop();
        LOGGER.info("ZipkinSpanCollectorSink stopped. Metrics:{}", sinkCounter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized LifecycleState getLifecycleState() {
        return lifeCycleState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Status process() throws EventDeliveryException {

        Status status = Status.BACKOFF;
        final Channel channel = getChannel();
        final Transaction txn = channel.getTransaction();
        txn.begin();
        try {
            Event event = channel.take();
            if (event != null) {
                final List<LogEntry> logEntries = new ArrayList<LogEntry>(batchSize);
                logEntries.add(create(event));
                int count = 1;
                while ((event = channel.take()) != null && count < batchSize) {
                    count++;
                    logEntries.add(create(event));
                }

                client.Log(logEntries);
                sinkCounter.incrementBatchCompleteCount();
                status = Status.READY;
            } else {
                sinkCounter.incrementBatchEmptyCount();
            }
            txn.commit();
        } catch (final TTransportException e) {
            txn.rollback();
            LOGGER.error("Got a TTransportException. Will close current Transport and create new connection/client.");
            try {
                connect();
                LOGGER.info("Reconnect succeeded.");
            } catch (final TTransportException e1) {
                LOGGER.error("Trying to reconnect failed.", e1);
            }
        } catch (final Throwable e) {
            txn.rollback();
            throw new EventDeliveryException(e);
        } finally {
            txn.close();
        }

        return status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void configure(final Context context) {
        hostName = context.getString(HOSTNAME_CONFIG_PROP_NAME);
        port = context.getInteger(PORT_CONFIG_PROP_NAME);
        batchSize = context.getInteger(BATCH_SIZE_PROP_NAME, DEFAULT_BATCH_SIZE);

        if (sinkCounter == null) {
            sinkCounter = new SinkCounter(getName());
        }

        LOGGER.info("Configuring ZipkinSpanCollectorSink. hostname: " + hostName + ", port: " + port + ", batchsize: "
            + batchSize);
    }

    private LogEntry create(final Event event) {
        final byte[] body = event.getBody();

        final LogEntry logEntry = new LogEntry();
        logEntry.setCategory(event.getHeaders().get(SCRIBE_CATEGORY));
        logEntry.setMessage(new String(body));
        return logEntry;
    }

    private void connect() throws TTransportException {
        if (transport != null) {
            transport.close();
        }
        transport = new TFramedTransport(new TSocket(hostName, port));
        final TProtocol protocol = new TBinaryProtocol(transport);
        client = new ZipkinCollector.Client(protocol);
        try {
            transport.open();
            sinkCounter.incrementConnectionCreatedCount();
        } catch (final TTransportException e) {
            LOGGER.error("Staring ZipkinSpanCollectorSink failed!", e);
            sinkCounter.incrementConnectionFailedCount();
            lifeCycleState = LifecycleState.ERROR;
            throw e;
        }

    }

}
