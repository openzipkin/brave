# brave-core #

brave-core contains the core brave implementations used to set up and keep track of
tracing state. 

Since brave 3.0.0 you should use:
 
   * At client side:
      * `com.github.kristofa.brave.ClientRequestInterceptor` : Starts a new span for a new outgoing request. Submits cs annotation.
      * `com.github.kristofa.brave.ClientResponseInterceptor` : Deals with response from request. Submits cr annotation.
   * At server side:
      * `com.github.kristofa.brave.ServerRequestInterceptor` : Intercepts incoming requests. Submits sr annotation.
      * `com.github.kristofa.brave.ServerResponseInterceptor` : Intercepts response for request. Submits ss annotation.

For each of these classes you will have to implement and adapter that will deal with platform specifics.

Because http integration is very common there is a separate module: `brave-http` which builds upon `brave-core`.
You should check this out of you want to do http integrations.

## instantiating interceptor dependencies

To instantiate the client interceptors you will need a `ClientTracer` instance and to
instantiate the server interceptors you will need a `ServerTracer` instance. You can create those as following:

`Brave.getClientTracer(SpanCollector, List<TraceFilter>)`

`Brave.getServerTracer(SpanCollector, List<TraceFilter>)`

### SpanCollector ###

The `SpanCollector` will receive all spans and can do with them whatever needed. You can create your own
implementations but have some implementations that you can use:

   * `LoggingSpanCollector` : Part of brave-core. This implementation will simply log the spans using 'java.util.Logger' (INFO log level).
   * `EmptySpanCollector` : Part of brave-core. Does nothing.
   * `ZipkinSpanCollector` : Part of `brave-zipkin-spancollector` module. Span collector that supports sending spans directly to `zipkin-collector` service or Scribe.

### TraceFilter ###

You might not want to trace all requests that are being submitted:

   * to avoid performance overhead
   * to avoid running out of storage

and you don't need to trace all requests to come to usable data.

A TraceFilter's purpose (`com.github.kristofa.brave.TraceFilter`) is to decide if a given 
request should get traced or not. Both
the ClientTracer and ServerTracer take a List of TraceFilters which should be the same
and before starting with a new Span they will check the TraceFilters. If one of 
the TraceFilters says we should not trace the request it will not be traced.

The decision, should trace (true) or not (false) is taken by the first request and should
be passed through to all subsequent requests. This has as a consequence that we either
trace a full request tree or none of the requests at all which is good. We don't want incomplete traces.

There is a TraceFilter implementation that comes with brave-core which is 
`com.github.kristofa.brave.FixedSampleRateTraceFilter`. This 
TraceFilter is created with a fixed sample rate provided through its constructor. The
sample rate can't be adapted at run time.  Behaviour:

*   sample rate <= 0 : Non of the requests will be traced. Means tracing is disabled
*   sample rate = 1 : All requests will be traced.
*   sample rate > 1 : For example 3, every third request will be traced.

If you want to use a TraceFilter implementation which allows adapting sample rate at run
time see `brave-tracefilters` project which contains a TraceFilter with ZooKeeper support.



## about EndpointSubmitter ##

Each Annotation part of a Span can have an Endpoint assigned. 
The Endpoint specifies the service/application from which the annotation is submitted, identified by
ip address, port, service name.

During the lifecycle of an application the Endpoint is fixed. This means the Endpoint can
be set up once ideally when initialising your application.

Initialising the Endpoint for Brave is done through the EndpointSubmitter.  Once it is set up all
annotations submitted through ClientTracer, ServerTracer or AnnotationSubmitter will use the Endpoint.

In the brave-resteasy-spring project the Endpoint is set up in `BravePreProcessInterceptor` when it
receives the first request so before any annotations are submitted.


## brave-core public api ##

All api access is centralized in `com.github.kristofa.brave.Brave`.

This class contains only static methods. Reason is that the returned components should
share the same trace/span state. This state is maintained as a static singleton in this
class.

### Brave.getEndpointSubmitter ##

> public static EndpointSubmitter getEndpointSubmitter()

Each annotation that is being submitted (including cs, cr, sr, ss) has an endpoint 
(host, port, service name) assigned. For a given service/application instance the endpoint 
only needs to be set once and will be reused for all submitted annotations.

The Endpoint should be set using the EndpointSubmitter before any annotation/span is
created.  

In the brave-resteasy-spring module the Endpoint is set in 
`com.github.kristofa.brave.resteasy.BravePreProcessInterceptor` when receiving the first
request.

### Brave.getClientTracer ###

> public static ClientTracer getClientTracer(final SpanCollector collector, final List<TraceFilter> traceFilter)

Get a ClientTracer that will be initialized with a specific SpanCollector and a List of custom TraceFilters.

The ClientTracer is used to initiate a new span when doing a request to another service. It will generate the cs 
(client send) and cr (client received) annotations. When the cr annotation is set the span 
will be submitted to SpanCollector if not filtered by one of the TraceFilters.

For more information on TraceFilters, see earlier section 'about trace filters'.


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
To bind the same state to your new Thread you can use the `ServerSpanThreadBinder` yourself as explained earlier
or you can use `com.github.kristofa.brave.BraveExecutorService`.

BraveExecutorService implements the java.util.concurrent.ExecutorService interface and acts as a decorator for
an existing ExecutorService.  It also uses the ServerSpanThreadBinder which it takes in its constructor but
once set up if is transparent for your code and will make sure any thread you start through the ExecutorService
will get proper trace/span state.

Instead of using BraveExecutorService or the ServerSpanThreadBinder directly you can also
use the `BraveCallable` and `BraveRunnable`. These are used internally by the BraveExecutorService.
