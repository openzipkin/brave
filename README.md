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

The above image shows how Brave integrates with Zipkin back-end components. This is probably what you want
to do as those components have already proven themselves. However you can 
create new SpanCollector implementations that submit Span/Trace data to other data stores or processing
engines.


## Example implementation ##

The [brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example) is a good starting point 
to get you up to speed on how you can implement brave in your own apps.

## Maven artifacts ##

Version 2.0 is available in Maven central. So you can simply add the dependencies you want
 to your pom.xml. You will need at least:

    
    <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-impl</artifactId>
        <version>2.0</version>
    </dependency>
    
For other dependencies see README.md files for sub modules.
   

## Version history ##

### 2.0.1-SNAPSHOT ###

*  Done: Adapt ZipkinSpanCollector so it is able to reconnect to Scribe / Zipkin Collector in case of temporary network connectivity issues. 
*  Done: Adapt Flume ZipkinSpanCollectorSink so it is able to reconnect to Zipkin Collector in case of temporary network connectivity issues.

### 2.0 ###

Brave 1.0 was an alternative implementation of Dapper with integration with the Zipkin 
backend components.
Brave 2.0 has the Zipkin core thrift classes as part of its api. This has as consequence that it will be easier to share 
components or extensions between Zipkin/Brave. It is compatible with Zipkin 1.1

Implemented changes:

*   Done: Use Zipkin-core thrift generated classes as part of api.
*   Done: Binary annotation support.
*   Done: Cut dependencies with Twitter specific libraries. Only rely on Thrift.
*   Done: Performance and through-put optimizations for [zipkin-span-collector](https://github.com/kristofa/brave/tree/master/brave-zipkin-spancollector). It uses queue to store spans and separate thread for submitting spans to span collector / scribe. 
It tries to buffer spans to send them in batches to avoid communication overhead.
*   Done: Make sampling / trace filtering work properly. See [here](https://github.com/kristofa/brave/tree/master/brave-impl) for details.
*   Done: Add [flume](http://flume.apache.org) support for transporting spans. See [flume-zipkin-collector-sink](https://github.com/kristofa/brave/tree/master/flume-zipkin-collector-sink). 
*   Done: Add TraceFilter implementation that uses Zookeeper for globally adjusting sample rate or enable/disable tracing all together. See [brave-tracefilters](https://github.com/kristofa/brave/tree/master/brave-tracefilters)
*   Done: Extend threading support by introducing BraveExecutorService, BraveCallable and BraveRunnable.


### 1.0 ###

*   Functional but not usable for production usage, only for development and testing.

