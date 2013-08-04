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
    
Version 2.0-SNAPSHOT is not available on a central Maven repo. You can deploy the jars
in your local environment.    

## Version history ##

### 2.0-SNAPSHOT ###

Brave 1.0 was an alternative implementation of Dapper with integration with the Zipkin 
backend components.
Brave 2.0-SNAPSHOT has the Zipkin core thrift classes as part of its api. This has as consequence that it will be easier to share 
components or extensions between Zipkin/Brave. 

*   Done: Use Zipkin-core thrift generated classes as part of api.
*   Done: Binary annotation support.
*   Done: Cut dependencies with Twitter specific libraries. Only rely on Thrift.
*   Done: Rework zipkin-span-collector so it uses a separate thread with a queue in between for submitting spans to span collector / scribe. This means less overhead in applications.
*   TODO: Add TraceFilter implementations that use Zookeeper for globally adjusting sample rate or enable/disable tracing all together.
*   TODO: Add [flume](http://flume.apache.org) support for transporting spans.


### 1.0 ###

*   Functional but might not be ideal for large scale production usage. Usable for development and testing.

