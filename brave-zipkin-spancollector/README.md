# brave-zipkin-spancollector #

Latest release available in Maven central:

    <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-zipkin-spancollector</artifactId>
        <version>2.1.1</version>
    </dependency>


[Brave](https://github.com/kristofa/brave) SpanCollector that is used to submit spans to the [Zipkin](https://github.com/twitter/zipkin/) span-collector-service or Scribe
or Flume configured with Scribe source.

![Zipkin SpanCollector overview](https://raw.github.com/wiki/kristofa/brave/ZipkinSpanCollector.png)


If you use this SpanCollector you can reuse the the Zipkin back-end (zipkin-collector-service, Cassandra back-end store, zipkin-query, zipkin-web zipkin web).
For information on how to set up the Zipkin backend components see [here](http://twitter.github.io/zipkin/install.html).

The brave-zipkin-spancollector can also be used as an example of how to communicate with the Zipkin Collector from Java.
It uses the Zipkin Core and ZipKin Collector Thrift generated Java classes and does not depend on Finagle.