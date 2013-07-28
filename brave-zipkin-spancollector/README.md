# brave-zipkin-spancollector #

[Brave](https://github.com/kristofa/brave) SpanCollector that submits spans to [Zipkin](https://github.com/twitter/zipkin/) span collector.
Advantage is that you can reuse the Zipkin back-end (zipkin collector, Cassandra back-end store and zipkin web UI).

For information on how to set up the Zipkin backend components see [here](http://twitter.github.io/zipkin/install.html).

The brave zipkin spancollector can also be used as an example of how to communicate with the Zipkin Collector from Java.
It uses the Zipkin Core and ZipKin Collector Thrift generated Java classes and does not depend on Finagle.