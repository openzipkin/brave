[![Build Status](https://circleci.com/gh/openzipkin/brave.svg?style=svg)](https://circleci.com/gh/openzipkin/brave)
[![Maven Central](https://img.shields.io/maven-central/v/io.zipkin.brave/brave.svg)](https://maven-badges.herokuapp.com/maven-central/io.zipkin.brave/brave)
[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin)

# Brave
Brave is a library used to capture latency information about distributed
operations. It reports this data to [Zipkin](http://zipkin.io) as spans.

Zipkin is based on [Dapper](http://research.google.com/pubs/pub36356.html). Dapper (dutch) = Brave (english)... So, that's where the name comes from.

You can look at our [example project](https://github.com/openzipkin/brave-webmvc-example) for how to trace a simple web application.

## What's included

Brave's dependency-free [tracer library](brave/) works against JRE6+.
This is the underlying api that instrumentation use to time operations
and add tags that describe them. This library also includes code that
parses `X-B3-TraceId` headers.

Most users won't write tracing code directly. Rather, they reuse instrumentation
others have written. Check our [instrumentation](instrumentation/) and [Zipkin's list](http://zipkin.io/pages/existing_instrumentations.html)
before rolling your own. Common tracing libraries like JDBC, Servlet
and Spring already exist. Instrumentation written here are tested and
benchmarked.

If you are trying to trace legacy applications, you may be interested in
[Spring XML Configuration](brave-spring-beans/). This allows you to setup
tracing without any custom code.

You may want to put trace IDs into your log files, or change thread local
behavior. Look at our [context libraries](context/), for integration with
tools such as SLF4J.

## Artifacts
### Library Releases
Releases are uploaded to [Bintray](https://bintray.com/openzipkin/maven/brave) and synchronized to [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.zipkin.brave%22)
### Library Snapshots
Snapshots are uploaded to [JFrog](http://oss.jfrog.org/artifactory/oss-snapshot-local) after commits to master.
