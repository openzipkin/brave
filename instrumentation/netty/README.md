# brave-instrumentation-netty

This module contains netty http handler.
The filters extract trace state from incoming requests. Then, they
reports Zipkin how long each request takes, along with relevant tags
like the http url. The exception handler ensures any errors are also
sent to Zipkin.

To enable tracing you need to do these:
```java
public class HttpSnoopServerInitializer extends ChannelInitializer<SocketChannel> {
  private HttpTracing httpTracing;

  public HttpSnoopServerInitializer(HttpTracing httpTracing) {
    this.httpTracing = httpTracing;
  }

  @Override
  public void initChannel(SocketChannel ch) throws Exception {
    ChannelPipeline p = ch.pipeline();

    p.addLast("decoder", new HttpRequestDecoder());
    p.addLast("encoder", new HttpResponseEncoder());
    p.addLast("aggregator", new HttpObjectAggregator(1048576));
    
    //enable tracing
    p.addLast("braveResponse", NettyTracing.create(httpTracing).createHttpResponseHandler());
    p.addLast("braveRequest", NettyTracing.create(httpTracing).createHttpRequestHandler());

    p.addLast("handler", new HttpSnoopServerHandler(httpTracing));
    //p.addLast("exception", new ExceptionHandler());//Make sure this is the last line when init the pipeline.
  }
}

```
