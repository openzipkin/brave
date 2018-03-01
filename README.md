[![Build Status](https://travis-ci.org/openzipkin/brave.svg?branch=master)](https://travis-ci.org/openzipkin/brave)
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
[Spring XML Configuration](spring-beans/). This allows you to setup
tracing without any custom code.

You may want to put trace IDs into your log files, or change thread local
behavior. Look at our [context libraries](context/), for integration with
tools such as SLF4J.

## Artifacts
All artifacts publish to the group ID "io.zipkin.brave". We use a common
release version for all components.
### Library Releases
Releases are uploaded to [Bintray](https://bintray.com/openzipkin/maven/brave) and synchronized to [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.zipkin.brave%22)
### Library Snapshots
Snapshots are uploaded to [JFrog](http://oss.jfrog.org/artifactory/oss-snapshot-local) after commits to master.
### Version alignments
When using multiple brave components, you'll want to align versions in
one place. This allows you to more safely upgrade, with less worry about
conflicts.

You can use our Maven instrumentation BOM (Bill of Materials) for this:

Ex. in your dependencies section, import the BOM like this:
```xml
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.zipkin.brave</groupId>
        <artifactId>brave-bom</artifactId>
        <version>${brave.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
```

Now, you can leave off the version when choosing any supported
instrumentation. Also any indirect use will have versions aligned:
```xml
<dependency>
  <groupId>io.zipkin.brave</groupId>
  <artifactId>brave-instrumentation-okhttp3</artifactId>
</dependency>
```

A great example of this in practice is using a newer version of Brave
than what's packaged in [Spring Cloud Sleuth](https://github.com/spring-cloud/spring-cloud-sleuth). However, you should take
care not to accidentally use an earlier version. If you hard-assign a
version of Brave and also use an umbrella project like Sleuth, always
double check that your Brave version is valid (equal to or later) when
updating the version of the umbrella project.
