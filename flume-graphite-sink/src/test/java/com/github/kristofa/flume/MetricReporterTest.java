package com.github.kristofa.flume;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

import org.junit.Before;
import org.junit.Test;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.google.common.collect.ImmutableSortedMap;

public class MetricReporterTest {

    private static final long VALUE_1 = 11;
    private static final String METRIC_NAME = "metricName";
    private MetricReporter metricReporter;
    private MetricRegistry mockMetricRegistry;
    private ScheduledReporter mockScheduledReporter;
    private HistogramBuilder mockHistogramBuilder;

    @Before
    public void setup() {

        mockMetricRegistry = mock(MetricRegistry.class);
        mockScheduledReporter = mock(ScheduledReporter.class);
        mockHistogramBuilder = mock(HistogramBuilder.class);
        metricReporter = new MetricReporter(mockMetricRegistry, mockScheduledReporter, mockHistogramBuilder);
    }

    @Test
    public void testUpdateNoMetricYet() {

        final Map<String, Histogram> emptyMap = new HashMap<String, Histogram>();
        final SortedMap<String, Histogram> emptyMetricMap = ImmutableSortedMap.copyOf(emptyMap);
        when(mockMetricRegistry.getHistograms()).thenReturn(emptyMetricMap);
        final Histogram mockHistogram = mock(Histogram.class);
        when(mockHistogramBuilder.buildHistogram()).thenReturn(mockHistogram);

        metricReporter.update(METRIC_NAME, VALUE_1);

        verify(mockMetricRegistry).getHistograms();
        verify(mockHistogramBuilder).buildHistogram();
        verify(mockMetricRegistry).register(METRIC_NAME, mockHistogram);
        verify(mockHistogram).update(VALUE_1);
        verifyNoMoreInteractions(mockMetricRegistry, mockScheduledReporter, mockHistogramBuilder, mockHistogram);
    }

    @Test
    public void testUpdateMetricAlreadyPresent() {
        final Histogram mockHistogram = mock(Histogram.class);
        final Map<String, Histogram> map = new HashMap<String, Histogram>();
        map.put(METRIC_NAME, mockHistogram);
        final SortedMap<String, Histogram> metricMap = ImmutableSortedMap.copyOf(map);
        when(mockMetricRegistry.getHistograms()).thenReturn(metricMap);

        metricReporter.update(METRIC_NAME, VALUE_1);

        verify(mockMetricRegistry).getHistograms();
        verify(mockHistogram).update(VALUE_1);
        verifyNoMoreInteractions(mockMetricRegistry, mockScheduledReporter, mockHistogramBuilder, mockHistogram);
    }

    @Test
    public void testClose() {
        metricReporter.close();
        verify(mockScheduledReporter).close();
        verifyNoMoreInteractions(mockMetricRegistry, mockScheduledReporter, mockHistogramBuilder);
    }

}
