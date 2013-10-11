package com.github.kristofa.brave;

import java.util.List;
import java.util.Random;

public class BraveContext {
	private final SimpleServerAndClientSpanStateImpl spanState;
	
	private final Random randomGenerator;
	
	private final EndPointSubmitter endpointSubmitter;
	
	private final AnnotationSubmitter annotationSubmitter;
	
	public BraveContext(){
		spanState = new SimpleServerAndClientSpanStateImpl();
		randomGenerator   = new Random();
		endpointSubmitter = new EndPointSubmitterImpl(spanState);
		annotationSubmitter = new AnnotationSubmitterImpl(spanState);
	}
	
	public EndPointSubmitter getEndPointSubmitter() {
        return endpointSubmitter;
    }
	
	public ClientTracer getClientTracer(final SpanCollector collector, final List<TraceFilter> traceFilters) {
        return new ClientTracerImpl(spanState, randomGenerator, collector, traceFilters);
    }
	
	public ServerTracer getServerTracer(final SpanCollector collector, final List<TraceFilter> traceFilters) {
        return new ServerTracerImpl(spanState, randomGenerator, collector, traceFilters);
    }
	
	public AnnotationSubmitter getServerSpanAnnotationSubmitter() {
        return annotationSubmitter;
    }
}
