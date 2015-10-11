# brave-spancollector-scribe #

SpanCollector that is used to submit spans to a Scribe compatible source.
This can be Scribe itself or the [Zipkin Collector Service](https://github.com/openzipkin/zipkin/tree/master/zipkin-collector-service)
or another Scribe compatible source like Flume configured with Scribe source.

![Scribe SpanCollector overview](https://raw.github.com/wiki/kristofa/brave/ZipkinSpanCollector.png)

The `ScribeSpanCollector` is build in such a way that it has no or very minimal impact on your application performance:

*    Submitted spans are put on an in memory queue to be processed by 1 or more threads. This means that submitting the spans to the back-end service is
asynchronous.  The number of threads that is being used is configurable.
*    The queue is a BlockingQueue with fixed capacity.  The capacity is also configurable. When the queue runs full we drop the spans and log a warning message.
This approach has again been chosen to minimize the impact on the application. Having a well functioning application is more important as having Zipkin tracing.
*    The `SpanProcessingThread` does not submit every individual span immediately to the back-end service. It buffers spans and sends them in batches as much as possible.
However it makes sure that it does not keeps holding onto spans. If the buffer is not full after 10 seconds it sends the received spans in any case.


If you use this SpanCollector you can reuse the the Zipkin back-end (zipkin-collector-service, Cassandra back-end store, zipkin-query, zipkin-web).
For information on how to set up the Zipkin backend components see [here](https://github.com/openzipkin/zipkin).
