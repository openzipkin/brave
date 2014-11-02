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

You should use brave instead of Zipkin if:

*   You can't use [Finagle](https://github.com/twitter/finagle).
*   You don't want to add Scala as a dependency to your Java project.
*   You want out of the box integration support for [RESTEasy](http://resteasy.jboss.org), [Jersey](https://jersey.java.net), [Apache HttpClient](http://hc.apache.org/httpcomponents-client-4.3.x/index.html).

Brave uses the Zipkin thrift generated classes as part of its api so it is easy to use existing
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

The applications and services have Brave integration at client and server side, for example through the RestEasy support module.
They submit spans to the `ZipkinSpanCollector` which submits them to Flume.

I introduced [Flume](http://flume.apache.org/) instead of Scribe as Flume is still actively maintained, easier to deploy,
has good documentation and extensions are written in Java.

The `ZipkinSpanCollectorSink`, part of `flume-zipkin-collector-sink` module submits spans to the Zipkin collector service.
The `ZipkinMetricsSink`, part of `flume-zipkin-metrics-sink` module submits custom annotations with duration (to measure certain sections
of the code) to the [Metrics](http://metrics.codahale.com) library.  Metrics builds histograms for the metrics and sends the data
to a back-end system for storage and visualisation. Metrics supports multiple back-ends but the sink implementation today supports
[graphite](http://graphite.wikidot.com).

If you use the `ZooKeeperSamplingTraceFilter` from `brave-tracefilters` module you can enable/disable tracing or adjust
sample rate using [ZooKeeper](http://zookeeper.apache.org) as also indicated on the drawing.



## Example implementation ##

The [brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example) is a good starting point 
to get you up to speed on how you can implement brave in your own apps.

## Maven artifacts ##

Version 2.3 is available in Maven central. So you can simply add the dependencies you want
 to your pom.xml. You will need at least:

    
    <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-impl</artifactId>
        <version>2.3</version>
    </dependency>
    
For other dependencies see README.md files for sub modules.

## Contribution ##

If you would like to contribute to brave here are some topics that might give you inspiration :wink:

* Database tracing: Hibernate / Spring JDBC template support
* Similar as introduction of brave-client in version 2.2.1, introduce brave-server which adds an abstraction about ServerTracer which should remove code duplication and improve consistency between implementations.
* ca / sa annotation support: Submitting Client Address and Server Address annotations.
* Aggregation of data. Realtime (eg using [Storm](http://storm-project.net)).
* Extend `flume-zipkin-metrics-sink` with support for other back-ends next to graphite.
* Improve [Jersey](https://jersey.java.net) support by updating Jersey to latest version and implements `ContainerRequestFilter` and `ContainerResponseFilter` iso current servlet filter.
* Support for other frameworks.

Feel free to submit pull requests.   

## Version history ##

Unfortunately I did not use [Semantic Versioning](http://semver.org) from the start.
The 2.x.x versions of brave are currently being extensively tested and will soon end up in 
production.  However from now on I'll try to stick to Semantic Versioning when it comes to
bug fixing /  new functionality / backwards compatibility.

### 2.4-SNAPSHOT ###

* New feature: Add server side tracing support for Spring mvc based services. [42](https://github.com/kristofa/brave/pull/42)
* New feature: Add MySQL support (trace queries). [43](https://github.com/kristofa/brave/pull/43)
* Improvement: Don't enforce log4j to users of brave. Make dependency `provided`. [41](https://github.com/kristofa/brave/pull/41)
* New feature: Add Jersey 2 support [#38](https://github.com/kristofa/brave/pull/38)

### 2.3 ###

* Add basic building blocks for asynch client support. See [#30](https://github.com/kristofa/brave/pull/30).
* Bugfix for aligning service names in rest-easy implementation. See [#32](https://github.com/kristofa/brave/pull/32).
* Lower log messages from ERROR to WARN. See [#36](https://github.com/kristofa/brave/pull/36).
* Support for filtering span names with variable content. See [#33](https://github.com/kristofa/brave/pull/33).

### 2.2.1 ###

* Introduce `brave-client` module that prevents code duplication and adds consistency to different brave integration implementations. brave-client is used by all client implementations (RESTEasy, Jersey, Apache HttpClient). See [#27](https://github.com/kristofa/brave/issues/27) and [#29](https://github.com/kristofa/brave/issues/29) by [srapp](https://github.com/srapp). 
* Improve compatibility with Zipkin. Hex encode trace id's. See [#26](https://github.com/kristofa/brave/issues/26) by [klette](https://github.com/klette) and fix [#28](https://github.com/kristofa/brave/issues/28) by [srapp](https://github.com/srapp).
* Bug fix in Jersey implementation by [srapp](https://github.com/srapp) . See [#22](https://github.com/kristofa/brave/issues/22)
* Fix dependency issue. See [#20](https://github.com/kristofa/brave/issues/20) by [K-jo](https://github.com/K-Jo).

### 2.2.0 ###

* Bugfix in spring resteasy integration. Mentioned in issue [#3](https://github.com/kristofa/brave-resteasy-example/issues/3). Logic to set span name resulted in different values for client vs server side. As a result span names were not consistent in zipkin-web.
* Bugfix for `flume-zipkin-collector-sink`, see [#16](https://github.com/kristofa/brave/issues/16). Bug in Flume Zipkin SpanCollectorSink resulted in lost spans. Thanks to [leonly0224](https://github.com/leonly0224) for catching this.
* Bugfix for `flume-zipkin-metrics-sink`. Same issue as [#16](https://github.com/kristofa/brave/issues/16).
* Add `brave-apache-http-interceptors` module. Which adds Apache HttpClient support. 
* Bugfix / improvement, see issues [#15](https://github.com/kristofa/brave/issues/15), [#18](https://github.com/kristofa/brave/issues/18), [#19](https://github.com/kristofa/brave/issues/19).
Makes it possible to set Endpoint service name from ClientTracer. This means you can name a service yourself (eg when accessing cache, database,...). But it also means you can set same service name
for client and server parts of span which result in better inspection in zipkin-web (see [#18](https://github.com/kristofa/brave/issues/18) ). Thanks to [eirslett](https://github.com/eirslett) for implementing this. Both RestEasy and apache http client integration
have been adapted to make use of this.
* Test `flume-zipkin-collector-sink` and `flume-zipkin-metrics-sink` with flume 1.5.0 + update libraries to 1.5.0
* Update `flume-zipkin-collector-sink` to add connection and socket timeout config entry.

### 2.1.1 ###

* Bugfix: Make submitting annotations thread-safe. Add synchronisation to avoid ArrayIndexOutOfBoundsException or lost annotations.
* RestEasy BravePostProcessInterceptor clears span after 'server send' annotation is submitted. This prevents ThreadLocal classloader leak when used in Tomcat.
* [Ryan Tenney](https://github.com/ryantenney) : Make logger name configurable for `LoggingSpanCollectorImpl`.
* [Ryan Tenney](https://github.com/ryantenney) : Update slf4j to version 1.7.5 and use parameters in log statements iso string concatenation.

### 2.1.0 ###

* [Henrik Nordvik](https://github.com/zerd): Add Jersey support. See `brave-jersey` module.
* Add `flume-zipkin-metrics` module. See README.md of that module for more information.
* [pettyjamesm](https://github.com/pettyjamesm): Added alternative Brave public api for use cases where the ThreadLocal
approach is not working and you want to have more control over the context scope.
* Update `brave-resteasy-spring` module by adding `BraveClientExecutionInterceptor`. This interceptor can be used with the 
RestEasy client framework and makes the usage of Brave with RestEasy even easier as it now supports both server and client side.

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

