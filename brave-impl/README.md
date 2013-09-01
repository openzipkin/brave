# brave-impl #

brave-impl contains implementations of the components used to set up and keep track of
tracing state. It has a ClientTracer implementation which is used to set up new spans when
invoking new service requests and it has a ServerTracer implementation used to set up 
existing span state when receiving service requests.  

The [brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example) is a 
good starting point to get you up to speed on how you can implement brave in your 
own apps.

## about span collectors ##

All spans that are submitted by brave end up in a SpanCollector of your choice 
(com.github.kristofa.brave.SpanCollector).

A SpanCollector is responsible for receiving spans and acting upon them. There is 1 
SpanCollector implementation part of brave-impl: com.github.kristofa.brave.LoggingSpanCollectorImpl.
This SpanCollector simply logs spans through log4j. This can be used for testing / during development.

The most interesting SpanCollector is the ZipkinSpanCollector, this can be found in 
brave-zipkin-spancollector project.


## about trace filters ##

You might not want to trace all requests that are being submitted:

*   to avoid performance overhead
*   to avoid running out of storage

and having a big enough sample might be good enough.

A TraceFilter's purpose (com.github.kristofa.brave.TraceFilter) is to decide if a given 
request should get traced or not. Both
the ClientTracer and ServerTracer take a List of TraceFilters which should be the same
and before starting with a new Span they will check the TraceFilters. If one of 
the TraceFilters says we should not trace the request it will not be traced.

The decision, should trace (true) or not (false), is taken by the first request and should
be passed through to all subsequent requests. This has as a consequence that we either
trace a full request tree or none of the requests at all which is actually good.

There is a TraceFilter implementation that comes with brave-impl which is 
com.github.kristofa.brave.FixedSampleRateTraceFilter. This 
TraceFilter is created with a fixed sample rate provided through its constructor. The
sample rate can't be adapted at run time.  Behaviour:

*   sample rate <= 0 : Non of the requests will be traced.
*   sample rate = 1 : All requests will be traced.
*   sample rate > 1 : For example 3, every third request will be traced.

If you want to use a TraceFilter implementation which allows adapting sample rate at run
time see brave-tracefilters project which contains a TraceFilter with ZooKeeper support.


## brave-impl public api ##

All api access is centralized in com.github.kristofa.brave.Brave

This class contains only static methods. Reason is that the returned components should
share the same trace/span state which is maintained as a singleton in com.github.kristofa.brave.Brave.

### Brave.getEndPointSubmitter ###

> public static EndPointSubmitter getEndPointSubmitter()
.
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

For mor information on TraceFilters, see section below 'about trace filters'


### Brave.getServerTracer ###

> public static ServerTracer getServerTracer(final SpanCollector collector, final List<TraceFilter> traceFilter)

Get a ServerTracer that will be initialized with a specific SpanCollector and a List of custom TraceFilters.
The ServerTracer and ClientTracer should share the same SpanCollector and the same TraceFilters!

The ServerTracer will generate sr (server received) and ss (server send) annotations. When ss annotation is set
the span will be submitted to SpanCollector if our span needs to get traced (as decided by ClientTracer).

The ServerTracer sets the span state for an incoming request. You can see how it is
used in the brave-resteasy-spring module in the com.github.kristofa.brave.resteasy.BravePreProcessInterceptor
and the com.github.kristofa.brave.resteasy.BravePostProcessInterceptor


### Brave.getServerSpanAnnotationSubmitter ###

> public static AnnotationSubmitter getServerSpanAnnotationSubmitter()

The AnnotationSubmitter is used to submit application specific annotations.

### Brave.getServerSpanThreadBinder ###

> public static ServerSpanThreadBinder getServerSpanThreadBinder()

To be used in case you execute logic in new threads within you service and if you submit 
annotations or new requests from those threads.
The span state is bound to the request thread using ThreadLocal variable. When you start new threads it means
that the span state that was set in the request thread is not available in those new
threads. The ServerSpanThreadBinder allows you to bind the original span state to the
new thread. See also the section below: 'brave and multi threading'.


## brave and multi threading ##

Brave uses ThreadLocal variables to keep track of trace/span state. By doing this it is
able to support keeping track of state for multiple requests (multiple threads).

However when you start a new Thread yourself from a request Thread brave will lose its trace/span state.
To bind the same state to your new Thread you can use the ServerSpanThreadBinder yourself as explained earlier
or you can use `com.github.kristofa.brave.BraveExecutorService`.

BraveExecutorService implements the java.util.concurrent.ExecutorService interface and acts as a decorator for
an existing ExecutorService.  It also uses the ServerSpanThreadBinder which it takes in its constructor but
once set up if is transparent for your code and will make sure any thread you start through the ExecutorService
will get proper trace/span state.