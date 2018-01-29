# brave-instrumentation-netty-codec-http

This module contains a tracing decorators for [Netty's Http Codec](https://github.com/netty/netty/tree/4.1/codec-http) 4.x.

`NettyHttpTracing.serverHandler()` extracts trace state from incoming requests,
and reports to Zipkin how long each take, along with relevant tags like the
http url.

## Configuration

To enable tracing for an http server you need to add it to your pipeline:
```java
NettyHttpTracing nettyHttpTracing = NettyHttpTracing.create(httpTracing);
ChannelPipeline pipeline = ch.pipeline();
... add your infrastructure handlers, in particular HttpRequestDecoder and HttpResponseEncoder
pipeline.addLast("tracing", nettyHttpTracing.serverHandler());
... add your application handlers
```
