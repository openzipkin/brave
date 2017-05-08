# brave-instrumentation-okhttp3
This module contains a tracing decorator for [OkHttp](https://github.com/square/okhttp) 3.x.
`TracingCallFactory` adds trace headers to outgoing requests. It
then reports to Zipkin how long each request takes, along with relevant
tags like the http url.

To enable tracing, wrap your client using `TracingCallFactory`.

```java
callFactory = TracingCallFactory.create(tracing, okhttp);
```
