package com.github.kristofa.flume;

import java.util.Arrays;

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
 * 
 * @author adriaens
 */
public class ZipkinSpanCollectorSink extends AbstractSink implements Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipkinSpanCollectorSink.class);
    private static final String PORT_CONFIG_PROP_NAME = "port";
    private static final String HOSTNAME_CONFIG_PROP_NAME = "hostname";
    private static final String SCRIBE_CATEGORY = "category";

    private TTransport transport;
    private ZipkinCollector.Client client;
    private String hostName;
    private int port;
    private LifecycleState lifeCycleState;
    private SinkCounter sinkCounter;

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start() {
        super.start();
        lifeCycleState = LifecycleState.START;
        sinkCounter.start();
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
            final Event event = channel.take();
            if (event != null) {
                final byte[] body = event.getBody();

                final LogEntry logEntry = new LogEntry();
                logEntry.setCategory(event.getHeaders().get(SCRIBE_CATEGORY));
                logEntry.setMessage(new String(body));
                client.Log(Arrays.asList(logEntry));
                sinkCounter.incrementBatchCompleteCount();
                status = Status.READY;
            } else {
                sinkCounter.incrementBatchEmptyCount();
            }
            txn.commit();
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

        if (sinkCounter == null) {
            sinkCounter = new SinkCounter(getName());
        }

        LOGGER.info("Configuring ZipkinSpanCollectorSink. Host: " + hostName + ", Port: " + port);
    }

}
