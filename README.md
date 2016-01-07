# brave #

[![Build Status](https://travis-ci.org/openzipkin/brave.svg?branch=master)](https://travis-ci.org/openzipkin/brave)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.kristofa/brave.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.kristofa/brave)

**Brave moved to OpenZipkin.** [Read more about it here](http://kdevlog.blogspot.de/2015/07/brave-moved-to-openzipkin-org.html).

Java distributed tracing implementation compatible with [Zipkin](https://github.com/twitter/zipkin/).

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

The `ZipkinSpanCollectorSink`, part of [flume-zipkin-collector-sink](http://github.com/kristofa/flume-zipkin-collector-sink) repo submits spans to the Zipkin collector service.
The `ZipkinMetricsSink`, part of [flume-zipkin-metrics-sink](http://github.com/kristofa/flume-zipkin-metrics-sink) module submits custom annotations with duration (to measure certain sections
of the code) to the [Metrics](http://metrics.codahale.com) library.  Metrics builds histograms for the metrics and sends the data
to a back-end system for storage and visualisation. Metrics supports multiple back-ends but the sink implementation today supports
[graphite](http://graphite.wikidot.com).

If you use the `ZooKeeperTraceSampler` from `brave-tracesampler-zookeeper` module you can enable/disable tracing or adjust
sample rate using [ZooKeeper](http://zookeeper.apache.org) as also indicated on the drawing.


## Example implementation ##

The [brave-resteasy-example](https://github.com/kristofa/brave-resteasy-example) is a good starting point 
to get you up to speed on how you can implement brave in your own apps.

## Maven artifacts ##

Maven artifacts for each release are published to Maven Central. 

## Changelog ##

For an overview of the available releases see [Github releases](https://github.com/kristofa/brave/releases).
As of release 2.0 we try to stick to [Semantic Versioning](http://semver.org).
