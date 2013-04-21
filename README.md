# brave #


Java implementation of [Dapper](http://research.google.com/pubs/pub36356.html) and inspired by [Zipkin](https://github.com/twitter/zipkin/).

dapper (dutch) = brave (english)... so that's where the name comes from.

## introduction ##

I advise you to read the [Dapper](http://research.google.com/pubs/pub36356.html) paper, but in
short:

> What we want to achieve is understand system behavior and performance of complex distributed systems.
> We want to do this with minimal impact on existing code by introducing some small common libraries that
> are reusable and don't interfere with the existing business logic or architecture. Besides not impacting
> business logic or architecute we off course also want it to have a neglectable impact on performance.

I looked into reusing zipkin 'as is' but did not find an elegant way to use the exising Scala code/api's 
into the Java/Spring code I want to incorporate it.  I'm however very thankful to Twitter for open sourcing
Zipkin! Is is by seeing their [Zipkin video and presentation](http://www.infoq.com/presentations/Zipkin) that
I got to know Zipkin/Dapper and that I saw the potential and the simplicity of the solution.

As you can read later, brave can be integrated in Zipkin so that the cassandra back-end store
and web ui are reusable.

## about spans and traces ##

*   span: A single client/server request/response. Can have an optional parent span id and is part of a trace.
*   trace: Represents a single logical request. Can contain lots of service requests each represented as a span.


![Distributed tracing overview](https://raw.github.com/wiki/kristofa/brave/distributed_tracing.png)

The above image shows how Brave integrates with Zipkin. This is an option but not required. You can
create new SpanCollector implementations that submit Span/Trace data to other data stores or processing
engines.

## public api ##

TODO


