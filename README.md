# brave #

[![Build Status](https://travis-ci.org/openzipkin/brave.svg?branch=master)](https://travis-ci.org/openzipkin/brave)
[![Maven Central](https://img.shields.io/maven-central/v/io.zipkin.brave/brave.svg)](https://maven-badges.herokuapp.com/maven-central/io.zipkin.brave/brave)

Java Distributed Tracing implementation compatible with [Zipkin](http://zipkin.io).

Zipkin is based on [Dapper](http://research.google.com/pubs/pub36356.html).

dapper (dutch) = brave (english)... so that's where the name comes from.

## introduction ##

More information on Distributed Tracing and OpenZipkin here: <https://openzipkin.github.io>

You can use brave if you use the JVM and:

*   You can't use [Finagle](https://github.com/twitter/finagle).
*   You don't want to add Scala as a dependency to your Java project.
*   You want out of the box integration support for [RESTEasy](http://resteasy.jboss.org), [Jersey](https://jersey.java.net), [Apache HttpClient](http://hc.apache.org/httpcomponents-client-4.3.x/index.html).

Brave is compatible with OpenZipkin backends such as [zipkin-server](https://github.com/openzipkin/zipkin/blob/master/zipkin-server/README.md)

A deep dive on Brave's api can be found [here](brave/README.md)

## Maven artifacts ##

Maven artifacts for each release are published to Maven Central. 

## Changelog ##

For an overview of the available releases see [Github releases](https://github.com/kristofa/brave/releases).
As of release 2.0 we try to stick to [Semantic Versioning](http://semver.org).

Brave was redesigned starting with version 4. Please see the
[README](brave/README.md) for details on how to use Brave's Tracer.
