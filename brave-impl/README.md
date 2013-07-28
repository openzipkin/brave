# brave-impl #

brave-impl contains implementations of the components used to set up and keep track of
tracing state. It has a ClientTracer implementation which is used to set up new spans when
invoking new service requests and it has a ServerTracer implementation used to set up 
existing span state.  

The [brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example) is a 
good starting point to get you up to speed on how you can implement brave in your 
own apps.


## brave-impl public api ##

All api access is centralized in com.github.kristofa.brave.Brave

This class contains only static methods. Reason is that the returned components should
share the same trace/span state which is maintained as a singleton in com.github.kristofa.brave.Brave.

### Brave.getEndPointSubmitter ###

> public static EndPointSubmitter getEndPointSubmitter()

Each annotation that is being submitted (including cs, cr, sr, ss) has an endpoint 
(host, port, service name) assigned. For a given service/application instance the endpoint 
only needs to be set once and will be reused for all submitted annotations.

The EndPoint needs to be set using the EndPointSubmitter before any annotation/span is
created.

In the brave-resteasy-spring module the EndPoint is set in 
com.github.kristofa.brave.resteasy.BravePreProcessInterceptor.

### Brave.getClientTracer ###

> public static ClientTracer getClientTracer(final SpanCollector collector, final List<TraceFilter> traceFilter)

Get a ClientTracer that will be initialized with a specific SpanCollector and a List of custom TraceFilters.

The ClientTracer is used to initiate a new span when doing a request to another service. It will generate the cs 
(client send) and cr (client received) annotations. When the cr annotation is set the span 
will be submitted to SpanCollector if not filtered by one of the TraceFilters.


### Brave.getServerTracer ###

> public static ServerTracer getServerTracer(final SpanCollector collector)

Get a ServerTracer that will be initialized with a specific SpanCollector.
The ServerTracer and ClientTracer should share the same SpanCollector.

The ServerTracer will generate sr (server received) and ss (server send) annotations. When ss annotation is set
the span will be submitted to SpanCollector if our span needs to get traced (as decided by ClientTracer).

The ServerTracer sets the span state for an incoming request. You can see how it is
used in the brave-resteasy-spring module in the com.github.kristofa.brave.resteasy.BravePreProcessInterceptor
and the com.github.kristofa.brave.resteasy.BravePostProcessInterceptor

### Brave.getLoggingSpanCollector ###

> public static SpanCollector getLoggingSpanCollector

Returns a SpanCollector that will log the collected span through sl4j. Can be used during
testing or debugging.

### Brave.getTraceAllTraceFilter ###

> public static TraceFilter getTraceAllTraceFilter()

Returns a TraceFilter that will trace all requests. To be used when debugging or
during development.

### Brave.getServerSpanAnnotationSubmitter ###

> public static AnnotationSubmitter getServerSpanAnnotationSubmitter()

The AnnotationSubmitter is used to submit application specific annotations.

### Brave.getServerSpanThreadBinder ###

> public static ServerSpanThreadBinder getServerSpanThreadBinder()

To be used in case you execute logic in new threads within you service and if you submit 
annotations or new requests from those threads.
The span state is bound to the request thread. When you start new threads it means
that the span state that was set in the request thread is not available in those new
threads. The ServerSpanThreadBinder allows you to bind the original span state to the
new thread.
