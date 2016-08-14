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

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.SpanCollector;
import java.time.Instant;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;

public final class BraveSpanImplTest {

    private SpanCollector mockSpanCollector;
    private Brave brave;

    @Before
    public void setup() {
        mockSpanCollector = mock(SpanCollector.class);
        // -1062731775 = 192.168.0.1
        final Brave.Builder builder = new Brave.Builder(-1062731775, 8080, "unknown");
        brave = builder.spanCollector(mockSpanCollector).traceSampler(Sampler.create(1)).build();
    }

    @Test
    public void test() {
        String operationName = "test-test";
        Optional<Span> parent = Optional.empty();
        Instant start = Instant.now();
        Optional<ServerTracer> serverTracer = Optional.empty();

        BraveSpanImpl span = BraveSpanImpl.create(brave, operationName, parent, start, serverTracer);

        assert null != span.spanId;
        assert operationName.equals(span.operationName) : "span.operationName was " + span.operationName;
        assert !span.parent.isPresent();
        assert !span.serverTracer.isPresent();
        assert span.baggage.isEmpty();

        span.finish();

        assert null != span.spanId;
        assert operationName.equals(span.operationName) : "span.operationName was " + span.operationName;
        assert !span.parent.isPresent();
        assert !span.serverTracer.isPresent();
        assert span.baggage.isEmpty();
    }

    @Test
    public void test_withServerTracer() {
        String operationName = "test-test_withServerTracer";
        Optional<Span> parent = Optional.empty();
        Instant start = Instant.now();
        Optional<ServerTracer> serverTracer = Optional.of(brave.serverTracer());

        BraveSpanImpl span = BraveSpanImpl.create(brave, operationName, parent, start, serverTracer);

        assert null != span.spanId;
        assert operationName.equals(span.operationName) : "span.operationName was " + span.operationName;
        assert !span.parent.isPresent();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.baggage.isEmpty();

        span.finish();

        assert null != span.spanId;
        assert operationName.equals(span.operationName) : "span.operationName was " + span.operationName;
        assert !span.parent.isPresent();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.baggage.isEmpty();
    }

    @Test
    public void test_withParent() {
        String operationName = "test-test_withParent";
        Instant start = Instant.now();
        Optional<ServerTracer> serverTracer = Optional.empty();

        Optional<Span> parent = Optional.of(
                BraveSpanImpl.create(brave, operationName, Optional.empty(), start.minusMillis(100), serverTracer));

        BraveSpanImpl span = BraveSpanImpl.create(brave, operationName, parent, start, serverTracer);

        assert null != span.spanId;
        assert operationName.equals(span.operationName) : "span.operationName was " + span.operationName;
        assert span.parent.isPresent();
        assert span.parent.get() == parent.get();
        assert !span.serverTracer.isPresent();
        assert span.baggage.isEmpty();

        span.finish();

        assert null != span.spanId;
        assert operationName.equals(span.operationName) : "span.operationName was " + span.operationName;
        assert span.parent.isPresent();
        assert span.parent.get() == parent.get();
        assert !span.serverTracer.isPresent();
        assert span.baggage.isEmpty();
    }

    @Test
    public void test_withParent_withServerTracer() {
        String operationName = "test-test_withParent_withServerTracer";
        Instant start = Instant.now();
        Optional<ServerTracer> serverTracer = Optional.of(brave.serverTracer());

        Optional<Span> parent = Optional.of(
                BraveSpanImpl.create(brave, operationName, Optional.empty(), start.minusMillis(100), serverTracer));

        BraveSpanImpl span = BraveSpanImpl.create(brave, operationName, parent, start, serverTracer);

        assert null != span.spanId;
        assert operationName.equals(span.operationName) : "span.operationName was " + span.operationName;
        assert span.parent.isPresent();
        assert span.parent.get() == parent.get();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.baggage.isEmpty();

        span.finish();

        assert null != span.spanId;
        assert operationName.equals(span.operationName) : "span.operationName was " + span.operationName;
        assert span.parent.isPresent();
        assert span.parent.get() == parent.get();
        assert span.serverTracer.isPresent();
        assert brave.serverTracer().equals(span.serverTracer.get());
        assert span.baggage.isEmpty();
    }

}
