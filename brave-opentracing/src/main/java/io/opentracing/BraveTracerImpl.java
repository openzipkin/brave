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

import java.util.Map;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.BraveHttpHeaders;

import io.opentracing.propagation.Format;
import java.util.HashMap;

public final class BraveTracerImpl extends AbstractTracer {

    final Brave brave;

    public BraveTracerImpl() {
        this(new Brave.Builder());
    }

    public BraveTracerImpl(Brave.Builder builder) {
        brave = builder.build();
    }

    @Override
    BraveSpanBuilderImpl createSpanBuilder(String operationName) {
        return BraveSpanBuilderImpl.create(brave, operationName);
    }

    @Override
    Map<String, Object> getTraceState(SpanContext spanContext) {
        Span span = (Span)spanContext;

        return new HashMap<String,Object>() {{
            SpanId spanId = ((BraveSpanImpl)span).spanId;
            put(BraveHttpHeaders.Sampled.getName(), "1");
            put(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(spanId.getTraceId()));
            put(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(spanId.getSpanId()));
            if (null != spanId.getParentSpanId()) {
                put(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(spanId.getParentSpanId()));
            }
        }};
    }

    Map<String, String> getBaggage(Span span) {
        return ((BraveSpanImpl)span).baggage;
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        brave.clientTracer().startNewSpan(((BraveSpanImpl)spanContext).operationName);
        brave.clientTracer().setClientSent();
        super.inject(spanContext, format, carrier);
        ((BraveSpanImpl)spanContext).setClientTracer(brave.clientTracer());
    }

    @Override
    public <C> SpanBuilder extract(Format<C> format, C carrier) {

        BraveSpanBuilderImpl builder = (BraveSpanBuilderImpl) super.extract(format, carrier);

        if (null != builder.traceId
                && null != builder.parentSpanId
                && null != builder.operationName) {

            brave.serverTracer().setStateCurrentTrace(
                    builder.traceId,
                    builder.parentSpanId,
                    null,
                    builder.operationName);

            brave.serverTracer().setServerReceived();
            builder.withServerTracer(brave.serverTracer());
            return builder;
        }
        return NoopSpanBuilder.INSTANCE;
    }

}
