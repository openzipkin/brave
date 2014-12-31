# brave-zipkin-spancollector #

Latest release available in Maven central:

    <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-zipkin-spancollector</artifactId>
        <version>2.4.1</version>
    </dependency>


[Brave](https://github.com/kristofa/brave) SpanCollector that is used to submit spans to the [Zipkin](https://github.com/twitter/zipkin/) span-collector-service or Scribe
or Flume configured with Scribe source.

![Zipkin SpanCollector overview](https://raw.github.com/wiki/kristofa/brave/ZipkinSpanCollector.png)

The `ZipkinSpanCollector` is build in such a way that it has no or very minimal impact on your application performance:

*    Submitted spans are put on an in memory queue to be processed by 1 or more threads. This means that submitting the spans to the back-end service is
asynchronous.  The number of threads that is being used is configurable.
*    The queue is a BlockingQueue with fixed capacity.  The capacity is also configurable. When the queue runs full we drop the spans and log a warning message.
This approach has again been chosen to minimize the impact on the application. Having a well functioning application is more important as having the Zipkin performance information.
*    The `SpanProcessingThread` does not submit every individual span immediately to the back-end service. It buffers spans and sends them in batches as much as possible.
However it makes sure that it does not keeps holding onto spans. If the buffer is not full after 10 seconds it sends the received spans in any case.


If you use this SpanCollector you can reuse the the Zipkin back-end (zipkin-collector-service, Cassandra back-end store, zipkin-query, zipkin-web zipkin web).
For information on how to set up the Zipkin backend components see [here](http://twitter.github.io/zipkin/install.html).

The brave-zipkin-spancollector can also be used as an example of how to communicate with the Zipkin Collector from Java.
It uses the Zipkin Core and ZipKin Collector Thrift generated Java classes and does not depend on Finagle.
