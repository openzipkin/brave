# brave-impl #

Latest release available in Maven central:

    <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-impl</artifactId>
        <version>2.0.2</version>
    </dependency>


brave-impl contains implementations of the components used to set up and keep track of
tracing state. It has a ClientTracer implementation which is used to set up new spans when
invoking new service requests and it has a ServerTracer implementation used to set up 
existing span state when receiving service requests.  

The [brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example) is a 
good starting point to get you up to speed on how you can implement brave in your 
own apps.

## sequence diagram ##

![Client and ServerTracer usage.](https://raw.github.com/wiki/kristofa/brave/brave-client-servertracer-usage.png)

The sequence diagram shows a service that executes a request to another service.
It shows interaction with the brave Client- , ServerTracer and SpanCollector.

The actions taking place in both services are also indicated by the coloured boxes. The
blue box indicates the caller service, the green box indicates the callee. Typically both
services run in separate JVM.

Some explanation:

*    002 : The ClientTracer can decide if we should trace the new request or not. It will
internally use 1 or more TraceFilter implementations which will decide on tracing or not. 
In case we should not trace the request null will be returned. Otherwise the new trace id,
span id and parent span id will be returned.
*    004 / 005 / 007 : We need to forward the trace state to the service which we call. When
using HTTP this is typically done by adding specific HTTP headers. When using other protocols the
trace data should be submitted in another way.
*    012 : In case we receive a request without or with incomplete tracing state the ServerTracer will
just as the ClientTracer decide if we should trace the current request or not by using 1 or more 
TraceFilter implementations.
*    016 : If the ServerTracer receives 'Server Send' and it has an active span it will add the 
Server Send annotation to the span and submit it to the SpanCollector.
*    019 : If the ClientTracer receives 'Client Received' and it has an active span it will add the
Client Received annotation to the span and submit it to the SpanCollector.

The trace/span state is maintained in a ThreadLocal variable so parallel requests where each
request is executed in a separate thread are supported.

The sequence diagram does not show this but off course typically a service receives requests and
execute new requests. In that case the ClientTracer and ServerTracer logic exists in the 
same JVM/Service.  In this case the span state between Server/Client Tracer is shared. A
new client request will use the incoming request as parent. Also the 
Client and Server Tracers should use the same TraceFilter(s) and SpanCollector.

The ServerTracer logic (green box) is implemented in brave-resteasy-spring project for
integration in a RestEasy service.

Code example of the ClientTracer logic (blue box) can be found in brave-resteasy-example.

## about EndPointSubmitter ##

Each Annotation part of a Span can have an EndPoint assigned. 
The EndPoint specifies the service/application from which the annotation is submitted, identified by
ip address, port, service name.

During the lifecycle of an application the EndPoint is fixed. This means the Endpoint can
be set up once ideally when initialising your application.

Initialising the Endpoint for Brave is done through the EndPointSubmitter.  Once it is set up all
annotations submitted through ClientTracer, ServerTracer or AnnotationSubmitter will use the EndPoint.

In the brave-resteasy-spring project the EndPoint is set up in `BravePreProcessInterceptor` when it
receives the first request so before any annotations are submitted.

## about span collectors ##

All spans that are submitted by brave end up in a SpanCollector of your choice 
(com.github.kristofa.brave.SpanCollector).

A SpanCollector is responsible for receiving spans and acting upon them. There are 2 
SpanCollector implementations part of brave-impl: 

*    `com.github.kristofa.brave.LoggingSpanCollectorImpl` : This SpanCollector simply logs spans through log4j. This can be used for testing / during development.
*    `com.github.kristofa.brave.EmptySpanCollectorImpl` : This SpanCollector does nothing with the spans it receives. Can be used when disabling tracing.

The most interesting SpanCollector is the ZipkinSpanCollector. This one can be found in 
[brave-zipkin-span-collector](https://github.com/kristofa/brave/tree/master/brave-zipkin-spancollector) project.


## about trace filters ##

You might not want to trace all requests that are being submitted:

*   to avoid performance overhead
*   to avoid running out of storage

and you don't need to trace all requests to come to usable data.

A TraceFilter's purpose (`com.github.kristofa.brave.TraceFilter`) is to decide if a given 
request should get traced or not. Both
the ClientTracer and ServerTracer take a List of TraceFilters which should be the same
and before starting with a new Span they will check the TraceFilters. If one of 
the TraceFilters says we should not trace the request it will not be traced.

The decision, should trace (true) or not (false) is taken by the first request and should
be passed through to all subsequent requests. This has as a consequence that we either
trace a full request tree or none of the requests at all which is good. We don't want incomplete traces.

There is a TraceFilter implementation that comes with brave-impl which is 
`com.github.kristofa.brave.FixedSampleRateTraceFilter`. This 
TraceFilter is created with a fixed sample rate provided through its constructor. The
sample rate can't be adapted at run time.  Behaviour:

*   sample rate <= 0 : Non of the requests will be traced. Means tracing is disabled
*   sample rate = 1 : All requests will be traced.
*   sample rate > 1 : For example 3, every third request will be traced.

If you want to use a TraceFilter implementation which allows adapting sample rate at run
time see `brave-tracefilters` project which contains a TraceFilter with ZooKeeper support.


## brave-impl public api ##

All api access is centralized in `com.github.kristofa.brave.Brave`.

This class contains only static methods. Reason is that the returned components should
share the same trace/span state. This state is maintained as a static singleton in this
class.

### Brave.getEndPointSubmitter ###

> public static EndPointSubmitter getEndPointSubmitter()

Each annotation that is being submitted (including cs, cr, sr, ss) has an endpoint 
(host, port, service name) assigned. For a given service/application instance the endpoint 
only needs to be set once and will be reused for all submitted annotations.

The EndPoint should be set using the EndPointSubmitter before any annotation/span is
created.  

In the brave-resteasy-spring module the EndPoint is set in 
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