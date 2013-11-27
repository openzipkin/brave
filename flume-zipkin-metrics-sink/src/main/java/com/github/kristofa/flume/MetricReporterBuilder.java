package com.github.kristofa.flume;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.flume.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteReporter.Builder;

/**
 * Builds a {@link MetricReporter} based on the Flume configuration. </p> Before calling
 * {@link MetricReporterBuilder#build(Context)} you should make sure it is configured first using
 * {@link MetricReporterBuilder#configure(Context)}.
 * 
 * @author kristof
 */
class MetricReporterBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricReporterBuilder.class);

    private static final String UNIFORM_RESERVOIR = "uniform";
    private static final String NR_OF_SAMPLES = "nrofsamples";

    private static final String EXPONENTIALLY_DECAYING_RESERVOIR = "exponentiallydecaying";
    private static final String DECAY_FACTOR = "decayfactor";

    private static final String SLIDING_TIME_WINDOW_RESERVOIR = "slidingtimewindow";
    private static final String WINDOW_IN_SECONDS = "windowseconds";

    private static final String SLIDING_WINDOW_RESERVOIR = "slidingwindow";

    private static final String DEFAULT_RESERVOIR = UNIFORM_RESERVOIR;
    private static final String GRAPHITE_HOST = "graphitehost";
    private static final String GRAPHITE_PORT = "graphiteport";
    private static final String METRICS_RESERVOIR = "reservoir";
    private static final String METRIC_PREFIX = "metricprefix";
    private static final String POLL_TIME = "polltime";

    private boolean succcesfulConfigured = false;
    private String graphiteHost;
    private Integer graphitePort;
    private String metricPrefix;
    private int pollTimeMinutes;
    private HistogramBuilder histogramBuilder;

    /**
     * Configures builder using Flume Context.
     * 
     * @param context Flume Context.
     */
    public void configure(final Context context) {
        succcesfulConfigured = false;
        graphiteHost = context.getString(GRAPHITE_HOST);
        graphitePort = context.getInteger(GRAPHITE_PORT);

        if (graphiteHost == null || graphitePort == null) {
            throw new IllegalStateException(GRAPHITE_HOST + " and " + GRAPHITE_PORT + " properties are mandatory.");
        }

        LOGGER.info("Graphite host: {}", graphiteHost);
        LOGGER.info("Graphite port: {}", graphitePort);

        metricPrefix = context.getString(METRIC_PREFIX);
        pollTimeMinutes = context.getInteger(POLL_TIME, 1);
        LOGGER.info("Reporter poll time in minutes: {}", pollTimeMinutes);

        final String reservoir = context.getString(METRICS_RESERVOIR, DEFAULT_RESERVOIR);

        if (UNIFORM_RESERVOIR.equals(reservoir)) {
            LOGGER.info("Reservoir: {}", UNIFORM_RESERVOIR);
            initializeUniformHistogramBuilder(context);
        } else if (EXPONENTIALLY_DECAYING_RESERVOIR.equals(reservoir)) {
            LOGGER.info("Reservoir: {}", EXPONENTIALLY_DECAYING_RESERVOIR);
            initializeExponentiallyDecayingReservoir(context);
        } else if (SLIDING_TIME_WINDOW_RESERVOIR.equals(reservoir)) {
            LOGGER.info("Reservoir: {}", SLIDING_TIME_WINDOW_RESERVOIR);
            initializeSlidingTimeWindowReservoir(context);
        } else if (SLIDING_WINDOW_RESERVOIR.equals(reservoir)) {
            LOGGER.info("Reservoir: {}", SLIDING_WINDOW_RESERVOIR);
            initializeSlidingWindowReservoir(context);
        } else {
            throw new IllegalStateException("Invalid value for reservoir: " + reservoir);
        }
        succcesfulConfigured = true;
    }

    /**
     * Builds MetricReporter. Should be called after configuring using {@link MetricReporterBuilder#configure(Context)}.
     * 
     * @param context
     * @return
     */
    public MetricReporter build() {
        if (!succcesfulConfigured) {
            throw new IllegalStateException("MetricReporterBuilder is not successfully configured.");
        }

        final MetricRegistry metricRegistry = new MetricRegistry();
        final Graphite graphite = new Graphite(new InetSocketAddress(graphiteHost, graphitePort));

        final Builder builder =
            GraphiteReporter.forRegistry(metricRegistry).convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS).filter(MetricFilter.ALL);
        if (StringUtils.isNotBlank(metricPrefix)) {
            LOGGER.info("Metric prefix: {}", metricPrefix);
            builder.prefixedWith(metricPrefix);
        }

        final GraphiteReporter reporter = builder.build(graphite);
        reporter.start(pollTimeMinutes, TimeUnit.MINUTES);

        return new MetricReporter(metricRegistry, reporter, histogramBuilder);
    }

    private void initializeUniformHistogramBuilder(final Context context) {
        final Integer nrOfSamples = context.getInteger(NR_OF_SAMPLES);
        if (nrOfSamples == null) {
            histogramBuilder = new UniformHistogramBuilder();
        } else {
            histogramBuilder = new UniformHistogramBuilder(nrOfSamples);
        }
    }

    private void initializeExponentiallyDecayingReservoir(final Context context) {
        final Integer nrOfSamples = context.getInteger(NR_OF_SAMPLES);
        final String decayFactorAsString = context.getString(DECAY_FACTOR);
        if (nrOfSamples != null && decayFactorAsString != null) {
            final Double decayFactor = Double.valueOf(decayFactorAsString);
            histogramBuilder = new ExponentiallyDecayingHistogramBuilder(nrOfSamples, decayFactor);
        } else {
            histogramBuilder = new ExponentiallyDecayingHistogramBuilder();
        }
    }

    private void initializeSlidingTimeWindowReservoir(final Context context) {
        final Integer windowInSeconds = context.getInteger(WINDOW_IN_SECONDS);
        if (windowInSeconds == null) {
            throw new IllegalStateException(SLIDING_TIME_WINDOW_RESERVOIR + " configuration needs mandatory "
                + WINDOW_IN_SECONDS + " config entry.");
        }
        histogramBuilder = new SlidingTimeWindowHistogramBuilder(windowInSeconds);
    }

    private void initializeSlidingWindowReservoir(final Context context) {
        final Integer nrOfMeasurements = context.getInteger(NR_OF_SAMPLES);
        if (nrOfMeasurements == null) {
            throw new IllegalStateException(SLIDING_WINDOW_RESERVOIR + " configuration needs mandatory " + NR_OF_SAMPLES
                + " config entry.");
        }
        histogramBuilder = new SlidingWindowHistogramBuilder(nrOfMeasurements);

    }
}
