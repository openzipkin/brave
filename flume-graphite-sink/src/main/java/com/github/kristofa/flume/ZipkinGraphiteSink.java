package com.github.kristofa.flume;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.instrumentation.SinkCounter;
import org.apache.flume.lifecycle.LifecycleState;
import org.apache.flume.sink.AbstractSink;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TIOStreamTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.LogEntry;
import com.twitter.zipkin.gen.Span;

public class ZipkinGraphiteSink extends AbstractSink implements Configurable {

    private static final String SCRIBE_CATEGORY = "category";
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipkinGraphiteSink.class);
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final String PORT_CONFIG_PROP_NAME = "port";
    private static final String HOSTNAME_CONFIG_PROP_NAME = "hostname";
    private static final String BATCH_SIZE_PROP_NAME = "batchsize";

    private String hostName;
    private int port;
    private SinkCounter sinkCounter;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private LifecycleState lifeCycleState;
    private MetricRegistry metricRegistry;
    private GraphiteReporter reporter;

    @Override
    public synchronized void start() {
        super.start();
        lifeCycleState = LifecycleState.START;
        sinkCounter.start();
        metricRegistry = new MetricRegistry();
        final Graphite graphite = new Graphite(new InetSocketAddress(hostName, port));
        reporter =
            GraphiteReporter.forRegistry(metricRegistry).convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS).filter(MetricFilter.ALL).build(graphite);
        reporter.start(1, TimeUnit.MINUTES);
    }

    @Override
    public synchronized void stop() {
        LOGGER.info("Stopping ZipkinGraphiteSink.");
        reporter.close();
        lifeCycleState = LifecycleState.STOP;
        sinkCounter.stop();
        super.stop();
        LOGGER.info("ZipkinGraphiteSink stopped. Metrics:{}", sinkCounter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized LifecycleState getLifecycleState() {
        return lifeCycleState;
    }

    @Override
    public Status process() throws EventDeliveryException {
        Status status = Status.BACKOFF;
        final Channel channel = getChannel();
        final Transaction txn = channel.getTransaction();
        txn.begin();
        try {
            Event event = channel.take();
            if (event != null) {

                process(create(event));
                int count = 1;
                while ((event = channel.take()) != null && count < batchSize) {
                    count++;
                    try {
                        process(create(event));
                    } catch (final TException e) {
                        LOGGER.error("We were unable to build spans from received data...", e);
                    }
                }
                sinkCounter.incrementBatchCompleteCount();
                status = Status.READY;
            } else {
                sinkCounter.incrementBatchEmptyCount();
            }
            txn.commit();
        } catch (final IOException e) {
            txn.rollback();
            LOGGER.error("IOException", e);
        } catch (final Throwable e) {
            txn.rollback();
            throw new EventDeliveryException(e);
        } finally {
            txn.close();
        }

        return status;
    }

    @Override
    public void configure(final Context context) {
        hostName = context.getString(HOSTNAME_CONFIG_PROP_NAME);
        port = context.getInteger(PORT_CONFIG_PROP_NAME);
        batchSize = context.getInteger(BATCH_SIZE_PROP_NAME, DEFAULT_BATCH_SIZE);

        if (sinkCounter == null) {
            sinkCounter = new SinkCounter(getName());
        }

        LOGGER.info("Configuring ZipkinGraphiteSink. hostname: " + hostName + ", port: " + port + ", batchsize: "
            + batchSize);

    }

    private LogEntry create(final Event event) {
        final byte[] body = event.getBody();

        final LogEntry logEntry = new LogEntry();
        logEntry.setCategory(event.getHeaders().get(SCRIBE_CATEGORY));
        logEntry.setMessage(new String(body));
        return logEntry;
    }

    private void process(final LogEntry logEntry) throws TException, IOException {
        final Base64 base64 = new Base64();
        final byte[] decodedSpan = base64.decode(logEntry.getMessage());

        final ByteArrayInputStream buf = new ByteArrayInputStream(decodedSpan);
        final TProtocolFactory factory = new TBinaryProtocol.Factory();
        final TProtocol proto = factory.getProtocol(new TIOStreamTransport(buf));
        final Span span = new Span();
        span.read(proto);

        for (final Annotation annotation : span.getAnnotations()) {
            int duration = annotation.getDuration();
            if (duration > 0) {
                duration = duration / 1000; // Convert from micro- to milliseconds
                final String value = annotation.getValue();
                final int equalSignIndex = value.indexOf("=");
                String metricName = null;
                if (equalSignIndex > -1) {
                    metricName = annotation.getValue().substring(0, equalSignIndex);
                } else {
                    metricName = annotation.getValue();
                }
                Histogram histogram = metricRegistry.getHistograms().get(metricName);
                if (histogram == null) {
                    histogram = metricRegistry.histogram(metricName);
                }
                histogram.update(duration);

            }
        }

    }

}
