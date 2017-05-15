[![Build Status](https://circleci.com/gh/openzipkin/brave.svg?style=svg)](https://circleci.com/gh/openzipkin/brave)
[![Maven Central](https://img.shields.io/maven-central/v/io.zipkin.brave/brave.svg)](https://maven-badges.herokuapp.com/maven-central/io.zipkin.brave/brave)
[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin)

# Brave
Brave is a library used to capture latency information about distributed
operations. It reports this data to [Zipkin](http://zipkin.io) as spans.

Zipkin is based on [Dapper](http://research.google.com/pubs/pub36356.html). Dapper (dutch) = Brave (english)... So, that's where the name comes from.

## What's included

Brave's dependency-free [tracer library](brave/) works against JRE6+.
This is the underlying api that instrumentation use to time operations
and add tags that describe them. This library also includes code that
parses `X-B3-TraceId` headers.

Most users won't write tracing code directly. Rather, they reuse instrumentation
code  others have written. Check for [instrumentation written here](instrumentation/) and [Zipkin's list](http://zipkin.io/pages/existing_instrumentations.html)
before rolling your own. Common tracing libraries like JDBC, Servlet
and Spring already exist. Instrumentation written here are tested and
benchmarked.

You can look at our [example project](https://github.com/openzipkin/brave-webmvc-example) for how to trace a simple web application.

If you are trying to trace legacy applications, you may be interested in
[Spring XML Configuration](brave-spring-beans/). This allows you to setup
tracing without any custom code.

You may want to put trace IDs into your log files, or change thread local
behavior. Look at our [context libraries](context/), for integration with
tools such as SLF4J.

## Writing new instrumentation
We worked very hard to make writing new instrumentation easy and efficient.
Most of our built-in instrumentation are 50-100 lines of code, yet allow
flexible configuration of tags and sampling policy.

If you need to write new http instrumentation, check [our docs](instrumentation/http/README.md),
as this shows how to write it in a way that is least effort for you and
easy for others to configure. For example, we have a standard [test suite](instrumentation/http-tests)
you can use to make sure things interop, and standard configuration works.

If you need to do something not http, you'll want to use our [tracer library](brave/README.md).
If you are in this position, you may find our [feature tests](brave/src/test/java/brave/features)
helpful.

Regardless, you may need support along the way. Please reach out on [gitter](https://gitter.im/openzipkin/zipkin),
as there's usually others around to help.

## Artifacts
### Library Releases
Releases are uploaded to [Bintray](https://bintray.com/openzipkin/maven/brave) and synchronized to [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.zipkin.brave%22)
### Library Snapshots
Snapshots are uploaded to [JFrog](http://oss.jfrog.org/artifactory/oss-snapshot-local) after commits to master.
