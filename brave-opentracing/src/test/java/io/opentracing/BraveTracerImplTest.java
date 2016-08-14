/**
 * Copyright 2016 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing;

import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;


public final class BraveTracerImplTest {

    @Test
    public void test_buildSpan() {
        String operationName = "test-test_buildSpan";
        BraveTracerImpl tracer = new BraveTracerImpl();
        tracer.brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilderImpl builder = (BraveSpanBuilderImpl) tracer.buildSpan(operationName);

        assert operationName.equals(builder.operationName) : builder.operationName;
        assert null == builder.parentSpanId : builder.parentSpanId;
        assert builder.references.isEmpty();
        assert null == builder.serverTracer : builder.serverTracer;
        assert builder.start.isBefore(Instant.now().plusMillis(1));
        assert null == builder.traceId : builder.traceId;

        BraveSpanImpl span = builder.createSpan();

        assert null != span.spanId;
        assert 0 != span.spanId.getSpanId() : span.spanId.getSpanId();
        assert 0 != span.spanId.getTraceId() : span.spanId.getTraceId();
        assert null == span.spanId.getParentSpanId() : span.spanId.getParentSpanId();
        assert operationName.equals(span.operationName) : "span.operationName was " + span.operationName;
        assert !span.parent.isPresent();
        assert !span.serverTracer.isPresent();
        assert span.baggage.isEmpty();

        span.finish();
    }

    @Test
    public void test_buildSpan_child() {
        String parentOperationName = "test-test_buildSpan_child-parent";
        String operationName = "test-test_buildSpan_child";
        BraveTracerImpl tracer = new BraveTracerImpl();
        tracer.brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilderImpl builder = (BraveSpanBuilderImpl) tracer.buildSpan(parentOperationName);

        assert parentOperationName.equals(builder.operationName) : builder.operationName;
        assert null == builder.parentSpanId : builder.parentSpanId;
        assert builder.references.isEmpty();
        assert null == builder.serverTracer : builder.serverTracer;
        assert builder.start.isBefore(Instant.now().plusMillis(1));
        assert null == builder.traceId : builder.traceId;

        BraveSpanImpl parent = builder.createSpan();

        assert null != parent.spanId;
        assert 0 != parent.spanId.getSpanId() : parent.spanId.getSpanId();
        assert 0 != parent.spanId.getTraceId() : parent.spanId.getTraceId();
        assert null == parent.spanId.getParentSpanId() : parent.spanId.getParentSpanId();
        assert parentOperationName.equals(parent.operationName) : "span.operationName was " + parent.operationName;
        assert !parent.parent.isPresent();
        assert !parent.serverTracer.isPresent();
        assert parent.baggage.isEmpty();

        builder = (BraveSpanBuilderImpl) tracer.buildSpan(operationName).asChildOf((Span)parent);
        BraveSpanImpl span = builder.createSpan();

        assert operationName.equals(span.operationName) : "span.operationName was " + span.operationName;
        assertChildToParent(span, parent, false);

        span.finish();
        parent.finish();
    }

    @Test
    public void test_buildSpan_seperate_traces() {
        String parentOperationName = "test-test_buildSpan_seperate_traces-parent";
        String operationName = "test-test_buildSpan_seperate_traces";
        BraveTracerImpl tracer = new BraveTracerImpl();
        tracer.brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilderImpl builder = (BraveSpanBuilderImpl) tracer.buildSpan(parentOperationName);

        assert parentOperationName.equals(builder.operationName) : builder.operationName;
        assert null == builder.parentSpanId : builder.parentSpanId;
        assert builder.references.isEmpty();
        assert null == builder.serverTracer : builder.serverTracer;
        assert builder.start.isBefore(Instant.now().plusMillis(1));
        assert null == builder.traceId : builder.traceId;

        BraveSpanImpl first = builder.createSpan();

        assert null != first.spanId;
        assert 0 != first.spanId.getSpanId() : first.spanId.getSpanId();
        assert 0 != first.spanId.getTraceId() : first.spanId.getTraceId();
        assert null == first.spanId.getParentSpanId() : first.spanId.getParentSpanId();
        assert parentOperationName.equals(first.operationName) : "span.operationName was " + first.operationName;
        assert !first.parent.isPresent();
        assert !first.serverTracer.isPresent();
        assert first.baggage.isEmpty();

        first.finish();
        builder = (BraveSpanBuilderImpl) tracer.buildSpan(operationName);
        BraveSpanImpl second = builder.createSpan();

        assert null != second.spanId;
        assert 0 != second.spanId.getSpanId() : second.spanId.getSpanId();
        assert 0 != second.spanId.getSpanId() : second.spanId.getSpanId();
        assert 0 != second.spanId.getTraceId() : second.spanId.getTraceId();

        assert first.spanId.getTraceId() != second.spanId.getTraceId()
                : "child: " + first.spanId.getTraceId() + " ; parent: " + second.spanId.getTraceId();

        assert null == second.spanId.getParentSpanId() : second.spanId.getParentSpanId();
        assert operationName.equals(second.operationName) : "span.operationName was " + second.operationName;
        assert !first.parent.isPresent();
        assert !second.serverTracer.isPresent();
        assert second.baggage.isEmpty();

        second.finish();
    }

    @Test
    public void test_buildSpan_same_trace() {
        String parentOperationName = "test-test_buildSpan_same_trace-parent";
        String operationName1 = "test-test_buildSpan_same_trace-1";
        String operationName2 = "test-test_buildSpan_same_trace-2";
        BraveTracerImpl tracer = new BraveTracerImpl();
        tracer.brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilderImpl builder = (BraveSpanBuilderImpl) tracer.buildSpan(parentOperationName);

        assert parentOperationName.equals(builder.operationName) : builder.operationName;
        assert null == builder.parentSpanId : builder.parentSpanId;
        assert builder.references.isEmpty();
        assert null == builder.serverTracer : builder.serverTracer;
        assert builder.start.isBefore(Instant.now().plusMillis(1));
        assert null == builder.traceId : builder.traceId;

        BraveSpanImpl parent = builder.createSpan();

        assert null != parent.spanId;
        assert 0 != parent.spanId.getSpanId() : parent.spanId.getSpanId();
        assert 0 != parent.spanId.getTraceId() : parent.spanId.getTraceId();
        assert null == parent.spanId.getParentSpanId() : parent.spanId.getParentSpanId();
        assert parentOperationName.equals(parent.operationName) : "span.operationName was " + parent.operationName;
        assert !parent.parent.isPresent();
        assert !parent.serverTracer.isPresent();
        assert parent.baggage.isEmpty();

        builder = (BraveSpanBuilderImpl) tracer.buildSpan(operationName1).asChildOf((Span)parent);
        BraveSpanImpl first = builder.createSpan();

        assert operationName1.equals(first.operationName) : "span.operationName was " + first.operationName;
        assertChildToParent(first, parent, false);

        first.finish();
        builder = (BraveSpanBuilderImpl) tracer.buildSpan(operationName2).asChildOf((Span)parent);
        BraveSpanImpl second = builder.createSpan();

        assert operationName2.equals(second.operationName) : "span.operationName was " + second.operationName;
        assertChildToParent(second, parent, false);

        second.finish();
        parent.finish();
    }

    @Test
    public void test_buildSpan_notChild_seperate_traces() {
        String parentOperationName = "test-test_buildSpan_notChild_seperate_traces-parent";
        String operationName = "test-test_buildSpan_notChild_seperate_traces";
        BraveTracerImpl tracer = new BraveTracerImpl();
        tracer.brave.serverTracer().clearCurrentSpan();
        BraveSpanBuilderImpl builder = (BraveSpanBuilderImpl) tracer.buildSpan(parentOperationName);

        assert parentOperationName.equals(builder.operationName) : builder.operationName;
        assert null == builder.parentSpanId : builder.parentSpanId;
        assert builder.references.isEmpty();
        assert null == builder.serverTracer : builder.serverTracer;
        assert builder.start.isBefore(Instant.now().plusMillis(1));
        assert null == builder.traceId : builder.traceId;

        BraveSpanImpl first = builder.createSpan();

        assert null != first.spanId;
        assert 0 != first.spanId.getSpanId() : first.spanId.getSpanId();
        assert 0 != first.spanId.getTraceId() : first.spanId.getTraceId();
        assert null == first.spanId.getParentSpanId() : first.spanId.getParentSpanId();
        assert parentOperationName.equals(first.operationName) : "span.operationName was " + first.operationName;
        assert !first.parent.isPresent();
        assert !first.serverTracer.isPresent();
        assert first.baggage.isEmpty();

        builder = (BraveSpanBuilderImpl) tracer.buildSpan(operationName);
        BraveSpanImpl second = builder.createSpan();

        assert null != second.spanId;
        assert 0 != second.spanId.getSpanId() : second.spanId.getSpanId();
        assert 0 != second.spanId.getSpanId() : second.spanId.getSpanId();
        assert 0 != second.spanId.getTraceId() : second.spanId.getTraceId();

        assert first.spanId.getTraceId() != second.spanId.getTraceId()
                : "child: " + first.spanId.getTraceId() + " ; parent: " + second.spanId.getTraceId();

        assert null == second.spanId.getParentSpanId() : second.spanId.getParentSpanId();
        assert operationName.equals(second.operationName) : "span.operationName was " + second.operationName;
        assert !first.parent.isPresent();
        assert !second.serverTracer.isPresent();
        assert second.baggage.isEmpty();

        first.finish();
        second.finish();
    }

    @Test
    public void testGetTraceState() {
        String operationName = "test-testGetTraceState";
        BraveTracerImpl tracer = new BraveTracerImpl();

        Optional<Span> parent = Optional.empty();
        Instant start = Instant.now();
        Optional<ServerTracer> serverTracer = Optional.empty();

        BraveSpanImpl span = BraveSpanImpl.create(tracer.brave, operationName, parent, start, serverTracer);

        assert tracer.getTraceState(span).containsKey(BraveHttpHeaders.TraceId.getName());
        assert tracer.getTraceState(span).containsKey(BraveHttpHeaders.SpanId.getName());
        assert tracer.getTraceState(span).containsKey(BraveHttpHeaders.Sampled.getName());

        assert tracer.getTraceState(span).get(BraveHttpHeaders.TraceId.getName())
                .equals(IdConversion.convertToString(span.spanId.getTraceId()));

        assert tracer.getTraceState(span).get(BraveHttpHeaders.SpanId.getName())
                .equals(IdConversion.convertToString(span.spanId.getSpanId()));

        span.finish();
    }

    @Test
    public void testGetBaggage() {
        String operationName = "test-testGetBaggage";
        BraveTracerImpl tracer = new BraveTracerImpl();

        BraveSpanBuilderImpl builder = (BraveSpanBuilderImpl) tracer.buildSpan(operationName);

        Span span = builder.withBaggageItem("baggage-key-1", "baggage-value-1").start();

        assert tracer.getBaggage(span).containsKey("baggage-key-1");
        assert "baggage-value-1".equals(tracer.getBaggage(span).get("baggage-key-1"));

        span.finish();
    }

    @Test
    public void testInject() {
        String operationName = "test-testInject";
        BraveTracerImpl tracer = new BraveTracerImpl();
        BraveSpanBuilderImpl builder = (BraveSpanBuilderImpl) tracer.buildSpan(operationName);

        assert operationName.equals(builder.operationName) : builder.operationName;
        assert null == builder.parentSpanId : builder.parentSpanId;
        assert builder.references.isEmpty();
        assert null == builder.serverTracer : builder.serverTracer;
        assert builder.start.isBefore(Instant.now().plusMillis(1));
        assert null == builder.traceId : builder.traceId;

        BraveSpanImpl span = builder.createSpan();

        assert null != span.spanId;
        assert 0 != span.spanId.getSpanId() : span.spanId.getSpanId();
        assert 0 != span.spanId.getTraceId() : span.spanId.getTraceId();
        assert null == span.spanId.getParentSpanId() : span.spanId.getParentSpanId();
        assert operationName.equals(span.operationName) : "span.operationName was " + span.operationName;
        assert !span.parent.isPresent();
        assert !span.serverTracer.isPresent();
        assert span.baggage.isEmpty();

        Map<String,String> map = new HashMap<>();
        TextMapInjectAdapter adapter = new TextMapInjectAdapter(map);
        tracer.inject(span, Format.Builtin.TEXT_MAP, adapter);

        assert map.containsKey(BraveHttpHeaders.TraceId.getName());
        assert map.containsKey(BraveHttpHeaders.SpanId.getName());

        span.finish();
    }

    @Test
    public void testExtract() {

        Map<String,String> map = new HashMap<String,String>() {{
            put(BraveHttpHeaders.Sampled.getName(), "1");
            put(BraveHttpHeaders.TraceId.getName(), "123");
            put(BraveHttpHeaders.SpanId.getName(), "234");
        }};

        TextMapExtractAdapter adapter = new TextMapExtractAdapter(map);
        BraveTracerImpl tracer = new BraveTracerImpl();
        BraveSpanBuilderImpl builder = (BraveSpanBuilderImpl) tracer.extract(Format.Builtin.TEXT_MAP, adapter);

        assert 291 == builder.traceId : builder.traceId;
        assert 564 == builder.parentSpanId : builder.parentSpanId;
    }

    @Test
    public void test_stack() throws InterruptedException {

        BraveTracerImpl tracer = new BraveTracerImpl();
        tracer.brave.serverTracer().clearCurrentSpan();

        long start = System.currentTimeMillis() - 10000;

        // start a span
        try ( Span span0 = tracer.buildSpan("span-0")
                .withStartTimestamp(start)
                .withTag("description", "top level initial span in the original process")
                .start() ) {


            try ( Span span1 = tracer.buildSpan("span-1")
                    .withStartTimestamp(start +100)
                    .asChildOf(span0)
                    .withTag("description", "the first inner span in the original process")
                    .start() ) {

                assertChildToParent((BraveSpanImpl) span1, (BraveSpanImpl) span0, false);

                try ( Span span2 = tracer.buildSpan("span-2")
                        .withStartTimestamp(start +200)
                        .asChildOf(span1)
                        .withTag("description", "the second inner span in the original process")
                        .start() ) {

                    assertChildToParent((BraveSpanImpl) span2, (BraveSpanImpl) span1, false);

                    // cross process boundary
                    Map<String,String> map = new HashMap<>();
                    tracer.inject(span2.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(map));

                    try ( Span span3 = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(map))
                            .withStartTimestamp(start +300)
                            .withTag("description", "the third inner span in the second process")
                            .start() ) {

                        assertChildToParent((BraveSpanImpl) span3, (BraveSpanImpl) span2, true);

                        try ( Span span4 = tracer.buildSpan("span-4")
                                .withStartTimestamp(start +400)
                                .asChildOf(span3)
                                .withTag("description", "the fourth inner span in the second process")
                                .start() ) {

                            assertChildToParent((BraveSpanImpl) span4, (BraveSpanImpl) span3, false);

                            // cross process boundary
                            map = new HashMap<>();
                            tracer.inject(span4.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(map));

                            try ( Span span5 = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(map))
                                    .withStartTimestamp(start +500)
                                    .withTag("description", "the fifth inner span in the third process")
                                    .start() ) {

                                assertChildToParent((BraveSpanImpl) span5, (BraveSpanImpl) span4, true);

                                try ( Span span6 = tracer.buildSpan("span-6")
                                        .withStartTimestamp(start +600)
                                        .asChildOf(span5)
                                        .withTag("description", "the sixth inner span in the third process")
                                        .start() ) {

                                    assertChildToParent((BraveSpanImpl) span6, (BraveSpanImpl) span5, false);

                                    try ( Span span7 = tracer.buildSpan("span-7")
                                            .withStartTimestamp(start +700)
                                            .asChildOf(span6)
                                            .withTag("description", "the seventh span in the third process")
                                            .start() ) {

                                        assertChildToParent((BraveSpanImpl) span7, (BraveSpanImpl) span6, false);

                                        // cross process boundary
                                        map = new HashMap<>();

                                        tracer.inject(
                                                span7.context(),
                                                Format.Builtin.TEXT_MAP,
                                                new TextMapInjectAdapter(map));

                                        try ( Span span8 = tracer
                                                .extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(map))
                                                .withStartTimestamp(start +800)
                                                .withTag("description", "the eight inner span in the fourth process")
                                                .start() ) {

                                            assertChildToParent((BraveSpanImpl) span8, (BraveSpanImpl) span7, true);

                                            try ( Span span9 = tracer.buildSpan("span-9")
                                                    .withStartTimestamp(start +900)
                                                    .asChildOf(span8)
                                                    .withTag("description", "the ninth inner span in the fouth process")
                                                    .start() ) {

                                                assertChildToParent((BraveSpanImpl) span9, (BraveSpanImpl) span8, false);
                                                Thread.sleep(10);
                                            }
                                            Thread.sleep(10);
                                        }
                                        Thread.sleep(10);
                                    }
                                    Thread.sleep(10);
                                }
                                Thread.sleep(10);
                            }
                            Thread.sleep(10);
                        }
                        Thread.sleep(10);
                    }
                    Thread.sleep(10);
                }
                Thread.sleep(10);
            }
            Thread.sleep(10);
        }
    }

    private void assertChildToParent(BraveSpanImpl span, BraveSpanImpl parent, boolean extracted) {
        assert null != span.spanId;
        assert 0 != span.spanId.getSpanId() : span.spanId.getSpanId();

        assert parent.spanId.getTraceId() == span.spanId.getTraceId()
                : "parent: " + parent.spanId.getTraceId() + " ; child: " + span.spanId.getTraceId();

        assert parent.spanId.getSpanId() == span.spanId.getParentSpanId()
                : "parent: " + parent.spanId.getSpanId() + " ; child: " + span.spanId.getParentSpanId();

        assert extracted || span.parent.isPresent();
        assert extracted || span.parent.get().equals(parent);
        assert !extracted || span.serverTracer.isPresent();
        assert span.baggage.isEmpty();
    }
}
