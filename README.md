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


## about spans, traces and architecture ##

*   span: A single client/server request/response. Can have an optional parent span id and is part of a trace.
*   trace: A tree of spans.


![Distributed tracing overview](https://raw.github.com/wiki/kristofa/brave/distributed_tracing.png)

So as you can see a single span is submitted twice:

*   from the client side, the initiator, with cs (client send) and cr (client received) annotations 
*   from the server side with sr (server received) and ss (server send) annotations.

The above image shows how I intend to use Brave in production and also how it integrates with the Zipkin back-end components. 
I introduced [Flume](http://flume.apache.org/) instead of Scribe as Flume is still actively maintained, easier to deploy,
has good documentation and extensions are written in Java.

The `ZipkinSpanCollectorSink` from `flume-zipkin-collector-sink` module submits spans to the Zipkin collector service.
The `ZipkinGraphiteSink` is still work in progress but will be used to submit custom annotations with duration (to measure certain sections
of the code) to [graphite](http://graphite.wikidot.com) for visualisation.

If you use the `ZooKeeperSamplingTraceFilter` from `brave-tracefilters` module you can enable/disable tracing or adjust
sample rate using [ZooKeeper](http://zookeeper.apache.org).



## Example implementation ##

The [brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example) is a good starting point 
to get you up to speed on how you can implement brave in your own apps.

## Maven artifacts ##

Version 2.0.2 is available in Maven central. So you can simply add the dependencies you want
 to your pom.xml. You will need at least:

    
    <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-impl</artifactId>
        <version>2.0.2</version>
    </dependency>
    
For other dependencies see README.md files for sub modules.

## Contribution ##

If you would like to contribute to brave, here are some topics that might give you inspiration :wink:

* Database tracing: Hibernate / Spring JDBC template support
* ca / sa annotation support: Submitting Client Address and Server Address annotations.
* Support for other frameworks for example guice,...
* Aggregation of data. Realtime (eg Storm) or batch (Hadoop).

Feel free to submit pull requests.   

## Version history ##

Unfortunately I did not use [Semantic Versioning](http://semver.org) from the start.
The 2.x.x versions of brave are currently being extensively tested and will soon end up in 
production.  However from now on I'll try to stick to Semantic Versioning when it comes to
bug fixing /  new functionality / backwards compatibility.

### 2.1.0-SNAPSHOT ###

* [Henrik Nordvik](https://github.com/zerd): Add Jersey support. See `brave-jersey` module.
* Add `flume-zipkin-metrics` module. See README.md of that module for more information.
* [pettyjamesm](https://github.com/pettyjamesm): Added alternative Brave public api for use cases where the ThreadLocal
approach is not working and you want to have more control over the context scope.
* Also add RestEasy client support in brave-resteasy-spring module.

### 2.0.2 ###

* Done: `ZipkinSpanCollector`: Give the option to log error but not throw exception when connection with collector can't be set up during initialisation.
* Done: Make `ZipkinSpanCollector` more configurable. Allow configuration of queue size, batch size, nr of processing threads, socket time out.
* Done: Important bugfix in `ZipkinSpanCollector`. The processing thread(s) catch and log all exceptions to prevent they end prematurely.
* Done: Rename 'batchSize' config property of `ZipkinSpanCollectorSink` to 'batchsize' to bring it inline with other config parameters (all lower case).

### 2.0.1 ###

*  Done: Adapt ZipkinSpanCollector so it is able to reconnect to Scribe / Zipkin Collector in case of temporary network connectivity issues. 
*  Done: Adapt Flume ZipkinSpanCollectorSink so it is able to reconnect to Zipkin Collector in case of temporary network connectivity issues.
*  Done: BraveCallable constructor is public.

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

