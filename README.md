# brave #


Java implementation of [Zipkin](https://github.com/twitter/zipkin/).

Zipkin is based on [Dapper](http://research.google.com/pubs/pub36356.html).

dapper (dutch) = brave (english)... so that's where the name comes from.

## introduction ##

I advise you to read the [Dapper](http://research.google.com/pubs/pub36356.html) paper, but in
short:

> What we want to achieve is understand system behavior and performance of complex distributed systems.
> We want to do this with minimal impact on existing code by introducing some small common libraries that
> are reusable and don't interfere with the existing business logic or architecture. Besides not impacting
> business logic or architecute we off course also want it to have a neglectable impact on performance.

I looked into reusing zipkin 'as is' but did not find an elegant way to use the exising Scala code/api's 
into the Java/Spring code I want to incorporate it.  

However Brave uses the Zipkin thrift generated classes as part of its api so it is easy to use existing
Zipkin components with Brave (zipkin-collector, zipkin-query, zipkin-ui, cassandra store,...). 

I'm very thankful to Twitter for open sourcing
Zipkin! Is is by seeing their [Zipkin video and presentation](http://www.infoq.com/presentations/Zipkin) that
I got to know Zipkin/Dapper and that I saw the potential and the simplicity of the solution.


## about spans and traces ##

*   span: A single client/server request/response. Can have an optional parent span id and is part of a trace.
*   trace: A tree of spans.


![Distributed tracing overview](https://raw.github.com/wiki/kristofa/brave/distributed_tracing.png)

So as you can see a single span is submitted twice:

*   from the client side, the initiator, with cs (client send) and cr (client received) annotations 
*   from the server side with sr (server received) and ss (server send) annotations.

The above image shows how Brave integrates with Zipkin. This is an option but not required. You can
create new SpanCollector implementations that submit Span/Trace data to other data stores or processing
engines.

## brave-impl public api ##

All api access is centralized in com.github.kristofa.brave.Brave

This class contains only static methods. Reason is that the returned components should
share the same span state which is maintained as a singleton in com.github.kristofa.brave.Brave.

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

> public static ClientTracer getClientTracer(final SpanCollector collector, final TraceFilter traceFilter)

Get a ClientTracer that will be initialized with a specific SpanCollector and a custom TraceFilter.

The ClientTracer is used to initiate a new span when doing a request to another service. It will generate the cs 
(client send) and cr (client received) annotations. When the cr annotation is set the span 
will be submitted to SpanCollector if not filtered by TraceFilter.


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

## Example implementation ##

The [brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example) is a good starting point 
to get you up to speed on how you can implement brave in your own apps.

## RESTEasy integration ##

The brave-resteasy-spring module has RESTEasy pre- and postprocess interceptor implementations
that use the ServerTracer to set up the span state.

There is a separate example application that shown how to set up and configure the
RESTEAsy integration using Spring -> [https://github.com/kristofa/brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example)

brave-resteasy-spring puts the Spring and RESTEasy dependencies to scope provided which means you have to
add the dependencies to your own application. Important to know is that you should use a recent RESTEasy version otherwise
the integration might not work. It does for example not work with RESTEasy 2.2.1.GA. It does work with 2.3.5.Final which is
also used in brave-resteasy-example. When it comes to Spring the oldest version I tried out and which worked was 3.0.5. 

## Zipkin integration ##

There is a ZipkinSpanCollector which converts the Brave spans to Zipkin flavour and submits them
to the Zipkin SpanCollector. See [https://github.com/kristofa/brave-zipkin-spancollector](https://github.com/kristofa/brave-zipkin-spancollector).

## Maven artifacts ##

Version 1.0 is available in Maven central. So you can simple add the dependencies you need to your pom.xml:


    <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-interfaces</artifactId>
        <version>1.0</version>
    </dependency>
    <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-impl</artifactId>
        <version>1.0</version>
    </dependency>
     <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-resteasy-spring</artifactId>
        <version>1.0</version>
    </dependency>

## Version history ##

### 2.0-SNAPSHOT ###

Brave 1.0 was an alternative implementation of Dapper with Zipkin compatibility.
Brave 2.0-SNAPSHOT is a Java implementation of Zipkin. The Zipkin thrift classes are part 
of the public api of Brave. This has as consequence that it will be easier to share 
components or extensions between Zipkin/Brave. 


*   Binary annotation support
*   TODO: Zookeeper support to enable/disable tracing and update sample rate globally for all services.
*   TODO: Add [logstash](http://logstash.net) support for routing spans.


### 1.0 ###

*   Fully functional but might not be ideal for large scale production usage.

