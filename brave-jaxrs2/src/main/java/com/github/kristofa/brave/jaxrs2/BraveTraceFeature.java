package com.github.kristofa.brave.jaxrs2;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;

public class BraveTraceFeature implements Feature {

    private Brave brave;
    private SpanNameProvider spanNameProvider;

    public BraveTraceFeature(Brave brave) {
        this(brave, new DefaultSpanNameProvider());
    }
    
    public BraveTraceFeature(Brave brave, SpanNameProvider spanNameProvider) {
        this.brave = brave;
        this.spanNameProvider = spanNameProvider;
    }

    public boolean configure(FeatureContext context) {
        context.register(new BraveClientRequestFilter(spanNameProvider, brave.clientRequestInterceptor()));
        context.register(new BraveClientResponseFilter(brave.clientResponseInterceptor()));
        context.register(new BraveContainerRequestFilter(brave.serverRequestInterceptor(), spanNameProvider));
        context.register(new BraveContainerResponseFilter(brave.serverResponseInterceptor()));
        return true;
    }

}
